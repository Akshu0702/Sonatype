package com.motivitylabs.mls.itsmapi;

import java.util.EnumSet;
import java.util.Set;

/**
 * The life of a provisioning ticket. The only moves allowed are the ones listed here; everything else is refused with
 * {@link IllegalTransitionException}, whoever asks.
 *
 * <pre>
 * RAISED -> PENDING_APPROVAL -> APPROVED -> PROVISIONING -> ACTIVE -> TESTING -> REPORTED -> CLOSED
 *                            \-> REJECTED -> CLOSED
 * </pre>
 */
public enum TicketState {
    RAISED, PENDING_APPROVAL, APPROVED, REJECTED, PROVISIONING, ACTIVE, TESTING, REPORTED, CLOSED;

    public Set<TicketState> next() {
        return switch (this) {
            case RAISED -> EnumSet.of(PENDING_APPROVAL);
            case PENDING_APPROVAL -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(PROVISIONING);
            case REJECTED -> EnumSet.of(CLOSED);
            case PROVISIONING -> EnumSet.of(ACTIVE);
            case ACTIVE -> EnumSet.of(TESTING);
            case TESTING -> EnumSet.of(REPORTED);
            case REPORTED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(TicketState.class);
        };
    }

    public boolean canMoveTo(TicketState to) {
        return next().contains(to);
    }

    /** Moves a person may ask for directly. The others happen only as the result of an approval decision or of provisioning. */
    public static final Set<TicketState> MANUAL_TARGETS = EnumSet.of(TESTING, REPORTED, CLOSED);

    public static class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(TicketState from, TicketState to) {
            super("A ticket cannot move from " + from + " to " + to);
        }
    }
}
