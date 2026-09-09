$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=========================================="
Write-Host " CollabMind Docker Smoke Test"
Write-Host "=========================================="
Write-Host ""

.\scripts\verify-health.ps1
.\scripts\smoke-chat-history.ps1
.\scripts\smoke-mcp-shopping.ps1
.\scripts\smoke-mcp-github.ps1
.\scripts\smoke-mcp-audit.ps1
.\scripts\e2e-realtime-ai.ps1
.\scripts\e2e-realtime-github.ps1

Write-Host ""
Write-Host "=========================================="
Write-Host " Docker smoke tests passed."
Write-Host "=========================================="
