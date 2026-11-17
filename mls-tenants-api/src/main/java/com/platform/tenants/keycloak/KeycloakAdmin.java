package com.platform.tenants.keycloak;

import java.util.List;

/** The Keycloak operations provisioning needs. Every "create" is paired with an existence probe so steps are idempotent. */
public interface KeycloakAdmin {

    boolean realmExists(String realm);

    void createRealm(String realm, String displayName);

    boolean clientExists(String realm, String clientId);

    void createPublicClient(String realm, String clientId, List<String> redirectUris);

    boolean userExists(String realm, String username);

    void createUser(String realm, String username, String email, String temporaryPassword);

    /** Merges these into the sign-in client's attributes (a null value removes one), which the sign-in pages read to look like the tenant. */
    void setClientAttributes(String realm, String clientId, java.util.Map<String, String> attributes);

    /** The name the sign-in pages show for the realm. */
    void setRealmDisplayName(String realm, String displayName);

    /** A disabled realm refuses every sign-in, code exchange and token refresh. */
    void setRealmEnabled(String realm, boolean enabled);

    /** Gives the user a new password that must be changed at the next sign-in. False when there is no such user. */
    boolean resetPassword(String realm, String username, String temporaryPassword);
}
