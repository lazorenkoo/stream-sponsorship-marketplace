package com.streambid.channel;

import com.streambid.user.UserRepository;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChannelController {

    private final ChannelRepository channels;
    private final UserRepository users;

    public ChannelController(ChannelRepository channels, UserRepository users) {
        this.channels = channels;
        this.users = users;
    }

    @GetMapping("/api/channels")
    public List<ConnectedChannel> myChannels(Principal principal) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return channels.findByUser(user.id());
    }
}
