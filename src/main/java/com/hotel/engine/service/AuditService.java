package com.hotel.engine.service;

import com.hotel.engine.model.AuditEventEntity;
import com.hotel.engine.repository.AuditRepository;
import com.hotel.engine.security.CurrentUser;
import org.springframework.stereotype.Service;

/** Records who did what, for the activity timeline, reservation history and audit log. */
@Service
public class AuditService {

    private final AuditRepository repo;
    private volatile boolean muted;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    public void log(String action, String entityType, String entityRef, String detail) {
        if (!muted) {
            repo.save(new AuditEventEntity(CurrentUser.name(), action, entityType, entityRef, detail));
        }
    }

    /** Switches logging off while the demo hotel is generated, so the log only shows real activity. */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}
