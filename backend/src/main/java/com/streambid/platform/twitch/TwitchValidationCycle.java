package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentials;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.channel.ChannelRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.StreamingPlatform;
import com.streambid.security.TokenCipher;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchValidationCycle {

    private final ChannelRepository channels;
    private final ChannelCredentialsRepository credentials;
    private final TokenCipher cipher;
    private final TwitchTokenValidator validator;
    private final TwitchCredentialRefresh refresh;
    private final Clock clock;

    public TwitchValidationCycle(ChannelRepository channels, ChannelCredentialsRepository credentials, TokenCipher cipher,
                                  TwitchTokenValidator validator, TwitchCredentialRefresh refresh, Clock twitchClock) {
        this.channels = channels;
        this.credentials = credentials;
        this.cipher = cipher;
        this.validator = validator;
        this.refresh = refresh;
        this.clock = twitchClock;
    }

    public boolean check(UUID channelId) {
        var channel = channels.find(channelId).orElse(null);
        var snapshot = credentials.find(channelId).orElse(null);
        if (channel == null || channel.platform() != StreamingPlatform.TWITCH || snapshot == null) {
            return false;
        }
        try {
            try {
                return validate(channel, snapshot);
            } catch (TwitchAccessTokenRejectedException exception) {
                refresh.refreshRejected(channelId, cipher.decrypt(snapshot.accessTokenCiphertext(), channelId + ":access"));
                snapshot = credentials.find(channelId).orElse(null);
                return snapshot != null && validate(channel, snapshot);
            }
        } catch (TwitchAccessTokenRejectedException | TwitchReauthorizationRequiredException exception) {
            if (snapshot != null) {
                credentials.deleteIfUnchanged(snapshot);
            }
            return false;
        }
    }

    private boolean validate(ConnectedChannel channel, ChannelCredentials snapshot) {
        String access = cipher.decrypt(snapshot.accessTokenCiphertext(), channel.id() + ":access");
        var expires = validator.validate(access, channel.externalChannelId());
        return credentials.markValidated(snapshot, OffsetDateTime.now(clock), OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
    }
}
