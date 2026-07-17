# LEARNINGS — agentOS (Rivet)

Análise de `agentos` (Apache-2.0, Rivet) feita em 2026-07-17, com o objetivo de decidir o que
aproveitar para o sandbox do SquadX. Este doc registra o que é verdade **no código** — não no README —
e o que efetivamente portamos.

## 0. TL;DR

**agentOS não substitui nosso sandbox Docker.** Ele resolve um problema adjacente e delega
exatamente a nossa carga de trabalho para sandboxes externos. Mas o *raciocínio de segurança* dele é
de alta qualidade e várias peças são portáveis. Portamos o classificador anti-SSRF e o padrão de
guard estrutural; o resto ficou registrado aqui.

## 1. O que o agentOS realmente é (o README engana)

Verificado contra `Cargo.lock` e as crates:

- **Não existe VM WASM.** Não há `wasmtime` nem `wasmer` no lockfile. O único motor é **V8 130**
  (`crates/v8-runtime`). "WASM" ali é a implementação `WebAssembly.*` do próprio V8 mais um shim WASI
  escrito em JS (`crates/execution/assets/runners/wasi-module.js`). A fronteira de isolamento real é o
  **isolate do V8**. A frase "the same isolation technology trusted by browsers worldwide" é
  literalmente verdadeira; a leitura "sandbox WASM" que ela induz, não.
- **"Runs inside your process" é impreciso.** O sidecar é um binário nativo separado, spawnado pelo
  host Node (`packages/sidecar-binary`). Os isolates vivem no processo dele. O que se quer dizer é
  "sem VM/container", não "sem processo".
- **Não há fuel.** `max_fuel` é wall-clock em milissegundos (`crates/execution/src/wasm.rs:4078`),
  não metering por instrução — sem wasmtime não há fuel. E `max_wasm_stack_bytes` é inaplicável:
  falha closed por design (`wasm.rs:4081`).
- **Em produção depende de gVisor por baixo.** Reescreveram o confinamento de host mounts (deletaram
  `openat2(RESOLVE_BENEATH)`, que o gVisor não suporta) para rodar sob runsc. **Os próprios autores
  não tratam o V8 como anel suficiente sozinho.**
- **Workloads pesados saem para E2B/Daytona** (`packages/agentos-sandbox`, README:119): browsers,
  binários nativos, dev servers. E2B é ao mesmo tempo o baseline de benchmark deles e o escape hatch
  oficial.

**Consequência para o SquadX:** nosso caso (Claude Code/Codex rodando `git`, `pnpm install`,
compilando, subindo dev server, com live-view VNC) cai inteiro na categoria que o agentOS delega.
Trocar Docker por agentOS não está na mesa. Os números de cold start (6 ms vs 440 ms) comparam coisas
diferentes.

## 2. O que portamos

### 2.1 Classificador anti-SSRF + recheck pós-DNS → `client/docker/egress-dns-proxy.py`

`crates/kernel/src/network_policy.rs:34` classifica ranges restritos (`10/8`, `172.16/12`,
`192.168/16`, CGNAT `100.64/10`, link-local `169.254/16`, multicast, reservado) e — o detalhe que
importa — faz isso **também para IPv6 IPv4-mapped e IPv4-compatible**, com testes para
`::ffff:169.254.169.254`. Um check que só olha a forma v6 manda o endpoint de metadata direto.

Além disso, `crates/native-sidecar/src/service.rs:483` reavalia **cada endereço resolvido** contra a
policy (anti-DNS-rebinding), não só o hostname.

Portamos os dois em `is_restricted_addr()` e no `_pin_answer` do nosso dns-proxy: um nome que passa
no allowlist mas resolve para um range restrito é rebinding, não CDN — o endereço não é fixado.

### 2.2 Guard estrutural em CI → `client/tests/test_architecture_guards.py`

`crates/native-sidecar/tests/architecture_guards.rs` **quebra o build** se `std::fs`, `std::net`,
`reqwest`, `Command::new` ou `std::env::var` aparecem fora de um allowlist de módulos. É enforcement
de chokepoint, não convenção em doc.

Portamos a *ideia*, adaptada ao nosso problema real: um teste que falha quando um knob de segurança
declarado não tem consumidor, com uma lista de quarentena que só pode encolher.

O agentOS dá nome ao anti-padrão: **"dead-cap"** — valor setado na config e silenciosamente nunca
lido (`crates/native-sidecar/CLAUDE.md`: *"this is exactly how `AGENTOS_WASM_MAX_STACK_BYTES` was set
into env but never read"*). Tínhamos vários, e eram graves — ver §4.

## 3. O que vale portar depois (não feito)

- **`PermissionsPolicy` tipada** (`crates/vm-config/src/lib.rs:427`): um objeto único com `fs`,
  `network`, `child_process`, `process`, `env`, `binding`, cada um `Mode | Rules`, `deny_unknown_fields`,
  deny-by-default consistente em 4 camadas. Nosso V36 fez só a fatia de egress por squad; o resto da
  policy (fs, exec) segue espalhado entre `SecurityConfig` e env vars.
  - Duas armadilhas deles a **não** copiar: `Ask` está no schema e vira `deny` silenciosamente
    (`native-sidecar-core/src/permissions.rs:291`); `child_process` casa **só o nome do binário, não o
    argv**, então `deny bash` não impede `sh -c bash`.
- **Resolver symlink antes de checar permissão** (`permissions.rs:459`): `realpath` antes de toda
  checagem, com o raciocínio TOCTOU escrito no código. Relevante para `SandboxFileOps` e worktrees.
- **CPU-time real por thread** (`crates/v8-runtime/src/timeout.rs`): amostra o clock de CPU da thread
  a cada 50ms em vez de wall-clock ingênuo — um processo bloqueado em input não morre, um loop
  infinito morre. Nosso `execute_streaming` usa wall-clock de 1800s e, pior, **não mata o processo**
  no timeout (`manager.py:439-445`).
- **Descriptors nunca cruzam a fronteira** (`docs/design/unified-sidecar-runtime.md`): *"Descriptors
  remain in the trusted sidecar. VMs use opaque, generation-checked capabilities."* É o ponto de
  design mais afiado do projeto.

## 4. O que a análise revelou sobre *nós* (o achado mais valioso)

Comparar com o agentOS foi útil menos pelo código dele e mais pelo que a comparação expôs aqui:

1. **O egress não era aplicado. De forma alguma.** `AgentSandbox(network_policy=)` nunca foi passado
   por nenhum call site de produção — só por um teste. O caminho não-sidecar exigia
   `self._network_policy` truthy, então nada era aplicado. Um default que precisa ser passado para
   valer não é um default.
2. **`enable_vnc` (default on) rebaixava `network=none` para `bridge`**, dando egress irrestrito a
   todo agente com live-view — o gap [ALTO] #1 do nosso próprio threat model, aberto.
3. **`EgressSidecarConfig` não tinha nenhum caller** — a camada 2 da RFC-0006 estava especificada e
   não construída, enquanto o allowlist real resolvia via `dig` uma vez e deixava UDP 53 aberto (o
   que torna o allowlist decorativo: a própria query exfiltra).
4. **AppArmor nunca foi aplicado**, e o default nomeava `squadx-agent`, um perfil que **não existe no
   repo** — se a property tivesse funcionado, todo container falharia ao subir.
5. **Sidecar × live-view estava quebrado** (porta VNC consultada no container errado) e **sidecar ×
   warm pool eram mutuamente exclusivos**; o pool ignorava `environment`, então um container do pool
   nunca poderia carregar as API keys.

Padrão comum: **o teste passa, o doc descreve, e nada está ligado.** Daí o guard.

## 5. Honestidade sobre o que ainda não sabemos

O caminho real (sidecar + dns-proxy + ipset) **não foi exercitado contra um daemon Docker** — não
havia um na máquina onde isto foi feito. O teste de integração existe e afirma as quatro alegações
(host allowlisted alcançável, não-allowlisted bloqueado, metadata bloqueado, agente não consegue dar
`iptables -F`), mas está por rodar. O ipset exige `xt_set` no kernel do host; sem ele o script aborta
com default-DROP em vigor. **A lacuna de verificação da RFC-0006 continua aberta** — só ficou menor e
mais explícita.
