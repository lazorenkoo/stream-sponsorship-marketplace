package com.streambid.platform.twitch;

import com.streambid.platform.StreamState;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwitchProviderTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/helix");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TwitchTokenService tokens = mock(TwitchTokenService.class);
    private final TwitchProperties properties = new TwitchProperties("test-client", "secret", "", "");

    @Test
    void readsLiveViewersAndSendsAuthorizationHeaders() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andExpect(header("Client-Id", "test-client"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"data":[{"user_id":"123","type":"live","viewer_count":42}]}
                        """, MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        var status = provider.getStreamStatus("123");
        assertThat(status.state()).isEqualTo(StreamState.LIVE);
        assertThat(status.viewers()).isEqualTo(42L);
        server.verify();
    }
}
