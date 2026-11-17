package com.motivitylabs.mls.itsmapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** The ITSM Product's own service: tickets for on-demand lab environments. Needs only PostgreSQL, the sign-in server and tenants-service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ItsmApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsmApiApplication.class, args);
    }
}
