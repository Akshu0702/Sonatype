package com.motivitylabs.mls.itsmapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stands in for the provisioning API: it accepts every request, creates nothing, and reports every environment ready at once. */
public class FakeProvisioningClient implements ProvisioningClient {

    @Override
    public String request(Request request) {
        return "fake-" + request.idempotencyKey();
    }

    @Override
    public Status status(String reference) {
        return new Status(State.READY, "Fake provisioning: nothing was created");
    }

    @Configuration
    static class Wiring {
        @Bean
        @ConditionalOnMissingBean(ProvisioningClient.class)
        ProvisioningClient provisioningClient() {
            return new FakeProvisioningClient();
        }
    }
}
