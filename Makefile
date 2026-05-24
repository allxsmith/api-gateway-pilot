# api-gateway-pilot - local development convenience targets
.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help up down logs ps db clean build test

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

up: ## Start the local stack (docker-compose)
	$(COMPOSE) up -d

down: ## Stop the local stack
	$(COMPOSE) down

logs: ## Tail local stack logs
	$(COMPOSE) logs -f

ps: ## Show local stack status
	$(COMPOSE) ps

db: ## Open a psql shell on the local database
	$(COMPOSE) exec postgres psql -U apipilot -d apipilot

clean: ## Stop the local stack and remove volumes
	$(COMPOSE) down -v

build: ## Build the Java services
	mvn -q -T1C package

test: ## Run Java tests
	mvn -q test
