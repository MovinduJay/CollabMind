package org.collabmind.ai.agent.web;

import jakarta.validation.Valid;
import org.collabmind.ai.agent.application.AiAgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/respond")
    public AiPromptResponse respond(@Valid @RequestBody AiPromptRequest request) {
        return aiAgentService.generateResponse(request);
    }
}
