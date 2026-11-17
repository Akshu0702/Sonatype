package com.platform.security.features;

import com.platform.security.features.FeatureCodeCheck.Catalogue;
import com.platform.security.features.FeatureCodeCheck.Use;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs {@link FeatureCodeCheck} once every bean exists. In a non-development environment a key that is malformed or not in the catalogue stops the
 * start; in development it is logged. A service that uses no feature keys never calls tenants-service here.
 *
 * <p>{@code platform.features.check} overrides the default: {@code fail}, {@code warn} or {@code off}. Profiles {@code dev}, {@code local} and
 * {@code test} default to {@code warn}; everything else defaults to {@code fail}.
 */
public class FeatureCodeStartupCheck implements SmartInitializingSingleton {

    public enum Mode {
        FAIL, WARN, OFF;

        public static Mode resolve(Environment env) {
            String set = env.getProperty("platform.features.check");
            if (set != null && !set.isBlank()) {
                return valueOf(set.trim().toUpperCase());
            }
            return env.acceptsProfiles(Profiles.of("dev", "local", "test")) ? WARN : FAIL;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(FeatureCodeStartupCheck.class);
    private static final List<String> SKIP = List.of("org.springframework.", "jakarta.", "java.", "javax.", "com.zaxxer.", "org.hibernate.", "org.apache.", "tools.jackson.", "com.fasterxml.");

    private final ApplicationContext context;
    private final Supplier<Catalogue> catalogue;
    private final Mode mode;
    private final int attempts;
    private final long retryMillis;

    public FeatureCodeStartupCheck(ApplicationContext context, Supplier<Catalogue> catalogue, Mode mode, int attempts, long retryMillis) {
        this.context = context;
        this.catalogue = catalogue;
        this.mode = mode;
        this.attempts = Math.max(1, attempts);
        this.retryMillis = retryMillis;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (mode == Mode.OFF) {
            return;
        }
        List<Use> uses = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name, false);
            if (type == null) {
                continue;
            }
            Class<?> user = ClassUtils.getUserClass(type);
            if (SKIP.stream().noneMatch(user.getName()::startsWith)) {
                uses.addAll(FeatureCodeCheck.usesIn(user));
            }
        }
        if (uses.isEmpty()) {
            return;
        }
        List<String> problems = new ArrayList<>(FeatureCodeCheck.malformed(uses));
        Catalogue loaded = load();
        if (loaded == null) {
            problems.add("The catalogue could not be read from tenants-service, so " + uses.size() + " feature key(s) in use could not be checked");
        } else {
            problems.addAll(FeatureCodeCheck.unknown(uses, loaded));
        }
        if (problems.isEmpty()) {
            LOG.info("Feature keys checked against the catalogue: {} in use, all known", uses.size());
            return;
        }
        String message = "Feature key check failed:\n  - " + String.join("\n  - ", problems);
        if (mode == Mode.FAIL) {
            throw new IllegalStateException(message);
        }
        LOG.warn(message);
    }

    private Catalogue load() {
        for (int i = 1; i <= attempts; i++) {
            try {
                return catalogue.get();
            } catch (RuntimeException ex) {
                LOG.warn("Reading the feature catalogue failed (attempt {} of {}): {}", i, attempts, ex.getMessage());
                if (i < attempts) {
                    try {
                        Thread.sleep(retryMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
