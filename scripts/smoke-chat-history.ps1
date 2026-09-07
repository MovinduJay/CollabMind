$ErrorActionPreference = "Stop"

$baseIdentity = "http://localhost:8082"
$baseChat = "http://localhost:8081"

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

function Send-ChatMessage($conversationId, $headers, $content) {
    return Invoke-RestMethod `
        -Method Post `
        -Uri "$baseChat/api/conversations/$conversationId/messages" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{
            clientMessageId = (New-Guid).ToString()
            content = $content
        } | ConvertTo-Json -Compress)
}

Write-Host "Registering two users..."

$userA = Register-TestUser "HistoryUserA"
$userB = Register-TestUser "HistoryUserB"

$headersA = @{ Authorization = "Bearer $($userA.accessToken)" }
$headersB = @{ Authorization = "Bearer $($userB.accessToken)" }

Write-Host "Creating conversation..."

$conversation = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations" `
    -Headers $headersA `
    -ContentType "application/json" `
    -Body (@{
        name = "History Smoke Test Room"
    } | ConvertTo-Json -Compress)

Write-Host "Joining user B..."

$joined = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations/$($conversation.id)/members" `
    -Headers $headersB `
    -ContentType "application/json"

Write-Host "Sending messages..."

$m1 = Send-ChatMessage $conversation.id $headersA "Hello from user A"
$m2 = Send-ChatMessage $conversation.id $headersB "Hello from user B"
$m3 = Send-ChatMessage $conversation.id $headersA "Testing latest message history"

$latest = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseChat/api/conversations/$($conversation.id)/messages/latest?limit=2" `
    -Headers $headersA

$afterZero = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseChat/api/conversations/$($conversation.id)/messages?afterSequence=0&limit=10" `
    -Headers $headersA

$myConversations = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseChat/api/conversations" `
    -Headers $headersA

$members = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseChat/api/conversations/$($conversation.id)/members" `
    -Headers $headersA

Write-Host ""
Write-Host "CONVERSATION ID: $($conversation.id)"
Write-Host "MEMBER COUNT: $($joined.memberCount)"
Write-Host "SENT SEQUENCES: $($m1.sequenceNumber), $($m2.sequenceNumber), $($m3.sequenceNumber)"
Write-Host "LATEST COUNT: $($latest.Count)"
Write-Host "AFTER ZERO COUNT: $($afterZero.Count)"
Write-Host "MY CONVERSATIONS COUNT: $($myConversations.Count)"
Write-Host "MEMBERS COUNT: $($members.Count)"

if ($joined.memberCount -ne 2) {
    throw "Expected member count to be 2"
}

if ($afterZero.Count -lt 3) {
    throw "Expected at least 3 messages from history endpoint"
}

if ($latest.Count -ne 2) {
    throw "Expected latest endpoint to return 2 messages"
}

Write-Host ""
Write-Host "Smoke test passed."
