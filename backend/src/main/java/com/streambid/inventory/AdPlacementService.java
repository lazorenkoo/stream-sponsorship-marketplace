package com.streambid.inventory;

import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdPlacementService {
    private final AdPlacementRepository placements;
    private final UserRepository users;
    private final Clock clock = Clock.systemUTC();

    public AdPlacementService(AdPlacementRepository placements, UserRepository users) {
        this.placements = placements;
        this.users = users;
    }

    @Transactional
    public AdPlacement create(String email, AdPlacementRequest request) {
        UUID owner = requireStreamer(email);
        var now = clock.instant();
        AdPlacement placement;
        try {
            placement = new AdPlacement(UUID.randomUUID(), request.channelId(), request.title(), request.position(),
                    request.widthPx(), request.heightPx(), request.durationSeconds(), request.minimumPrice(), request.currency(),
                    request.negotiable(), request.capacity(), false, 0, now, now);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid advertising placement details");
        }
        return placements.insertOwned(placement, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    }

    private UUID requireStreamer(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!users.findRoles(user.id()).contains("STREAMER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user.id();
    }
}
