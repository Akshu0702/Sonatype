package com.motivitylabs.mls.itsmapi;

/** The signed-in person, as the ticket service needs to know them. {@code approver} is worked out by {@link ApproverDirectory}, never taken from the request. */
public record Actor(String subject, String name, boolean approver) {
}
