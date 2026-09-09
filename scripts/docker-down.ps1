$ErrorActionPreference = "Stop"

Write-Host "Stopping CollabMind Docker Compose environment..."
docker compose down

Write-Host "Stopped."
