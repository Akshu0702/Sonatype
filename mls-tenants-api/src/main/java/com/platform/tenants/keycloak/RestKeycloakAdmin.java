package com.platform.tenants.keycloak;

import com.platform.tenants.config.PlatformProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Keycloak Admin REST API over the container network, authenticated as the
 * platform-automation service account in the master realm (client credentials).
 */
@Component
public class RestKeycloakAdmin implements KeycloakAdmin {

    private static final long TOKEN_SKEW_SECONDS = 30;

    private final PlatformProperties props;
    private final RestClient http;

    private volatile String token;
    private volatile Instant tokenExpires = Instant.EPOCH;

    private final String loginTheme;

    public RestKeycloakAdmin(PlatformProperties props,
                             @org.springframework.beans.factory.annotation.Value("${platform.login-theme:mls}") String loginTheme) {
        this.props = props;
        this.loginTheme = loginTheme;
        this.http = RestClient.builder().baseUrl(props.keycloakInternalUrl()).build();
    }

    @Override
    public boolean realmExists(String realm) {
        return exists("/admin/realms/{realm}", realm);
    }

    @Override
    public void createRealm(String realm, String displayName) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("realm", realm),
                Map.entry("displayName", displayName),
                Map.entry("enabled", true),
                Map.entry("loginTheme", loginTheme),
                Map.entry("sslRequired", "external"),
                Map.entry("registrationAllowed", false),
                Map.entry("loginWithEmailAllowed", true),
                // Usernames are emails, so a federated login can only ever match an account by its (trusted) email.
                Map.entry("registrationEmailAsUsername", true),
                Map.entry("duplicateEmailsAllowed", false),
                Map.entry("bruteForceProtected", true),
                Map.entry("accessTokenLifespan", 300),
                Map.entry("organizationsEnabled", true));
        post("/admin/realms", body);
        // The admin role only reaches the new realm's roles in tokens issued after the realm exists, so a cached token would get 403s.
        tokenExpires = Instant.EPOCH;
    }

    @Override
    public boolean clientExists(String realm, String clientId) {
        List<Map<String, Object>> found = authorized().get()
                .uri("/admin/realms/{realm}/clients?clientId={id}", realm, clientId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return found != null && !found.isEmpty();
    }

    @Override
    public void createPublicClient(String realm, String clientId, List<String> redirectUris) {
        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "enabled", true,
                "publicClient", true,
                "standardFlowEnabled", true,
                "directAccessGrantsEnabled", false,
                "implicitFlowEnabled", false,
                "redirectUris", redirectUris,
                "webOrigins", List.of("+"),
                "attributes", Map.of("pkce.code.challenge.method", "S256"));
        post("/admin/realms/" + realm + "/clients", body);
    }

    @Override
    public boolean userExists(String realm, String username) {
        List<Map<String, Object>> found = authorized().get()
                .uri("/admin/realms/{realm}/users?username={u}&exact=true", realm, username)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return found != null && !found.isEmpty();
    }

    @Override
    public void createUser(String realm, String username, String email, String temporaryPassword) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", "Tenant",
                "lastName", "Administrator",
                "emailVerified", true,
                "enabled", true,
                "requiredActions", List.of("UPDATE_PASSWORD"),
                "credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));
        post("/admin/realms/" + realm + "/users", body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setClientAttributes(String realm, String clientId, Map<String, String> attributes) {
        List<Map<String, Object>> found = authorized().get().uri("/admin/realms/{realm}/clients?clientId={id}", realm, clientId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (found == null || found.isEmpty()) {
            throw new KeycloakException("The sign-in client of realm " + realm + " does not exist");
        }
        Map<String, Object> client = new java.util.HashMap<>(found.get(0));
        Map<String, Object> merged = new java.util.HashMap<>(client.get("attributes") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
        attributes.forEach((k, v) -> {
            if (v == null) {
                merged.put(k, "");   // Keycloak keeps an attribute that is merely left out of the update; a blank one counts as unset
            } else {
                merged.put(k, v);
            }
        });
        client.put("attributes", merged);
        authorized().put().uri("/admin/realms/{realm}/clients/{id}", realm, client.get("id")).contentType(MediaType.APPLICATION_JSON)
                .body(client).retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new KeycloakException("Keycloak client update failed: HTTP " + res.getStatusCode().value());
                })
                .toBodilessEntity();
    }

    @Override
    public void setRealmDisplayName(String realm, String displayName) {
        authorized().put().uri("/admin/realms/{realm}", realm).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("realm", realm, "displayName", displayName))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new KeycloakException("Keycloak realm update failed: HTTP " + res.getStatusCode().value());
                })
                .toBodilessEntity();
    }

    @Override
    public void setRealmEnabled(String realm, boolean enabled) {
        authorized().put().uri("/admin/realms/{realm}", realm).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("realm", realm, "enabled", enabled))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new KeycloakException("Keycloak realm update failed: HTTP " + res.getStatusCode().value());
                })
                .toBodilessEntity();
    }

    @Override
    public boolean resetPassword(String realm, String username, String temporaryPassword) {
        List<Map<String, Object>> found = authorized().get()
                .uri("/admin/realms/{realm}/users?username={u}&exact=true", realm, username)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (found == null || found.isEmpty()) {
            return false;
        }
        Object id = found.get(0).get("id");
        authorized().put().uri("/admin/realms/{realm}/users/{id}/reset-password", realm, id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new KeycloakException("Keycloak password reset failed: HTTP " + res.getStatusCode().value());
                })
                .toBodilessEntity();
        return true;
    }

    // ---- plumbing ----

    private boolean exists(String path, Object... vars) {
        try {
            authorized().get().uri(path, vars).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private void post(String path, Object body) {
        authorized().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new KeycloakException("Keycloak " + path + " failed: HTTP " + res.getStatusCode().value());
                })
                .toBodilessEntity();
    }

    private RestClient authorized() {
        return http.mutate().defaultHeader("Authorization", "Bearer " + accessToken()).build();
    }

    private String accessToken() {
        if (token == null || Instant.now().isAfter(tokenExpires.minusSeconds(TOKEN_SKEW_SECONDS))) {
            synchronized (this) {
                if (token == null || Instant.now().isAfter(tokenExpires.minusSeconds(TOKEN_SKEW_SECONDS))) {
                    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                    form.add("grant_type", "client_credentials");
                    form.add("client_id", props.automationClientId());
                    form.add("client_secret", props.automationClientSecret());
                    Map<String, Object> res = http.post()
                            .uri("/realms/master/protocol/openid-connect/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                            .retrieve().body(new ParameterizedTypeReference<>() { });
                    if (res == null || res.get("access_token") == null) {
                        throw new KeycloakException("Keycloak token request returned no access_token");
                    }
                    tokenExpires = Instant.now().plusSeconds(((Number) res.getOrDefault("expires_in", 60)).longValue());
                    token = (String) res.get("access_token");
                }
            }
        }
        return token;
    }
}
