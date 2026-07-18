"""Tests for the egress DNS proxy (RFC-0006 §3 layer 2).

The proxy ships inside the egress-proxy image rather than the daemon package, so it
is loaded here by path. It is worth testing outside the container because its two
jobs — deciding what may resolve, and refusing to pin an answer that points somewhere
restricted — are exactly where a mistake silently reopens egress.
"""

import importlib.util
import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

_PROXY_PATH = Path(__file__).resolve().parents[1] / "docker" / "egress-dns-proxy.py"


def _load_proxy():
    spec = importlib.util.spec_from_file_location("egress_dns_proxy", _PROXY_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


proxy = _load_proxy()


class TestPolicyMatching:
    def _policy(self, **kw):
        return proxy.Policy({"defaultAction": "deny", "allowDomains": [], "denyDomains": [], **kw})

    def test_default_is_deny(self):
        assert self._policy().allows("anything.example") is False

    def test_exact_allow(self):
        p = self._policy(allowDomains=["api.anthropic.com"])
        assert p.allows("api.anthropic.com") is True
        assert p.allows("api.openai.com") is False

    def test_wildcard_allows_subdomains(self):
        p = self._policy(allowDomains=["*.pypi.org"])
        assert p.allows("files.pypi.org") is True

    def test_wildcard_also_allows_the_bare_domain(self):
        """`*.pypi.org` in a policy means pypi.org and everything under it — the
        iptables path stripped the `*.` before resolving, so this matches intent."""
        p = self._policy(allowDomains=["*.pypi.org"])
        assert p.allows("pypi.org") is True

    def test_allow_entry_covers_subdomains(self):
        p = self._policy(allowDomains=["github.com"])
        assert p.allows("codeload.github.com") is True

    def test_lookalike_domain_is_not_allowed(self):
        """The suffix check must not match `evil-github.com` or `github.com.evil.io`."""
        p = self._policy(allowDomains=["github.com"])
        assert p.allows("evil-github.com") is False
        assert p.allows("github.com.evil.io") is False

    def test_deny_beats_allow(self):
        p = self._policy(allowDomains=["*.example"], denyDomains=["bad.example"])
        assert p.allows("good.example") is True
        assert p.allows("bad.example") is False

    def test_trailing_dot_and_case_are_normalised(self):
        p = self._policy(allowDomains=["api.anthropic.com"])
        assert p.allows("API.Anthropic.Com.") is True


class TestRestrictedAddresses:
    """An allowlisted name resolving into these ranges is rebinding, not a CDN."""

    @pytest.mark.parametrize(
        "addr",
        [
            "169.254.169.254",  # AWS/GCP/Azure IMDS
            "169.254.170.2",  # ECS task credentials
            "127.0.0.1",
            "10.1.2.3",
            "192.168.1.1",
            "172.16.0.1",
            "100.64.0.1",  # CGNAT
            "::1",
            "fe80::1",
            "fd00::1",
        ],
    )
    def test_restricted(self, addr):
        assert proxy.is_restricted_addr(addr) is True

    @pytest.mark.parametrize("addr", ["8.8.8.8", "160.79.104.10", "2606:4700::1111"])
    def test_public_addresses_are_pinnable(self, addr):
        assert proxy.is_restricted_addr(addr) is False

    def test_ipv4_mapped_ipv6_metadata_is_caught(self):
        """The bypass a naive v6 check waves through: same endpoint, different spelling."""
        assert proxy.is_restricted_addr("::ffff:169.254.169.254") is True

    def test_unparseable_is_refused(self):
        assert proxy.is_restricted_addr("not-an-ip") is True


class TestResolverBehaviour:
    def _resolver(self, policy_cfg, pinner=None):
        policy = proxy.Policy(policy_cfg)
        pinner = pinner or MagicMock(pin=MagicMock(return_value=True))
        return proxy.Resolver(policy, pinner, [("8.8.8.8", 53)]), pinner

    @staticmethod
    def _query(name="api.anthropic.com", qtype="A"):
        from dnslib import DNSRecord

        return DNSRecord.question(name, qtype).pack()

    @staticmethod
    def _answer(name, addr, qtype="A"):
        from dnslib import AAAA, RR, A, DNSRecord

        record = DNSRecord.question(name, qtype).reply()
        rdata = AAAA(addr) if qtype == "AAAA" else A(addr)
        record.add_answer(RR(name, getattr(__import__("dnslib").QTYPE, qtype), rdata=rdata, ttl=60))
        return record.pack()

    def test_denied_name_is_never_forwarded_upstream(self):
        """The exfiltration path: the query itself carries the data, so it must not
        leave the namespace at all — NXDOMAIN without asking anyone."""
        from dnslib import RCODE, DNSRecord

        resolver, _ = self._resolver(
            {"defaultAction": "deny", "allowDomains": ["api.anthropic.com"], "denyDomains": []}
        )
        with patch.object(resolver, "_forward") as forward:
            reply = resolver.handle(self._query("secret-data.attacker.example"))

        forward.assert_not_called()
        assert DNSRecord.parse(reply).header.rcode == RCODE.NXDOMAIN

    def test_allowed_name_is_resolved_and_pinned(self):
        resolver, pinner = self._resolver(
            {"defaultAction": "deny", "allowDomains": ["api.anthropic.com"], "denyDomains": []}
        )
        upstream = self._answer("api.anthropic.com", "160.79.104.10")
        with patch.object(resolver, "_forward", return_value=upstream):
            reply = resolver.handle(self._query("api.anthropic.com"))

        assert reply == upstream
        # Pinned with the ports the allow rule permits (default http/https here).
        pinner.pin.assert_called_once()
        addr_arg, ports_arg = pinner.pin.call_args.args
        assert addr_arg == "160.79.104.10"
        assert set(ports_arg) == {80, 443}

    def test_allowed_name_resolving_to_metadata_is_not_pinned(self):
        """DNS rebinding: the name passes the allowlist, the address must not pass."""
        resolver, pinner = self._resolver(
            {"defaultAction": "deny", "allowDomains": ["rebind.example"], "denyDomains": []}
        )
        upstream = self._answer("rebind.example", "169.254.169.254")
        with patch.object(resolver, "_forward", return_value=upstream):
            resolver.handle(self._query("rebind.example"))

        pinner.pin.assert_not_called()

    def test_upstream_failure_servfails_rather_than_opening_up(self):
        from dnslib import RCODE, DNSRecord

        resolver, _ = self._resolver(
            {"defaultAction": "deny", "allowDomains": ["api.anthropic.com"], "denyDomains": []}
        )
        with patch.object(resolver, "_forward", return_value=None):
            reply = resolver.handle(self._query("api.anthropic.com"))

        assert DNSRecord.parse(reply).header.rcode == RCODE.SERVFAIL

    def test_malformed_query_is_dropped_not_crashed_on(self):
        resolver, _ = self._resolver({"defaultAction": "deny", "allowDomains": [], "denyDomains": []})
        assert resolver.handle(b"\x00\x01garbage") == b""


class TestPinnerUsesIpset:
    def test_pin_adds_ip_port_entries_with_a_timeout(self):
        pinner = proxy.IpSetPinner("squadx_allow4", "squadx_allow6", 3600)
        with patch.object(proxy.subprocess, "run") as run:
            run.return_value = MagicMock(returncode=0, stderr="")
            assert pinner.pin("8.8.8.8", [443]) is True
        argv = run.call_args.args[0]
        # hash:ip,port entry, not a bare ip — this is what restores per-port enforcement.
        assert argv[:4] == ["ipset", "add", "squadx_allow4", "8.8.8.8,tcp:443"]
        assert "timeout" in argv and "3600" in argv

    def test_pin_adds_one_entry_per_port(self):
        pinner = proxy.IpSetPinner("squadx_allow4", "squadx_allow6", 60)
        with patch.object(proxy.subprocess, "run") as run:
            run.return_value = MagicMock(returncode=0, stderr="")
            pinner.pin("1.2.3.4", [80, 443, 22])
        entries = [call.args[0][3] for call in run.call_args_list]
        assert entries == ["1.2.3.4,tcp:80", "1.2.3.4,tcp:443", "1.2.3.4,tcp:22"]

    def test_v6_addresses_go_to_the_v6_set(self):
        pinner = proxy.IpSetPinner("squadx_allow4", "squadx_allow6", 60)
        with patch.object(proxy.subprocess, "run") as run:
            run.return_value = MagicMock(returncode=0, stderr="")
            pinner.pin("2606:4700::1111", [443])
        assert run.call_args.args[0][2] == "squadx_allow6"
        assert run.call_args.args[0][3] == "2606:4700::1111,tcp:443"

    def test_ipset_failure_is_reported_not_swallowed(self):
        pinner = proxy.IpSetPinner("squadx_allow4", "squadx_allow6", 60)
        with patch.object(proxy.subprocess, "run") as run:
            run.return_value = MagicMock(returncode=1, stderr="set not found")
            assert pinner.pin("8.8.8.8", [443]) is False


class TestPolicyPorts:
    def test_ports_come_from_the_matching_allow_entry(self):
        policy = proxy.Policy(
            {"defaultAction": "deny",
             "allowDomains": [{"pattern": "github.com", "ports": [80, 443, 22]}],
             "denyDomains": []}
        )
        assert policy.ports_for("github.com") == {80, 443, 22}
        assert policy.ports_for("codeload.github.com") == {80, 443, 22}

    def test_bare_string_entry_takes_default_ports(self):
        policy = proxy.Policy(
            {"defaultAction": "deny", "allowDomains": ["api.anthropic.com"], "denyDomains": []}
        )
        assert policy.ports_for("api.anthropic.com") == {80, 443}

    def test_denied_name_has_no_ports(self):
        policy = proxy.Policy(
            {"defaultAction": "deny", "allowDomains": [], "denyDomains": ["x.example"]}
        )
        assert policy.ports_for("x.example") == set()


def test_policy_config_from_sidecar_config_is_consumable_by_the_proxy():
    """The daemon writes this JSON; the proxy reads it. Keep the two in step."""
    from squadx_client.docker.network_policy import POLICY_AGENT_DEFAULT, EgressSidecarConfig

    cfg = EgressSidecarConfig(policy=POLICY_AGENT_DEFAULT).to_dns_config()
    policy = proxy.Policy(json.loads(json.dumps(cfg)))

    assert policy.allows("api.anthropic.com") is True
    assert policy.allows("files.pypi.org") is True
    assert policy.allows("metadata.google.internal") is False
    assert policy.allows("attacker.example") is False
