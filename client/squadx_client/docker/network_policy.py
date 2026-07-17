"""Network policy enforcement for sandboxed containers (RFC-0006 / ADR-0008).

Egress is default-deny with a domain allowlist, enforced in the egress sidecar: the
agent is cap-drop ALL and shares the sidecar's netns, so it can neither program nor
undo these rules. Domain allowlisting is done by a DNS proxy that is the only resolver
the agent can reach — see ``generate_sidecar_setup_script`` and docker/egress-dns-proxy.py.
"""
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional
import json
import logging

logger = logging.getLogger(__name__)

class EgressAction(str, Enum):
    ALLOW = "allow"
    DENY = "deny"

@dataclass
class EgressRule:
    action: EgressAction
    target: str  # domain pattern (e.g., "*.pypi.org", "api.github.com")
    ports: list[int] = field(default_factory=lambda: [80, 443])
    description: str = ""

@dataclass
class NetworkPolicy:
    """Network policy for sandbox egress control."""
    default_action: EgressAction = EgressAction.DENY
    rules: list[EgressRule] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "defaultAction": self.default_action.value,
            "egress": [
                {"action": r.action.value, "target": r.target, "ports": r.ports}
                for r in self.rules
            ]
        }

    @classmethod
    def from_dict(cls, data: dict) -> "NetworkPolicy":
        rules = [
            EgressRule(
                action=EgressAction(r["action"]),
                target=r["target"],
                ports=r.get("ports", [80, 443]),
                description=r.get("description", "")
            )
            for r in data.get("egress", [])
        ]
        return cls(
            default_action=EgressAction(data.get("defaultAction", "deny")),
            rules=rules
        )

# Predefined policies for common use cases
POLICY_NONE = NetworkPolicy(default_action=EgressAction.DENY, rules=[])

POLICY_PACKAGE_MANAGERS = NetworkPolicy(
    default_action=EgressAction.DENY,
    rules=[
        EgressRule(EgressAction.ALLOW, "*.pypi.org", description="Python packages"),
        EgressRule(EgressAction.ALLOW, "files.pythonhosted.org", description="PyPI files"),
        EgressRule(EgressAction.ALLOW, "registry.npmjs.org", description="npm packages"),
        EgressRule(EgressAction.ALLOW, "registry.yarnpkg.com", description="Yarn packages"),
        EgressRule(EgressAction.ALLOW, "repo1.maven.org", description="Maven Central"),
        EgressRule(EgressAction.ALLOW, "plugins.gradle.org", description="Gradle plugins"),
        EgressRule(EgressAction.ALLOW, "*.githubusercontent.com", description="GitHub raw"),
        EgressRule(EgressAction.ALLOW, "github.com", [80, 443, 22], description="GitHub"),
    ]
)

POLICY_FULL_ACCESS = NetworkPolicy(
    default_action=EgressAction.ALLOW,
    rules=[
        EgressRule(EgressAction.DENY, "169.254.169.254", description="Block cloud metadata"),
        EgressRule(EgressAction.DENY, "metadata.google.internal", description="Block GCP metadata"),
    ]
)

# Cloud instance-metadata endpoints, denied under every policy (SSRF -> credentials).
# Mirrors egress_guard.CLOUD_METADATA_TARGETS (host-side Phase 0); kept here so the
# sidecar (Phase 1, RFC-0006) enforces it too, as defense in depth.
_METADATA_DENY_RULES = [
    EgressRule(EgressAction.DENY, "169.254.169.254", description="Cloud metadata (IMDS)"),
    EgressRule(EgressAction.DENY, "169.254.170.2", description="AWS ECS task credentials"),
    EgressRule(EgressAction.DENY, "metadata.google.internal", description="GCP metadata"),
]

# The sane production default (ADR-0008 / RFC-0006): default-deny with an allowlist
# that actually lets agents work — LLM providers + package registries + git — while
# blocking metadata. Unlike POLICY_NONE (deny-all, breaks everything) and
# POLICY_PACKAGE_MANAGERS (no LLM access), this is usable as the standing default.
POLICY_AGENT_DEFAULT = NetworkPolicy(
    default_action=EgressAction.DENY,
    rules=[
        EgressRule(EgressAction.ALLOW, "api.anthropic.com", description="Anthropic API"),
        EgressRule(EgressAction.ALLOW, "api.openai.com", description="OpenAI API"),
        EgressRule(EgressAction.ALLOW, "generativelanguage.googleapis.com", description="Google AI"),
        EgressRule(EgressAction.ALLOW, "github.com", [80, 443, 22], description="GitHub"),
        EgressRule(EgressAction.ALLOW, "*.githubusercontent.com", description="GitHub raw"),
        EgressRule(EgressAction.ALLOW, "*.pypi.org", description="Python packages"),
        EgressRule(EgressAction.ALLOW, "files.pythonhosted.org", description="PyPI files"),
        EgressRule(EgressAction.ALLOW, "registry.npmjs.org", description="npm packages"),
        EgressRule(EgressAction.ALLOW, "repo1.maven.org", description="Maven Central"),
        *_METADATA_DENY_RULES,
    ],
)

def get_predefined_policy(name: str) -> NetworkPolicy:
    """Get a predefined network policy by name.

    ``agent-default`` (recommended) is default-deny + a working allowlist. ``deny-all``
    blocks everything; ``full`` allows everything except metadata (debugging only). The
    legacy ``none``/``package-managers`` names are deprecated (misleading: ``none`` is
    deny-all, ``package-managers`` omits LLM APIs) — kept only for back-compat.
    """
    policies = {
        "agent-default": POLICY_AGENT_DEFAULT,
        "deny-all": POLICY_NONE,
        "full": POLICY_FULL_ACCESS,
        # deprecated aliases
        "none": POLICY_NONE,
        "package-managers": POLICY_PACKAGE_MANAGERS,
    }
    policy = policies.get(name)
    if policy is None:
        raise ValueError(f"Unknown policy: {name}. Available: {list(policies.keys())}")
    return policy


@dataclass
class EgressSidecarConfig:
    """Configuration for the egress control sidecar container.

    ``proxy_uid`` is load-bearing, not cosmetic. The agent shares the sidecar's network
    namespace, so a single OUTPUT chain governs both — there is no "the agent's rules"
    versus "the proxy's rules". The uid is the only thing that separates them: the
    proxy must reach upstream DNS, the agent must not, and `-m owner --uid-owner` is
    what expresses that. It must match the uid the proxy actually runs as in the image.
    """
    image: str = "squadx/egress-proxy:latest"
    dns_port: int = 15353
    proxy_uid: int = 0
    ipset_v4: str = "squadx_allow4"
    ipset_v6: str = "squadx_allow6"
    # Pinned addresses expire, so the allow set tracks DNS instead of growing for the
    # life of the run. Long enough to outlive a normal TTL, short enough that a stale
    # address does not stay reachable after the name stops resolving to it.
    pin_timeout_seconds: int = 3600
    config_path: str = "/etc/squadx/egress-policy.json"
    policy: NetworkPolicy = field(default_factory=lambda: POLICY_AGENT_DEFAULT)

    def to_dns_config(self) -> dict:
        """Generate DNS proxy configuration."""
        allow_domains = []
        deny_domains = []

        for rule in self.policy.rules:
            if not _is_ip_or_cidr(rule.target):
                if rule.action == EgressAction.ALLOW:
                    allow_domains.append(rule.target)
                else:
                    deny_domains.append(rule.target)

        return {
            "defaultAction": self.policy.default_action.value,
            "allowDomains": allow_domains,
            "denyDomains": deny_domains,
            "listenPort": self.dns_port,
        }

    def ip_rules(self) -> list[str]:
        """Static iptables rules for IP/CIDR targets, which need no DNS at all."""
        rules = []
        for rule in self.policy.rules:
            if not _is_ip_or_cidr(rule.target):
                continue
            if rule.action == EgressAction.DENY:
                rules.append(f"iptables -A OUTPUT -d {rule.target} -j DROP")
            else:
                for port in rule.ports:
                    rules.append(
                        f"iptables -A OUTPUT -d {rule.target} -p tcp --dport {port} -j ACCEPT"
                    )
        return rules


def _is_ip_or_cidr(target: str) -> bool:
    """Check if target is an IP address or CIDR range."""
    import re
    ip_pattern = r'^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(/\d{1,2})?$'
    return bool(re.match(ip_pattern, target))


def generate_sidecar_setup_script(config: EgressSidecarConfig) -> str:
    """Build the sidecar's egress setup script (RFC-0006 §3, layer 2).

    Replaces the one-shot `dig` allowlist. That approach pinned whatever addresses a
    domain happened to have at setup time — stale the moment a CDN rotated — and left
    UDP 53 open, which made the allowlist decorative: an agent could exfiltrate through
    DNS queries themselves without ever opening a TCP connection.

    Here the proxy is the only resolver the agent can reach, so a name off the
    allowlist is never resolved, and the addresses that *are* returned are pinned into
    an ipset the moment the agent is told about them. The pin set therefore always
    matches what the agent was just handed.

    Ordering matters and is deliberate:

      - default DROP is set *before* the flush, so re-applying (warm-pool reuse) never
        opens a window with no policy;
      - the proxy is started *before* the DNS redirect is installed, so the agent
        cannot slip a query through while nothing is listening;
      - `-m owner --uid-owner` separates proxy traffic from agent traffic. They share
        the netns and thus a single OUTPUT chain, so uid is the only distinction
        available: the proxy may reach upstream DNS, the agent may only reach the proxy.

    Fails closed: if ipset or the proxy cannot start, `set -e` aborts before the
    permissive rules are added, leaving default DROP in force.
    """
    dns_cfg = json.dumps(config.to_dns_config(), separators=(",", ":"))
    uid = config.proxy_uid

    lines = [
        "#!/bin/sh",
        "set -e",
        "",
        "# Default-deny first: DROP is set before the flush so re-application never",
        "# leaves the namespace briefly unpoliced.",
        "iptables -P OUTPUT DROP",
        "iptables -F OUTPUT",
        "iptables -t nat -F OUTPUT",
        "",
        "# Address sets the DNS proxy pins allowed answers into. `-exist` keeps this",
        "# idempotent across pool reuse; flush so a recycled sidecar cannot inherit the",
        "# previous run's addresses.",
        f"ipset create {config.ipset_v4} hash:ip family inet timeout "
        f"{config.pin_timeout_seconds} -exist",
        f"ipset create {config.ipset_v6} hash:ip family inet6 timeout "
        f"{config.pin_timeout_seconds} -exist",
        f"ipset flush {config.ipset_v4}",
        f"ipset flush {config.ipset_v6}",
        "",
        "mkdir -p /etc/squadx",
        f"cat > {config.config_path} <<'SQUADX_POLICY_EOF'",
        dns_cfg,
        "SQUADX_POLICY_EOF",
        "",
        "# Restart the proxy so a recycled sidecar serves this run's policy, not the",
        "# previous one's.",
        "pkill -f egress-dns-proxy.py 2>/dev/null || true",
        f"/usr/local/bin/egress-dns-proxy.py --config {config.config_path} \\",
        f"  --port {config.dns_port} --ipset-v4 {config.ipset_v4} \\",
        f"  --ipset-v6 {config.ipset_v6} --pin-timeout {config.pin_timeout_seconds} \\",
        "  >/var/log/egress-dns-proxy.log 2>&1 &",
        "",
        "# Wait for the listener: installing the redirect first would point the agent's",
        "# resolver at a closed port and turn a policy failure into a confusing outage.",
        "for i in $(seq 1 50); do",
        f"  nc -z -u -w1 127.0.0.1 {config.dns_port} 2>/dev/null && break",
        "  sleep 0.1",
        "done",
        "",
        "iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT",
        "iptables -A OUTPUT -o lo -j ACCEPT",
        "",
        "# Only the proxy may talk to upstream DNS. Same chain governs both processes,",
        "# so uid is what separates them.",
        f"iptables -A OUTPUT -m owner --uid-owner {uid} -p udp --dport 53 -j ACCEPT",
        f"iptables -A OUTPUT -m owner --uid-owner {uid} -p tcp --dport 53 -j ACCEPT",
        "",
        "# Everyone else's DNS is redirected into the proxy.",
        f"iptables -t nat -A OUTPUT -p udp --dport 53 -m owner ! --uid-owner {uid} "
        f"-j REDIRECT --to-ports {config.dns_port}",
        f"iptables -t nat -A OUTPUT -p tcp --dport 53 -m owner ! --uid-owner {uid} "
        f"-j REDIRECT --to-ports {config.dns_port}",
        "",
        "# Encrypted DNS would bypass the proxy entirely. DoT has a dedicated port and",
        "# is dropped here; DoH rides ordinary :443 and is instead handled by the fact",
        "# that a DoH provider has to be on the allowlist to be reachable at all.",
        "iptables -A OUTPUT -p tcp --dport 853 -j DROP",
        "iptables -A OUTPUT -p udp --dport 853 -j DROP",
        "",
    ]

    if config.policy.default_action == EgressAction.DENY:
        lines += [
            "# The allowlist proper: reachable addresses are exactly those the proxy",
            "# just resolved from an allowed name.",
            f"iptables -A OUTPUT -m set --match-set {config.ipset_v4} dst -j ACCEPT",
            f"iptables -A OUTPUT -m set --match-set {config.ipset_v6} dst -j ACCEPT",
            "",
        ]

    lines += config.ip_rules()
    lines.append("")

    if config.policy.default_action == EgressAction.ALLOW:
        # Debugging only: the DENY rules above still stand, this just lifts the floor.
        lines.append("iptables -P OUTPUT ACCEPT")

    return "\n".join(lines) + "\n"
