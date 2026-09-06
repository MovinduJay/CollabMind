package org.collabmind.ai.audit.infrastructure;

import org.collabmind.ai.audit.domain.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {

    List<AiRequestLog> findTop20ByOrderByCreatedAtDesc();
}
