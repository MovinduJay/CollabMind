$ErrorActionPreference = "Stop"

$baseIdentity = "http://localhost:8082"
$baseChat = "http://localhost:8081"
$wsBase = "ws://localhost:8083/ws/chat"

function Assert-Health($name, $url) {
    try {
        $response = Invoke-RestMethod $url -TimeoutSec 5
        if ($response.status -ne "UP") {
            throw "$name is not UP"
        }
        Write-Host "${name}: UP"
    } catch {
        throw "$name is DOWN or unreachable at $url"
    }
}

function Register-TestUser($displayName) {
    $email = "$($displayName.ToLower())+$((New-Guid).ToString())@example.com"

    return Invoke-RestMethod `
        -Method Post `
        -Uri "$baseIdentity/api/auth/register" `
        -ContentType "application/json" `
        -Body (@{
            displayName = $displayName
            email = $email
            password = "password123"
        } | ConvertTo-Json -Compress)
}

function Send-WebSocketJson($socket, $object) {
    $json = $object | ConvertTo-Json -Compress -Depth 20
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $segment = [ArraySegment[byte]]::new($bytes)

    $socket.SendAsync(
        $segment,
        [System.Net.WebSockets.WebSocketMessageType]::Text,
        $true,
        [Threading.CancellationToken]::None
    ).GetAwaiter().GetResult()

    Write-Host "Client command: $json"
}

function Receive-WebSocketText($socket, $timeoutSeconds) {
    $buffer = New-Object byte[] 65536
    $segment = [ArraySegment[byte]]::new($buffer)

    $cts = [Threading.CancellationTokenSource]::new()
    $cts.CancelAfter([TimeSpan]::FromSeconds($timeoutSeconds))

    $builder = [System.Text.StringBuilder]::new()

    do {
        $result = $socket.ReceiveAsync($segment, $cts.Token).GetAwaiter().GetResult()

        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            throw "WebSocket closed by server"
        }

        $chunk = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
        [void]$builder.Append($chunk)
    } while (-not $result.EndOfMessage)

    return $builder.ToString()
}

function Wait-ForEvent($socket, $eventType, $timeoutSeconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($timeoutSeconds)

    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $remaining = [Math]::Max(1, ($deadline - [DateTimeOffset]::UtcNow).TotalSeconds)

        try {
            $text = Receive-WebSocketText $socket $remaining
            Write-Host "Server event: $text"

            $event = $text | ConvertFrom-Json

            if ($event.eventType -eq $eventType) {
                return $event
            }
        } catch {
            if ($_.Exception.Message -like "*canceled*" -or $_.Exception.Message -like "*cancelled*") {
                break
            }

            throw
        }
    }

    throw "Timed out waiting for event: $eventType"
}

Write-Host ""
Write-Host "Checking service health..."

Assert-Health "identity-service" "$baseIdentity/actuator/health"
Assert-Health "chat-core" "$baseChat/actuator/health"
Assert-Health "ai-orchestrator" "http://localhost:8084/actuator/health"
Assert-Health "realtime-gateway" "http://localhost:8083/actuator/health"
Assert-Health "tool-mcp-server" "http://localhost:8085/actuator/health"

Write-Host ""
Write-Host "Registering two users..."

$userA = Register-TestUser "RealtimeUserA"
$userB = Register-TestUser "RealtimeUserB"

$headersA = @{ Authorization = "Bearer $($userA.accessToken)" }
$headersB = @{ Authorization = "Bearer $($userB.accessToken)" }

Write-Host "Creating conversation as user A..."

$conversation = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations" `
    -Headers $headersA `
    -ContentType "application/json" `
    -Body (@{
        name = "Automated Realtime AI E2E Room"
    } | ConvertTo-Json -Compress)

Write-Host "Joining user B..."

$joined = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations/$($conversation.id)/members" `
    -Headers $headersB `
    -ContentType "application/json"

Write-Host ""
Write-Host "Conversation ID: $($conversation.id)"
Write-Host "User A ID: $($userA.userId)"
Write-Host "User B ID: $($userB.userId)"
Write-Host "Member count: $($joined.memberCount)"

if ($joined.memberCount -ne 2) {
    throw "Expected memberCount to be 2"
}

Write-Host ""
Write-Host "Connecting WebSocket as user A..."

$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$uri = [Uri]::new("$wsBase`?token=$($userA.accessToken)")

$socket.ConnectAsync(
    $uri,
    [Threading.CancellationToken]::None
).GetAwaiter().GetResult()

Wait-ForEvent $socket "CONNECTED" 10 | Out-Null

Send-WebSocketJson $socket @{
    commandId = (New-Guid).ToString()
    commandType = "SUBSCRIBE_CONVERSATION"
    conversationId = $conversation.id
    payload = @{}
}

$subscribed = Wait-ForEvent $socket "SUBSCRIBED_CONVERSATION" 10

if ($subscribed.payload.subscriberCount -lt 1) {
    throw "Expected subscriberCount >= 1"
}

Send-WebSocketJson $socket @{
    commandId = (New-Guid).ToString()
    commandType = "SEND_MESSAGE"
    conversationId = $conversation.id
    payload = @{
        clientMessageId = (New-Guid).ToString()
        content = "@shopping find me a birthday gift under Rs. 10,000"
    }
}

Wait-ForEvent $socket "MESSAGE_CREATED" 10 | Out-Null
Wait-ForEvent $socket "AI_STAGE_UPDATED" 10 | Out-Null
$aiMessageCreated = Wait-ForEvent $socket "AI_MESSAGE_CREATED" 30

if ($aiMessageCreated.payload.message.content -notmatch "MCP tool bridge: shopping.search") {
    throw "Expected AI message to include MCP shopping bridge result"
}

$socket.CloseAsync(
    [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
    "E2E test complete",
    [Threading.CancellationToken]::None
).GetAwaiter().GetResult()

Write-Host ""
Write-Host "E2E realtime AI test passed."


