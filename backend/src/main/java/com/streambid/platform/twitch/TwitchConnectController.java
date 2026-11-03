package com.streambid.platform.twitch;

import com.streambid.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import com.streambid.channel.ConnectedChannel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final TwitchOAuthCallback callback;

    public TwitchConnectController(UserRepository users, TwitchAuthorizationService authorization, TwitchOAuthCallback callback) {
        this.users = users;
        this.authorization = authorization;
        this.callback = callback;
    }

    @GetMapping("/api/channels/connect/twitch/callback")
    public ConnectedChannel callback(Principal principal, HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam(required = false) String code,
                                     @RequestParam(required = false) String state,
                                     @RequestParam(required = false) String error) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return callback.complete(user.id(), request.getSession(false), code, state, error);
    }
    @PostMapping("/api/channels/connect/twitch")
    public Map<String, String> connect(Principal principal, HttpSession session) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("authorizationUrl", authorization.authorizationUrl(session, user.id()));
    }
}
