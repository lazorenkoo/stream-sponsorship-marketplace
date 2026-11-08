package com.streambid.creative;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;
import java.util.List;

@RestController
public class CreativeController {
    private final CreativeUploadService uploads;
    private final CreativeReadService reads;

    public CreativeController(CreativeUploadService uploads, CreativeReadService reads) {
        this.uploads = uploads;
        this.reads = reads;
    }
    @GetMapping("/api/creatives/{id}")
    public ResponseEntity<CreativeView> find(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CreativeView.from(reads.find(principal.getName(), id)));
    }

    @GetMapping("/api/creatives")
    public ResponseEntity<List<CreativeView>> list(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(principal.getName(), after, limit));
    }

    @GetMapping("/api/creatives/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id, Principal principal) {
        var content = reads.content(principal.getName(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.parseMediaType(content.format().contentType()))
                .contentLength(content.bytes().length).header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"" + id + "." + content.format().extension() + "\"")
                .body(content.bytes());
    }
    @GetMapping("/api/creatives/{id}/reviews")
    public ResponseEntity<List<CreativeReviewEvent>> reviews(@PathVariable UUID id, Principal principal,
            @RequestParam(required = false) Long beforeVersion, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.reviews(principal.getName(), id, beforeVersion, limit));
    }

    @PostMapping(value = "/api/creatives", consumes = "multipart/form-data")
    public ResponseEntity<CreativeView> upload(Principal principal, @RequestParam String title, @RequestParam MultipartFile file) {
        if (file.getSize() > 10485760) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds ten MiB");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(10485761);
            if (bytes.length > 10485760) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds ten MiB");
            var saved = uploads.upload(principal.getName(), title, bytes);
            return ResponseEntity.created(URI.create("/api/creatives/" + saved.id())).cacheControl(CacheControl.noStore())
                    .body(CreativeView.from(saved));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read uploaded image");
        }
    }
}
