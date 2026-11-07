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
    private final PlacementAvailabilityRepository availability;
    private final Clock clock = Clock.systemUTC();

    public AdPlacementService(AdPlacementRepository placements, UserRepository users, PlacementAvailabilityRepository availability) {
        this.placements = placements;
        this.users = users;
        this.availability = availability;
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
    @Transactional
    public PlacementAvailability addAvailability(String email, UUID id, AvailabilityRequest request) {
        UUID owner = requireStreamer(email);
        var placement = placements.lockOwned(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlacementAvailability window;
        try {
            window = new PlacementAvailability(UUID.randomUUID(), id, request.startsAt(), request.endsAt());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability end must be after start");
        }
        if (!window.startsAt().isAfter(clock.instant())
                || java.time.Duration.between(window.startsAt(), window.endsAt()).getSeconds() < placement.durationSeconds()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability must be in the future and cover placement duration");
        }
        if (!placements.touchInactive(id, owner, request.expectedVersion(), clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Placement has changed or is active");
        }
        return availability.insertOwned(window, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public java.util.List<PlacementAvailability> availability(String email, UUID id, java.time.Instant after, int limit) {
        UUID owner = requireStreamer(email);
        placements.findOwned(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability limit must be between 1 and 100");
        }
        return availability.listOwned(id, owner, after, limit);
    }
    @Transactional
    public void removeAvailability(String email, UUID placementId, UUID windowId, long expectedVersion) {
        UUID owner = requireStreamer(email);
        placements.lockOwned(placementId, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected version cannot be negative");
        }
        if (!placements.touchInactive(placementId, owner, expectedVersion, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Placement has changed or is active");
        }
        if (!availability.deleteOwned(windowId, placementId, owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability window not found");
        }
    }
}
