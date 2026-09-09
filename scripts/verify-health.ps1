$services = @(
    @{ Name = "identity-service"; Url = "http://localhost:8082/actuator/health" },
    @{ Name = "chat-core"; Url = "http://localhost:8081/actuator/health" },
    @{ Name = "ai-orchestrator"; Url = "http://localhost:8084/actuator/health" },
    @{ Name = "realtime-gateway"; Url = "http://localhost:8083/actuator/health" },
    @{ Name = "tool-mcp-server"; Url = "http://localhost:8085/actuator/health" }
)

foreach ($service in $services) {
    try {
        $response = Invoke-RestMethod $service.Url -TimeoutSec 5

        if ($response.status -eq "UP") {
            Write-Host "$($service.Name): UP"
        } else {
            Write-Host "$($service.Name): $($response.status)"
        }
    } catch {
        Write-Host "$($service.Name): DOWN"
    }
}
