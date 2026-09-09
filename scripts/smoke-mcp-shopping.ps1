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

$toolName = $listResponse.result.tools[0].name

if ($toolName -ne "shopping.search") {
    throw "Expected shopping.search tool"
}

Write-Host "Found tool: $toolName"

Write-Host "Calling shopping.search..."

$callResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseMcp/mcp" `
    -ContentType "application/json" `
    -Body (@{
        jsonrpc = "2.0"
        id = "shopping-call-test"
        method = "tools/call"
        params = @{
            name = "shopping.search"
            arguments = @{
                userMessage = "@shopping find me a birthday gift under Rs. 10,000"
                contextSummary = "Testing MCP shopping bridge."
            }
        }
    } | ConvertTo-Json -Depth 20 -Compress)

$text = $callResponse.result.content[0].text

Write-Host $text

if ($text -notmatch "MCP tool bridge: shopping.search") {
    throw "Expected MCP shopping bridge result"
}

Write-Host ""
Write-Host "MCP shopping smoke test passed."
