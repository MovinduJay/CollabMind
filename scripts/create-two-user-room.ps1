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

$userA = Register-TestUser "MovinduA"
$userB = Register-TestUser "MovinduB"

$headersA = @{ Authorization = "Bearer $($userA.accessToken)" }
$headersB = @{ Authorization = "Bearer $($userB.accessToken)" }

$conversation = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations" `
    -Headers $headersA `
    -ContentType "application/json" `
    -Body (@{
        name = "Two User Realtime Test Room"
    } | ConvertTo-Json -Compress)

$joined = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseChat/api/conversations/$($conversation.id)/members" `
    -Headers $headersB `
    -ContentType "application/json"

$members = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseChat/api/conversations/$($conversation.id)/members" `
    -Headers $headersA

$output = @"
USER A TOKEN:
$($userA.accessToken)

USER A ID:
$($userA.userId)

USER B TOKEN:
$($userB.accessToken)

USER B ID:
$($userB.userId)

CONVERSATION ID:
$($conversation.id)

MEMBER COUNT:
$($joined.memberCount)
"@

$output | Set-Clipboard

Write-Host $output
Write-Host "`nMembers:"
$members | Format-Table

Write-Host "`nCopied tokens and conversation ID to clipboard."
