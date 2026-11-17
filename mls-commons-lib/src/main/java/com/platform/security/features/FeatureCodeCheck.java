package com.platform.security.features;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the feature keys and Product keys that code asks about, and the ones that are not in the catalogue. A key counts as used when it appears in
 * {@link RequiresFeature} or as a literal in {@code @features.on('...')} or {@code @features.productOn('...')} inside {@code @PreAuthorize} or
 * {@code @PostAuthorize}. A key built at run time cannot be found this way; use a literal.
 *
 * <p>Why it exists: a feature is off unless the catalogue says otherwise, so a misspelt or retired key does not fail, it quietly hides an API from
 * every tenant. This turns that into a failed start.
 */
public final class FeatureCodeCheck {

    /** {@code <product>.<group>.<name>}, the same rule the catalogue table enforces. */
    static final Pattern FEATURE_KEY = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*){2,}$");
    static final Pattern PRODUCT_KEY = Pattern.compile("^[a-z][a-z0-9]*$");
    private static final Pattern IN_EXPRESSION = Pattern.compile("@features\\.(on|productOn)\\(\\s*'([^']*)'\\s*\\)");

    /** What tenants-service says exists. */
    public record Catalogue(Set<String> products, Set<String> features) {
    }

    /** A key used by code, and where. */
    public record Use(String key, boolean product, String where) {
    }

    private FeatureCodeCheck() {
    }

    /** Every key a class asks about. */
    public static List<Use> usesIn(Class<?> type) {
        List<Use> uses = new ArrayList<>();
        RequiresFeature onType = AnnotatedElementUtils.findMergedAnnotation(type, RequiresFeature.class);
        if (onType != null) {
            for (String k : onType.value()) {
                uses.add(new Use(k, false, type.getName()));
            }
        }
        PreAuthorize onTypePre = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
        if (onTypePre != null) {
            expression(onTypePre.value(), type.getName(), uses);
        }
        for (Method m : ReflectionUtils.getUniqueDeclaredMethods(type)) {
            String where = type.getName() + "#" + m.getName();
            RequiresFeature r = AnnotatedElementUtils.findMergedAnnotation(m, RequiresFeature.class);
            if (r != null) {
                for (String k : r.value()) {
                    uses.add(new Use(k, false, where));
                }
            }
            PreAuthorize pre = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
            if (pre != null) {
                expression(pre.value(), where, uses);
            }
            PostAuthorize post = AnnotatedElementUtils.findMergedAnnotation(m, PostAuthorize.class);
            if (post != null) {
                expression(post.value(), where, uses);
            }
        }
        return uses;
    }

    private static void expression(String text, String where, List<Use> uses) {
        Matcher m = IN_EXPRESSION.matcher(text);
        while (m.find()) {
            uses.add(new Use(m.group(2), "productOn".equals(m.group(1)), where));
        }
    }

    /** Keys that are not well formed. Needs no catalogue. */
    public static List<String> malformed(List<Use> uses) {
        List<String> out = new ArrayList<>();
        for (Use u : uses) {
            if (!(u.product() ? PRODUCT_KEY : FEATURE_KEY).matcher(u.key()).matches()) {
                out.add((u.product() ? "Product key '" : "Feature key '") + u.key() + "' is not well formed (" + u.where() + ")");
            }
        }
        return out;
    }

    /** Well-formed keys that the catalogue does not have. */
    public static List<String> unknown(List<Use> uses, Catalogue catalogue) {
        Set<String> seen = new TreeSet<>();
        List<String> out = new ArrayList<>();
        for (Use u : uses) {
            boolean wellFormed = (u.product() ? PRODUCT_KEY : FEATURE_KEY).matcher(u.key()).matches();
            boolean known = u.product() ? catalogue.products().contains(u.key()) : catalogue.features().contains(u.key());
            if (wellFormed && !known && seen.add(u.product() + u.key() + u.where())) {
                out.add((u.product() ? "Product '" : "Feature '") + u.key() + "' is not in the catalogue (" + u.where() + ")");
            }
        }
        return out;
    }
}
