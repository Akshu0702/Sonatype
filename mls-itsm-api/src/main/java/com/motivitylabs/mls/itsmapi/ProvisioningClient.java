package com.motivitylabs.mls.itsmapi;

import java.util.UUID;

/**
 * How an approved ticket gets its environment. The real implementation will call the provisioning API (Step Functions behind a private API
 * Gateway); until then {@link FakeProvisioningClient} answers, so the ticket flow can be built and tested on its own.
 */
public interface ProvisioningClient {

    /**
     * @param idempotencyKey the ticket id: asking twice for the same ticket must not create two environments
     * @param owner          the requester, written to the environment's mandatory owner tag
     * @param ticket         the ticket reference, written to the mandatory ticket tag
     */
    record Request(UUID idempotencyKey, UUID tenantId, String ticket, String owner, String config, int ttlHours, String buildSha256) {
    }

    enum State { PENDING, READY, FAILED }

    record Status(State state, String detail) {
    }

    /** Starts provisioning and returns a reference to follow it by. */
    String request(Request request);

    Status status(String reference);
}
