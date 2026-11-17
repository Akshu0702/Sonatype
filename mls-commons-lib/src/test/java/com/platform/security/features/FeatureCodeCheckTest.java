package com.platform.security.features;

import com.platform.security.features.FeatureCodeCheck.Catalogue;
import com.platform.security.features.FeatureCodeCheck.Use;
import com.platform.security.features.FeatureCodeStartupCheck.Mode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The keys code uses are found, checked against the catalogue at startup, and an API behind a feature that is off answers 404. */
class FeatureCodeCheckTest {

    private static final Catalogue CATALOGUE = new Catalogue(Set.of("platform", "itsm", "automation"),
            Set.of("itsm.tickets.tickets_create", "itsm.tickets.tickets_view_own", "automation.status.status_overview"));

    // ---- classes under test

    @RequiresFeature("itsm.tickets.tickets_create")
    static class OnClass {
        @RequiresFeature({"automation.status.status_overview"})
        public void one() { }

        @PreAuthorize("hasAuthority('x') and @features.on('itsm.tickets.tickets_view_own') and @features.productOn('itsm')")
        public void two() { }
    }

    static class Misspelt {
        @RequiresFeature("itsm.tickets.tickets_creat")
        public void one() { }

        @PreAuthorize("@features.productOn('aws-automation')")
        public void two() { }

        @RequiresFeature("Itsm.Tickets")
        public void three() { }
    }

    static class Clean {
        public void nothing() { }
    }

    @RequiresFeature("itsm.tickets.tickets_create")
    static class Guarded {
        public void create() { }

        @RequiresFeature("automation.status.status_overview")
        public void both() { }
    }

    // ---- finding the keys

    @Test
    void keysAreFoundOnClassesMethodsAndInAuthorizationExpressions() {
        List<Use> uses = FeatureCodeCheck.usesIn(OnClass.class);
        assertThat(uses).extracting(Use::key).containsExactlyInAnyOrder("itsm.tickets.tickets_create", "automation.status.status_overview",
                "itsm.tickets.tickets_view_own", "itsm");
        assertThat(uses).filteredOn(Use::product).extracting(Use::key).containsExactly("itsm");
        assertThat(FeatureCodeCheck.usesIn(Clean.class)).isEmpty();
    }

    @Test
    void knownKeysPassAndUnknownOrMalformedOnesAreNamedWithWhereTheyAre() {
        assertThat(FeatureCodeCheck.malformed(FeatureCodeCheck.usesIn(OnClass.class))).isEmpty();
        assertThat(FeatureCodeCheck.unknown(FeatureCodeCheck.usesIn(OnClass.class), CATALOGUE)).isEmpty();

        List<Use> bad = FeatureCodeCheck.usesIn(Misspelt.class);
        assertThat(FeatureCodeCheck.unknown(bad, CATALOGUE)).singleElement().asString()
                .contains("itsm.tickets.tickets_creat").contains("Misspelt#one");
        assertThat(FeatureCodeCheck.malformed(bad)).hasSize(2).anyMatch(m -> m.contains("aws-automation")).anyMatch(m -> m.contains("Itsm.Tickets"));
    }

    // ---- the startup check

    private ApplicationContextRunner start(Class<?> bean, Supplier<Catalogue> catalogue, Mode mode, int attempts) {
        return new ApplicationContextRunner().withBean(bean)
                .withUserConfiguration(Cfg.class)
                .withBean("catalogueSupplier", Supplier.class, () -> catalogue)
                .withPropertyValues("t.mode=" + mode, "t.attempts=" + attempts);
    }

    @org.springframework.context.annotation.Configuration
    static class Cfg {
        @Bean
        @SuppressWarnings("unchecked")
        FeatureCodeStartupCheck check(org.springframework.context.ApplicationContext ctx, Supplier<Catalogue> catalogueSupplier,
                                      org.springframework.core.env.Environment env) {
            return new FeatureCodeStartupCheck(ctx, catalogueSupplier, Mode.valueOf(env.getProperty("t.mode")), Integer.parseInt(env.getProperty("t.attempts")), 1);
        }
    }

    @Test
    void startupFailsOnAnUnknownKeyUnlessTheModeOnlyWarns() {
        start(Misspelt.class, () -> CATALOGUE, Mode.FAIL, 1).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).isInstanceOf(IllegalStateException.class);
            assertThat(rootMessage(ctx.getStartupFailure())).contains("tickets_creat").contains("aws-automation");
        });
        start(Misspelt.class, () -> CATALOGUE, Mode.WARN, 1).run(ctx -> assertThat(ctx).hasNotFailed());
        start(Misspelt.class, () -> CATALOGUE, Mode.OFF, 1).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void startupPassesWhenEveryKeyIsInTheCatalogue() {
        start(OnClass.class, () -> CATALOGUE, Mode.FAIL, 1).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void aServiceWithNoKeysNeverAsksForTheCatalogue() {
        AtomicInteger asked = new AtomicInteger();
        start(Clean.class, () -> { asked.incrementAndGet(); return CATALOGUE; }, Mode.FAIL, 1).run(ctx -> assertThat(ctx).hasNotFailed());
        assertThat(asked).hasValue(0);
    }

    @Test
    void aCatalogueThatCannotBeReadIsRetriedAndThenFailsClosedOrWarns() {
        AtomicInteger asked = new AtomicInteger();
        Supplier<Catalogue> down = () -> { asked.incrementAndGet(); throw new IllegalStateException("connection refused"); };
        start(OnClass.class, down, Mode.FAIL, 3).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessage(ctx.getStartupFailure())).contains("could not be read");
        });
        assertThat(asked).hasValue(3);
        start(OnClass.class, down, Mode.WARN, 1).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void theModeDefaultsToFailOutsideDevelopmentAndCanBeSet() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        assertThat(Mode.resolve(env)).isEqualTo(Mode.FAIL);
        env.setActiveProfiles("prod");
        assertThat(Mode.resolve(env)).isEqualTo(Mode.FAIL);
        for (String dev : List.of("dev", "local", "test")) {
            org.springframework.mock.env.MockEnvironment d = new org.springframework.mock.env.MockEnvironment();
            d.setActiveProfiles(dev);
            assertThat(Mode.resolve(d)).isEqualTo(Mode.WARN);
        }
        env.setProperty("platform.features.check", "off");
        assertThat(Mode.resolve(env)).isEqualTo(Mode.OFF);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    // ---- reading the catalogue and the 404 guard, against a stand-in for tenants-service

    private static final UUID TENANT = UUID.randomUUID();

    private HttpServer standIn(String catalogueBody, String featuresBody) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/internal/catalogue", ex -> reply(ex, catalogueBody));
        s.createContext("/internal/tenants/" + TENANT + "/features", ex -> reply(ex, featuresBody));
        s.start();
        return s;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void theCatalogueIsReadFromTenantsServiceAndAnUnreachableOneThrows() throws Exception {
        HttpServer s = standIn("{\"products\":[\"platform\",\"itsm\"],\"features\":[\"itsm.tickets.tickets_create\"],\"extra\":1}", "[]");
        try {
            Catalogue c = new TenantFeatures("http://127.0.0.1:" + s.getAddress().getPort(), Duration.ofSeconds(5)).catalogue();
            assertThat(c.products()).containsExactlyInAnyOrder("platform", "itsm");
            assertThat(c.features()).containsExactly("itsm.tickets.tickets_create");
        } finally {
            s.stop(0);
        }
        assertThatThrownBy(() -> new TenantFeatures("http://127.0.0.1:1", Duration.ofSeconds(5)).catalogue()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void anApiWhoseFeatureIsOffAnswers404AndOneThatIsLiveIsLetThrough() throws Exception {
        String live = "[{\"key\":\"itsm.tickets.tickets_create\",\"product\":\"itsm\",\"entitled\":true,\"enabled\":true,\"effective\":true},"
                + "{\"key\":\"automation.status.status_overview\",\"product\":\"automation\",\"entitled\":false,\"enabled\":true,\"effective\":false}]";
        HttpServer s = standIn("{}", live);
        try {
            FeatureGuardInterceptor guard = new FeatureGuardInterceptor(new TenantFeatures("http://127.0.0.1:" + s.getAddress().getPort(), Duration.ofSeconds(5)));
            HandlerMethod create = new HandlerMethod(new Guarded(), Guarded.class.getMethod("create"));
            HandlerMethod both = new HandlerMethod(new Guarded(), Guarded.class.getMethod("both"));
            TenantContext_run(() -> {
                assertThat(guard.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), create)).isTrue();
                assertThatThrownBy(() -> guard.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), both))
                        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
                assertThat(guard.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), "not a handler method")).isTrue();
            });
        } finally {
            s.stop(0);
        }
    }

    private static void TenantContext_run(Runnable r) {
        com.platform.security.tenancy.TenantContext.runAs(TENANT, r);
    }
}
