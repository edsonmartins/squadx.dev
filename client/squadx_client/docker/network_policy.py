"""Network policy enforcement for sandboxed containers (RFC-0006 / ADR-0008).

Egress is default-deny with a domain allowlist, enforced in the egress sidecar: the
agent is cap-drop ALL and shares the sidecar's netns, so it can neither program nor
undo these rules. Domain allowlisting is done by a DNS proxy that is the only resolver
the agent can reach — see ``generate_sidecar_setup_script`` and docker/egress-dns-proxy.py.
"""
import json
import logging
from dataclasses import dataclass, field
from enum import Enum

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
        EgressRule(EgressAction.DENY, "169.254.169.254", description="Block cloud metadata (IMDS)"),
        EgressRule(EgressAction.DENY, "169.254.170.2", description="Block AWS ECS task credentials"),
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

# Contract with the backend's SandboxEgressPolicy enum (dispatched as
# `sandbox_egress_policy` on the task payload). Kept as an explicit map rather than a
# lowercase/underscore transform so that renaming a preset here cannot silently change
# what an existing backend's value means.
_BACKEND_POLICY_NAMES = {
    "AGENT_DEFAULT": "agent-default",
    "DENY_ALL": "deny-all",
    "FULL": "full",
}


def policy_name_from_backend(value: str | None) -> str | None:
    """Map a backend SandboxEgressPolicy enum name to a local preset name.

    Returns None when the value is absent or unrecognised, so the caller falls back to
    its own default rather than to no policy. Enum drift must downgrade, not crash and
    not open up: an installed daemon outlives any given backend, so a value this
    version has never heard of is expected, not exceptional.
    """
    if not value:
        return None
    name = _BACKEND_POLICY_NAMES.get(str(value).strip().upper())
    if name is None:
        logger.warning(
            "unknown_backend_egress_policy value=%r — falling back to the daemon default",
            value,
        )
    return name


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
        """Generate DNS proxy configuration.

        Allow entries carry their ports so the proxy can pin ``(ip, port)`` pairs rather
        than bare IPs: a domain restricted to :443 stays restricted to :443 even after
        its address is known, instead of opening every port on that address.
        """
        allow_domains = []
        deny_domains = []

        for rule in self.policy.rules:
            if not _is_ip_or_cidr(rule.target):
                if rule.action == EgressAction.ALLOW:
                    allow_domains.append({"pattern": rule.target, "ports": list(rule.ports)})
                else:
                    deny_domains.append(rule.target)

        return {
            "defaultAction": self.policy.default_action.value,
            "allowDomains": allow_domains,
            "denyDomains": deny_domains,
            "listenPort": self.dns_port,
        }

    def ip_deny_rules(self) -> list[str]:
        """DROP rules for IP/CIDR DENY targets (e.g. cloud metadata). IPv4 only — the
        only literal-IP targets in the presets are v4, and ``_is_ip_or_cidr`` matches v4.
        """
        return [
            f"iptables -A OUTPUT -d {rule.target} -j DROP"
            for rule in self.policy.rules
            if _is_ip_or_cidr(rule.target) and rule.action == EgressAction.DENY
        ]

    def ip_allow_rules(self) -> list[str]:
        """Per-port ACCEPT rules for IP/CIDR ALLOW targets, which need no DNS at all."""
        rules = []
        for rule in self.policy.rules:
            if _is_ip_or_cidr(rule.target) and rule.action == EgressAction.ALLOW:
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

      - default DROP is set (both families) *before* the flush, so re-applying
        (warm-pool reuse) never opens a window with no policy;
      - loopback and established are allowed *before* the readiness probe and the
        proxy start, because both talk over loopback (an earlier version put the
        probe ahead of the loopback ACCEPT, so it could never succeed);
      - the proxy is started and confirmed listening (a TCP probe — a UDP one cannot
        tell "open" from "silently dropped") *before* the DNS redirect is installed;
      - `-m owner --uid-owner` separates proxy traffic from agent traffic. They share
        the netns and thus a single OUTPUT chain, so uid is the only distinction
        available: the proxy may reach upstream DNS, the agent may only reach the proxy.

    Both IPv4 and IPv6 are programmed. IPv6 gets the same default-deny and the same
    ipset allowlist; the agent's own IPv6 DNS is simply dropped (v6 default-deny), since
    the proxy resolves AAAA over its v4 upstream and pins the v6 address — the agent
    reaches v6 hosts without ever needing v6 DNS itself. Leaving ip6tables unset would
    make the whole allowlist bypassable over IPv6.

    Fails closed: if ipset or the proxy cannot start, `set -e` aborts before the
    permissive rules are added, leaving default DROP in force.
    """
    dns_cfg = json.dumps(config.to_dns_config(), separators=(",", ":"))
    uid = config.proxy_uid
    port = config.dns_port
    v4, v6 = config.ipset_v4, config.ipset_v6
    timeout = config.pin_timeout_seconds

    lines = [
        "#!/bin/sh",
        "set -e",
        "",
        "# Default-deny both families first; DROP is set before the flush so",
        "# re-application (warm-pool reuse) never leaves the namespace briefly unpoliced.",
        "iptables -P OUTPUT DROP",
        "ip6tables -P OUTPUT DROP",
        "iptables -F OUTPUT",
        "ip6tables -F OUTPUT",
        "iptables -t nat -F OUTPUT",
        "ip6tables -t nat -F OUTPUT",
        "",
        "# REDIRECT sends the agent's DNS to the loopback proxy; the kernel treats a",
        "# locally-generated packet routed into 127.0.0.0/8 as a martian unless",
        "# route_localnet is on for the namespace. Best-effort: some hosts forbid the",
        "# write, in which case REDIRECT may still work depending on kernel/version.",
        "echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true",
        "",
        "# (ip,port) sets, not plain ip: the proxy pins one entry per allowed port, so a",
        "# domain restricted to :443 does not become every-port-open once its ip is known.",
        "# `-exist` + flush keeps a recycled sidecar from inheriting the previous run.",
        f"ipset create {v4} hash:ip,port family inet timeout {timeout} -exist",
        f"ipset create {v6} hash:ip,port family inet6 timeout {timeout} -exist",
        f"ipset flush {v4}",
        f"ipset flush {v6}",
        "",
        "mkdir -p /etc/squadx",
        f"cat > {config.config_path} <<'SQUADX_POLICY_EOF'",
        dns_cfg,
        "SQUADX_POLICY_EOF",
        "",
        "# Loopback + established must be allowed before anything else: the readiness",
        "# probe below reaches the proxy over loopback, and so do the proxy's replies.",
        "iptables -A OUTPUT -o lo -j ACCEPT",
        "ip6tables -A OUTPUT -o lo -j ACCEPT",
        "iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT",
        "ip6tables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT",
        "",
    ]

    # Explicit IP denies (cloud metadata) BEFORE any allow: a DROP must win even if an
    # allowed name ever resolved into a denied address.
    deny_rules = config.ip_deny_rules()
    if deny_rules:
        lines.append("# Explicit IP denies (cloud metadata) — placed ahead of every allow.")
        lines += deny_rules
        lines.append("")

    lines += [
        "# Only the proxy may reach upstream DNS; the agent may only reach the proxy",
        "# (redirect below). Same chain governs both, so uid is what separates them.",
        f"iptables -A OUTPUT -m owner --uid-owner {uid} -p udp --dport 53 -j ACCEPT",
        f"iptables -A OUTPUT -m owner --uid-owner {uid} -p tcp --dport 53 -j ACCEPT",
        f"ip6tables -A OUTPUT -m owner --uid-owner {uid} -p udp --dport 53 -j ACCEPT",
        f"ip6tables -A OUTPUT -m owner --uid-owner {uid} -p tcp --dport 53 -j ACCEPT",
        "",
        "# DNS-over-TLS would bypass the proxy; drop it on both families. DoH rides :443",
        "# and is instead gated by its provider needing to be on the allowlist at all.",
        "iptables -A OUTPUT -p tcp --dport 853 -j DROP",
        "iptables -A OUTPUT -p udp --dport 853 -j DROP",
        "ip6tables -A OUTPUT -p tcp --dport 853 -j DROP",
        "ip6tables -A OUTPUT -p udp --dport 853 -j DROP",
        "",
        "# Start the proxy, then wait for it to actually listen (TCP probe — reliable,",
        "# unlike a UDP one) before pointing the agent's resolver at it.",
        "pkill -f egress-dns-proxy.py 2>/dev/null || true",
        f"/usr/local/bin/egress-dns-proxy.py --config {config.config_path} \\",
        f"  --port {port} --ipset-v4 {v4} --ipset-v6 {v6} \\",
        f"  --pin-timeout {timeout} >/var/log/egress-dns-proxy.log 2>&1 &",
        "",
        "for _ in $(seq 1 50); do",
        f"  nc -z -w1 127.0.0.1 {port} 2>/dev/null && break",
        "  sleep 0.1",
        "done",
        "",
        "# Redirect the agent's DNS (v4) into the proxy. v6 DNS from the agent is dropped",
        "# by the v6 default-deny; the proxy resolves AAAA over v4 and pins the address.",
        f"iptables -t nat -A OUTPUT -p udp --dport 53 -m owner ! --uid-owner {uid} "
        f"-j REDIRECT --to-ports {port}",
        f"iptables -t nat -A OUTPUT -p tcp --dport 53 -m owner ! --uid-owner {uid} "
        f"-j REDIRECT --to-ports {port}",
        "",
    ]

    if config.policy.default_action == EgressAction.DENY:
        lines += [
            "# The allowlist proper: reachable (ip,port) pairs are exactly those the",
            "# proxy just pinned from an allowed name. `dst,dst` matches dest ip + port.",
            f"iptables -A OUTPUT -m set --match-set {v4} dst,dst -j ACCEPT",
            f"ip6tables -A OUTPUT -m set --match-set {v6} dst,dst -j ACCEPT",
            "",
        ]

    allow_rules = config.ip_allow_rules()
    if allow_rules:
        lines += allow_rules
        lines.append("")

    if config.policy.default_action == EgressAction.ALLOW:
        # Debugging only: the DENY rules above still stand, this just lifts the floor.
        lines.append("iptables -P OUTPUT ACCEPT")
        lines.append("ip6tables -P OUTPUT ACCEPT")

    return "\n".join(lines) + "\n"
