.PHONY: infra-up infra-down

infra-up:
	docker compose -f ./docker-compose.infra.yml up

infra-down:
	docker compose -f docker-compose.infra.yml down -v

