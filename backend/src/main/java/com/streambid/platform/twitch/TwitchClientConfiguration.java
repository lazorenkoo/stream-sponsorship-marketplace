package com.streambid.platform.twitch;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Clock;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.streambid.security.TokenCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
@EnableConfigurationProperties({TwitchProperties.class, TwitchOAuthProperties.class})
public class TwitchClientConfiguration {

    @Bean
    public TokenCipher tokenCipher(@Value("${security.token-encryption-key:}") String key) {
        return new TokenCipher(key);
    }
    @Bean
    public RestClient twitchRestClient(TwitchProperties properties) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(properties.apiUrl())
                .requestFactory(requests).build();
    }

    @Bean
    public Clock twitchClock() {
        return Clock.systemUTC();
    }
}
