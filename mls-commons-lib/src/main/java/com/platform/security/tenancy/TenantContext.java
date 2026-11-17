package com.platform.security.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant the current thread is acting for. Set once per request by
 * {@code TenantContextFilter} from the VERIFIED token, never from request input,
 * and read by the Hibernate tenancy hook that sets the Postgres session variable
 * the row-level security policies check.
 *
 * <p>Threads that are not serving a request (Kafka consumers, schedulers) have no
 * context and must establish one explicitly with {@link #runAs}. With no context
 * the database returns zero rows: absence of a tenant never grants access.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant context on this thread");
        }
        return id;
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code action} as {@code tenantId}, restoring whatever context existed before. */
    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
