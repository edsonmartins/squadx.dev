# Sandboxing de Execução de Código para AI Agents Enterprise

**Firecracker microVMs oferecem a melhor relação isolamento-overhead para execução de código por AI agents autônomos**, emergindo como consenso da indústria. AWS Lambda, E2B, Fly.io e CodeSandbox dependem do Firecracker para execução de código não-confiável — entregando isolamento KVM em nível de hardware com **overhead de memória <5 MiB** e **boot em ≤125ms**. Para a abordagem enterprise-first do SquadX, a arquitetura recomendada é uma estratégia faseada: começar com containers Docker hardened para desenvolvimento rápido, integrar gVisor como upgrade drop-in de runtime, e migrar para Firecracker/Kata Containers para multi-tenancy em produção. O SquadX adota LiteLLM para LLM routing com um framework customizado de agentes, priorizando controle total sobre sandboxing e segurança para deployment enterprise.

Este relatório cobre o espectro completo de tecnologias de sandboxing, estudos de caso de produção, requisitos de compliance enterprise, e guidance específico de implementação para o SquadX.

---

## 1. Docker Hardening: Fundação Sólida mas Isolamento Insuficiente Sozinho

Containers Docker compartilham o kernel do host — esta é sua limitação fundamental de segurança. No entanto, com hardening abrangente, Docker fornece uma camada inicial prática com **overhead de performance negligível**. A seguinte configuração representa um container de agent totalmente hardened:

```bash
docker run \
  --name agent-sandbox-$(uuidgen) \
  --rm \
  --read-only \
  --tmpfs /tmp:size=100M,mode=1777,noexec,nosuid \
  --tmpfs /home/agent/.cache:size=200M,noexec,nosuid \
  --network=agent-sandbox-net \
  --cap-drop=ALL \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=/etc/docker/seccomp/squadx-agent.json \
  --security-opt apparmor=squadx-agent-profile \
  --user 1000:1000 \
  --memory=512m --memory-swap=512m \
  --cpus=1.0 --pids-limit=256 \
  --ulimit nofile=1024:2048 \
  -v /srv/squadx/workspaces/${TASK_ID}:/workspace:rw \
  squadx-sandbox:latest
```

### 1.1 Isolamento de Rede: O Controle Mais Crítico

**Isolamento de rede** é o controle mais crítico. `--network=none` fornece isolamento completo mas bloqueia instalação de pacotes. Para agents que precisam de `npm install` ou `pip install`, use uma rede bridge customizada com filtragem de egresso via iptables ou um proxy transparente Squid/Envoy enforçando allowlists de domínios.

As novas Sandbox Network Policies do Docker suportam configs de proxy baseadas em JSON com regras deny-by-default, adicionando **latência de ~1–5ms por request**.

**Configuração de proxy transparente com Squid:**

```bash
# /etc/squid/squid.conf
acl allowed_domains dstdomain .npmjs.org .pypi.org .github.com
http_access allow allowed_domains
http_access deny all

# Redirecionar tráfego para Squid via iptables
iptables -t nat -A PREROUTING -i agent-br0 -p tcp --dport 80 \
  -j REDIRECT --to-port 3128
iptables -t nat -A PREROUTING -i agent-br0 -p tcp --dport 443 \
  -j REDIRECT --to-port 3129
```

### 1.2 Isolamento de Filesystem

**Isolamento de filesystem** via `--read-only` com montagens `tmpfs` direcionadas (usando flags `noexec,nosuid`) previne malware persistente e injeção de binários. 

A flag `noexec` em tmpfs é **crítica** — ela impede que executáveis carregados sejam executados. Limites de tamanho em tmpfs agem como quotas de disco já que tmpfs é backed por RAM.

```bash
# Exemplo de tmpfs seguro
--tmpfs /tmp:size=100M,mode=1777,noexec,nosuid,nodev

# O que cada flag faz:
# size=100M     - Limite de tamanho (previne enchimento de RAM)
# mode=1777     - Permissões padrão (sticky bit)
# noexec        - NÃO PODE executar binários
# nosuid        - Ignora bit SUID
# nodev         - Ignora device files
```

### 1.3 Remoção de Capabilities

**Remoção de capabilities** com `--cap-drop=ALL` remove todas as 14 capabilities Linux padrão. Para execução típica de código, **nenhuma capability precisa ser re-adicionada**.

Combinado com `no-new-privileges` (que bloqueia escalação SUID) e o perfil seccomp padrão do Docker (que bloqueia ~44 syscalls perigosas incluindo `mount`, `reboot`, e `bpf`), a superfície de ataque encolhe dramaticamente.

**Lista de capabilities Linux que são removidas:**

```
CAP_CHOWN            - Mudar ownership de arquivos
CAP_DAC_OVERRIDE     - Bypass permissões de arquivo
CAP_FOWNER           - Bypass permissões baseadas em UID
CAP_FSETID           - Bypass limpeza de bits SUID/SGID
CAP_KILL             - Bypass verificações de permissão de kill
CAP_SETGID           - Manipular IDs de processo
CAP_SETUID           - Manipular UIDs de processo
CAP_NET_BIND_SERVICE - Bind a portas <1024
CAP_NET_RAW          - Usar sockets RAW
CAP_SYS_CHROOT       - Usar chroot()
CAP_AUDIT_WRITE      - Escrever em kernel audit log
CAP_SETFCAP          - Setar file capabilities
... e mais
```

### 1.4 Limites de Recursos

**Limites de recursos** previnem denial-of-service:

- `--pids-limit=256` - Para fork bombs
- `--memory=512m --memory-swap=512m` - Valores iguais desabilitam swap
- `--cpus=1.0` - Limita CPU a 1 core

Overhead de performance de todos esses controles é **negligível** — as features de segurança do Docker miram operações de control-path, não data-path.

### 1.5 Vetores de Escape Conhecidos

**Vetores de escape conhecidos** permanecem uma preocupação:

- **CVE-2024-21626** - runc file descriptor leak
- **CVEs 2025** - runc maskedPaths bypass, procfs write redirects

Container escapes **NÃO são teóricos**. Por isso:

❌ **NUNCA expor Docker socket** a containers de agents  
❌ **NUNCA usar `--privileged`**  
✅ **SEMPRE manter runc/containerd atualizados**  
✅ **SEMPRE usar user namespace remapping** (`userns-remap`)

User namespace remapping garante que root dentro do container mapeia para um UID não-privilegiado no host, limitando o impacto de escape.

### 1.6 Tabela Resumo: Camadas de Defesa Docker

| Camada de defesa | Mecanismo | O que previne |
|------------------|-----------|---------------|
| **Rede** | Bridge filtrado ou `network=none` | Exfiltração de dados, comunicação C2 |
| **Filesystem** | `--read-only` + `tmpfs` limitado | Malware persistente, injeção de binário |
| **Recursos** | Limites de memória, CPU, PID | DoS, fork bombs, esgotamento de recursos |
| **Capabilities** | `--cap-drop=ALL` | Escalação de privilégios |
| **Seccomp** | Perfil padrão + custom | Exploração de kernel via syscalls |
| **Usuário** | Non-root + namespace remapping | Container escape → root no host |
| **Monitoramento** | Falco (eBPF) | Detecção de anomalia em runtime |

---

## 2. Firecracker MicroVMs: O Padrão da Indústria para Código Não-Confiável

O problema fundamental com Docker — kernel compartilhado — **não pode ser resolvido apenas com hardening**. Para AI agents autônomos que geram e executam código arbitrário e não-confiável, **isolamento em nível de hardware via virtualização KVM** é o padrão de produção.

### 2.1 Firecracker: A Tecnologia por Trás do AWS Lambda

**Firecracker**, construído pela AWS em Rust, executa cada invocação do Lambda e cada tarefa Fargate. Seu design minimalista (apenas 5 dispositivos emulados, ~50K linhas de Rust vs ~2M linhas de C do QEMU) cria uma superfície de ataque mínima.

**Performance Excepcional:**

- VMM pronto em **8 CPU-milissegundos**
- Guest userspace alcançado em **≤125ms**
- Overhead de memória: **≤5 MiB por microVM**
- Performance de CPU: **>95% do bare metal**
- Snapshot restoration: warm starts em **<5ms**

**Processo Jailer** adiciona:
- chroot
- namespaces
- cgroups
- Perfil seccomp com allowlist de apenas **24 syscalls**

### 2.2 Kata Containers: Firecracker com UX de Kubernetes

**Kata Containers** encapsula Firecracker (ou Cloud Hypervisor/QEMU) com orquestração nativa do Kubernetes via CRI. Fornece a UX de container enquanto entrega isolamento de VM.

**Specs:**
- Startup: **150–300ms** (dependendo do hypervisor)
- Overhead de CPU: **2–5%**
- I/O: near-native com virtio-fs
- Requer bare metal ou nested virtualization

**Limitação:** GCP suporta nested virt; AWS requer instâncias bare metal (`.metal`) — não é blocker mas é constraint.

### 2.3 gVisor: Kernel User-Space do Google

**gVisor** (`runsc`) toma uma abordagem diferente: um kernel user-space escrito em Go memory-safe que intercepta todas as syscalls, reduzindo a exposição ao kernel do host para apenas **~55 syscalls** (vs ~300+ com Docker padrão).

**Características:**
- Integra como runtime Docker drop-in: `--runtime=runsc`
- Usado no Google Cloud Run, App Engine, e GKE Sandbox
- Startup: **50–100ms** (equivalente a Docker)
- Sem overhead de VM

**Trade-off:**
- Penalidade de I/O de **10–30%** devido a handling de syscall em user-space
- Algumas syscalls não implementadas
- Para AI agents fazendo heavy file I/O ou instalação de pacotes, isso importa

### 2.4 WebAssembly (WASM/WASI): Não Viável Ainda

**WebAssembly (WASM/WASI)** **NÃO é viável** para o caso de uso do SquadX hoje:

❌ Python sob WASI tem threading desabilitado  
❌ Sem módulos de networking  
❌ Sem dynamic library loading  
❌ Stdlib limitado  
❌ Node.js WASI module avisa explicitamente contra usar para código não-confiável

WASM excele para sistemas de plugins e edge computing, mas **não pode sandboxar código general-purpose arbitrário em produção**.

### 2.5 Tabela Comparativa: Tecnologias de Isolamento

| Tecnologia | Isolamento | Startup | Overhead memória | Penalidade I/O | Enterprise ready |
|------------|------------|---------|------------------|----------------|------------------|
| **Docker (runc)** | Namespace apenas | 50–100ms | ~5–10 MB | Negligível | ✅ Com hardening |
| **gVisor (runsc)** | Kernel user-space | 50–100ms | ~10–30 MB | 10–30% | ✅ Produção Google |
| **Kata Containers** | KVM hardware | 150–300ms | ~30–120 MB | Near-native | ✅ Baidu, Alibaba |
| **Firecracker** | KVM hardware | ≤125ms | ≤5 MiB | Near-native | ✅ AWS Lambda, E2B |
| **WASM/WASI** | Capability sandbox | Sub-ms | Minimal | Variável | ❌ Não para código geral |

---

## 3. Estudos de Caso de Produção: A Indústria Converge para Isolamento de VM

Examinar como plataformas líderes realmente fazem sandbox de execução de código revela uma tendência clara: **a indústria está migrando de containers para microVMs para workloads não-confiáveis**.

### 3.1 E2B: Arquitetura de Referência para AI Agent Sandboxing

**E2B** é a arquitetura de referência mais diretamente relevante. Construído especificamente para sandboxing de AI agents, E2B usa Firecracker microVMs com:

- **~150ms** de criação de sandbox
- Sessões de até **24 horas**
- Snapshotting baseado em template para startup rápido
- Modelo de autenticação dual: API keys da plataforma + tokens short-lived por sandbox
- Acesso a arquivos criptograficamente assinado

**Adoção:** E2B reporta adoção por **~50% das empresas Fortune 500**.

**Case Study - Manus AI:**  
Manus AI escolheu explicitamente E2B ao invés de Docker devido a:
- **10–20s spawn time** do Docker
- Falta de funcionalidade completa de OS

### 3.2 GitHub Codespaces: Modelo de VM Dedicada

**GitHub Codespaces** executa cada workspace em uma **VM dedicada** — nunca co-localizada na mesma VM que outro usuário.

**Copilot Coding Agent:**
- Opera como "outside collaborator"
- Acesso read-only ao repo
- Restrito a branches `copilot/`
- Requer aprovação humana antes de merge

**Pattern:** Tratar agents como colaboradores não-confiáveis — poderoso para adoção enterprise.

### 3.3 Gitpod: 6 Anos de Kubernetes → VMs Dedicadas

**Evolução do Gitpod** é um conto de advertência. Após **6 anos** rodando workspaces baseados em Kubernetes (pods com isolamento de namespace), concluíram que:

> **"Kubernetes não foi construído para isolamento de processos em um único node"**

Migraram para **VMs dedicadas por desenvolvedor**.

**Validação:** Para AI agents — que são mais adversariais que desenvolvedores — isso valida isolamento em nível de VM como não-negociável.

### 3.4 Replit: Containers + Nix + Snapshot Engine

**Replit** combina:
- Isolamento de container
- Nix para ambientes reproduzíveis
- **Snapshot/rollback engine** para desenvolvimento AI reversível

**Insight de Pesquisa:** Scans de segurança apenas por AI são não-determinísticos. Recomendam abordagens híbridas combinando análise estática com reasoning LLM — achado aplicável diretamente a scanning de código gerado por agents.

### 3.5 Cursor 2.0: Sandboxes sem Internet por Default

**Cursor** sandbox terminais de agents com:
- Acesso ao workspace
- **Sem internet por default**
- Admins enterprise podem enforçar políticas de sandbox para todos os devs

**Insight de Engenharia Chave:**  
**Latência de provisionamento de sandbox é o gargalo crítico** em escala, não tempo de inferência do modelo.

### 3.6 Anthropic Claude Code: Defense-in-Depth Mais Completa

**Claude Code** implementa a defense-in-depth mais completa documentada publicamente:

1. **Linux VM** via `VZVirtualMachine` (macOS)
2. **bubblewrap + seccomp** dentro da VM para confinamento adicional em nível de processo
3. Allowlisting explícito de diretórios/rede

**Camadas:**
```
[Aplicação] → [bubblewrap+seccomp] → [VM Linux] → [Host macOS]
```

### 3.7 Tabela Resumo: Escolhas de Produção

| Plataforma | Tecnologia de Sandbox | Tempo de Startup | Multi-tenant Seguro |
|------------|----------------------|------------------|---------------------|
| **AWS Lambda** | Firecracker | ≤125ms | ✅ |
| **E2B** | Firecracker (templates) | ~150ms | ✅ |
| **GitHub Codespaces** | VMs dedicadas | ~30-60s | ✅ |
| **Gitpod** | VMs dedicadas (pós-migração) | ~20-40s | ✅ |
| **Replit** | Containers + Nix | ~5-15s | ⚠️ |
| **Google Cloud Run** | gVisor | 50-100ms | ✅ |
| **Cursor 2.0** | Containers isolados | Variável | ⚠️ |
| **Claude Code** | VM + bubblewrap | Variável | ✅ |

---

## 4. OpenHands SDK: Acelera Desenvolvimento mas Precisa de Wrapper Hardened

### 4.1 Visão Geral do OpenHands

**OpenHands** (anteriormente OpenDevin) é uma plataforma MIT-licensed com:
- **65.8K estrelas GitHub**
- **$18.8M** em Series A funding
- **5M+ downloads**

**SDK V1** (lançado dezembro 2025) é modular:
- `openhands-sdk` - Core agent framework
- `openhands-tools` - Terminal, file editor, browser
- `openhands-workspace` - Ambientes de execução Docker/remote
- `openhands-agent-server` - REST/WebSocket API de produção

### 4.2 Abstrações Valiosas

O SDK fornece abstrações substanciais sobre Docker puro:

**Arquitetura Event-Stream:**
- Replay determinístico
- Sistema de caching de 3 camadas para rebuilds rápidos
- Portabilidade de workspace (mesmo código de agent funciona localmente, em Docker, ou Kubernetes)
- Ferramentas de debugging built-in (VNC, VSCode Web, browser access)

**Exemplo de Uso:**

```python
from openhands.workspace import DockerWorkspace

with DockerWorkspace(
    base_image="nikolaik/python-nodejs:python3.12-nodejs22",
    host_port=8010,
) as workspace:
    conversation = Conversation(agent=agent, workspace=workspace)
    conversation.send_message("Construa um app calculadora")
    conversation.run()
```

A classe `DockerWorkspace` encapsula:
- Lifecycle de container
- Volume management
- Execução de ação
- API limpa

### 4.3 Limitações de Segurança Significativas

**Limitações de segurança são significativas para uso enterprise:**

❌ Usa isolamento Docker padrão (kernel compartilhado)  
❌ Sem integração gVisor ou microVM  
❌ Sem engine de network policy built-in  
❌ Blog próprio reconhece que sandboxing Docker **"não previne exfiltração de secrets"** já que agents retêm acesso a `curl` e source code  
❌ Limites de memória apenas recentemente adicionados (PR #6616)  
❌ Limites de CPU em modo Docker não são built-in  
❌ Risk assessment baseado em LLM é não-determinístico e dependente de modelo — "soft block" no máximo

### 4.4 Abordagem Recomendada: SDK como Acelerador, Não como Boundary de Segurança

**A abordagem certa é usar OpenHands como acelerador, não como boundary de segurança.**

Sua arquitetura modular permite ao SquadX:

1. ✅ Adotar `openhands-workspace` para desenvolvimento rápido
2. ✅ Encapsular em camada de segurança customizada:
   - Trocar runtime Docker para gVisor (`runsc`)
   - Adicionar Kubernetes network policies
   - Injetar secrets via Vault (não env vars)
   - Camada Falco para runtime monitoring
3. ✅ Classe abstrata `BaseWorkspace` fornece escape hatch — SquadX pode depois substituir runtime com implementação baseada em Firecracker mantendo agent framework do SDK

**Economia de Tempo:** Estimada em **3–6 meses** de engenharia vs construir do zero.

### 4.5 Exemplo de Wrapper de Segurança

```python
from openhands.workspace import BaseWorkspace
import subprocess

class HardenedFirecrackerWorkspace(BaseWorkspace):
    """
    Wrapper customizado que usa Firecracker ao invés de Docker,
    mantendo compatibilidade com OpenHands SDK.
    """
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.vm_id = self._create_firecracker_vm()
        self.vault_token = self._get_vault_token()
    
    def _create_firecracker_vm(self):
        # Provisionar Firecracker microVM com template snapshot
        # Implementação customizada
        pass
    
    def execute_action(self, action):
        # Injetar secrets de Vault antes da execução
        # Executar no Firecracker VM
        # Auditar todos os comandos
        pass
```

---

## 5. Compliance Enterprise: Controles em Camadas Além do Sandbox

### 5.1 Defense in Depth é Não-Negociável

**Defense in depth** é obrigatório. Pesquisa da Palo Alto Networks Unit 42 (maio 2025) afirma:

> "Nenhuma mitigação única é suficiente. Uma estratégia em camadas, defense-in-depth, é necessária para efetivamente reduzir risco em aplicações agentic."

SquadX deve implementar **4 camadas:**

### 5.2 Camada 1: Scanning Pré-Execução

**Scanning pré-execução** executa antes de qualquer código gerado por agent:

1. **Semgrep** com regras customizadas para:
   - Reverse shells
   - Acesso direto a env vars
   - Network listeners
   - Subprocess calls suspeitos

2. **Dependency SCA** via:
   - Snyk
   - `pip-audit` / `npm audit`
   - Trivy para scanning de imagens

3. **Secrets detection** via:
   - GitGuardian
   - TruffleHog
   - Custom regex patterns

**Exemplo de Regra Semgrep Customizada:**

```yaml
rules:
  - id: agent-reverse-shell
    pattern-either:
      - pattern: subprocess.call(['bash', '-i'])
      - pattern: os.system('bash -i')
      - pattern: socket.connect(($HOST, $PORT))
    message: "Possível reverse shell detectado em código gerado por agent"
    severity: ERROR
    languages: [python]
```

### 5.3 Camada 2: Runtime Monitoring

**Runtime monitoring** com Falco (projeto graduado CNCF):

**Falco com eBPF-based syscall monitoring:**

```yaml
# /etc/falco/falco_rules.local.yaml
- rule: Agent Container Spawned Shell
  desc: Detectar shell spawning em containers de agent
  condition: >
    spawned_process and
    container.image.repository contains "squadx-sandbox" and
    proc.name in (bash, sh, zsh, fish)
  output: >
    Shell spawned em agent container (user=%user.name 
    container=%container.id command=%proc.cmdline)
  priority: WARNING

- rule: Agent Unauthorized Outbound Connection
  desc: Conexão outbound não autorizada
  condition: >
    outbound and
    container.image.repository contains "squadx-sandbox" and
    not fd.sip in (allowed_ips)
  output: >
    Conexão outbound não autorizada (dest=%fd.rip:%fd.rport 
    container=%container.id)
  priority: ERROR
```

**Combinado com AppArmor profiles:**

```
# /etc/apparmor.d/squadx-agent-profile
profile squadx-agent-profile flags=(attach_disconnected,mediate_deleted) {
  # Denegar tudo por default
  deny /** wlx,
  
  # Permitir apenas workspace
  /workspace/** rw,
  /tmp/** rw,
  
  # Denegar network
  deny network,
  
  # Denegar capabilities
  deny capability,
}
```

### 5.4 Camada 3: Secret Management

**Secret management** deve usar HashiCorp Vault com **Agent Sidecar pattern**:

```yaml
# Pod com Vault Agent Sidecar
apiVersion: v1
kind: Pod
metadata:
  name: agent-pod
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "squadx-agent"
    vault.hashicorp.com/agent-inject-secret-db: "database/creds/readonly"
spec:
  containers:
  - name: agent
    image: squadx-sandbox:latest
    volumeMounts:
    - name: vault-secrets
      mountPath: /vault/secrets
      readOnly: true
  volumes:
  - name: vault-secrets
    emptyDir:
      medium: Memory  # tmpfs - nunca toca disco
```

**Secrets renderizados:**
- Para volumes backed por tmpfs (nunca tocando disco)
- Auto-rotacionados com **15-minute TTLs**
- Auto-revogados após conclusão de task

**❌ NUNCA usar environment variables** para secrets de produção:
- Visíveis via `docker inspect`
- Visíveis via `/proc`
- Aparecem em logs
- Aparecem em crash dumps

**Controle de Network Egress** é o complemento crítico:  
Se agents não podem alcançar URLs arbitrárias, não podem exfiltrar secrets.

### 5.5 Camada 4: Audit Logging

**Audit logging** para compliance SOC 2 deve capturar:

1. **Todo comando executado:**
   - Args completos
   - Exit codes
   - Duração
   - User/agent que executou

2. **Todas as mudanças de arquivo:**
   - Before/after SHA-256 hashes
   - Timestamp
   - Path completo

3. **Todos os network requests:**
   - Destination IP/port
   - Bytes transferidos
   - Response codes

4. **Todas as chamadas de API:**
   - Endpoints
   - Payloads (sanitizados)

5. **Todos os prompts/tool calls de LLM:**
   - Modelo usado
   - Tokens
   - Latência

**Logs devem ser tamper-proof:**

```python
import hashlib
import hmac
import json

class TamperProofLogger:
    def __init__(self, secret_key):
        self.secret_key = secret_key
        self.previous_hash = "0" * 64
    
    def log_event(self, event):
        event['previous_hash'] = self.previous_hash
        event['timestamp'] = time.time()
        
        # Serializar e assinar
        event_json = json.dumps(event, sort_keys=True)
        signature = hmac.new(
            self.secret_key.encode(),
            event_json.encode(),
            hashlib.sha256
        ).hexdigest()
        
        event['signature'] = signature
        self.previous_hash = signature
        
        # Escrever para S3 com Object Lock
        s3.put_object(
            Bucket='squadx-audit-logs',
            Key=f'{date}/{event_id}.json',
            Body=json.dumps(event),
            ObjectLockMode='GOVERNANCE',
            ObjectLockRetainUntilDate=datetime.now() + timedelta(days=2555)  # 7 anos
        )
```

**Escrever para storage append-only:**
- S3 Object Lock ou storage WORM-compliant
- Assinar cada entry com HMAC-SHA256
- Encaminhar via sidecar collector que agents não podem acessar

**Retenção:** **7 anos** para cobrir:
- SOC 2: 3–12 meses
- HIPAA: 6 anos
- GDPR: requisitos variados

### 5.6 Graduated Trust Levels

**Níveis graduados de confiança** mapeiam operações para tiers de permissão:

| Nível | Operações Permitidas | Requisitos |
|-------|----------------------|------------|
| **0 - Sandboxed** | Análise read-only, sem rede, sem writes, sem secrets | Automático |
| **1 - Código** | Execução de código em sandbox, writes em tmpfs | Automático |
| **2 - Packages** | Egresso filtrado para instalação de pacotes, secrets scoped | Aprovação agent |
| **3+ - APIs** | Acesso a database, cloud APIs | Aprovação humana, credenciais time-limited do Vault, audit trail completo |

**Mapeamento para SquadX:**
- Editar arquivos → Nível 1
- Rodar testes → Nível 1–2
- Instalar dependências → Nível 2
- Acessar APIs → Nível 3

### 5.7 Air-Gapped Deployments

Para **deployments air-gapped**, manter:

1. **Harbor private registry** para imagens Docker
2. **Offline package mirrors:**
   - Verdaccio para npm
   - devpi para PyPI
   - Artifactory para multi-language
3. **Local LLM hosting** via vLLM/Ollama
4. **Offline CVE database feeds** para Trivy

**Transferir imagens/dependências via sneakernet** (mídia USB) de host de staging conectado à internet.

---

## 6. Scaling para 10.000+ Agents Concorrentes: Kubernetes e Smart Caching

### 6.1 Container Startup é o Gargalo Primário

**Container startup é o gargalo primário** para sessões interativas de AI agent. Pesquisa USENIX descobriu que criação e conexão de rede respondem por **90% do tempo de startup do Docker**.

**Estratégias de Mitigação:**

#### 6.1.1 Warm Container Pools

**Pools de containers warm:** Pré-criar 50–200 containers com networking já configurado.

Google Agent Sandbox entrega latência sub-segundo com warm pools — **até 90% de melhoria** vs cold starts.

```python
# Pseudocódigo de warm pool
class WarmContainerPool:
    def __init__(self, pool_size=100):
        self.available = queue.Queue()
        self.in_use = {}
        
        # Pré-criar containers
        for _ in range(pool_size):
            container = docker.create_container(
                image='squadx-sandbox:latest',
                network='agent-net',
                # ... hardening completo
            )
            self.available.put(container)
    
    def acquire(self, task_id):
        if self.available.empty():
            # Criar on-demand se pool esgotado
            container = self._create_container()
        else:
            container = self.available.get()
        
        self.in_use[task_id] = container
        container.start()
        return container
    
    def release(self, task_id):
        container = self.in_use.pop(task_id)
        container.stop()
        container.remove()
        
        # Repor pool
        new_container = self._create_container()
        self.available.put(new_container)
```

#### 6.1.2 Snapshot Restoration

**Snapshot restoration:** Firecracker pode restaurar de snapshots em **<5ms**.

- GKE Pod Snapshots trazem checkpoint/restore para Kubernetes
- E2B usa template snapshots para ~150ms de criação de sandbox

```bash
# Criar snapshot de Firecracker VM
firecracker-ctl snapshot create \
  --vm-id template-python \
  --snapshot-path /snapshots/python-base.snap \
  --mem-file-path /snapshots/python-base.mem

# Restaurar de snapshot (<5ms)
firecracker-ctl snapshot load \
  --snapshot-path /snapshots/python-base.snap \
  --mem-file-path /snapshots/python-base.mem
```

#### 6.1.3 Otimização de Imagens

**Otimização de imagens:**

- **Alpine-based images** startam 2–5x mais rápido que Ubuntu
- **"Golden images" pré-construídas** com dependências comuns:
  - `squadx-python-base`
  - `squadx-node-base`
  - `squadx-fullstack-base`
- Eliminam tempo de instalação
- **Pré-pull** images para todos os nodes via DaemonSets

```dockerfile
# Golden image exemplo
FROM python:3.11-alpine

# Instalar deps comuns UMA VEZ
RUN pip install --no-cache-dir \
    requests pandas numpy flask fastapi \
    pytest black flake8

# Criar user não-root
RUN adduser -D -u 1000 agent
USER agent

# Workspace
WORKDIR /workspace
```

#### 6.1.4 BuildKit Cache Mounts

**BuildKit cache mounts:** Mesmo quando layer cache misses, `--mount=type=cache` preserva pacotes baixados.

```dockerfile
# Sem cache mount: 8 minutos cada build
RUN pip install -r requirements.txt

# Com cache mount: 8 min primeira vez, 1.5 min depois
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt
```

### 6.2 Estimativas de Densidade

**Estimativas de densidade** em instância cloud típica (16 vCPU, 64 GB RAM):

- **~200 containers** @ 256 MB cada (Python agent com dependências)
- **~100 containers** @ 512 MB cada (ambiente dev completo)
- **~400 containers** @ 128 MB cada (execução de script minimal)

**Rodar >100 containers requer kernel tuning:**

```bash
# /etc/sysctl.d/99-squadx.conf
kernel.pid_max = 4194304
net.ipv4.neigh.default.gc_thresh1 = 80000
net.ipv4.neigh.default.gc_thresh2 = 90000
net.ipv4.neigh.default.gc_thresh3 = 100000
fs.file-max = 2097152
net.netfilter.nf_conntrack_max = 1048576
net.ipv4.ip_local_port_range = 10000 65535
```

### 6.3 Kubernetes com KEDA

**Kubernetes com KEDA** é a camada de orquestração recomendada.

**KEDA** fornece autoscaling event-driven baseado em queue depth — superior para workloads bursty de AI agents (70+ scalers built-in).

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: agent-scaler
spec:
  scaleTargetRef:
    name: agent-deployment
  minReplicaCount: 10    # Baseline
  maxReplicaCount: 1000  # Burst capacity
  
  triggers:
  - type: rabbitmq
    metadata:
      queueName: agent-tasks
      queueLength: "5"  # Target: 5 tasks per pod
  
  advanced:
    horizontalPodAutoscalerConfig:
      behavior:
        scaleUp:
          stabilizationWindowSeconds: 0   # Agressivo
          policies:
          - type: Percent
            value: 200  # Dobrar a cada minuto
            periodSeconds: 60
        scaleDown:
          stabilizationWindowSeconds: 300  # Conservador
          policies:
          - type: Pods
            value: 10
            periodSeconds: 60
```

**Karpenter** lida com provisionamento de nodes.

**RuntimeClass** habilita seleção de runtime por workload:

```yaml
apiVersion: node.k8s.io/v1
kind: RuntimeClass
metadata:
  name: firecracker
handler: kata-fc  # Kata Containers com Firecracker

---
apiVersion: v1
kind: Pod
metadata:
  name: trusted-agent
spec:
  runtimeClassName: firecracker  # Usar Firecracker para este pod
  containers:
  - name: agent
    image: squadx-sandbox:latest
```

### 6.4 Google Agent Sandbox

**Google Agent Sandbox**, anunciado no KubeCon NA 2025, é um primitivo Kubernetes purpose-built para execução de código de AI agent.

- Construído em gVisor/Kata
- Python SDK
- Agora um projeto CNCF
- Mira diretamente o caso de uso do SquadX

### 6.5 Custo em Escala

**Custo em escala:**

**1.000 execuções/dia** (média 5min, 0.5 vCPU + 512MB):
- **~$19/mês** no Fargate Spot
- **~$108/mês** em instância EC2 Spot dedicada

**100.000 execuções/dia:**
- Cluster EC2 Spot de 10–30 nodes com instâncias Graviton (ARM)
- **~$1.000–$3.000/mês**

**Custos ocultos a orçar:**
- NAT Gateway: $0.045/hr + $0.045/GB
- CloudWatch Logs: $0.50/GB ingerido
- Data transfer

Tipicamente adicionam **30–50% aos custos brutos de compute**.

---

## 7. Integração SquadX Live: xterm.js e WebSocket Streaming

### 7.1 xterm.js: Padrão da Indústria

Para o feature "SquadX Live" (streaming do que agents estão fazendo em tempo real), **xterm.js** é o padrão da indústria para terminal streaming.

**Usado por:**
- VS Code
- Azure Cloud Shell
- Portainer
- Proxmox VE

**Features:**
- Rendering acelerado por GPU
- Sobre WebSockets
- Zero dependências

### 7.2 Stack de Implementação

```
Browser (xterm.js) ↔ WebSocket ↔ API Gateway ↔ Docker Attach API ↔ Container
```

**Conectar a containers via endpoint WebSocket attach do Docker:**

```javascript
// Frontend - xterm.js
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';

const term = new Terminal({
  cursorBlink: true,
  fontSize: 14,
  theme: { background: '#1e1e1e' }
});

const fitAddon = new FitAddon();
term.loadAddon(fitAddon);
term.open(document.getElementById('terminal'));
fitAddon.fit();

const ws = new WebSocket(
  `wss://api.squadx.dev/agents/${agentId}/terminal`
);

ws.onmessage = (event) => {
  term.write(event.data);
};

term.onData((data) => {
  ws.send(data);
});
```

```python
# Backend - WebSocket proxy para Docker
import asyncio
import websockets
import docker

async def terminal_proxy(websocket, agent_id):
    client = docker.from_env()
    container = client.containers.get(agent_id)
    
    # Attach ao container via WebSocket
    exec_id = container.exec_run(
        '/bin/bash',
        stdin=True,
        tty=True,
        stream=True,
        socket=True
    )
    
    sock = exec_id.output
    
    async def send_to_browser():
        while True:
            data = sock.recv(4096)
            if not data:
                break
            await websocket.send(data.decode('utf-8', errors='ignore'))
    
    async def recv_from_browser():
        async for message in websocket:
            sock.sendall(message.encode('utf-8'))
    
    await asyncio.gather(
        send_to_browser(),
        recv_from_browser()
    )
```

### 7.3 Visualização de Mudanças de Filesystem

Para **visualização de mudanças de filesystem**, usar `inotifywait` dentro de containers para emitir change events:

```bash
# Dentro do container
inotifywait -m -r -e modify,create,delete /workspace | while read path action file; do
  echo "{\"type\":\"fs_change\",\"action\":\"$action\",\"path\":\"$path/$file\"}" | \
    curl -X POST http://api/events -d @-
done
```

### 7.4 Visualização de Browser Automation

Para **visibilidade de browser automation**, noVNC (VNC server dentro de container + noVNC client no browser) entrega **30–60 FPS** de desktop streaming:

```dockerfile
# Container com VNC para browser automation
FROM selenium/standalone-chrome:latest

# Instalar noVNC
RUN apt-get update && apt-get install -y novnc websockify

# Expor VNC via WebSocket
CMD ["/opt/bin/entry_point.sh"]
```

```javascript
// Frontend - noVNC client
import RFB from '@novnc/novnc/core/rfb';

const rfb = new RFB(
  document.getElementById('screen'),
  `wss://api.squadx.dev/agents/${agentId}/vnc`
);
```

### 7.5 Developer Experience para Produção

Para **DX de produção**, fornecer:

**devcontainer.json** para setup local com um clique:

```json
{
  "name": "SquadX Agent Dev",
  "image": "squadx-sandbox:latest",
  "features": {
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  },
  "forwardPorts": [8000, 5173],
  "postCreateCommand": "pip install -r requirements.txt",
  "customizations": {
    "vscode": {
      "extensions": [
        "ms-python.python",
        "ms-azuretools.vscode-docker"
      ]
    }
  }
}
```

**Docker Compose** para desenvolvimento local com constraints idênticos a produção:

```yaml
version: '3.8'
services:
  agent-dev:
    build: .
    read_only: true
    tmpfs:
      - /tmp:size=100M,noexec,nosuid
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    mem_limit: 512m
    cpus: 1.0
    pids_limit: 256
    volumes:
      - ./workspace:/workspace:rw
    networks:
      - agent-net

networks:
  agent-net:
    driver: bridge
```

**Multi-stage Dockerfiles** onde stage dev inclui ferramentas de debug e stage prod é minimal:

```dockerfile
# Stage base
FROM python:3.11-alpine AS base
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Stage dev (com debug tools)
FROM base AS dev
RUN pip install --no-cache-dir pytest ipdb black flake8
CMD ["python", "-m", "debugpy", "--listen", "0.0.0.0:5678", "app.py"]

# Stage prod (minimal)
FROM base AS prod
COPY . .
USER 1000:1000
CMD ["python", "app.py"]
```

---

## 8. Arquitetura Recomendada: Abordagem Faseada de Docker para MicroVMs

A decisão entre Docker, gVisor, Firecracker e serviços gerenciados depende do estágio atual do SquadX e timeline:

### 8.1 Fase 1 — MVP (meses 0–3): Docker Hardened + LiteLLM

**Começar com LiteLLM** para LLM routing e slim wrapper sobre Docker para sandboxing.

**Aplicar hardening completo de Docker:**
- `--cap-drop=ALL`
- `--read-only`
- `--user 1000:1000`
- `no-new-privileges`
- Perfis seccomp
- Proxy de filtragem de rede
- Injeção de secrets baseada em Vault

**Deploy Falco** para runtime monitoring.

**Resultado:** Produto funcional e razoavelmente seguro em **semanas, não meses**.

**Risco aceito:** Kernel compartilhado significa que container escapes são teoricamente possíveis. Mitigar com user namespace remapping e patching agressivo.

**Checklist de Implementação:**

```bash
# 1. Setup LiteLLM para LLM routing
pip install litellm

# 2. Criar imagem base hardened
docker build -t squadx-sandbox:v1 -f Dockerfile.hardened .

# 3. Deploy Falco
helm install falco falcosecurity/falco \
  --set falco.rules_file=/etc/falco/squadx-rules.yaml

# 4. Setup Vault
vault secrets enable -path=squadx kv-v2

# 5. Configurar network filtering
iptables -A FORWARD -i agent-br0 -o eth0 -j ACCEPT
iptables -A FORWARD -i agent-br0 -j DROP
```

### 8.2 Fase 2 — Production Hardening (meses 3–6): gVisor Runtime

**Trocar runtime padrão `runc` do Docker para `runsc` do gVisor** — **mudança de configuração de uma linha** em Docker daemon config ou Kubernetes RuntimeClass.

Adiciona imediatamente isolamento de kernel user-space (reduzindo exposição de syscall do host de ~300 para ~55) sem mudar código de aplicação.

Aceitar overhead de I/O de 10–30% como custo de segurança.

**Implementação:**

```json
// /etc/docker/daemon.json
{
  "runtimes": {
    "runsc": {
      "path": "/usr/local/bin/runsc"
    }
  },
  "default-runtime": "runsc"
}
```

```bash
# Reinstalar Docker com gVisor
sudo systemctl restart docker

# Testar
docker run --runtime=runsc alpine uname -a
```

**Kubernetes RuntimeClass:**

```yaml
apiVersion: node.k8s.io/v1
kind: RuntimeClass
metadata:
  name: gvisor
handler: runsc

---
apiVersion: v1
kind: Pod
spec:
  runtimeClassName: gvisor
  containers:
  - name: agent
    image: squadx-sandbox:latest
```

**Risco aceito:** gVisor não fornece isolamento em nível de hardware. Uma vulnerabilidade de kernel no Sentry do gVisor poderia ser explorada.

### 8.3 Fase 3 — Enterprise Multi-Tenancy (meses 6–12): Firecracker/Kata

**Deploy Kata Containers com Cloud Hypervisor** no Kubernetes para o boundary de isolamento mais forte.

Usar **Firecracker snapshot/restore** para warm starts sub-5ms.

Rodar em instâncias bare-metal (AWS `.metal`, GCP C2D, ou hardware on-premises).

Satisfaz os requisitos de segurança enterprise mais rigorosos — código de diferentes clientes compartilha hosts físicos com segurança através de isolamento KVM em nível de hardware.

**Implementação:**

```bash
# 1. Instalar Kata Containers
wget https://github.com/kata-containers/kata-containers/releases/download/3.2.0/kata-static-3.2.0-amd64.tar.xz
sudo tar -xvf kata-static-3.2.0-amd64.tar.xz -C /
sudo ln -s /opt/kata/bin/kata-runtime /usr/local/bin/kata-runtime

# 2. Configurar Cloud Hypervisor como hypervisor
sudo mkdir -p /etc/kata-containers/
sudo cat > /etc/kata-containers/configuration.toml <<EOF
[hypervisor.clh]
path = "/opt/kata/bin/cloud-hypervisor"
kernel = "/opt/kata/share/kata-containers/vmlinux.container"
image = "/opt/kata/share/kata-containers/kata-containers.img"
EOF

# 3. Kubernetes RuntimeClass
kubectl apply -f - <<EOF
apiVersion: node.k8s.io/v1
kind: RuntimeClass
metadata:
  name: kata-fc
handler: kata-qemu
EOF

# 4. Deploy pod com Kata
kubectl apply -f - <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: kata-agent
spec:
  runtimeClassName: kata-fc
  containers:
  - name: agent
    image: squadx-sandbox:latest
EOF
```

**Opção Paralela:** Integrar infraestrutura Firecracker gerenciada da E2B para faster time-to-market, transicionando para self-hosted quando economia de escala ou requisitos air-gap exigirem.

### 8.4 Framework de Decisão

| Requisito | Docker + hardening | gVisor | Firecracker/Kata | E2B (gerenciado) |
|-----------|-------------------|--------|------------------|------------------|
| **Tempo para implementar** | 1–2 semanas | 1 dia (runtime swap) | 2–3 meses | 1 semana |
| **Isolamento p/ código não-confiável** | ⚠️ MVP aceitável | ✅ Forte | ✅ Mais forte | ✅ Mais forte |
| **Performance I/O** | ✅ Nativa | ⚠️ Penalidade 10–30% | ✅ Near-native | ✅ Near-native |
| **Air-gap / on-prem** | ✅ Completo | ✅ Completo | ✅ Completo | ⚠️ BYOC disponível |
| **Complexidade operacional** | Baixa | Baixa | Alta | Muito baixa |
| **SOC 2 / HIPAA compatível** | ✅ Com controles | ✅ Com controles | ✅ Com controles | ✅ Buscando SOC 2 |
| **Custo em escala** | Mais baixo | Baixo | Médio (bare metal) | Mais alto (per-sandbox) |
| **Multi-tenant seguro** | ❌ Não suficiente | ⚠️ Aceitável | ✅ Recomendado | ✅ Recomendado |

### 8.5 Diagrama de Evolução da Arquitetura

```
FASE 1 (MVP - Meses 0-3)
┌─────────────────────────────────────────┐
│ LiteLLM + Custom Agent Framework        │
│   ↓                                     │
│ Docker Hardened (runc)                  │
│   • --cap-drop=ALL                      │
│   • --read-only                         │
│   • Network filtering proxy             │
│   • Vault secrets                       │
│   • Falco monitoring                    │
└─────────────────────────────────────────┘
         ↓ Runtime swap (1 dia)

FASE 2 (Production - Meses 3-6)
┌─────────────────────────────────────────┐
│ LiteLLM + Custom Agent Framework        │
│   ↓                                     │
│ gVisor (runsc)                          │
│   • User-space kernel                   │
│   • ~55 syscalls expostos               │
│   • Drop-in replacement                 │
│   • Mesmo hardening da Fase 1           │
└─────────────────────────────────────────┘
         ↓ Infra upgrade (2-3 meses)

FASE 3 (Enterprise - Meses 6-12)
┌─────────────────────────────────────────┐
│ Custom SDK + LiteLLM                    │
│   ↓                                     │
│ Kata Containers / Firecracker           │
│   • KVM hardware isolation              │
│   • Snapshot/restore <5ms               │
│   • Bare metal instances                │
│   • Strongest isolation                 │
└─────────────────────────────────────────┘
```

---

## 9. Conclusão: 3 Achados Críticos para Arquitetura do SquadX

O panorama de sandboxing para execução de código de AI agents amadureceu rapidamente. Três achados se destacam como particularmente importantes para decisões de arquitetura do SquadX:

### 9.1 Firecracker MicroVMs Venceram o Consenso de Produção

**Firecracker microVMs venceram o consenso de produção.** Toda plataforma major executando código não-confiável em escala — AWS Lambda, E2B, Fly.io, CodeSandbox — usa Firecracker ou isolamento comparável em nível de hardware.

A migração dolorosa do Gitpod de pods Kubernetes para VMs dedicadas após 6 anos confirma que isolamento apenas de container é insuficiente para multi-tenancy de produção com workloads não-confiáveis.

**SquadX deve planejar Firecracker/Kata como estado final**, mesmo se começando com Docker.

### 9.2 O Gargalo Real é Latência de Provisionamento, Não Inferência do Modelo

**O gargalo real é latência de provisionamento, não inferência do modelo.** A equipe de engenharia do Cursor descobriu que startup de sandbox domina o tempo total de iteração do agent.

O **pattern template-snapshot-restore** (pioneirizado pela E2B e agora disponível via GKE Pod Snapshots) é o pattern arquitetural chave:
1. Pré-configurar ambientes
2. Fazer snapshot
3. Restaurar on-demand em <5ms

Isso deve ser consideração de design first-class, não afterthought.

### 9.3 LiteLLM + Custom Framework: Abordagem Adotada pelo SquadX

**O SquadX adotou LiteLLM para LLM routing com um framework customizado de agentes.** Isso oferece controle total sobre o sandboxing e a execução, sem depender de abstrações de terceiros para isolamento de segurança.

**Vantagens:**
- Routing unificado para 100+ provedores de LLM
- Cost tracking integrado por provider/modelo
- Fallback automático entre provedores
- Sem vendor lock-in em abstrações de execução
- Controle total sobre o sandboxing Docker/gVisor/Firecracker

---

## 10. Recursos e Próximos Passos

### 10.1 Recursos Técnicos

**Docker Security:**
- Docker Security Best Practices: https://docs.docker.com/engine/security/
- Seccomp Profiles: https://docs.docker.com/engine/security/seccomp/
- AppArmor Profiles: https://docs.docker.com/engine/security/apparmor/

**Firecracker:**
- Firecracker GitHub: https://github.com/firecracker-microvm/firecracker
- Firecracker Docs: https://firecracker-microvm.github.io/
- AWS Lambda Firecracker: https://aws.amazon.com/blogs/opensource/firecracker-open-source-secure-fast-microvm-serverless/

**gVisor:**
- gVisor Docs: https://gvisor.dev/docs/
- gVisor GitHub: https://github.com/google/gvisor
- G