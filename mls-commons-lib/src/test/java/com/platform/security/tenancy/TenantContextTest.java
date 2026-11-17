package com.platform.security.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @AfterEach
    void clean() {
        TenantContext.clear();
    }

    @Test
    void emptyByDefaultAndRequireFails() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runAsRestoresThePreviousContext() {
        TenantContext.set(A);
        TenantContext.runAs(B, () -> assertThat(TenantContext.require()).isEqualTo(B));
        assertThat(TenantContext.require()).isEqualTo(A);
    }

    @Test
    void runAsClearsWhenThereWasNoPreviousContext_evenIfTheActionThrows() {
        assertThatThrownBy(() -> TenantContext.runAs(A, () -> {
            throw new RuntimeException("boom");
        })).hasMessage("boom");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void resolverMapsContextToTenantAndNoContextToTheSentinel() {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TenantIdentifierResolver.NO_TENANT);
        TenantContext.set(A);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(A.toString());
    }
}
