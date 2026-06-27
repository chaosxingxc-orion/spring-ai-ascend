# WorkMate Workbench — common developer tasks.
# Run `make` (or `make help`) to list targets.
.DEFAULT_GOAL := help

help: ## Show available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2}'

setup: ## Create workmate-api/.env.local from the template (set your LLM key in it)
	@test -f workmate-api/.env.local || cp workmate-api/.env.local.example workmate-api/.env.local
	@echo "Edit workmate-api/.env.local and set WORKMATE_LLM_API_KEY"

dev: ## Start the whole local stack (Postgres + API + UI) in one command
	./scripts/dev.sh

db: ## Start Postgres only (docker compose)
	docker compose -f docker/docker-compose.yml up -d

api: ## Run the backend only (workmate-api on :8080)
	./scripts/run-local.sh

ui: ## Run the frontend only (workmate-ui on :5174)
	./scripts/dev-ui.sh

test: ## Run backend + frontend tests
	cd workmate-api && ../../../mvnw test
	cd workmate-ui && npm ci && npm test

build: ## Build backend jar + frontend bundle
	cd workmate-api && ../../../mvnw -DskipTests package
	cd workmate-ui && npm ci && npm run build

stop: ## Stop Postgres (and remove the container)
	docker compose -f docker/docker-compose.yml down

compose-up: setup build ## Build images and run the full stack in Docker (Postgres + API + UI)
	docker compose -f docker/docker-compose.yml --profile app up -d --build

compose-down: ## Stop the full Docker stack
	docker compose -f docker/docker-compose.yml --profile app down

.PHONY: help setup dev db api ui test build stop compose-up compose-down
