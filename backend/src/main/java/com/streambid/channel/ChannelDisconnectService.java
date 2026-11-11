package com.streambid.channel;

import com.streambid.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelDisconnectService {

    private final ChannelRepository channels;
    private final ChannelCredentialsRepository credentials;
    private final UserRepository users;
    private final ChannelListingRepository listings;

    public ChannelDisconnectService(ChannelRepository channels, ChannelCredentialsRepository credentials, UserRepository users, ChannelListingRepository listings) {
        this.channels = channels;
        this.credentials = credentials;
        this.users = users;
        this.listings = listings;
    }

    @Transactional
    public void disconnect(UUID channelId, String email) {
        var user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        channels.lockOwned(channelId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        listings.hideExisting(channelId);
        credentials.delete(channelId);
    }
}
