package com.platform.security.features;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An API that exists only while the tenant has these features live. When one is off, the request is answered 404 as if the API did not exist.
 * On a class it applies to every handler in it. A feature key is {@code <product>.<group>.<name>}. The keys are checked against the catalogue
 * at startup (see {@link FeatureCodeCheck}), so a misspelt key stops a release instead of hiding an API from every tenant.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresFeature {

    /** Feature keys; all of them must be live. */
    String[] value();
}
