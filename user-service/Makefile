.PHONY: run test build docker-build docker-run docker-stop

run:
	./gradlew bootRun

test:
	./gradlew test

build:
	./gradlew bootJar -x test

docker-build:
	docker build -t user-service:latest .

docker-run:
	docker run -p 8080:8080 \
		--network rentflow_default \
		--env-file .env \
		user-service:latest

docker-stop:
	docker stop user-service || true
