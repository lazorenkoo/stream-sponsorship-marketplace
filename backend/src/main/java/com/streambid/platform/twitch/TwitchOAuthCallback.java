package com.streambid.platform.twitch;

import com.streambid.channel.ConnectedChannel;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchOAuthCallback {

    private final TwitchOAuthState states;
    private final TwitchCodeExchange exchange;
    private final TwitchIdentityService identity;
    private final TwitchConnectionStore store;

    public TwitchOAuthCallback(TwitchOAuthState states, TwitchCodeExchange exchange,
                               TwitchIdentityService identity, TwitchConnectionStore store) {
        this.states = states;
        this.exchange = exchange;
        this.identity = identity;
        this.store = store;
    }

    public ConnectedChannel complete(UUID owner, HttpSession session, String code, String state, String error) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing OAuth session");
        }
        states.consume(session, owner, state);
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Twitch authorization was not granted");
        }
        var tokens = exchange.exchange(code);
        var verifiedChannel = identity.identify(tokens);
        return store.save(owner, verifiedChannel, tokens);
    }
}
