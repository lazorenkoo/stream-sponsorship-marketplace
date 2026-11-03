package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentials;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.security.TokenCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchCredentialRefresh {

    private final ChannelCredentialsRepository credentials;
    private final TokenCipher cipher;
    private final TwitchCodeExchange exchange;

    public TwitchCredentialRefresh(ChannelCredentialsRepository credentials, TokenCipher cipher, TwitchCodeExchange exchange) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.exchange = exchange;
    }

    @Transactional(noRollbackFor = TwitchReauthorizationRequiredException.class)
    public TwitchUserTokens refreshRejected(UUID channelId, String rejectedAccessToken) {
        var current = credentials.lock(channelId).orElseThrow(TwitchReauthorizationRequiredException::new);
        String access = cipher.decrypt(current.accessTokenCiphertext(), channelId + ":access");
        String refresh = cipher.decrypt(current.refreshTokenCiphertext(), channelId + ":refresh");
        if (!access.equals(rejectedAccessToken)) {
            return new TwitchUserTokens(access, refresh, current.expiresAt().toInstant());
        }
        TwitchUserTokens rotated;
        try {
            rotated = exchange.refresh(refresh);
        } catch (TwitchReauthorizationRequiredException exception) {
            credentials.delete(channelId);
            throw exception;
        }
        credentials.save(new ChannelCredentials(channelId,
                cipher.encrypt(rotated.accessToken(), channelId + ":access"),
                cipher.encrypt(rotated.refreshToken(), channelId + ":refresh"),
                OffsetDateTime.ofInstant(rotated.expiresAt(), ZoneOffset.UTC), current.lastValidatedAt()));
        return rotated;
    }
}
