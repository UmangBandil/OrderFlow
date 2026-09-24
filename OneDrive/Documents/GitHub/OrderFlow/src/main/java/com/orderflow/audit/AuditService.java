package com.orderflow.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Records an audit entry in its own transaction so it survives business rollbacks where desired. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long userId, String action, String entityType, String entityId, Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog(
                userId,
                action,
                entityType,
                entityId,
                oldValue == null ? null : String.valueOf(oldValue),
                newValue == null ? null : String.valueOf(newValue));
        auditLogRepository.save(entry);
        log.debug("Audit: user={} action={} {}/{}", userId, action, entityType, entityId);
    }
}
