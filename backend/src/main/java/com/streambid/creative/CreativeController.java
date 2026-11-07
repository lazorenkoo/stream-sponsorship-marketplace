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

@RestController
public class CreativeController {
    private final CreativeUploadService uploads;

    public CreativeController(CreativeUploadService uploads) {
        this.uploads = uploads;
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
