package com.streambid.auth;

import com.streambid.user.UserRepository;
import com.streambid.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRoleService {

    private final UserRepository users;

    public UserRoleService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public void addRole(String email, UserRole role) {
        if (role == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid public role");
        }
        var user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        users.addRole(user.id(), role);
    }
}
