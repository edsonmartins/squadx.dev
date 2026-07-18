# SquadX Egress Firewall Sidecar
# RFC-0006 / ADR-0008 Phase 1 — enforces default-deny + domain-allowlist egress.
#
# This container OWNS the network namespace that the (untrusted, cap-drop ALL) agent
# joins via `network_mode=container:<this>`. It holds NET_ADMIN so it can program
# iptables; the agent, sharing the netns but capability-less, cannot alter the rules.
#
# The actual policy is injected at runtime by the daemon
# (DockerManager.apply_network_setup -> generate_sidecar_setup_script), which starts the
# DNS proxy here and programs iptables/ipset around it. This image's job is to (1)
# provide those tools and the proxy, (2) drop cloud-metadata egress immediately as a
# baseline, and (3) stay alive so the shared netns persists for the whole run.
#
# Host kernel requirement: the ipset match (`-m set`) needs the xt_set module. Where it
# is unavailable the injected script aborts under `set -e` with default-DROP still in
# force — the run fails closed rather than silently running unfiltered.
#
# Build (context = client/docker, like agent.Dockerfile):
#   cd client/docker && docker build -f egress-proxy.Dockerfile -t squadx/egress-proxy:latest .
#
# Run (started by the daemon with):
#   --cap-add=NET_ADMIN --security-opt no-new-privileges:true
#   -p <vnc>:<vnc>        # the agent's VNC is published here, not on the agent
#
FROM alpine:3.20

# iptables/ip6tables to program egress rules; ipset for the address set the DNS proxy
# pins allowed answers into (needs xt_set on the host kernel); python3 + dnslib for the
# proxy itself; netcat to wait for its listener before redirecting DNS at it; bind-tools
# retained for debugging; tini for clean PID 1 signal handling.
RUN apk add --no-cache \
      iptables ip6tables ipset bind-tools tini \
      python3 py3-pip netcat-openbsd \
 && pip install --no-cache-dir --break-system-packages dnslib==0.9.25

COPY egress-proxy-entrypoint.sh /usr/local/bin/egress-proxy-entrypoint.sh
COPY egress-dns-proxy.py /usr/local/bin/egress-dns-proxy.py
RUN chmod +x /usr/local/bin/egress-proxy-entrypoint.sh /usr/local/bin/egress-dns-proxy.py

# Cloud instance-metadata endpoints, denied as an always-on baseline (defense in depth
# with the injected policy and the host-side Phase 0 block).
ENV SQUADX_METADATA_TARGETS="169.254.169.254 169.254.170.2"

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/egress-proxy-entrypoint.sh"]
