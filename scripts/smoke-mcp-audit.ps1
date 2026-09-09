$ErrorActionPreference = "Stop"

$baseMcp = "http://localhost:8085"

Write-Host "Checking tool-mcp-server health..."

$health = Invoke-RestMethod "$baseMcp/actuator/health"

if ($health.status -ne "UP") {
    throw "tool-mcp-server is not UP"
}

Write-Host "tool-mcp-server: UP"

Write-Host "Calling shopping.search..."

Invoke-RestMethod `
    -Method Post `
    -Uri "$baseMcp/mcp" `
    -ContentType "application/json" `
    -Body (@{
        jsonrpc = "2.0"
        id = "audit-shopping-success"
        method = "tools/call"
        params = @{
            name = "shopping.search"
            arguments = @{
                userMessage = "@shopping find me a birthday gift under Rs. 10,000"
                contextSummary = "Testing audit success."
            }
        }
    } | ConvertTo-Json -Depth 20 -Compress) | Out-Null

Write-Host "Calling unsupported tool to create failure audit..."

$failureResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseMcp/mcp" `
    -ContentType "application/json" `
    -Body (@{
        jsonrpc = "2.0"
        id = "audit-tool-failure"
        method = "tools/call"
        params = @{
            name = "unknown.tool"
            arguments = @{
                userMessage = "test unsupported tool"
            }
        }
    } | ConvertTo-Json -Depth 20 -Compress)

if (-not $failureResponse.error) {
    throw "Expected unsupported tool error"
}

Write-Host "Reading audit summary..."

$summary = Invoke-RestMethod "$baseMcp/mcp/audit/summary"

Write-Host ""
Write-Host "Total invocations: $($summary.totalInvocations)"
Write-Host "Successful invocations: $($summary.successfulInvocations)"
Write-Host "Failed invocations: $($summary.failedInvocations)"
Write-Host "Success rate: $($summary.successRate)"
Write-Host "Average latency ms: $($summary.averageLatencyMs)"
Write-Host "Max latency ms: $($summary.maxLatencyMs)"

if ($summary.totalInvocations -lt 2) {
    throw "Expected at least 2 audit records"
}

if ($summary.successfulInvocations -lt 1) {
    throw "Expected at least 1 successful audit record"
}

if ($summary.failedInvocations -lt 1) {
    throw "Expected at least 1 failed audit record"
}

Write-Host ""
Write-Host "Reading recent audit records..."

$recentRaw = Invoke-WebRequest "$baseMcp/mcp/audit/recent?limit=10" -UseBasicParsing
$recentJson = $recentRaw.Content
$recent = @($recentJson | ConvertFrom-Json)

Write-Host "Recent count: $($recent.Count)"

if ($recent.Count -lt 1) {
    throw "Expected recent audit records"
}

$recent | Format-Table toolName, success, latencyMs, createdAt

Write-Host ""
Write-Host "MCP audit smoke test passed."
