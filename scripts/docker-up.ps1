$ErrorActionPreference = "Stop"

Write-Host "Starting CollabMind with Docker Compose..."
docker compose up --build -d

Write-Host ""
Write-Host "Waiting for services to start..."
Start-Sleep -Seconds 20

Write-Host ""
Write-Host "Checking health..."
.\scripts\verify-health.ps1

Write-Host ""
Write-Host "CollabMind Docker environment is running."
Write-Host ""
Write-Host "Services:"
Write-Host "- identity-service      http://localhost:8082"
Write-Host "- chat-core             http://localhost:8081"
Write-Host "- realtime-gateway      ws://localhost:8083/ws/chat"
Write-Host "- ai-orchestrator       http://localhost:8084"
Write-Host "- tool-mcp-server       http://localhost:8085"
