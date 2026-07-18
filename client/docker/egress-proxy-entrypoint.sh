#!/bin/sh
# SquadX egress sidecar entrypoint (RFC-0006 / ADR-0008 Phase 1).
#
# Establishes an always-on baseline (loopback up, cloud-metadata dropped) and then
# stays alive so the network namespace persists for the whole run. The FULL policy
# (default-deny OUTPUT + the DNS proxy that domain-allowlists and pins answers into
# an ipset) is injected afterwards by the daemon via `apply_network_setup`, which runs
# `generate_sidecar_setup_script` in this netns — this container has NET_ADMIN, so
# unlike the agent it can actually apply it.
#
# Baseline is intentionally minimal and idempotent: it must not conflict with the
# injected default-deny script (which sets `iptables -P OUTPUT DROP`). We only add a
# metadata DROP here, so if the injected script is ever delayed there is still no path
# to the credentials endpoint.
set -eu

log() { echo "egress-proxy: $*" >&2; }

# Bring up loopback (shared by the agent that joins this netns).
ip link set lo up 2>/dev/null || true

# Always-on metadata block (belt-and-suspenders with the injected policy + host Phase 0).
for target in ${SQUADX_METADATA_TARGETS:-169.254.169.254 169.254.170.2}; do
    if ! iptables -C OUTPUT -d "$target" -j DROP 2>/dev/null; then
        iptables -I OUTPUT 1 -d "$target" -j DROP && log "metadata DROP $target"
    fi
done

log "baseline applied; awaiting injected policy and agent (netns owner staying alive)"

# Keep PID 1 alive so the netns persists. tini forwards signals for a clean stop.
exec tail -f /dev/null
