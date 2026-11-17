package com.motivitylabs.mls.itsmapi;

import com.platform.security.authz.RemotePermissionChecker;
import com.platform.security.jwt.PlatformAuthentication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may decide tickets. Two sources, either is enough:
 * <ul>
 *   <li>the IAM permission {@code itsm.approvals.decide}, when the IAM service is configured ({@code platform.iam.url}); this is the real source;</li>
 *   <li>the {@code itsm.approvers} setting (token subject or user name), so the service works standalone before IAM roles exist.</li>
 * </ul>
 * Nothing from the request, and no role claim from the sign-in server, is trusted. Whatever the source, a requester never decides their own ticket
 * (that rule is in {@link TicketService}).
 */
@Component
public class ApproverDirectory {

    public static final String PERMISSION = "itsm.approvals.decide";

    private final Set<String> configured;
    private final ObjectProvider<RemotePermissionChecker> perms;

    public ApproverDirectory(ItsmProperties properties, ObjectProvider<RemotePermissionChecker> perms) {
        this.configured = Set.copyOf(properties.approvers());
        this.perms = perms;
    }

    public Actor actor(Authentication authentication) {
        PlatformAuthentication platform = (PlatformAuthentication) authentication;
        String subject = platform.getPrincipal().subject();
        Object preferred = platform.getCredentials() == null ? null : platform.getCredentials().getClaims().get("preferred_username");
        Object displayName = platform.getCredentials() == null ? null : platform.getCredentials().getClaims().get("name");
        String userName = preferred == null ? null : preferred.toString();
        boolean approver = configured.contains(subject) || (userName != null && configured.contains(userName));
        RemotePermissionChecker checker = perms.getIfAvailable();
        if (!approver && checker != null) {
            approver = checker.has(PERMISSION);
        }
        String name = displayName != null ? displayName.toString() : (userName != null ? userName : subject);
        return new Actor(subject, name, approver);
    }
}
