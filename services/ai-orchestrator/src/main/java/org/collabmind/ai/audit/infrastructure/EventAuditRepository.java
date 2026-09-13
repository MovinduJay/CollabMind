package org.collabmind.ai.audit.infrastructure;
import org.collabmind.ai.audit.domain.EventAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface EventAuditRepository extends JpaRepository<EventAuditRecord, UUID> {}
