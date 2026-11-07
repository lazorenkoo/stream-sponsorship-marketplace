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
    @Transactional(readOnly = true)
    public AdPlacement find(String email, UUID id) {
        return placements.findOwned(id, requireStreamer(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public java.util.List<AdPlacement> list(String email, UUID after, int limit) {
        UUID owner = requireStreamer(email);
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Placement limit must be between 1 and 100");
        }
        return placements.listOwned(owner, after, limit);
    }

    @Transactional
    public AdPlacement update(String email, UUID id, AdPlacementUpdateRequest update) {
        UUID owner = requireStreamer(email);
        var current = placements.findOwned(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var request = update.placement();
        if (!current.channelId().equals(request.channelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Placement channel cannot be changed");
        }
        AdPlacement changed;
        try {
            var now = clock.instant().isBefore(current.updatedAt()) ? current.updatedAt() : clock.instant();
            changed = new AdPlacement(id, current.channelId(), request.title(), request.position(), request.widthPx(),
                    request.heightPx(), request.durationSeconds(), request.minimumPrice(), request.currency(),
                    request.negotiable(), request.capacity(), current.active(), current.version(), current.createdAt(), now);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid advertising placement details");
        }
        return placements.updateOwned(changed, owner, update.expectedVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Placement has changed; reload before editing"));
    }
}
