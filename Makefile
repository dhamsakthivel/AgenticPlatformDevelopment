AGENT=. services/agent/.venv/bin/uvicorn app.main:app --app-dir services/agent --port 8000
PLATFORM=services/platform/mvnw -f services/platform/pom.xml spring-boot:run
WEB=npm --prefix apps/web run dev

.PHONY: test-agent test-platform

test-agent:
	services/agent/.venv/bin/pytest -q services/agent/tests

test-platform:
	services/platform/mvnw -f services/platform/pom.xml -q test
