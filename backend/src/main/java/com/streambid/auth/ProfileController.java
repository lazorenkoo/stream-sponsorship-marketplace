package com.streambid.auth;

import com.streambid.user.UserRepository;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProfileController {

    private final UserRepository users;

    private final UserRoleService roles;

    public ProfileController(UserRepository users, UserRoleService roles) {
        this.users = users;
        this.roles = roles;
    }

    @PostMapping("/api/users/me/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(Principal principal, @Valid @RequestBody AddRoleRequest body,
                        HttpServletRequest request) {
        roles.addRole(principal.getName(), body.role());
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
    @GetMapping("/api/users/me")
    public UserProfile currentUser(Principal principal) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserProfile(user.id(), user.email(), user.displayName(), users.findRoles(user.id()));
    }
}
