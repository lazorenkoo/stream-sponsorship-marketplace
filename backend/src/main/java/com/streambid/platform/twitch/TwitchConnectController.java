package com.streambid.platform.twitch;

import com.streambid.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.Principal;
import com.streambid.security.AccountDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.PlatformUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "twitch.enabled", havingValue = "true")
public class TwitchConnectController {

    private final UserRepository users;
    private final TwitchAuthorizationService authorization;
    private final TwitchOAuthCallback callback;
    private final AccountDetailsService accounts;

    public TwitchConnectController(UserRepository users, TwitchAuthorizationService authorization, TwitchOAuthCallback callback, AccountDetailsService accounts) {
        this.users = users;
        this.authorization = authorization;
        this.callback = callback;
        this.accounts = accounts;
    }

    @GetMapping("/api/channels/connect/twitch/callback")
    public ResponseEntity<?> callback(Principal principal, HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam(required = false) String code,
                                     @RequestParam(required = false) String state,
                                     @RequestParam(required = false) String error) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String accept = request.getHeader("Accept");
        boolean browser = accept != null && accept.contains("text/html");
        ConnectedChannel channel;
        try {
            channel = callback.complete(user.id(), request.getSession(false), code, state, error);
        } catch (ResponseStatusException | PlatformUnavailableException failure) {
            if (browser) return backToProfile("failed");
            throw failure;
        }
        var account = accounts.loadUserByUsername(principal.getName());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                account.getUsername(), null, account.getAuthorities()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return browser ? backToProfile("connected") : ResponseEntity.ok(channel);
    }

    private ResponseEntity<Void> backToProfile(String status) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create("/profile?twitch=" + status)).build();
    }
    @PostMapping("/api/channels/connect/twitch")
    public Map<String, String> connect(Principal principal, HttpSession session) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("authorizationUrl", authorization.authorizationUrl(session, user.id()));
    }
}
