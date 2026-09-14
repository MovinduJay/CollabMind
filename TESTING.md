# CollabMind testing

The automated test pyramid is split so normal local development does not require Docker.

## Fast local checks

Frontend component tests and production build:

```powershell
cd D:\MyProjects\CollabMind\apps\web
npm test
npm run build
```

Browser journeys (install Chromium once):

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH='D:\CollabMindTools\playwright'
npx playwright install chromium
npm run test:e2e -- --project=chromium
```

Backend unit and HTTP contract tests:

```powershell
$env:JAVA_HOME='D:\CollabMindTools\jdk-21'
D:\CollabMindTools\apache-maven-3.9.11\bin\mvn.cmd test -f services\chat-core\pom.xml
D:\CollabMindTools\apache-maven-3.9.11\bin\mvn.cmd test -f services\realtime-gateway\pom.xml
D:\CollabMindTools\apache-maven-3.9.11\bin\mvn.cmd test -f services\ai-orchestrator\pom.xml

$env:JAVA_HOME='D:\CollabMindTools\jdk-23'
D:\CollabMindTools\apache-maven-3.9.11\bin\mvn.cmd test -f services\tool-mcp-server\pom.xml
```

OpenAI and Kapruka use local WireMock servers in automated tests. No API key, live request, purchase, or catalog dependency is involved.

## Container integration checks

`DistributedInfrastructureContainerTest` verifies real Redis atomic TTL behavior and Kafka produce/consume behavior. It automatically skips without Docker and runs on GitHub Actions.

## Load testing

Install k6, create a user and room, then run:

```powershell
$env:AUTH_TOKEN='<jwt>'
$env:CONVERSATION_ID='<room-uuid>'
$env:K6_TARGET_VUS='25'
$env:K6_RAMP_DURATION='10s'
$env:K6_SUSTAIN_DURATION='30s'
k6 run tests\load\realtime-websocket.js
```

The scenario defaults to ten concurrent WebSocket users and enforces acknowledgement and p95 latency thresholds. `K6_TARGET_VUS`, `K6_RAMP_DURATION`, and `K6_SUSTAIN_DURATION` make larger benchmark runs reproducible without changing the test source. Establish a baseline in a non-production environment before raising the load.

## CI evidence

GitHub Actions runs backend tests, coverage-enabled frontend tests, the production build, Chromium Playwright journeys, and Redis/Kafka container integration tests. Failed browser runs retain screenshots, video, traces, and an HTML report for 14 days.
