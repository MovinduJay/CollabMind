#!/usr/bin/env bash
set -euo pipefail

tag="${1:?image tag is required}"
owner="${GITHUB_REPOSITORY_OWNER,,}"
cd deploy/kubernetes/base
for service in web identity-service chat-core realtime-gateway ai-orchestrator tool-mcp-server; do
  kustomize edit set image "ghcr.io/movindujay/collabmind-$service:0.1.0=ghcr.io/$owner/collabmind-$service:$tag"
done
