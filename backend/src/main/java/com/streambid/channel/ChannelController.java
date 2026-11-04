package com.streambid.channel;

import com.streambid.user.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import com.streambid.platform.twitch.TwitchOAuthState;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChannelController {

    private final ChannelRepository channels;
    private final UserRepository users;
    private final ChannelDisconnectService disconnect;

    public ChannelController(ChannelRepository channels, UserRepository users, ChannelDisconnectService disconnect) {
        this.channels = channels;
        this.users = users;
        this.disconnect = disconnect;
    }

    @DeleteMapping("/api/channels/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID channelId, Principal principal, HttpServletRequest request) {
        disconnect.disconnect(channelId, principal.getName());
        TwitchOAuthState.cancel(request.getSession(false));
    }
    @GetMapping("/api/channels")
    public List<ChannelSummary> myChannels(Principal principal) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return channels.summariesByUser(user.id());
    }
}
