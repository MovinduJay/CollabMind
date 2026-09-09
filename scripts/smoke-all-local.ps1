$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=========================================="
Write-Host " CollabMind Local Smoke Test"
Write-Host "=========================================="
Write-Host ""

Write-Host "1/6 Checking service health..."
.\scripts\verify-health.ps1

Write-Host ""
Write-Host "2/6 Testing chat-core history and membership..."
.\scripts\smoke-chat-history.ps1

Write-Host ""
Write-Host "3/6 Testing MCP shopping tool..."
.\scripts\smoke-mcp-shopping.ps1

Write-Host ""
Write-Host "4/6 Testing MCP GitHub tool..."
.\scripts\smoke-mcp-github.ps1

Write-Host ""
Write-Host "5/6 Testing MCP audit observability..."
.\scripts\smoke-mcp-audit.ps1

Write-Host ""
Write-Host "6/6 Testing realtime WebSocket AI flows..."
.\scripts\e2e-realtime-ai.ps1
.\scripts\e2e-realtime-github.ps1

Write-Host ""
Write-Host "=========================================="
Write-Host " All local smoke tests passed."
Write-Host "=========================================="
