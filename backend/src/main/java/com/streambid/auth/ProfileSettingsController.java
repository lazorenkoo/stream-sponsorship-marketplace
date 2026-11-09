package com.streambid.auth;

import com.streambid.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me/settings")
public class ProfileSettingsController {
    private final UserRepository users;

    public ProfileSettingsController(UserRepository users) {
        this.users = users;
    }

    public record Settings(@NotBlank @Size(max = 80) String displayName,
                           @Size(max = 2048) String coverUrl,
                           @Size(max = 500) String channelDescription) {}

    @GetMapping
    public Settings read(Principal principal) {
        return users.profileSettings(principal.getName())
                .map(value -> new Settings(value.displayName(), value.coverUrl(), value.channelDescription()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @PutMapping
    public Settings save(Principal principal, @Valid @RequestBody Settings settings) {
        String cover = settings.coverUrl() == null ? "" : settings.coverUrl().strip();
        if (!cover.isEmpty()) {
            try {
                URI uri = URI.create(cover);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid HTTPS cover image URL");
            }
        }
        String description = settings.channelDescription() == null ? "" : settings.channelDescription().strip();
        var saved = new Settings(settings.displayName().strip(), cover.isEmpty() ? null : cover,
                description.isEmpty() ? null : description);
        if (!users.updateProfileSettings(principal.getName(), saved.displayName(), saved.coverUrl(), saved.channelDescription())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return saved;
    }
}
