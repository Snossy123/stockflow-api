package com.arafawood.api.service;

import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.AuditLog;
import com.arafawood.api.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(AppUser user, String action, String entity, Long entityId, String before, String after) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        logs.save(log);
    }
}
