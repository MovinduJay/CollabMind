package org.collabmind.toolmcp.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mcp/audit")
public class ToolAuditController {

    private final ToolAuditService toolAuditService;

    public ToolAuditController(ToolAuditService toolAuditService) {
        this.toolAuditService = toolAuditService;
    }

    @GetMapping("/recent")
    public List<ToolInvocationRecord> recent(
            @RequestParam(defaultValue = "25") int limit
    ) {
        return toolAuditService.recent(limit);
    }

    @GetMapping("/summary")
    public ToolAuditSummary summary() {
        return toolAuditService.summary();
    }
}
