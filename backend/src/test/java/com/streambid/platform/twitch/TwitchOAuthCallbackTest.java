package com.streambid.platform.twitch;

import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.ChannelInfo;
import com.streambid.platform.StreamingPlatform;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TwitchOAuthCallbackTest {

    private final TwitchOAuthState states = mock(TwitchOAuthState.class);
    private final TwitchCodeExchange exchange = mock(TwitchCodeExchange.class);
    private final TwitchIdentityService identity = mock(TwitchIdentityService.class);
    private final TwitchConnectionStore store = mock(TwitchConnectionStore.class);
    private final TwitchOAuthCallback callback = new TwitchOAuthCallback(states, exchange, identity, store);
    private final UUID owner = UUID.randomUUID();
    private final MockHttpSession session = new MockHttpSession();

    @Test
    void validatesStateBeforeNetworkAndStoresVerifiedIdentity() {
        var tokens = new TwitchUserTokens("access", "refresh", Instant.now().plusSeconds(3600));
        var channel = new ChannelInfo("verified-user", "Streamer", null);
        var saved = new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                channel.channelId(), channel.displayName(), OffsetDateTime.now());
        when(exchange.exchange("code")).thenReturn(tokens);
        when(identity.identify(tokens)).thenReturn(channel);
        when(store.save(owner, channel, tokens)).thenReturn(saved);
        assertThat(callback.complete(owner, session, "code", "state", null)).isEqualTo(saved);
        var order = inOrder(states, exchange, identity, store);
        order.verify(states).consume(session, owner, "state");
        order.verify(exchange).exchange("code");
        order.verify(identity).identify(tokens);
        order.verify(store).save(owner, channel, tokens);
    }

    @Test
    void invalidStateStopsAllNetworkAndPersistence() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(states).consume(session, owner, "wrong");
        assertThatThrownBy(() -> callback.complete(owner, session, "code", "wrong", null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(exchange, identity, store);
    }

    @Test
    void missingSessionAndDeniedAuthorizationDoNotExchangeCode() {
        assertThatThrownBy(() -> callback.complete(owner, null, "code", "state", null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> callback.complete(owner, session, null, "state", "provider-private-error"))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("provider-private-error");
        verify(states).consume(session, owner, "state");
        verifyNoInteractions(exchange, identity, store);
    }
}
