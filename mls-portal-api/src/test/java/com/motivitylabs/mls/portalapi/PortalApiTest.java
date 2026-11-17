package com.motivitylabs.mls.portalapi;

import com.motivitylabs.mls.portalapi.AwsEnvironments.Instance;
import com.motivitylabs.mls.portalapi.AwsEnvironments.Tagged;
import com.motivitylabs.mls.portalapi.AwsEnvironments.Vpc;
import com.platform.security.features.FeatureGuardInterceptor;
import com.platform.security.features.TenantFeatures;
import com.platform.security.tenancy.TenantContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortalApiTest {

    private static final Vpc VPC = new Vpc("vpc-1", "mls-vpc-dev-mgmt-001", "10.40.0.0/20", "mgmt");
    private static Instance instance(String id, String name, String state) {
        return new Instance(id, name, "t3.xlarge", state, "10.40.0.1", "ap-south-1a", "lab", "2026-11-14T10:41:10Z");
    }

    private static AwsEnvironments.Reader reader(AtomicInteger calls) {
        return new AwsEnvironments.Reader() {
            public String region() { return "ap-south-1"; }
            public List<Tagged<Vpc>> vpcs() { calls.incrementAndGet(); return List.of(new Tagged<>("dev", VPC), new Tagged<>(null, new Vpc("vpc-x", "other", "172.31.0.0/16", null))); }
            public List<Tagged<Instance>> instances() { return List.of(new Tagged<>("dev", instance("i-2", "b-lab", "running")), new Tagged<>("dev", instance("i-1", "a-mgmt", "stopped")), new Tagged<>("test", instance("i-3", "t", "running")), new Tagged<>(null, instance("i-4", "untagged", "running"))); }
        };
    }

    @Test
    void thingsAreGroupedByTheirEnvironmentTagAndUntaggedOnesAreLeftOut() {
        AwsEnvironments.Overview o = AwsEnvironments.group("ap-south-1", reader(new AtomicInteger()).vpcs(), reader(new AtomicInteger()).instances(), Instant.parse("2026-11-16T00:00:00Z"));
        assertThat(o.environments()).extracting(AwsEnvironments.Environment::name).containsExactly("dev", "test");
        AwsEnvironments.Environment dev = o.environments().get(0);
        assertThat(dev.vpcs()).containsExactly(VPC);
        assertThat(dev.instances()).extracting(Instance::name).containsExactly("a-mgmt", "b-lab");
        assertThat(dev.running()).isEqualTo(1);
        assertThat(o.source()).isEqualTo("aws");
    }

    @Test
    void anAnswerIsKeptForAMinuteSoTheAwsApiIsNotCalledPerRequest() {
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-16T00:00:00Z"));
        AwsEnvironments service = new AwsEnvironments(reader(calls), clock);
        service.overview();
        service.overview();
        assertThat(calls).hasValue(1);
        clock.now = clock.now.plusSeconds(61);
        service.overview();
        assertThat(calls).hasValue(2);
    }

    // ---- the Product guard: a tenant without the Product gets 404, one with it gets the data

    private static final UUID WITH = UUID.randomUUID();
    private static final UUID WITHOUT = UUID.randomUUID();

    private static HttpServer tenantsService() throws Exception {
        String with = "[{\"key\":\"automation.deployments_and_environments.environments_view\",\"product\":\"automation\",\"entitled\":true,\"enabled\":true,\"effective\":true}]";
        String without = "[{\"key\":\"automation.deployments_and_environments.environments_view\",\"product\":\"automation\",\"entitled\":false,\"enabled\":true,\"effective\":false}]";
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/internal/tenants/" + WITH + "/features", ex -> reply(ex, with));
        s.createContext("/internal/tenants/" + WITHOUT + "/features", ex -> reply(ex, without));
        s.start();
        return s;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void environmentsAreHiddenFromATenantWithoutTheProduct() throws Exception {
        HttpServer s = tenantsService();
        try {
            TenantFeatures features = new TenantFeatures("http://127.0.0.1:" + s.getAddress().getPort(), Duration.ofSeconds(5));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new EnvironmentsController(new AwsEnvironments(reader(new AtomicInteger()), Clock.systemUTC())))
                    .addInterceptors(new FeatureGuardInterceptor(features)).build();
            TenantContext.runAs(WITH, () -> {
                try {
                    mvc.perform(get("/environments")).andExpect(status().isOk()).andExpect(jsonPath("$.environments[0].name").value("dev"))
                            .andExpect(jsonPath("$.environments[0].instances[0].name").value("a-mgmt"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            TenantContext.runAs(WITHOUT, () -> {
                try {
                    mvc.perform(get("/environments")).andExpect(status().isNotFound());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            s.stop(0);
        }
    }

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
