package com.streambid.platform.twitch;

import com.streambid.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchConnectController {

    private final UserRepository users;
    private final TwitchAuthorizationService authorization;

    public TwitchConnectController(UserRepository users, TwitchAuthorizationService authorization) {
        this.users = users;
        this.authorization = authorization;
    }

    @PostMapping("/api/channels/connect/twitch")
    public Map<String, String> connect(Principal principal, HttpSession session) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("authorizationUrl", authorization.authorizationUrl(session, user.id()));
    }
}
