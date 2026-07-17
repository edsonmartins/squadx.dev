"""Tests for network policy enforcement module."""

import pytest

from squadx_client.docker.network_policy import (
    EgressAction,
    EgressRule,
    EgressSidecarConfig,
    NetworkPolicy,
    POLICY_AGENT_DEFAULT,
    POLICY_FULL_ACCESS,
    POLICY_NONE,
    POLICY_PACKAGE_MANAGERS,
    _is_ip_or_cidr,
    generate_sidecar_setup_script,
    get_predefined_policy,
    policy_name_from_backend,
)


class TestEgressRule:
    """Test EgressRule dataclass creation."""

    def test_basic_creation(self):
        rule = EgressRule(EgressAction.ALLOW, "*.pypi.org")
        assert rule.action == EgressAction.ALLOW
        assert rule.target == "*.pypi.org"
        assert rule.ports == [80, 443]
        assert rule.description == ""

    def test_custom_ports_and_description(self):
        rule = EgressRule(EgressAction.DENY, "10.0.0.0/8", [22, 8080], "internal net")
        assert rule.ports == [22, 8080]
        assert rule.description == "internal net"


class TestNetworkPolicy:
    """Test NetworkPolicy to_dict / from_dict round-trip and defaults."""

    def test_default_action_is_deny(self):
        policy = NetworkPolicy()
        assert policy.default_action == EgressAction.DENY
        assert policy.rules == []

    def test_to_dict(self):
        policy = NetworkPolicy(
            default_action=EgressAction.ALLOW,
            rules=[EgressRule(EgressAction.DENY, "169.254.169.254", [80])],
        )
        d = policy.to_dict()
        assert d["defaultAction"] == "allow"
        assert len(d["egress"]) == 1
        assert d["egress"][0]["target"] == "169.254.169.254"
        assert d["egress"][0]["ports"] == [80]

    def test_from_dict(self):
        data = {
            "defaultAction": "allow",
            "egress": [
                {"action": "deny", "target": "10.0.0.0/8", "ports": [443]},
                {"action": "allow", "target": "example.com"},
            ],
        }
        policy = NetworkPolicy.from_dict(data)
        assert policy.default_action == EgressAction.ALLOW
        assert len(policy.rules) == 2
        assert policy.rules[0].ports == [443]
        # Missing ports falls back to default [80, 443]
        assert policy.rules[1].ports == [80, 443]

    def test_round_trip(self):
        original = NetworkPolicy(
            default_action=EgressAction.DENY,
            rules=[
                EgressRule(EgressAction.ALLOW, "pypi.org", [443]),
                EgressRule(EgressAction.DENY, "192.168.0.0/16"),
            ],
        )
        restored = NetworkPolicy.from_dict(original.to_dict())
        assert restored.default_action == original.default_action
        assert len(restored.rules) == len(original.rules)
        for r, o in zip(restored.rules, original.rules):
            assert r.action == o.action
            assert r.target == o.target
            assert r.ports == o.ports


class TestPredefinedPolicies:
    """Test predefined policy objects and get_predefined_policy lookup."""

    def test_policy_none_denies_all(self):
        assert POLICY_NONE.default_action == EgressAction.DENY
        assert POLICY_NONE.rules == []

    def test_policy_package_managers_has_pypi(self):
        targets = [r.target for r in POLICY_PACKAGE_MANAGERS.rules]
        assert "*.pypi.org" in targets
        assert "registry.npmjs.org" in targets

    def test_policy_full_access_allows_by_default(self):
        assert POLICY_FULL_ACCESS.default_action == EgressAction.ALLOW
        # Blocks cloud metadata
        targets = [r.target for r in POLICY_FULL_ACCESS.rules]
        assert "169.254.169.254" in targets

    def test_get_predefined_policy_valid(self):
        assert get_predefined_policy("none") is POLICY_NONE
        assert get_predefined_policy("package-managers") is POLICY_PACKAGE_MANAGERS
        assert get_predefined_policy("full") is POLICY_FULL_ACCESS

    def test_get_predefined_policy_invalid_raises(self):
        with pytest.raises(ValueError, match="Unknown policy"):
            get_predefined_policy("nonexistent")


class TestIsIpOrCidr:
    """Test _is_ip_or_cidr helper."""

    def test_plain_ip(self):
        assert _is_ip_or_cidr("192.168.1.1") is True

    def test_cidr(self):
        assert _is_ip_or_cidr("10.0.0.0/8") is True

    def test_domain_name(self):
        assert _is_ip_or_cidr("pypi.org") is False

    def test_wildcard_domain(self):
        assert _is_ip_or_cidr("*.github.com") is False


class TestEgressSidecarConfig:
    """Test EgressSidecarConfig iptables rules and DNS config generation."""

    def test_ip_rules_block_deny_ips(self):
        policy = NetworkPolicy(
            default_action=EgressAction.ALLOW,
            rules=[EgressRule(EgressAction.DENY, "169.254.169.254")],
        )
        rules = EgressSidecarConfig(policy=policy).ip_rules()
        assert any("169.254.169.254" in r and "DROP" in r for r in rules)

    def test_to_dns_config_separates_allow_deny_domains(self):
        policy = NetworkPolicy(
            default_action=EgressAction.DENY,
            rules=[
                EgressRule(EgressAction.ALLOW, "pypi.org"),
                EgressRule(EgressAction.DENY, "evil.com"),
                EgressRule(EgressAction.DENY, "10.0.0.1"),  # IP, not domain
            ],
        )
        config = EgressSidecarConfig(policy=policy)
        dns = config.to_dns_config()
        assert dns["defaultAction"] == "deny"
        assert "pypi.org" in dns["allowDomains"]
        assert "evil.com" in dns["denyDomains"]
        # IPs are enforced by iptables directly; they are not names to resolve.
        assert "10.0.0.1" not in dns["allowDomains"]
        assert "10.0.0.1" not in dns["denyDomains"]
        assert dns["listenPort"] == 15353


class TestGenerateSidecarSetupScript:
    """The DNS-proxy topology (RFC-0006 §3 layer 2)."""

    def _script(self, policy=None):
        return generate_sidecar_setup_script(
            EgressSidecarConfig(policy=policy or POLICY_AGENT_DEFAULT)
        )

    def test_agent_dns_is_redirected_into_the_proxy(self):
        script = self._script()
        assert "REDIRECT --to-ports 15353" in script
        # Both transports, or the agent just asks over TCP.
        assert script.count("REDIRECT --to-ports 15353") == 2

    def test_only_the_proxy_may_reach_upstream_dns(self):
        """The netns is shared, so uid is the only thing separating the two."""
        script = self._script()
        assert "-m owner --uid-owner 0 -p udp --dport 53 -j ACCEPT" in script
        assert "-m owner ! --uid-owner 0" in script

    def test_udp_53_is_not_simply_open(self):
        """The regression this whole layer exists for: a blanket DNS ACCEPT made the
        allowlist decorative, since queries themselves carry data out."""
        assert "-p udp --dport 53 -j ACCEPT" not in self._script().replace(
            "-m owner --uid-owner 0 -p udp --dport 53 -j ACCEPT", ""
        )

    def test_encrypted_dns_is_dropped(self):
        script = self._script()
        assert "--dport 853 -j DROP" in script

    def test_allowlist_is_the_ipset_the_proxy_pins_into(self):
        script = self._script()
        assert "-m set --match-set squadx_allow4 dst -j ACCEPT" in script
        assert "-m set --match-set squadx_allow6 dst -j ACCEPT" in script

    def test_no_dig_resolution_remains(self):
        """One-shot resolution went stale the moment a CDN rotated addresses."""
        assert "dig +short" not in self._script()

    def test_proxy_starts_before_dns_is_redirected_at_it(self):
        script = self._script()
        assert script.index("egress-dns-proxy.py --config") < script.index("REDIRECT")

    def test_default_drop_precedes_the_flush(self):
        script = self._script()
        assert script.index("iptables -P OUTPUT DROP") < script.index("iptables -F OUTPUT")

    def test_recycled_sidecar_inherits_no_addresses_or_policy(self):
        script = self._script()
        assert "ipset flush squadx_allow4" in script
        assert "pkill -f egress-dns-proxy.py" in script

    def test_policy_json_is_embedded_for_the_proxy(self):
        script = self._script()
        assert "/etc/squadx/egress-policy.json" in script
        assert '"api.anthropic.com"' in script
        assert '"defaultAction":"deny"' in script


class TestBackendPolicyContract:
    """The backend dispatches `sandbox_egress_policy`; this maps it onto a preset.

    Installed daemons outlive any given backend, so an unknown or missing value must
    downgrade to the local default — never crash, and never mean "no policy".
    """

    def test_known_enum_names_map_to_presets(self):
        assert policy_name_from_backend("AGENT_DEFAULT") == "agent-default"
        assert policy_name_from_backend("DENY_ALL") == "deny-all"
        assert policy_name_from_backend("FULL") == "full"

    def test_mapped_names_are_all_resolvable_presets(self):
        """Guards the two sides drifting apart: every mapping must name a real preset."""
        for enum_name in ("AGENT_DEFAULT", "DENY_ALL", "FULL"):
            assert get_predefined_policy(policy_name_from_backend(enum_name)) is not None

    def test_absent_value_defers_to_the_daemon_default(self):
        assert policy_name_from_backend(None) is None
        assert policy_name_from_backend("") is None

    def test_unknown_value_downgrades_rather_than_raising(self):
        """A backend newer than this daemon will send names it has never heard of."""
        assert policy_name_from_backend("SOME_FUTURE_POLICY") is None

    def test_value_is_normalised(self):
        assert policy_name_from_backend("  agent_default  ") == "agent-default"
