.PHONY: help install dev build test lint format clean docker-up docker-down migrate

# Colors
BLUE := \033[34m
GREEN := \033[32m
RESET := \033[0m

help: ## Show this help
	@echo "$(BLUE)SquadX.dev$(RESET) - AI Development Squad Orchestration Platform"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(RESET) %s\n", $$1, $$2}'

# Development
install: ## Install all dependencies
	cd backend && ./mvnw dependency:resolve
	cd frontend && pnpm install
	cd client && pip install -e ".[dev]"

dev: ## Start development environment
	docker-compose up -d postgres redis
	@echo "Starting backend..."
	cd backend && ./mvnw spring-boot:run &
	@echo "Starting frontend..."
	cd frontend && pnpm dev &

dev-backend: ## Start backend only
	cd backend && ./mvnw spring-boot:run

dev-frontend: ## Start frontend only
	cd frontend && pnpm dev

dev-client: ## Start client daemon
	cd client && python -m squadx_client.main

# Docker
docker-up: ## Start all services with Docker
	docker-compose up -d

docker-down: ## Stop all Docker services
	docker-compose down

docker-build: ## Build all Docker images
	docker-compose build

docker-logs: ## Show Docker logs
	docker-compose logs -f

docker-monitoring: ## Start with monitoring (Prometheus + Grafana)
	docker-compose --profile monitoring up -d

# Database
migrate: ## Run database migrations (Flyway)
	cd backend && ./mvnw flyway:migrate

migrate-info: ## Show migration info
	cd backend && ./mvnw flyway:info

migrate-clean: ## Clean database (CAUTION: deletes all data)
	cd backend && ./mvnw flyway:clean

# Testing
test: ## Run all tests
	cd backend && ./mvnw test
	cd frontend && pnpm test
	cd client && pytest

test-backend: ## Run backend tests
	cd backend && ./mvnw test

test-frontend: ## Run frontend tests
	cd frontend && pnpm test

test-client: ## Run client tests
	cd client && pytest -v --cov=squadx_client

# Code Quality
lint: ## Run linters
	cd backend && ./mvnw checkstyle:check
	cd frontend && pnpm lint
	cd client && ruff check squadx_client

format: ## Format code
	cd backend && ./mvnw spotless:apply
	cd frontend && pnpm format
	cd client && ruff format squadx_client

type-check: ## Run type checks
	cd frontend && pnpm type-check
	cd client && mypy squadx_client --ignore-missing-imports

# Build
build: ## Build all modules
	cd backend && ./mvnw clean package -DskipTests
	cd frontend && pnpm build
	docker-compose build

build-backend: ## Build backend only
	cd backend && ./mvnw clean package -DskipTests

build-frontend: ## Build frontend only
	cd frontend && pnpm build

build-agent: ## Build the agent sandbox image (base :latest + live-view :live)
	cd client && docker build -f docker/agent.Dockerfile -t squadx/agent:latest .
	cd client && docker build -f docker/agent.Dockerfile --target live-view -t squadx/agent:live .

build-egress-proxy: ## Build the egress firewall sidecar image (RFC-0006)
	cd client/docker && docker build -f egress-proxy.Dockerfile -t squadx/egress-proxy:latest .

build-sandbox-images: build-agent build-egress-proxy ## Build the images the daemon runs (agent + egress sidecar)
	@echo "Built squadx/agent:latest, squadx/agent:live, squadx/egress-proxy:latest"

# Clean
clean: ## Clean build artifacts
	cd backend && ./mvnw clean
	cd frontend && rm -rf .next node_modules
	cd client && find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	cd client && find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null || true

# Utilities
db-shell: ## Open PostgreSQL shell
	docker-compose exec postgres psql -U squadx -d squadx

redis-cli: ## Open Redis CLI
	docker-compose exec redis redis-cli

logs-backend: ## Show backend logs
	docker-compose logs -f backend

logs-frontend: ## Show frontend logs
	docker-compose logs -f frontend
