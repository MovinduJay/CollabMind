#!/usr/bin/env bash
set -euo pipefail

wait_for() {
  local name="$1"
  local url="$2"
  for attempt in $(seq 1 90); do
    if curl --fail --silent --show-error "$url" >/dev/null; then
      echo "$name is ready"
      return 0
    fi
    sleep 2
  done
  echo "$name did not become ready: $url" >&2
  return 1
}

wait_for web http://localhost:5173/healthz
wait_for identity http://localhost:8082/actuator/health
wait_for chat-core http://localhost:8081/actuator/health
wait_for realtime-gateway http://localhost:8083/actuator/health
wait_for ai-orchestrator http://localhost:8084/actuator/health
wait_for tool-mcp-server http://localhost:8085/actuator/health

email="ci-$(date +%s)@example.com"
auth_response=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"displayName\":\"CI User\",\"email\":\"$email\",\"password\":\"test-password-123\"}" \
  http://localhost:8082/api/auth/register)
token=$(jq -er '.accessToken' <<<"$auth_response")

conversation=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $token" \
  -d '{"name":"CI smoke room"}' \
  http://localhost:8081/api/conversations)
jq -e '.id and .name == "CI smoke room"' <<<"$conversation" >/dev/null
export WS_TOKEN="$token"
export WS_CONVERSATION_ID
WS_CONVERSATION_ID=$(jq -er '.id' <<<"$conversation")
node scripts/ci/websocket-smoke.mjs

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "AUTH_TOKEN=$token" >>"$GITHUB_ENV"
  echo "CONVERSATION_ID=$WS_CONVERSATION_ID" >>"$GITHUB_ENV"
fi

echo "Full-stack authenticated REST and WebSocket smoke flow passed"
