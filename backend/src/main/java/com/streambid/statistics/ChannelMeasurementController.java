package com.streambid.statistics;

import com.streambid.channel.ChannelRepository;
import com.streambid.user.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChannelMeasurementController {
    private final ChannelRepository channels;
    private final UserRepository users;
    private final ChannelMeasurementRepository measurements;
    private final StatisticsProperties properties;
    private final StatisticsRetentionProperties retention;
    private final boolean collectionEnabled;
    private final com.streambid.channel.ChannelCredentialsRepository credentials;
    private final com.streambid.platform.PlatformRegistry platforms;

    public ChannelMeasurementController(ChannelRepository channels, UserRepository users,
                                        ChannelMeasurementRepository measurements, StatisticsProperties properties,
            StatisticsRetentionProperties retention, com.streambid.channel.ChannelCredentialsRepository credentials,
            com.streambid.platform.PlatformRegistry platforms, @org.springframework.beans.factory.annotation.Value("${statistics.enabled:false}") boolean collectionEnabled) {
        this.channels = channels;
        this.users = users;
        this.measurements = measurements;
        this.properties = properties;
        this.retention = retention;
        this.collectionEnabled = collectionEnabled;
        this.credentials = credentials;
        this.platforms = platforms;
    }

    @GetMapping("/api/channels/{channelId}/statistics")
    public ResponseEntity<ChannelStatisticsSummary> summary(@PathVariable UUID channelId, Principal principal,
            @RequestParam Instant from, @RequestParam Instant to) {
        UUID owner = requireOwner(channelId, principal);
        var range = requireRange(from, to);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(measurements.summarize(channelId, owner, range.from(), range.to()));
    }
    @GetMapping("/api/channels/{channelId}/measurements")
    public ResponseEntity<List<ChannelMeasurement>> history(@PathVariable UUID channelId, Principal principal,
            @RequestParam(defaultValue = "100") int limit, @RequestParam(required = false) Instant before) {
        UUID owner = requireOwner(channelId, principal);
        if (limit < 1 || limit > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 200");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(measurements.history(channelId, owner, before, limit));
    }
    @GetMapping("/api/channels/{channelId}/statistics/hourly")
    public ResponseEntity<List<HourlyStatistics>> hourly(@PathVariable UUID channelId, Principal principal,
            @RequestParam Instant from, @RequestParam Instant to) {
        UUID owner = requireOwner(channelId, principal);
        var range = requireRange(from, to);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(measurements.hourly(channelId, owner, range.from(), range.to()));
    }

    @GetMapping("/api/channels/{channelId}/statistics/metadata")
    public ResponseEntity<StatisticsMetadata> metadata(@PathVariable UUID channelId, Principal principal) {
        var channel = requireOwnedChannel(channelId, principal);
        boolean providerAvailable = platforms.find(channel.platform()).isPresent();
        boolean authorizationRequired = credentials.find(channelId).isEmpty();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new StatisticsMetadata(
                measurements.availability(channelId, channel.userId()), collectionEnabled, properties.getIntervalMs(),
                retention.enabled(), retention.enabled() ? retention.days() : null, 31, providerAvailable, authorizationRequired,
                collectionEnabled && providerAvailable && !authorizationRequired));
    }
    private UUID requireOwner(UUID channelId, Principal principal) {
        return requireOwnedChannel(channelId, principal).userId();
    }

    private com.streambid.channel.ConnectedChannel requireOwnedChannel(UUID channelId, Principal principal) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return channels.find(channelId).filter(channel -> channel.userId().equals(user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MeasurementRange requireRange(Instant from, Instant to) {
        try {
            return new MeasurementRange(from, to);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
