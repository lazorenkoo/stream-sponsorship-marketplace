package com.streambid.platform.twitch;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchOAuthState {

    private static final String SESSION_KEY = TwitchOAuthState.class.getName();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TwitchOAuthState(Clock twitchClock) {
        this.clock = twitchClock;
    }

    public String begin(HttpSession session, UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        synchronized (session) {
            session.setAttribute(SESSION_KEY, new Pending(state, userId, clock.instant().plusSeconds(600)));
        }
        return state;
    }

    public void consume(HttpSession session, UUID userId, String received) {
        synchronized (session) {
            Object stored = session.getAttribute(SESSION_KEY);
            session.removeAttribute(SESSION_KEY);
            if (!(stored instanceof Pending pending) || !pending.userId().equals(userId)
                    || !clock.instant().isBefore(pending.expiresAt()) || received == null || received.length() != 43
                    || !MessageDigest.isEqual(pending.state().getBytes(StandardCharsets.UTF_8),
                            received.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state");
            }
        }
    }

    private record Pending(String state, UUID userId, Instant expiresAt) {
        @Override
        public String toString() {
            return "Pending[redacted]";
        }
    }
}
