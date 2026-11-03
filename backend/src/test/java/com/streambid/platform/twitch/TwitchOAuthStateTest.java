package com.streambid.platform.twitch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwitchOAuthStateTest {

    private final Instant start = Instant.parse("2026-11-03T13:00:00Z");
    private final Clock clock = Clock.fixed(start, ZoneOffset.UTC);
    private final UUID owner = UUID.randomUUID();

    @Test
    void allowsOneUseAndGeneratesUnpredictableDistinctStates() {
        var states = new TwitchOAuthState(clock);
        var session = new MockHttpSession();
        String first = states.begin(session, owner);
        assertThat(first).hasSize(43);
        assertThatCode(() -> states.consume(session, owner, first)).doesNotThrowAnyException();
        assertThatThrownBy(() -> states.consume(session, owner, first)).isInstanceOf(ResponseStatusException.class);
        assertThat(states.begin(session, owner)).isNotEqualTo(first);
    }

    @Test
    void rejectsOtherSessionAndOtherOwner() {
        var states = new TwitchOAuthState(clock);
        var session = new MockHttpSession();
        String state = states.begin(session, owner);
        assertThatThrownBy(() -> states.consume(new MockHttpSession(), owner, state))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> states.consume(session, UUID.randomUUID(), state))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsExpiredStateAtTenMinuteBoundary() {
        Clock movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(start);
        var states = new TwitchOAuthState(movingClock);
        var session = new MockHttpSession();
        String state = states.begin(session, owner);
        when(movingClock.instant()).thenReturn(start.plusSeconds(600));
        assertThatThrownBy(() -> states.consume(session, owner, state)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMismatchAndConsumesPendingAttempt() {
        var states = new TwitchOAuthState(clock);
        var session = new MockHttpSession();
        String state = states.begin(session, owner);
        assertThatThrownBy(() -> states.consume(session, owner, "x".repeat(43)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> states.consume(session, owner, state)).isInstanceOf(ResponseStatusException.class);
    }
}
