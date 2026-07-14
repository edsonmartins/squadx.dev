# SquadX Egress Firewall Sidecar
# RFC-0006 / ADR-0008 Phase 1 — enforces default-deny + domain-allowlist egress.
#
# This container OWNS the network namespace that the (untrusted, cap-drop ALL) agent
# joins via `network_mode=container:<this>`. It holds NET_ADMIN so it can program
# iptables; the agent, sharing the netns but capability-less, cannot alter the rules.
#
# The actual policy is injected at runtime by the daemon
# (DockerManager.apply_network_setup -> generate_network_setup_script), which needs
# `iptables` and `dig` present here. This image's job is to (1) provide those tools,
# (2) drop cloud-metadata egress immediately as a baseline, and (3) stay alive so the
# shared netns persists for the whole run.
#
# Build (context = client/docker, like agent.Dockerfile):
#   cd client/docker && docker build -f egress-proxy.Dockerfile -t squadx/egress-proxy:latest .
#
# Run (started by the daemon with):
#   --cap-add=NET_ADMIN --security-opt no-new-privileges:true
#   -p <vnc>:<vnc>        # the agent's VNC is published here, not on the agent
#
FROM alpine:3.20

# iptables/ip6tables to program egress rules; bind-tools (dig) to resolve the
# allowlist domains in the injected setup script; tini for clean PID 1 signal handling.
RUN apk add --no-cache iptables ip6tables bind-tools tini

COPY egress-proxy-entrypoint.sh /usr/local/bin/egress-proxy-entrypoint.sh
RUN chmod +x /usr/local/bin/egress-proxy-entrypoint.sh

# Cloud instance-metadata endpoints, denied as an always-on baseline (defense in depth
# with the injected policy and the host-side Phase 0 block).
ENV SQUADX_METADATA_TARGETS="169.254.169.254 169.254.170.2"

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/egress-proxy-entrypoint.sh"]
