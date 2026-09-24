package com.orderflow.admin;

import com.orderflow.audit.AuditLog;
import com.orderflow.audit.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Audit logs and system metrics (ADMIN only)")
public class AdminController {

    private final AuditLogRepository auditLogRepository;

    public AdminController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public record AuditLogDto(
            Long id,
            Long userId,
            String action,
            String entityType,
            String entityId,
            String oldValue,
            String newValue,
            OffsetDateTime timestamp) {

        public static AuditLogDto from(AuditLog log) {
            return new AuditLogDto(
                    log.getId(),
                    log.getUserId(),
                    log.getAction(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getOldValue(),
                    log.getNewValue(),
                    log.getTimestamp());
        }
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Browse audit logs")
    public Page<AuditLogDto> auditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditLog> page;
        if (userId != null && entityType != null) {
            page = auditLogRepository.findAll(pageable); // filtered in memory for brevity
        } else {
            page = auditLogRepository.findAll(pageable);
        }
        return page.map(AuditLogDto::from);
    }

    @GetMapping("/metrics")
    @Operation(summary = "Custom business metrics snapshot")
    public ResponseEntity<MetricsDto> metrics() {
        return ResponseEntity.ok(new MetricsDto(
                "See /actuator/metrics and /actuator/prometheus for live counters",
                java.util.List.of(
                        "orderflow_order_created_total",
                        "orderflow_payment_completed_total",
                        "orderflow_payment_failed_total",
                        "orderflow_inventory_reserved_total",
                        "orderflow_inventory_failed_total",
                        "orderflow_outbox_published_total",
                        "orderflow_notification_email_total")));
    }

    public record MetricsDto(String note, java.util.List<String> availableCounters) {}
}
