package com.streambid.channel;

import com.streambid.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelListingService {
    private final ChannelRepository channels;
    private final ChannelListingRepository listings;
    private final UserRepository users;

    public ChannelListingService(ChannelRepository channels, ChannelListingRepository listings, UserRepository users) {
        this.channels = channels;
        this.listings = listings;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ChannelListing read(String email, UUID channelId) {
        return listings.readOwned(channelId, ownerId(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ChannelListing set(String email, UUID channelId, boolean listed) {
        UUID owner = ownerId(email);
        channels.lockOwned(channelId, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return listings.setOwned(channelId, owner, listed)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Reconnect the channel before listing it"));
    }

    private UUID ownerId(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }
}
