"""Tests for network policy enforcement module."""

import pytest

from squadx_client.docker.network_policy import (
    EgressAction,
    EgressRule,
    EgressSidecarConfig,
    NetworkPolicy,
    POLICY_FULL_ACCESS,
    POLICY_NONE,
    POLICY_PACKAGE_MANAGERS,
    _is_ip_or_cidr,
    generate_network_setup_script,
    get_predefined_policy,
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


class TestGenerateNetworkSetupScript:
    """Test generate_network_setup_script for allow-default and deny-default."""

    def test_deny_default_sets_output_drop(self):
        script = generate_network_setup_script(POLICY_NONE)
        assert "iptables -P OUTPUT DROP" in script
        assert "#!/bin/sh" in script

    def test_deny_default_allows_loopback_and_dns(self):
        script = generate_network_setup_script(POLICY_NONE)
        assert "-o lo -j ACCEPT" in script
        assert "--dport 53 -j ACCEPT" in script

    def test_allow_default_blocks_ip_targets(self):
        script = generate_network_setup_script(POLICY_FULL_ACCESS)
        assert "169.254.169.254" in script
        assert "-j DROP" in script
        # Should NOT set default OUTPUT to DROP
        assert "iptables -P OUTPUT DROP" not in script

    def test_deny_default_with_domain_rules_uses_dig(self):
        policy = NetworkPolicy(
            default_action=EgressAction.DENY,
            rules=[EgressRule(EgressAction.ALLOW, "*.pypi.org", [443])],
        )
        script = generate_network_setup_script(policy)
        assert "dig +short" in script
        assert "--dport 443 -j ACCEPT" in script


class TestEgressSidecarConfig:
    """Test EgressSidecarConfig iptables rules and DNS config generation."""

    def test_to_iptables_rules_redirects_dns(self):
        config = EgressSidecarConfig()
        rules = config.to_iptables_rules()
        # Must redirect UDP and TCP port 53 to local proxy
        assert any("--dport 53" in r and "-p udp" in r for r in rules)
        assert any("--dport 53" in r and "-p tcp" in r for r in rules)
        # Must block DNS-over-TLS
        assert any("--dport 853" in r and "DROP" in r for r in rules)

    def test_to_iptables_rules_blocks_deny_ips(self):
        policy = NetworkPolicy(
            default_action=EgressAction.ALLOW,
            rules=[EgressRule(EgressAction.DENY, "169.254.169.254")],
        )
        config = EgressSidecarConfig(policy=policy)
        rules = config.to_iptables_rules()
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
        # IPs should not appear in domain lists
        assert "10.0.0.1" not in dns["allowDomains"]
        assert "10.0.0.1" not in dns["denyDomains"]
        assert dns["listenPort"] == 15353
        assert dns["upstreamDNS"] == "8.8.8.8:53"
