.PHONY: run down ensure-compose

DOCKER_COMPOSE := docker compose

CLOJURE_BIN := $(shell command -v clojure 2>/dev/null)
LEIN_BIN := $(shell command -v lein 2>/dev/null)

ensure-compose:
	@running=$$($(DOCKER_COMPOSE) ps -q --status running 2>/dev/null | wc -l); \
	services=$$($(DOCKER_COMPOSE) config --services 2>/dev/null | wc -l); \
	if [ "$$services" -eq 0 ]; then \
		echo "Nenhum serviço encontrado no docker compose." >&2; \
		exit 1; \
	fi; \
	if [ "$$running" -lt "$$services" ]; then \
		echo "Subindo containers..."; \
		$(DOCKER_COMPOSE) up -d; \
	fi

run: ensure-compose
	@if [ -n "$(CLOJURE_BIN)" ]; then \
		$(CLOJURE_BIN) -M -m app.core; \
	elif [ -n "$(LEIN_BIN)" ]; then \
		$(LEIN_BIN) run; \
	else \
		echo "Instale a Clojure CLI ou Leiningen para usar make run." >&2; \
		exit 1; \
	fi

down:
	$(DOCKER_COMPOSE) down
