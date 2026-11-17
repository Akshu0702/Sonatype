package com.motivitylabs.mls.portalapi;

import com.platform.security.features.RequiresFeature;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The AWS environments of the lab. Part of the AWS Automation Product; a tenant without it gets 404. */
@RestController
public class EnvironmentsController {

    private final AwsEnvironments environments;

    public EnvironmentsController(AwsEnvironments environments) {
        this.environments = environments;
    }

    @GetMapping("/environments")
    @RequiresFeature("automation.deployments_and_environments.environments_view")
    public AwsEnvironments.Overview environments() {
        return environments.overview();
    }
}
