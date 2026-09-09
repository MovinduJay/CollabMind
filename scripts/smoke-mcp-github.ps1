$ErrorActionPreference = "Stop"

$baseMcp = "http://localhost:8085"

Write-Host "Checking tool-mcp-server health..."

$health = Invoke-RestMethod "$baseMcp/actuator/health"

if ($health.status -ne "UP") {
    throw "tool-mcp-server is not UP"
}

Write-Host "tool-mcp-server: UP"

Write-Host "Calling tools/list..."

$listResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseMcp/mcp" `
    -ContentType "application/json" `
    -Body (@{
        jsonrpc = "2.0"
        id = "tools-list-test"
        method = "tools/list"
        params = @{}
    } | ConvertTo-Json -Depth 20 -Compress)

$toolNames = $listResponse.result.tools | ForEach-Object { $_.name }

if ($toolNames -notcontains "github.repo_summary") {
    throw "Expected github.repo_summary tool"
}

if ($toolNames -notcontains "github.search_issues") {
    throw "Expected github.search_issues tool"
}

Write-Host "Found GitHub tools."

Write-Host "Calling github.repo_summary..."

$callResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseMcp/mcp" `
    -ContentType "application/json" `
    -Body (@{
        jsonrpc = "2.0"
        id = "github-summary-test"
        method = "tools/call"
        params = @{
            name = "github.repo_summary"
            arguments = @{
                userMessage = "@github summarize octocat/Hello-World"
                contextSummary = "Testing GitHub MCP bridge."
            }
        }
    } | ConvertTo-Json -Depth 20 -Compress)

$text = $callResponse.result.content[0].text

Write-Host $text

if ($text -notmatch "MCP tool bridge: github.repo_summary") {
    throw "Expected GitHub repo summary bridge result"
}

Write-Host ""
Write-Host "MCP GitHub smoke test passed."
