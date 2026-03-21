"""
Network policy enforcement for sandboxed containers.
Implements domain-based egress filtering using a lightweight DNS proxy approach.
Inspired by OpenSandbox's egress sidecar pattern.
"""
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional
import json
import logging
import shlex

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

def get_predefined_policy(name: str) -> NetworkPolicy:
    """Get a predefined network policy by name."""
    policies = {
        "none": POLICY_NONE,
        "package-managers": POLICY_PACKAGE_MANAGERS,
        "full": POLICY_FULL_ACCESS,
    }
    policy = policies.get(name)
    if policy is None:
        raise ValueError(f"Unknown policy: {name}. Available: {list(policies.keys())}")
    return policy


@dataclass
class EgressSidecarConfig:
    """Configuration for the egress control sidecar container."""
    image: str = "squadx/egress-proxy:latest"
    dns_port: int = 15353
    policy: NetworkPolicy = field(default_factory=lambda: POLICY_PACKAGE_MANAGERS)

    def to_iptables_rules(self) -> list[str]:
        """Generate iptables rules for DNS interception."""
        rules = [
            # Redirect DNS (UDP 53) to local proxy
            f"iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:{self.dns_port}",
            f"iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:{self.dns_port}",
            # Block direct DNS-over-TLS
            "iptables -A OUTPUT -p tcp --dport 853 -j DROP",
            "iptables -A OUTPUT -p udp --dport 853 -j DROP",
        ]

        # Add deny rules for specific IPs/CIDRs
        for rule in self.policy.rules:
            if rule.action == EgressAction.DENY and _is_ip_or_cidr(rule.target):
                rules.append(f"iptables -A OUTPUT -d {rule.target} -j DROP")

        return rules

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
            "upstreamDNS": "8.8.8.8:53",
        }


def _is_ip_or_cidr(target: str) -> bool:
    """Check if target is an IP address or CIDR range."""
    import re
    ip_pattern = r'^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(/\d{1,2})?$'
    return bool(re.match(ip_pattern, target))


def generate_network_setup_script(policy: NetworkPolicy) -> str:
    """Generate a shell script that sets up network filtering inside the container.
    Uses iptables + a simple domain-based approach without requiring a sidecar."""

    if policy.default_action == EgressAction.ALLOW:
        # Allow all, just block specific targets
        lines = ["#!/bin/sh", "set -e", ""]
        for rule in policy.rules:
            if rule.action == EgressAction.DENY and _is_ip_or_cidr(rule.target):
                lines.append(f"iptables -A OUTPUT -d {rule.target} -j DROP")
        return "\n".join(lines) + "\n"

    # Default deny: resolve allowed domains and create allow rules
    lines = [
        "#!/bin/sh",
        "set -e",
        "",
        "# Default: drop all outbound except established connections",
        "iptables -P OUTPUT DROP",
        "iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT",
        "iptables -A OUTPUT -o lo -j ACCEPT",  # Allow loopback
        "iptables -A OUTPUT -p udp --dport 53 -j ACCEPT",  # Allow DNS
        "",
    ]

    for rule in policy.rules:
        if rule.action == EgressAction.ALLOW:
            if _is_ip_or_cidr(rule.target):
                for port in rule.ports:
                    lines.append(f"iptables -A OUTPUT -d {rule.target} -p tcp --dport {port} -j ACCEPT")
            else:
                # For domain-based rules, resolve at setup time
                lines.append(f"# Allow {rule.target}")
                domain = rule.target.lstrip("*.")
                lines.append(f'for ip in $(dig +short {shlex.quote(domain)} 2>/dev/null); do')
                for port in rule.ports:
                    lines.append(f'  iptables -A OUTPUT -d "$ip" -p tcp --dport {port} -j ACCEPT')
                lines.append("done")
                lines.append("")

    return "\n".join(lines) + "\n"
