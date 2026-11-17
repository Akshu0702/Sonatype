package com.motivitylabs.mls.itsmapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStateTest {

    private static final List<TicketState[]> ALLOWED = List.of(
            new TicketState[] {TicketState.RAISED, TicketState.PENDING_APPROVAL},
            new TicketState[] {TicketState.PENDING_APPROVAL, TicketState.APPROVED},
            new TicketState[] {TicketState.PENDING_APPROVAL, TicketState.REJECTED},
            new TicketState[] {TicketState.APPROVED, TicketState.PROVISIONING},
            new TicketState[] {TicketState.REJECTED, TicketState.CLOSED},
            new TicketState[] {TicketState.PROVISIONING, TicketState.ACTIVE},
            new TicketState[] {TicketState.ACTIVE, TicketState.TESTING},
            new TicketState[] {TicketState.TESTING, TicketState.REPORTED},
            new TicketState[] {TicketState.REPORTED, TicketState.CLOSED});

    static Stream<Arguments> allowed() {
        return ALLOWED.stream().map(p -> Arguments.of(p[0], p[1]));
    }

    /** Every other pair of states: 81 pairs, 9 allowed, 72 not. */
    static Stream<Arguments> illegal() {
        List<Arguments> out = new ArrayList<>();
        for (TicketState from : TicketState.values()) {
            for (TicketState to : TicketState.values()) {
                boolean ok = ALLOWED.stream().anyMatch(p -> p[0] == from && p[1] == to);
                if (!ok) {
                    out.add(Arguments.of(from, to));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("allowed")
    void theLifecycleMovesAreAllowed(TicketState from, TicketState to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @MethodSource("illegal")
    void everyOtherMoveIsRefused(TicketState from, TicketState to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @Test
    void thereAreNineMovesAndSeventyTwoRefusals() {
        assertThat(allowed().count()).isEqualTo(9);
        assertThat(illegal().count()).isEqualTo(72);
    }

    @Test
    void aClosedTicketCannotMoveAnywhere() {
        assertThat(TicketState.CLOSED.next()).isEmpty();
    }

    @Test
    void personsMayAskDirectlyOnlyForTestingReportedAndClosed() {
        assertThat(TicketState.MANUAL_TARGETS).isEqualTo(EnumSet.of(TicketState.TESTING, TicketState.REPORTED, TicketState.CLOSED));
    }
}
