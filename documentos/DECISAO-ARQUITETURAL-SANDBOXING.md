# Decisão Arquitetural: Sandboxing de Execução de Código - SquadX

**Data:** Fevereiro 2026  
**Status:** ✅ APROVADO PARA IMPLEMENTAÇÃO  
**Stakeholders:** Edson (CTO), Time IntegrAllTech  
**Contexto:** SquadX.dev - Plataforma de AI Agents que executam código autonomamente

---

## 1. Decisão Final Recomendada

### 🎯 ESTRATÉGIA: "Phased Isolation Upgrade Path"

**Começar simples, escalar conforme necessidade.**

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  AGORA → Docker Hardened (Fase 1)                       │
│    ↓                                                     │
│  Trigger: Cliente Enterprise OU 100+ exec/dia           │
│    ↓                                                     │
│  UPGRADE → gVisor (Fase 2)                              │
│    ↓                                                     │
│  Trigger: SOC 2 Required OU Multi-tenant                │
│    ↓                                                     │
│  FINAL → Firecracker (Fase 3)                           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**POR QUÊ essa estratégia?**

✅ **Time-to-market:** MVP funcional em 1-2 semanas (não 3-6 meses)  
✅ **Risco controlado:** Cada fase adiciona isolamento sem quebrar features  
✅ **Cost-effective:** Não paga por isolamento que não precisa ainda  
✅ **Aprendizado:** Time aprende gradualmente ao invés de big bang  
✅ **Reversível:** Pode voltar atrás se necessário

---

## 2. Roadmap Detalhado com Triggers Objetivos

### FASE 1: Docker Hardened (COMEÇAR AGORA) ⭐

**Quando:** Meses 0-3 (MVP até primeiros clientes beta)

**Stack:**
- Docker com hardening completo
- OpenHands SDK V1 (opcional - acelera dev)
- Falco para runtime monitoring
- HashiCorp Vault para secrets
- Network filtering proxy (Squid/Envoy)

**Configuração Base:**
```bash
docker run \
  --rm --read-only \
  --cap-drop=ALL \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=/etc/docker/seccomp/agent.json \
  --user 1000:1000 \
  --memory=512m --cpus=1.0 --pids-limit=256 \
  --network=agent-sandbox-net \
  --tmpfs /tmp:size=100M,noexec,nosuid \
  -v /workspace:/workspace:rw \
  squadx-sandbox:latest
```

**Deliverables Obrigatórios:**
- [ ] Dockerfile hardened com todas as flags de segurança
- [ ] Network filtering proxy configurado (allowlist: npmjs.org, pypi.org, github.com)
- [ ] Vault integrado (secrets NUNCA em env vars)
- [ ] Falco rules customizadas para agents
- [ ] Seccomp profile customizado
- [ ] AppArmor profile
- [ ] Documentação de segurança para clientes beta

**Tempo de Implementação:** 1-2 semanas

**Custo Mensal (1000 exec/dia):** ~$19-108

**Nível de Segurança:**
- ⚠️ Kernel compartilhado (container escape possível teoricamente)
- ✅ Suficiente para beta e primeiros clientes não-enterprise
- ✅ Defense-in-depth via múltiplas camadas (network, filesystem, capabilities, seccomp)

**TRIGGERS PARA MIGRAR PARA FASE 2:**
```
❌ BLOCKER ABSOLUTO (migrar imediatamente):
   - CVE crítico de container escape publicado E sem patch

⚠️ TRIGGER URGENTE (migrar em 1 semana):
   - Cliente enterprise assinou E exige compliance documentado
   - Container escape detectado em ambiente de produção

✅ TRIGGER PLANEJADO (migrar em 1-2 meses):
   - Execuções/dia > 100 de forma consistente (média 7 dias)
   - Primeiro contrato enterprise > $50K/ano
   - Cliente pede auditoria de segurança
   - Multi-tenant workload (>1 cliente por host)
```

**Métricas a Monitorar (Dashboard Obrigatório):**
```python
# Criar dashboard Grafana com:
- agent_executions_per_day        # Trigger: > 100
- avg_container_startup_time_ms   # Baseline para comparar
- container_escape_attempts       # Falco alerts
- client_tier (beta|pro|enterprise)  # Trigger: primeiro enterprise
```

---

### FASE 2: gVisor Runtime (UPGRADE TÁTICO) ⚡

**Quando:** Quando QUALQUER trigger da Fase 1 acontecer

**O Que Muda:**
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

**Compatibilidade:** ✅ 100% backward compatible (zero mudança de código)

**Impacto:**
- ✅ **1 dia de trabalho** para implementar
- ⚠️ **10-30% slower I/O** (aceitável para segurança)
- ✅ **Zero mudança** de código da aplicação
- ✅ **Isolamento forte** (syscalls expostos: 300+ → 55)

**Deliverables Obrigatórios:**
- [ ] gVisor instalado em todos os hosts (runsc binary)
- [ ] Testes de performance (baseline vs gVisor)
- [ ] Métricas de I/O overhead (deve ser <40%)
- [ ] Rollback plan documentado
- [ ] Comunicação para clientes (upgrade de segurança)
- [ ] Atualizar documentação de compliance

**Tempo de Implementação:** 1 dia dev + 2 dias teste + 1 dia deploy = 4 dias

**Custo Adicional:** +10-30% CPU overhead

**Nível de Segurança:**
- ✅ User-space kernel (host kernel fortemente protegido)
- ✅ Produção do Google (Cloud Run, App Engine, GKE Sandbox)
- ✅ ~55 syscalls expostos ao host (vs ~300+)
- ⚠️ Ainda não é hardware-level isolation

**TRIGGERS PARA MIGRAR PARA FASE 3:**
```
❌ BLOCKER ABSOLUTO (migrar imediatamente):
   - Cliente enterprise exige on-premise E air-gapped
   - Compliance exige hardware-level isolation (auditoria externa)

⚠️ TRIGGER URGENTE (migrar em 2-3 meses):
   - Multi-tenant real (código de 2+ clientes enterprise no mesmo host)
   - Execuções/dia > 1000 de forma consistente
   - Cliente Fortune 500 assinou contrato

✅ TRIGGER PLANEJADO (migrar em 6-12 meses):
   - Receita > $100K MRR
   - >5 clientes enterprise pagando
   - Preparação para SOC 2 Type II
   - I/O overhead do gVisor > 40% (problema de performance)
```

**Métricas a Monitorar:**
```python
# Adicionar ao dashboard:
- gvisor_io_overhead_percent      # Alert se > 40%
- syscall_interception_latency_us # Baseline
- multi_tenant_customers_count    # Trigger: >= 2
- executions_per_day              # Trigger: > 1000
```

---

### FASE 3: Firecracker MicroVMs (PRODUCTION SCALE) 🚀

**Quando:** Quando QUALQUER trigger da Fase 2 acontecer

**Stack Completo:**
- Kata Containers (Kubernetes integration)
- Firecracker como hypervisor
- Bare metal instances (AWS i3.metal ou on-prem)
- Template snapshots para <5ms warm start
- Cloud Hypervisor (alternativa moderna ao QEMU)

**Arquitetura:**
```
┌─────────────────────────────────────────┐
│ Kubernetes Cluster (Orchestration)     │
│   ↓                                     │
│ Kata Containers (CRI Runtime)          │
│   ↓                                     │
│ Firecracker MicroVM (Hypervisor)       │
│   ↓                                     │
│ KVM (Hardware Virtualization)          │
└─────────────────────────────────────────┘
```

**Impacto:**
- ⚠️ **2-3 meses de trabalho** (requer infra significativa)
- ✅ **Performance near-native** (>95% bare metal)
- ✅ **Máximo isolamento possível** (KVM hardware-level)
- ⚠️ **Custo mais alto** (bare metal instances)
- ✅ **Startup <125ms** (com template snapshots <5ms)

**Deliverables Obrigatórios:**
- [ ] Kata Containers instalado e configurado
- [ ] Firecracker templates criados (Python 3.11, Node 22, Full-stack)
- [ ] Snapshot/restore pipeline funcionando (<5ms)
- [ ] Kubernetes RuntimeClass configurado
- [ ] Migração de workloads existentes (zero downtime)
- [ ] Load testing (10K+ concurrent VMs)
- [ ] Disaster recovery plan documentado
- [ ] Auditoria de segurança externa (recomendado para enterprise)

**Tempo de Implementação:** 8-12 semanas

**Custo Mensal Estimado:**
```
Configuração Base (1000 exec/dia):
- 3x i3.metal (AWS): ~$3000/mês
- NAT Gateway: ~$100/mês
- CloudWatch: ~$50/mês
Total: ~$3150/mês

Scale (10K exec/dia):
- 10-20x i3.metal: ~$10K-20K/mês
- Networking: ~$500/mês
- Monitoring: ~$200/mês
Total: ~$11K-21K/mês
```

**Nível de Segurança:**
- ✅ **Máximo possível** (KVM hardware isolation)
- ✅ **Multi-tenant production-safe** (código de diferentes clientes)
- ✅ **AWS Lambda usa isso** (validação máxima da indústria)
- ✅ **SOC 2, HIPAA, ISO 27001 ready**

**Métricas a Monitorar:**
```python
# Dashboard completo:
- microvm_startup_time_ms         # Target: <125ms cold, <5ms warm
- microvm_memory_overhead_mb      # Target: <150MB
- microvm_cpu_overhead_percent    # Target: <5%
- concurrent_microvms_count       # Capacity planning
- snapshot_restore_time_ms        # Target: <5ms
```

---

## 3. Comparação Lado-a-Lado: Quando Usar O Quê

| Critério | Docker Hardened | gVisor | Firecracker |
|----------|----------------|--------|-------------|
| **Usar quando** | MVP, beta, <100 exec/dia | Enterprise inicial, compliance | Multi-tenant, escala, máxima segurança |
| **Tempo implementar** | 1-2 semanas | 1 dia (swap runtime) | 2-3 meses |
| **Isolamento** | Namespace (⚠️ kernel shared) | User-space kernel (✅) | KVM hardware (✅✅) |
| **Performance** | Native (✅✅) | -10-30% I/O (⚠️) | ~95% native (✅) |
| **Startup time** | 50-100ms | 50-100ms | <125ms (cold), <5ms (warm) |
| **Memory overhead** | ~5-10 MB | ~10-30 MB | ~30-150 MB |
| **Multi-tenant safe** | ❌ NÃO | ⚠️ Aceitável | ✅ SIM |
| **SOC 2 ready** | ⚠️ Com controles | ✅ Sim | ✅✅ Sim |
| **Custo (1K exec/dia)** | ~$20 | ~$25 | ~$100-150 |
| **Custo (10K exec/dia)** | ~$200 | ~$250 | ~$1000-1500 |
| **Complexidade ops** | Baixa | Baixa | Alta |
| **Expertise necessária** | Docker | Docker + gVisor basics | Kubernetes + KVM + Firecracker |

---

## 4. Opção Pragmática: E2B Managed (Atalho Tático)

**Quando considerar E2B ao invés de self-hosted:**

✅ **SIM, usar E2B se:**
- Você quer MVP em **1 semana** (não 1-2 semanas)
- Time pequeno (<5 devs) sem expertise DevOps
- Foco total em features do produto (não em infra)
- Budget inicial OK (~$500-2000/mês)
- Não tem requisitos air-gap

❌ **NÃO usar E2B se:**
- Cliente exige on-premise / air-gapped
- Custo em escala é preocupação (>10K exec/dia)
- Quer controle total da stack
- Dados extremamente sensíveis (governo, militar)

**Transição E2B → Self-hosted:**
```
Mês 1-3:   E2B managed (validar produto)
Mês 3-6:   Migrar para Docker hardened (reduzir custo)
Mês 6-12:  Upgrade para gVisor (cliente enterprise)
Mês 12+:   Firecracker se multi-tenant
```

**Custo E2B:**
- Free tier: 20h/mês
- Pro: $100/mês (200h)
- Custom enterprise: Negociar

---

## 5. Decision Tree (Fluxograma de Decisão)

```
┌─────────────────────────────────┐
│ Precisa de MVP em <1 semana?    │
└────────┬───────────────┬────────┘
         │ SIM           │ NÃO
         ↓               ↓
    ┌────────┐    ┌──────────────┐
    │ E2B    │    │ Docker       │
    │ Managed│    │ Hardened     │
    └────┬───┘    └──────┬───────┘
         │               │
         └───────┬───────┘
                 ↓
    ┌───────────────────────────┐
    │ Cliente Enterprise?       │
    │ OU >100 exec/dia?         │
    └────────┬────────┬─────────┘
             │ SIM    │ NÃO
             ↓        ↓
        ┌────────┐  ┌──────────┐
        │ gVisor │  │ Continuar│
        └───┬────┘  │ Fase 1   │
            │       └──────────┘
            ↓
    ┌───────────────────────────┐
    │ Multi-tenant?             │
    │ OU >1000 exec/dia?        │
    │ OU SOC 2 Type II?         │
    └────────┬────────┬─────────┘
             │ SIM    │ NÃO
             ↓        ↓
      ┌───────────┐ ┌──────────┐
      │Firecracker│ │ Continuar│
      └───────────┘ │ Fase 2   │
                    └──────────┘
```

---

## 6. Checklist de Implementação por Fase

### ✅ FASE 1: Docker Hardened - Checklist Completo

**Semana 1: Setup Base**
- [ ] Criar Dockerfile hardened (ver template no doc técnico)
- [ ] Configurar seccomp profile customizado
- [ ] Configurar AppArmor profile
- [ ] Setup network bridge isolado
- [ ] Configurar proxy de filtragem (Squid ou Envoy)
- [ ] Testar que npm install / pip install funcionam via proxy

**Semana 2: Segurança & Monitoring**
- [ ] Instalar HashiCorp Vault
- [ ] Migrar todos os secrets para Vault (zero env vars)
- [ ] Instalar Falco com regras customizadas
- [ ] Criar dashboard Grafana com métricas chave
- [ ] Documentar procedimento de incident response
- [ ] Testar rollback em caso de falha

**Semana 3: Testes & Documentação**
- [ ] Load test (100 containers simultâneos)
- [ ] Penetration test básico (tentar escape)
- [ ] Documentação para clientes (security whitepaper)
- [ ] Runbook operacional (troubleshooting)
- [ ] Deploy para ambiente de staging
- [ ] Validar com 3-5 beta customers

**Pronto para produção quando:**
- ✅ Todos os checkboxes acima marcados
- ✅ Load test passou (latência p95 < 200ms)
- ✅ Zero escapes detectados em pen test
- ✅ Falco gerando <5 false positives/dia
- ✅ Startup time médio < 150ms

### ✅ FASE 2: gVisor - Checklist Completo

**Dia 1: Instalação**
- [ ] Instalar runsc binary em todos os hosts
- [ ] Backup da configuração atual do Docker
- [ ] Atualizar /etc/docker/daemon.json
- [ ] Restart Docker daemon (testar em 1 host primeiro)
- [ ] Validar que containers startam com runsc

**Dia 2: Testes A/B**
- [ ] Rodar 50% de workload em runc, 50% em runsc
- [ ] Comparar latências (deve ser <40% overhead)
- [ ] Comparar throughput de I/O
- [ ] Identificar workloads problemáticos (se overhead >40%)

**Dia 3: Rollout Gradual**
- [ ] Deploy para 10% de workload
- [ ] Monitorar por 24h (zero crashes)
- [ ] Deploy para 50% de workload
- [ ] Monitorar por 48h
- [ ] Deploy para 100%

**Dia 4: Validação**
- [ ] Atualizar documentação de compliance
- [ ] Notificar clientes enterprise
- [ ] Atualizar security whitepaper
- [ ] Criar rollback plan se necessário

**Pronto quando:**
- ✅ I/O overhead médio <35%
- ✅ Zero crashes em 7 dias
- ✅ Clientes enterprise notificados

### ✅ FASE 3: Firecracker - Checklist Completo

**Mês 1: Planning & Infra**
- [ ] Provisionar bare metal instances (3x i3.metal para começar)
- [ ] Setup Kubernetes cluster
- [ ] Instalar Kata Containers
- [ ] Configurar Firecracker como hypervisor
- [ ] Criar templates de base (Python, Node, Full-stack)

**Mês 2: Templates & Snapshots**
- [ ] Otimizar boot time dos templates (<200ms)
- [ ] Implementar snapshot/restore pipeline
- [ ] Validar warm start <10ms
- [ ] Load test (1000 concurrent microVMs)
- [ ] Tuning de performance

**Mês 3: Migration & Production**
- [ ] Migrar 10% de workload
- [ ] Monitorar estabilidade (1 semana)
- [ ] Migrar 50% de workload
- [ ] Migrar 100%
- [ ] Auditoria externa de segurança (opcional)

**Pronto quando:**
- ✅ Cold start <125ms, warm start <5ms
- ✅ 1000+ concurrent microVMs rodando
- ✅ Zero escapes em pen test externo
- ✅ SOC 2 audit passed (se aplicável)

---

## 7. Riscos e Mitigações

### Riscos da FASE 1 (Docker Hardened)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Container escape via CVE | Baixa | CRÍTICO | Patching agressivo, Falco alerts, user namespace remapping |
| Cliente recusa por falta de compliance | Média | Alto | Documentar controles, preparar upgrade para Fase 2 |
| Performance degrada com muitos containers | Média | Médio | Warm pools, monitorar densidade |
| Secrets vazam via logs | Baixa | Alto | NUNCA env vars, apenas Vault, scan de logs |

### Riscos da FASE 2 (gVisor)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| I/O overhead >40% inaceitável | Média | Alto | A/B testing, identificar workloads problemáticos, selective runtime |
| Bug no gVisor causa crashes | Baixa | Médio | Rollback plan, canary deployments |
| Syscall não implementado quebra app | Baixa | Médio | Testing extensivo, lista de syscalls suportados |

### Riscos da FASE 3 (Firecracker)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Complexidade operacional alta | Alta | Médio | Contratar DevOps expert, documentação extensiva |
| Custo >budget | Média | Alto | ROI analysis, usar Spot instances |
| Migração quebra workloads | Baixa | CRÍTICO | Testes extensivos, rollback plan, zero-downtime migration |
| Time não tem expertise | Alta | Médio | Treinamento, consultoria externa, ou usar E2B managed |

---

## 8. Estimativa de Custos Total

### Custo por Fase (1000 execuções/dia, 5min avg)

| Fase | Compute | Networking | Monitoring | Pessoal | Total/mês |
|------|---------|------------|------------|---------|-----------|
| **Fase 1** | $20-100 | $10 | $20 | 40h engenharia | ~$150 + engenharia |
| **Fase 2** | $25-130 (+30%) | $10 | $20 | 32h manutenção | ~$185 + manutenção |
| **Fase 3** | $3000-5000 | $100 | $50 | 160h inicial, 60h maint | ~$5150 + pessoal |

### Break-even Analysis

**Quando Firecracker se paga:**
```
E2B managed: $0.10/hora de sandbox
Self-hosted Firecracker: $0.02/hora (amortizado)

Break-even: ~50K horas/mês = ~70 execuções/hora 24/7
            = ~1700 execuções/dia

Conclusão: Firecracker self-hosted vale a pena com >1500 exec/dia
```

---

## 9. Recomendação Final do CTO

### Para o SquadX, minha recomendação é:

```
📅 SEMANA 1-2 (AGORA):
   ✅ Implementar Docker Hardened (Fase 1)
   ✅ NÃO usar OpenHands SDK ainda (adiciona complexidade)
   ✅ Focar em hardening completo + Vault + Falco
   ✅ Criar security whitepaper para clientes

📅 MÊS 3-4 (quando trigger atingido):
   ⚡ Upgrade para gVisor (Fase 2)
   ⚡ 1 dia de trabalho apenas
   ⚡ Comunicar upgrade de segurança para clientes

📅 MÊS 6-12 (se multi-tenant ou >1K exec/dia):
   🚀 Migrar para Firecracker (Fase 3)
   🚀 Considerar contratar DevOps specialist
   🚀 Ou usar E2B managed se budget permitir

🎯 ALTERNATIVA PRAGMÁTICA:
   Se time <5 pessoas E foco 100% em features:
   → Usar E2B managed nos primeiros 6 meses
   → Migrar para self-hosted quando >1500 exec/dia
```

### Por que NÃO começar com Firecracker direto?

❌ **2-3 meses de trabalho** antes de primeiro cliente  
❌ **Expertise que time não tem** ainda  
❌ **Over-engineering** para MVP  
❌ **Custo alto** sem receita  
❌ **Risco de never ship** (perfeição é inimiga do bom)

### Por que NÃO usar OpenHands SDK?

⚠️ **Adiciona camada de abstração** que precisaremos debugar  
⚠️ **Security controls não são production-ready** (admitem isso)  
⚠️ **Vendor lock-in** em abstrações deles  
✅ **Melhor:** Construir slim wrapper sobre Docker puro, mais controle

---

## 10. Próximos Passos Imediatos (Esta Semana)

### Segunda-feira:
- [ ] Edson aprova esta decisão arquitetural
- [ ] Compartilhar com time técnico
- [ ] Criar epic no Jira/Linear: "Fase 1: Docker Hardened Sandbox"

### Terça-feira:
- [ ] Dev 1: Criar Dockerfile hardened (usar template do doc técnico)
- [ ] Dev 2: Setup network filtering proxy
- [ ] DevOps: Instalar Vault em staging

### Quarta-feira:
- [ ] Dev 1: Seccomp + AppArmor profiles
- [ ] Dev 2: Integração Vault (migrar secrets)
- [ ] DevOps: Instalar Falco + rules

### Quinta-feira:
- [ ] Load testing (100 containers simultâneos)
- [ ] Ajustar configs baseado em resultados
- [ ] Criar dashboard Grafana

### Sexta-feira:
- [ ] Deploy para staging
- [ ] Documentação operacional
- [ ] Planning da próxima sprint

---

## 11. Critérios de Sucesso

### Fase 1 é sucesso quando:
✅ Startup time médio <150ms  
✅ Zero container escapes em 30 dias  
✅ Falco <5 false positives/dia  
✅ 3 clientes beta validaram segurança  
✅ Security whitepaper publicado

### Fase 2 é sucesso quando:
✅ I/O overhead <35%  
✅ Zero crashes em produção (7 dias)  
✅ Cliente enterprise aceita compliance docs  
✅ Syscalls suportados cobrem 100% use cases

### Fase 3 é sucesso quando:
✅ Cold start <125ms, warm <5ms  
✅ 1000+ concurrent microVMs estáveis  
✅ Auditoria externa de segurança passed  
✅ Custo/execução <50% do E2B managed

---

## 12. Glossário de Termos

**Container Escape:** Vulnerabilidade que permite processo dentro do container acessar host  
**Syscall:** System call - chamada que aplicação faz ao kernel do SO  
**Seccomp:** Filtro de syscalls no Linux  
**AppArmor:** Sistema de controle de acesso obrigatório (MAC) no Linux  
**Hardening:** Processo de tornar sistema mais seguro removendo superfície de ataque  
**Multi-tenant:** Múltiplos clientes compartilhando mesma infraestrutura  
**Warm start:** Inicialização rápida reutilizando estado pre-carregado  
**Cold start:** Inicialização completa do zero

---

## Aprovações

**CTO (Edson):**  
Assinatura: ________________  
Data: ____/____/2026

**Arquiteto de Segurança:**  
Assinatura: ________________  
Data: ____/____/2026

**Tech Lead:**  
Assinatura: ________________  
Data: ____/____/2026

---

**FIM DO DOCUMENTO**