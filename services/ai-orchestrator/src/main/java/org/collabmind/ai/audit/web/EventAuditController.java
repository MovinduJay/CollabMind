package org.collabmind.ai.audit.web;

import org.collabmind.ai.audit.domain.EventAuditRecord;
import org.collabmind.ai.audit.infrastructure.EventAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/event-audit")
public class EventAuditController {
    private final EventAuditRepository repository;
    public EventAuditController(EventAuditRepository repository) { this.repository = repository; }

    @GetMapping("/recent")
    public List<EventAuditRecord> recent(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "receivedAt"))).getContent();
    }
}
