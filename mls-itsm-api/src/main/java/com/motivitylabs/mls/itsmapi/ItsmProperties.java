package com.motivitylabs.mls.itsmapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param configurations the environment configurations a ticket may ask for (the provisioning API's list replaces this once it exists)
 * @param approvers      people (token subject or user name) who may decide tickets until the IAM service holds the approver role;
 *                       when {@code platform.iam.url} is set, the IAM permission {@code itsm.approvals.decide} counts as well
 * @param storageDir     where the local build store keeps uploaded builds
 * @param uploadMaxBytes the largest build ZIP accepted
 * @param corsOrigins    browser origins allowed to call this API directly (the console); empty means none, which is right when a gateway or
 *                       reverse proxy serves both under one address
 */
@ConfigurationProperties(prefix = "itsm")
public record ItsmProperties(
        @DefaultValue({"ubuntu-22.04-postgres-16", "ubuntu-24.04-postgres-16"}) List<String> configurations,
        List<String> approvers,
        @DefaultValue("./data/builds") String storageDir,
        @DefaultValue("104857600") long uploadMaxBytes,
        List<String> corsOrigins) {

    public ItsmProperties {
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
        approvers = approvers == null ? List.of() : List.copyOf(approvers);
        configurations = List.copyOf(configurations);
    }
}
