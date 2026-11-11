package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentials;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.channel.ChannelRepository;
import com.streambid.channel.ChannelListingRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.ChannelInfo;
import com.streambid.platform.StreamingPlatform;
import com.streambid.security.TokenCipher;
import com.streambid.user.UserRepository;
import com.streambid.user.UserRole;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchConnectionStore {

    private final ChannelRepository channels;
    private final ChannelCredentialsRepository credentials;
    private final UserRepository users;
    private final TokenCipher cipher;
    private final Clock clock;
    private final ChannelListingRepository listings;

    public TwitchConnectionStore(ChannelRepository channels, ChannelCredentialsRepository credentials,
                                 UserRepository users, TokenCipher cipher, Clock twitchClock, ChannelListingRepository listings) {
        this.channels = channels;
        this.credentials = credentials;
        this.users = users;
        this.cipher = cipher;
        this.clock = twitchClock;
        this.listings = listings;
    }

    @Transactional
    public ConnectedChannel save(UUID owner, ChannelInfo verifiedIdentity, TwitchUserTokens tokens) {
        var now = OffsetDateTime.now(clock);
        var proposed = new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                verifiedIdentity.channelId(), verifiedIdentity.displayName(), now);
        var channel = channels.connectVerified(proposed).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Channel is connected to another user"));
        if (credentials.find(channel.id()).isEmpty()) {
            listings.hideExisting(channel.id());
        }
        String access = cipher.encrypt(tokens.accessToken(), channel.id() + ":access");
        String refresh = cipher.encrypt(tokens.refreshToken(), channel.id() + ":refresh");
        credentials.save(new ChannelCredentials(channel.id(), access, refresh,
                OffsetDateTime.ofInstant(tokens.expiresAt(), ZoneOffset.UTC), now));
        users.addRole(owner, UserRole.STREAMER);
        return channel;
    }
}
