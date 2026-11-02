package com.streambid.auth;

import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import com.streambid.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public RegistrationService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid registration role");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 bytes");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        UserAccount user = new UserAccount(UUID.randomUUID(), email,
                passwords.encode(request.password()), request.displayName().strip());
        try {
            users.save(user);
            users.addRole(user.id(), request.role());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        return new UserProfile(user.id(), user.email(), user.displayName());
    }
}
