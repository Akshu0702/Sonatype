package com.platform.tenants.domain;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
        UUID tenantId,
        String tenantName,
        String displayName,
        String tenantCode,
        String domain,
        String adminEmail,
        String status,
        String tenantType,
        UUID parentTenantId,
        String parentAccessLevel,
        String realmName,
        int provisioningStepsCompleted,
        Instant createdAt) {

    public boolean isActive() {
        return "active".equals(status);
    }
}
