package com.streambid.channel;

import com.streambid.campaign.CampaignCandidate;
import com.streambid.campaign.CampaignCandidateRepository;
import com.streambid.platform.StreamingPlatform;
import java.util.Set;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
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
public class StreamerCatalogController {
    private final CampaignCandidateRepository channels;
    private final StreamerAudienceRepository audience;

    public StreamerCatalogController(CampaignCandidateRepository channels, StreamerAudienceRepository audience) {
        this.channels = channels;
        this.audience = audience;
    }

    @GetMapping("/api/catalog/streamers")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<StreamerCatalogPage> list(@RequestParam(required = false) StreamingPlatform platform,
            @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Streamer limit must be between 1 and 100");
        }
        var selected = platform == null ? Set.of(StreamingPlatform.values()) : Set.of(platform);
        var rows = channels.find(selected, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        var result = new StreamerCatalogPage(page, hasMore ? page.getLast().channelId() : null, hasMore,
                audience.find(page.stream().map(CampaignCandidate::channelId).toList()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/api/catalog/streamers/{id}")
    public ResponseEntity<CampaignCandidate> find(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(channels.findListed(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
}
