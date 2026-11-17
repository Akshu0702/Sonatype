package com.platform.security.features;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;

/** Answers 404 for a handler marked {@link RequiresFeature} when the tenant of the request does not have every one of its features live. */
public class FeatureGuardInterceptor implements HandlerInterceptor {

    private final TenantFeatures features;

    public FeatureGuardInterceptor(TenantFeatures features) {
        this.features = features;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        List<String> required = new ArrayList<>();
        RequiresFeature onClass = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
        RequiresFeature onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresFeature.class);
        for (RequiresFeature a : new RequiresFeature[] {onClass, onMethod}) {
            if (a != null) {
                required.addAll(List.of(a.value()));
            }
        }
        for (String key : required) {
            if (!features.on(key)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return true;
    }
}
