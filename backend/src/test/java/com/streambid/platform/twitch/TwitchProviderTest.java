package com.streambid.platform.twitch;

import com.streambid.platform.StreamState;
import java.time.Clock;
import java.time.Instant;
import com.streambid.platform.PlatformRateLimitException;
import com.streambid.platform.ChannelNotFoundException;
import com.streambid.platform.PlatformUnavailableException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void waitsUntilRateLimitResetWithoutSendingExtraRequests() {
        Clock movingClock = mock(Clock.class);
        Instant start = Instant.parse("2026-11-03T10:00:00Z");
        when(movingClock.instant()).thenReturn(start);
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Ratelimit-Reset", Long.toString(start.plusSeconds(120).getEpochSecond())));
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withSuccess("""
                        {"data":[{"user_id":"123","type":"live","viewer_count":42}]}
                        """, MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, movingClock);
        assertThatThrownBy(() -> provider.getStreamStatus("123"))
                .isInstanceOf(PlatformRateLimitException.class);
        assertThatThrownBy(() -> provider.getStreamStatus("123"))
                .isInstanceOf(PlatformRateLimitException.class);
        when(movingClock.instant()).thenReturn(start.plusSeconds(120));
        assertThat(provider.getStreamStatus("123").viewers()).isEqualTo(42L);
        server.verify();
    }
    @Test
    void refreshesRejectedTokenAndRetriesOnce() {
        when(tokens.accessToken()).thenReturn("old-token", "new-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andExpect(header("Authorization", "Bearer old-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andExpect(header("Authorization", "Bearer new-token"))
                .andRespond(withSuccess("""
                        {"data":[{"user_id":"123","type":"live","viewer_count":42}]}
                        """, MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThat(provider.getStreamStatus("123").viewers()).isEqualTo(42L);
        verify(tokens).invalidate("old-token");
        server.verify();
    }

    @Test
    void repeatedUnauthorizedResponseStopsAfterTwoRequests() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThatThrownBy(() -> provider.getStreamStatus("123")).isInstanceOf(PlatformUnavailableException.class);
        verify(tokens).invalidate("test-token");
        server.verify();
    }
    @Test
    void confirmsChannelExistsBeforeReportingOffline() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users?id=123"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"123","display_name":"Streamer","profile_image_url":"https://example.test/avatar"}]}
                        """, MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        var status = provider.getStreamStatus("123");
        assertThat(status.state()).isEqualTo(StreamState.OFFLINE);
        assertThat(status.viewers()).isNull();
        server.verify();
    }

    @Test
    void missingChannelIsNotReportedOffline() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/helix/users?id=123"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThatThrownBy(() -> provider.getStreamStatus("123")).isInstanceOf(ChannelNotFoundException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void upstreamFailureIsNotReportedOffline(int statusCode) {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThatThrownBy(() -> provider.getStreamStatus("123")).isInstanceOf(PlatformUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"data\":{}}", "not-json",
            "{\"data\":[{\"user_id\":\"999\",\"type\":\"live\",\"viewer_count\":42}]}",
            "{\"data\":[{\"user_id\":\"123\",\"type\":\"live\",\"viewer_count\":-1}]}",
            "{\"data\":[{\"user_id\":\"123\",\"type\":\"live\",\"viewer_count\":\"42\"}]}"
    })
    void rejectsMalformedStreamData(String body) {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("https://example.test/helix/streams?user_id=123"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThatThrownBy(() -> provider.getStreamStatus("123")).isInstanceOf(PlatformUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "../users", "123&user_id=999"})
    void invalidChannelIdMakesNoRequest(String channelId) {
        var provider = new TwitchProvider(builder.build(), tokens, properties, Clock.systemUTC());
        assertThatThrownBy(() -> provider.getStreamStatus(channelId)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tokens);
        server.verify();
    }
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
