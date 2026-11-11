package com.streambid.creative;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CreativeModerationController {
    private final CreativeModerationService moderation;

    public CreativeModerationController(CreativeModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/api/admin/creatives")
    public ResponseEntity<List<CreativeView>> pending(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.pending(principal.getName(), after, limit));
    }

    @PostMapping("/api/admin/creatives/{id}/review")
    public ResponseEntity<CreativeView> review(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody CreativeReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.review(principal.getName(), id, request));
    }
    @GetMapping("/api/admin/creatives/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id, Principal principal) {
        var content = moderation.content(principal.getName(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(content.bytes().length)
                .contentType(org.springframework.http.MediaType.parseMediaType(content.format().contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"" + id + "." + content.format().extension() + "\"")
                .body(content.bytes());
    }
}
