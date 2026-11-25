.DEFAULT_GOAL := ayuda

COMPOSE = docker compose -f infrastructure/platform/compose.yaml
WEB_NPM = npm --prefix frontend
CONTRACT_NPM = npm --prefix tools/web-contract
CUENTAS ?= infrastructure/platform/cuentas-desarrollo.env

.PHONY: ayuda dev cuentas estado logs parar instalar escenarios build lint test contrato

ayuda:
	@printf '%s\n' \
	    'make dev                     Arranca la plataforma con datos de prueba.' \
	    'make cuentas                 Muestra los usuarios y sus contraseñas de prueba.' \
	    'make estado                  Muestra el estado de la plataforma.' \
	    'make logs [SERVICIO=management]  Sigue los logs.' \
	    'make parar                   Detiene la plataforma y conserva los datos.' \
	    'make instalar                Instala las dependencias de Node.' \
	    'make escenarios              Inicia Vite con datos simulados.' \
	    'make build                   Compila las aplicaciones Java y el frontend.' \
	    'make lint                    Ejecuta Oxlint y verifica los tipos de TypeScript.' \
	    'make test                    Ejecuta las pruebas Java, web y del contrato.' \
	    'make contrato                Verifica OpenAPI y sus ejemplos.'

dev:
	./infrastructure/platform/prepare-local-config.sh
	$(COMPOSE) up --build --wait --wait-timeout 180
	$(COMPOSE) run --no-deps --rm -T management load-demo-fixture
	$(COMPOSE) run --no-deps --rm -T --env-from-file "$(CUENTAS)" management load-demo-web-accounts
	@address=$$($(COMPOSE) port nginx 80) && \
	    printf '\nWeb disponible en http://localhost:%s/\nConsultá las credenciales con make cuentas.\n' "$${address##*:}"

cuentas:
	@cat "$(CUENTAS)"

estado:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs --tail 100 --follow $(SERVICIO)

parar:
	$(COMPOSE) stop

instalar:
	$(WEB_NPM) ci
	$(CONTRACT_NPM) ci

escenarios:
	$(WEB_NPM) run dev -- --mode escenarios

build:
	./mvnw -DskipTests package
	$(WEB_NPM) run build

lint:
	$(WEB_NPM) run lint
	$(WEB_NPM) run typecheck

test: contrato
	./mvnw verify
	$(WEB_NPM) test

contrato:
	$(CONTRACT_NPM) run verify
