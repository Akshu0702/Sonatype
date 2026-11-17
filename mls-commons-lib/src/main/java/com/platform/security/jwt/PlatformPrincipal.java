package com.platform.security.jwt;

import java.util.UUID;

/** Who is calling and for which tenant, as established from a verified token. */
public record PlatformPrincipal(UUID tenantId, String subject, String issuer) {
}
