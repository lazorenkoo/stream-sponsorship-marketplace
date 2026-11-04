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

    public ChannelMeasurementController(ChannelRepository channels, UserRepository users,
                                        ChannelMeasurementRepository measurements) {
        this.channels = channels;
        this.users = users;
        this.measurements = measurements;
    }

    @GetMapping("/api/channels/{channelId}/measurements")
    public ResponseEntity<List<ChannelMeasurement>> history(@PathVariable UUID channelId, Principal principal,
            @RequestParam(defaultValue = "100") int limit, @RequestParam(required = false) Instant before) {
        var user = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        channels.find(channelId).filter(channel -> channel.userId().equals(user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (limit < 1 || limit > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 200");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(measurements.history(channelId, user.id(), before, limit));
    }
}
