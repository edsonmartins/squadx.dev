# 🎯 Decisões Técnicas - Parte 2 - SquadX.dev

## 8. Deployment

### ✅ Decisão: Docker Compose (Dev) + Kubernetes (Prod)

**Development:**

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_PASSWORD: squadx_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  backend:
    build: ./backend
    command: uvicorn app.main:app --reload --host 0.0.0.0
    ports:
      - "8000:8000"
    depends_on:
      - postgres
      - redis
    environment:
      DATABASE_URL: postgresql://...
      REDIS_URL: redis://...

  frontend:
    build: ./frontend
    command: npm run dev
    ports:
      - "3000:3000"
    depends_on:
      - backend

  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports:
      - "3001:3000"
    depends_on:
      - prometheus

# Um único comando: docker-compose up
```

**Production (Kubernetes):**

```yaml
# k8s/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: squadx-backend
spec:
  replicas: 3  # High availability
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: squadx/backend:latest
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: url
        livenessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8000
          initialDelaySeconds: 10
          periodSeconds: 5
```

**Justificativa:**
- ✅ **Dev/Prod parity:** Mesmo stack
- ✅ **Simplicidade (dev):** `docker-compose up` e pronto
- ✅ **Escalabilidade (prod):** Kubernetes auto-scale, self-healing
- ✅ **Portabilidade:** Funciona EKS, GKE, AKS

---

### ✅ Decisão: Cloud Provider - AWS (Preferred)

**Por que AWS:**

1. **Ecossistema completo:**
   - ECS/EKS (containers)
   - RDS (database managed)
   - ElastiCache (Redis managed)
   - S3 (storage)
   - CloudFront (CDN)
   - Route53 (DNS)
   - Secrets Manager
   - CloudWatch

2. **Compliance:**
   - SOC2 Type II certified
   - HIPAA eligible
   - LGPD compliant (São Paulo region)

3. **Familiaridade:**
   - Mercado brasileiro conhece
   - Muitos devs têm experiência
   - Farta documentação em PT-BR

4. **Global presence:**
   - 30+ regions
   - São Paulo region (latência baixa BR)
   - Multi-region easy

**Services específicos:**

```yaml
Compute:
  - ECS Fargate: Backend/Frontend containers
    (serverless, pay-per-use, no EC2 management)

Database:
  - RDS PostgreSQL: Multi-AZ, automated backups
    (db.t3.medium para início, escala conforme crescimento)

Cache:
  - ElastiCache Redis: Cluster mode enabled
    (cache.t3.small para início)

Storage:
  - S3: Artifacts, logs, backups
    (lifecycle policy: delete after 90 days)

CDN:
  - CloudFront: Frontend static assets
    (edge locations no Brasil)

Networking:
  - VPC: Private network
  - ALB: Load balancer
  - Route53: DNS

Security:
  - IAM: Identity management
  - KMS: Encryption keys
  - Secrets Manager: API keys, DB passwords
  - WAF: DDoS protection (via CloudFlare first)

Monitoring:
  - CloudWatch: Logs, basic metrics
  - Grafana Cloud: Advanced dashboards (hybrid)
```

**Custos estimados (produção):**

```
MVP (100 usuários):
  - ECS Fargate (2 vCPU, 4GB): $50/mês
  - RDS PostgreSQL (db.t3.small): $30/mês
  - ElastiCache (cache.t3.small): $15/mês
  - S3 + CloudFront: $10/mês
  - Data transfer: $10/mês
  - CloudWatch: $5/mês
  Total: ~$120/mês

Growth (1K usuários):
  - ECS Fargate (4 vCPU, 8GB): $150/mês
  - RDS (db.t3.medium + replica): $120/mês
  - ElastiCache (cache.t3.medium): $50/mês
  - S3 + CloudFront: $30/mês
  - Data transfer: $50/mês
  - CloudWatch: $20/mês
  Total: ~$420/mês

Scale (10K usuários):
  - ECS Fargate (16 vCPU, 32GB): $600/mês
  - RDS (db.r5.large + 2 replicas): $500/mês
  - ElastiCache (cache.r5.large): $200/mês
  - S3 + CloudFront: $100/mês
  - Data transfer: $200/mês
  - CloudWatch + Grafana: $100/mês
  Total: ~$1.700/mês
```

**Alternativa (se custo for crítico):**

**GCP:**
- Cloud Run (mais barato que ECS)
- Cloud SQL (similar RDS)
- Memorystore (Redis)
- Cloud CDN
- ~30% mais barato que AWS
- Porém: Ecosystem menor, menos familiar no BR

---

### ✅ Decisão: CI/CD - GitHub Actions

**Pipeline completo:**

```yaml
# .github/workflows/deploy.yml
name: Deploy to Production

on:
  push:
    branches: [main]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      
      - name: Install dependencies
        run: |
          pip install poetry
          poetry install
      
      - name: Lint
        run: |
          poetry run black --check .
          poetry run flake8 .
          poetry run mypy .
      
      - name: Test
        run: poetry run pytest --cov
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Node
        uses: actions/setup-node@v3
        with:
          node-version: '20'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Lint
        run: npm run lint
      
      - name: Type check
        run: npm run type-check
      
      - name: Test
        run: npm run test
      
      - name: Build
        run: npm run build

  deploy-backend:
    needs: [test-backend]
    runs-on: ubuntu-latest
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: us-east-1
      
      - name: Login to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin <ecr-url>
      
      - name: Build and push
        run: |
          docker build -t squadx/backend:${{ github.sha }} ./backend
          docker tag squadx/backend:${{ github.sha }} <ecr-url>/backend:latest
          docker push <ecr-url>/backend:latest
      
      - name: Deploy to ECS
        run: |
          aws ecs update-service --cluster squadx-prod --service backend --force-new-deployment

  deploy-frontend:
    needs: [test-frontend]
    runs-on: ubuntu-latest
    steps:
      - name: Build
        run: npm run build
      
      - name: Deploy to S3
        run: aws s3 sync ./out s3://squadx-frontend-prod
      
      - name: Invalidate CloudFront
        run: aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

**Justificativa:**
- ✅ **Native:** Integrado com GitHub
- ✅ **Free:** 2.000 min/mês grátis (private repos)
- ✅ **Matrix builds:** Test em múltiplas versões
- ✅ **Secrets management:** Encrypted
- ✅ **Community:** Muitas actions prontas

**Alternativas rejeitadas:**
- ❌ **GitLab CI:** Se não usar GitLab
- ❌ **CircleCI:** Pago depois de free tier
- ❌ **Jenkins:** Self-hosted, complexo

---

## 9. LLM Integration

### ✅ Decisão: LiteLLM (Multi-Provider Router)

**Por que LiteLLM:**

```python
# Um único código para 100+ providers
import litellm

# OpenAI
response = await litellm.acompletion(
    model="gpt-4",
    messages=[{"role": "user", "content": prompt}]
)

# Anthropic
response = await litellm.acompletion(
    model="claude-sonnet-4-5",
    messages=[{"role": "user", "content": prompt}]
)

# OpenRouter (agregador)
response = await litellm.acompletion(
    model="anthropic/claude-sonnet-4-5",
    messages=[{"role": "user", "content": prompt}]
)

# Local (Ollama)
response = await litellm.acompletion(
    model="ollama/qwen3:coder",
    messages=[{"role": "user", "content": prompt}],
    api_base="http://localhost:11434"
)
```

**Features essenciais:**

1. **Unified interface:**
   - Mesmo código, múltiplos providers
   - Fácil trocar modelo
   - Fácil A/B testing

2. **Automatic fallback:**
```python
litellm.set_verbose = True

response = await litellm.acompletion(
    model="gpt-4",
    messages=[...],
    fallbacks=[
        "claude-sonnet-4-5",
        "gpt-4-turbo"
    ]
)
# Se GPT-4 falhar, tenta Claude automaticamente
```

3. **Cost tracking:**
```python
cost = litellm.completion_cost(
    model="claude-sonnet-4-5",
    completion_tokens=500,
    prompt_tokens=1000
)
# Retorna: 0.018 USD
# Pricing atualizado automaticamente
```

4. **Caching:**
```python
# Cache responses (Redis)
response = await litellm.acompletion(
    model="gpt-4",
    messages=[...],
    caching=True,
    cache_params={
        "ttl": 3600,  # 1 hour
        "cache_key": "custom_key"
    }
)
```

5. **Rate limiting:**
```python
# Automatic rate limiting per provider
litellm.set_max_retries(3)
litellm.set_retry_policy({
    "openai": {
        "rpm": 60,  # requests per minute
        "tpm": 90000  # tokens per minute
    }
})
```

**Alternativas rejeitadas:**

❌ **Direct API calls:**
```python
# Ruim: código diferente por provider
# OpenAI
openai_client = OpenAI()
response = openai_client.chat.completions.create(...)

# Anthropic
anthropic_client = Anthropic()
response = anthropic_client.messages.create(...)

# Não tem fallback, não tem cost tracking
```

❌ **LangChain apenas:**
- Mais pesado
- Menos controle fino
- LiteLLM é usado por LangChain também

---

### ✅ Decisão: Model Strategy

**Recomendação por use case:**

```python
# Agent types → Models recomendados

COORDINATOR_MODEL = "claude-sonnet-4-5"
# Por quê: Melhor reasoning, task planning

FRONTEND_MODEL = "gpt-4o"  # ou "claude-sonnet-4-5"
# Por quê: Excelente para código React/CSS

BACKEND_MODEL = "claude-sonnet-4-5"
# Por quê: Melhor para API design, database

DEVOPS_MODEL = "gpt-4o"
# Por quê: Bom para configs YAML, scripts

QA_MODEL = "gpt-4o-mini"  # ou local
# Por quê: Tests são mais simples, pode usar modelo menor

FULLSTACK_MODEL = "gpt-4o"
# Por quê: General purpose, rápido
```

**Fallback strategy:**

```python
# Produção
PRIMARY = "claude-sonnet-4-5"
FALLBACK_1 = "gpt-4o"
FALLBACK_2 = "gpt-4-turbo"
FALLBACK_3 = "ollama/qwen3:coder"  # Local, sempre funciona

# Se todos falharem, task vai para retry queue
```

**Cost optimization:**

```python
# Use modelo menor para tarefas simples
if task.complexity == "low":
    model = "gpt-4o-mini"  # $0.15/$0.60 per 1M tokens
elif task.complexity == "medium":
    model = "gpt-4o"  # $2.50/$10 per 1M tokens
else:
    model = "claude-sonnet-4-5"  # $3/$15 per 1M tokens

# Ou use local (zero cost)
if client.config.prefer_local:
    model = "ollama/qwen3:coder"  # $0
```

---

### ✅ Decisão: Context Management

**Problema:** LLMs têm context window limitado

**Solução:**

1. **Sliding window (tarefas longas):**
```python
MAX_CONTEXT_TOKENS = 100000  # Claude 4.5: 200K

# Se contexto exceder, manter apenas:
context = {
    "system_prompt": "...",  # Sempre inclui
    "task_description": "...",  # Sempre inclui
    "recent_conversation": messages[-20:],  # Last 20 messages
    "relevant_files": get_top_k_files(k=5),  # RAG
    "execution_history": recent_executions[-10:]
}
```

2. **RAG (Retrieval-Augmented Generation):**
```python
# Embeddings dos arquivos do codebase
embeddings = await generate_embeddings(codebase_files)

# Quando agent precisa de contexto
relevant_files = vector_search(
    query=task.description,
    embeddings=embeddings,
    top_k=5
)

# Adiciona ao contexto
context["files"] = [file.content for file in relevant_files]
```

3. **Summarization (histórico longo):**
```python
# Se conversation > 50 mensagens
if len(conversation) > 50:
    # Summarize older messages
    summary = await llm.summarize(conversation[:30])
    
    context = {
        "summary": summary,
        "recent": conversation[-20:]
    }
```

---

## 10. Git Operations

### ✅ Decisão: GitPython (Automated Git)

**Operações automáticas:**

```python
# client/git/git_manager.py

from git import Repo

class GitManager:
    def __init__(self, workspace_path: str):
        self.repo = Repo(workspace_path)
    
    async def create_feature_branch(self, task_id: int):
        """Create branch for task"""
        branch_name = f"task-{task_id}-auto"
        
        # Ensure clean state
        if self.repo.is_dirty():
            raise Exception("Working directory not clean")
        
        # Create and checkout branch
        self.repo.git.checkout('main')
        self.repo.git.pull()
        new_branch = self.repo.create_head(branch_name)
        new_branch.checkout()
        
        return branch_name
    
    async def commit_changes(
        self,
        files: List[str],
        message: str,
        author: str = "SquadX Agent <agent@squadx.dev>"
    ):
        """Commit changes made by agent"""
        # Stage files
        self.repo.index.add(files)
        
        # Commit
        commit = self.repo.index.commit(
            message=message,
            author=author
        )
        
        return commit.hexsha
    
    async def create_pr(
        self,
        title: str,
        description: str,
        base_branch: str = "main"
    ) -> str:
        """Create PR via GitHub API"""
        # Push branch
        origin = self.repo.remote('origin')
        origin.push(self.repo.active_branch)
        
        # Create PR via GitHub API (using MCP GitHub server)
        pr = await github_mcp.create_pull_request(
            title=title,
            body=description,
            head=self.repo.active_branch.name,
            base=base_branch
        )
        
        return pr.html_url
    
    async def get_diff(self, staged: bool = True) -> str:
        """Get diff of changes"""
        if staged:
            return self.repo.git.diff('--staged')
        else:
            return self.repo.git.diff()
```

**Workflow completo:**

```python
# Agent executa task
async def execute_task(task: Task):
    git = GitManager(workspace_path)
    
    # 1. Create branch
    branch = await git.create_feature_branch(task.id)
    
    # 2. Agent faz mudanças
    changes = await agent.execute(task)
    
    # 3. Get diff (para approval)
    diff = await git.get_diff(staged=False)
    
    # 4. Se approval required, pedir aprovação
    if task.approval_required:
        approved = await request_approval(diff)
        if not approved:
            await git.rollback()
            return
    
    # 5. Commit
    commit_sha = await git.commit_changes(
        files=changes.files,
        message=f"[Agent] {task.title}\n\n{task.description}"
    )
    
    # 6. Create PR (opcional)
    if task.auto_create_pr:
        pr_url = await git.create_pr(
            title=task.title,
            description=f"Completed by SquadX Agent\n\n{task.description}"
        )
        
        return {"commit": commit_sha, "pr": pr_url}
```

**Safety checks:**

```python
async def pre_commit_checks(files: List[str]) -> bool:
    """Run checks before committing"""
    
    # 1. Secret scanner
    secrets_found = await scan_for_secrets(files)
    if secrets_found:
        logger.error(f"Secrets found in files: {secrets_found}")
        return False
    
    # 2. Large files
    for file in files:
        size = os.path.getsize(file)
        if size > 10_000_000:  # 10MB
            logger.error(f"File too large: {file} ({size} bytes)")
            return False
    
    # 3. File types allowed
    allowed_extensions = ['.py', '.js', '.ts', '.tsx', '.css', '.md', '.json']
    for file in files:
        ext = os.path.splitext(file)[1]
        if ext not in allowed_extensions:
            logger.warning(f"Unusual file extension: {file}")
    
    return True
```

---

## 11. Business Model

### ✅ Decisão: SaaS Subscription (NÃO Pay-per-use)

**Por que subscription:**

1. **Previsibilidade (cliente):**
   - Custo fixo mensal
   - Sem surpresas
   - Mais fácil aprovar budget

2. **Previsibilidade (nós):**
   - MRR previsível
   - Churn trackable
   - Valuation baseado em MRR

3. **Simplicidade:**
   - Stripe Subscriptions
   - Auto-renewal
   - Upgrade/downgrade fácil

**Tiers:**

```yaml
Starter Squad - $499/mês:
  squads: 1
  agents_simultaneous: 3
  projects: 5
  compute_hours: 100/mês
  support: Community (Discord)
  approval_workflow: ✅
  observability: Basic
  sla: None

Professional Squad - $1.499/mês:
  squads: 3
  agents_simultaneous: 10
  projects: Unlimited
  compute_hours: 400/mês
  support: Priority (email, 24h SLA)
  approval_workflow: ✅ + Custom
  observability: Advanced
  sla: 99% uptime
  custom_agents: ✅

Enterprise - Custom:
  squads: Unlimited
  agents_simultaneous: Unlimited
  projects: Unlimited
  compute_hours: Negotiated
  support: Dedicated manager
  approval_workflow: ✅ + Custom + Multi-level
  observability: Advanced + Custom dashboards
  sla: 99.9% uptime
  on_premise: ✅
  white_label: ✅
  dedicated_instance: ✅
```

**Alternativas rejeitadas:**

❌ **Pay-per-task:**
- Imprevisível para cliente
- Friction alto ("quanto vai custar?")
- Hard to price (task complexity varia)

❌ **Pay-per-token:**
- Muito granular
- Difícil explicar para não-técnicos
- Cliente não controla (agent usa quantos tokens quiser)

---

### ✅ Decisão: LLM Costs - Pass-through (NÃO incluir na subscription)

**Como funciona:**

```python
# Cliente configura própria API key
client_config = {
    "llm_provider": "openrouter",  # ou "anthropic", "openai", "ollama"
    "api_key": "sk-or-v1-...",  # Cliente paga direto para provider
    "preferred_models": [
        "anthropic/claude-sonnet-4-5",
        "openai/gpt-4o"
    ]
}

# SquadX:
# - Tracks tokens used ✅
# - Shows cost dashboard ✅
# - Alerts if exceeds budget ✅
# - NÃO cobra pela SquadX subscription ✅
```

**Justificativa:**

1. **Transparência:**
   - Cliente vê custo LLM real
   - Sem markup escondido
   - Trust aumenta

2. **Flexibilidade:**
   - Cliente pode usar modelo local (zero cost)
   - Pode negociar direto com Anthropic/OpenAI
   - Pode trocar provider se quiser

3. **Simplicidade:**
   - Não precisa markup/reconciliation
   - Não precisa advanced billing
   - Stripe Subscriptions simples

4. **Margin protection:**
   - Margin 100% na subscription
   - Não exposto a variação de custo LLM
   - Previsível

**Exemplo:**

```
Cliente Professional Squad:
  SquadX subscription: $1.499/mês (fixo)
  +
  LLM costs (variável):
    - 10M tokens Claude: ~$180
    - 5M tokens GPT-4: ~$100
  =
  Total: $1.779/mês

Próximo mês (uso diferente):
  SquadX: $1.499/mês (fixo)
  LLM: $400 (variável)
  Total: $1.899/mês

Cliente sabe: $1.499 fixo + LLM variável
```

---

## 12. Roadmap

### ✅ Decisão: MVP First (8 semanas)

**Por que MVP:**

1. **Validar conceito:**
   - Funciona end-to-end?
   - Agents conseguem fazer tarefas reais?
   - Performance aceitável?

2. **Feedback rápido:**
   - 2 meses vs 6 meses
   - Pivot se necessário
   - Iterar baseado em uso real

3. **Menor risco:**
   - Menor investimento inicial
   - Aprende antes de escalar
   - Evita desperdício

**Scope MVP (mínimo viável):**

```yaml
Backend:
  - Auth JWT: ✅
  - CRUD: Users, Orgs, Projects, Tasks
  - WebSocket: Basic (task assignment, progress)
  - Database: PostgreSQL (schema básico)
  - Cache: Redis (session apenas)

Frontend:
  - Auth: Login, register
  - Dashboard: Lista projetos, tasks
  - Kanban: 3 colunas (todo, doing, done)
  - Task modal: Create, view
  - Real-time: Updates via WebSocket

Client:
  - CLI: setup, start, status, logs
  - WebSocket: Connect, heartbeat, receive tasks
  - Agent: 1 tipo (Fullstack agent)
  - Executor: OpenHands SDK basic
  - Docker: Container isolado (network: none)
  - Git: Commit básico (sem PR)

Observability:
  - Logs: Structured (JSON)
  - Metrics: Task duration, tokens, cost
  - Dashboard: Grafana basic (optional)

FORA DO MVP:
  ❌ Multi-agent coordination (Phase 2)
  ❌ Approval workflow (Phase 2)
  ❌ MCP integration (Phase 2)
  ❌ Advanced RBAC (Phase 3)
  ❌ Billing automation (Phase 3)
  ❌ On-premise (Phase 4)
```

**Milestone MVP:**
```
Edson usando SquadX em 2-3 projetos reais IntegrAllTech
  - Tasks completadas end-to-end ✅
  - Commits feitos pelos agents ✅
  - Zero downtime 1 semana contínua ✅
  - Feedback positivo do Edson ✅
```

**Se MVP validado → Phase 2 (multi-agent)**
**Se MVP falhar → Pivot ou kill project**

---

### ✅ Decisão: Beta Pago (NÃO free trial infinito)

**Por que beta pago:**

1. **Commitment:**
   - Cliente paga = usa de verdade
   - Free = baixo commitment, churn alto
   - Feedback de cliente pagante é sério

2. **Revenue desde início:**
   - MRR em Phase 2 (mês 4)
   - Valida willingness to pay
   - Runway extension

3. **Filtro de qualidade:**
   - Só clientes sérios
   - Menos support overhead
   - Feedback focado

**Oferta beta:**

```yaml
Beta Program (3-5 clientes):
  Discount: 50% por 6 meses
  Pricing: Professional $749 (instead of $1.499)
  Commitment: 6 meses contract
  Requirements:
    - Feedback quinzenal (call 30min)
    - Case study ao final
    - Referências se satisfeito
  Benefits:
    - Influence roadmap
    - Priority support
    - Early access features
```

**Como recrutar:**

```
1. Edson's network (IntegrAllTech clients)
2. Direct outreach (LinkedIn, email)
3. Product Hunt launch (show interest list)
4. HackerNews "Show HN"
5. Dev communities (Reddit r/startups, r/entrepreneur)
```

**Alternativas rejeitadas:**

❌ **Free trial 30 dias:**
- Baixo commitment
- Churn alto depois trial
- Muitos "tire kickers"

❌ **Freemium:**
- Canibalizaria Pro tier
- Support overhead alto
- Hard to convert free → paid

❌ **Waitlist gratuita:**
- Sem validação de willingness to pay
- Pode ter 1000 signups mas 0 pagantes

---

## Resumo Final: Decisões Críticas

### ✅ FAZER (Must-have)

1. **Código local** (nunca vai para cloud)
2. **Docker sandbox** com network: none
3. **Multi-agent** (LangGraph orchestration)
4. **WebSocket** real-time (não polling)
5. **OpenTelemetry** observability desde início
6. **JWT + RBAC** segurança enterprise
7. **PostgreSQL + Redis** stack sólida
8. **FastAPI + Next.js** stack moderna
9. **LiteLLM** multi-provider flexibility
10. **MVP 8 semanas** validar antes de escalar

### ❌ NÃO FAZER (Evitar)

1. **Cloud execution** (compliance risk)
2. **Single agent** (não escala)
3. **REST polling** (latência alta)
4. **Build orchestrator do zero** (use LangGraph)
5. **Absorver LLM costs** (pass-through)
6. **Skip MVP** (build completo antes validar)
7. **Free trial infinito** (beta pago)
8. **Ignore observability** (desde Phase 1)
9. **Containers com network** (security risk)
10. **Skip approval workflow** (safety net)

---

## Decisões Pendentes (Futuro)

**Phase 3+:**
- [ ] CRM choice? (HubSpot vs Salesforce)
- [ ] Self-hosted architecture?
- [ ] Agent marketplace model?
- [ ] Custom skills format?
- [ ] Multi-region strategy?
- [ ] White-label implementation?
- [ ] API rate limiting strategy?
- [ ] Advanced RBAC (resource-level)?

Essas decisões podem ser postergadas até validar product-market fit (Phase 2).

---

## Conclusão

Este documento captura **TODAS as decisões técnicas** feitas durante planejamento:

✅ **Arquitetura:** Híbrida (cloud + local)
✅ **Stack:** FastAPI, Next.js, Python client
✅ **Segurança:** 7 camadas, código local
✅ **Sandbox:** Docker isolado, network: none
✅ **Multi-agent:** LangGraph orchestration
✅ **Comunicação:** WebSocket bidirectional
✅ **Observability:** OpenTelemetry completa
✅ **Deployment:** Docker Compose + K8s
✅ **LLM:** LiteLLM multi-provider
✅ **Git:** Automated operations
✅ **Business:** SaaS subscription, pass-through LLM
✅ **Roadmap:** MVP 8 semanas, beta pago

**Próximo passo:** Implementar! 🚀

---

**Documento vivo:** Atualizar conforme projeto evolui e decisões são revisitadas.

**Última atualização:** Fevereiro 2026
