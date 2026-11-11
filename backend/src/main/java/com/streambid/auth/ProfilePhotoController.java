package com.streambid.auth;

import com.streambid.creative.CreativeFormat;
import com.streambid.creative.CreativeImageValidator;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProfilePhotoController {
    private final DSLContext database;
    private final CreativeImageValidator images;

    public ProfilePhotoController(DSLContext database, CreativeImageValidator images) {
        this.database = database;
        this.images = images;
    }

    @PutMapping(value = "/api/users/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(Principal principal, @RequestParam MultipartFile photo) throws IOException {
        if (photo.isEmpty() || photo.getSize() > 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be under 1 MB");
        }
        byte[] bytes = photo.getBytes();
        try {
            var image = images.validate(bytes);
            if (image.format() != CreativeFormat.JPEG || image.widthPx() != 512 || image.heightPx() != 512) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a 512 × 512 JPEG");
        }
        if (database.execute("UPDATE users SET profile_photo = ? WHERE email = ?", bytes, principal.getName()) != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/api/users/me/photo")
    public ResponseEntity<byte[]> own(Principal principal) {
        return image(database.fetchOptional("SELECT profile_photo FROM users WHERE email = ?", principal.getName())
                .map(row -> row.get(0, byte[].class)).orElse(null));
    }

    @GetMapping("/api/catalog/streamers/{id}/photo")
    public ResponseEntity<byte[]> listed(@PathVariable UUID id) {
        return image(database.fetchOptional("""
                SELECT u.profile_photo FROM connected_channels c
                JOIN users u ON u.id = c.user_id
                JOIN channel_listings l ON l.channel_id = c.id AND l.listed
                JOIN channel_credentials t ON t.channel_id = c.id WHERE c.id = ?
                """, id).map(row -> row.get(0, byte[].class)).orElse(null));
    }

    private ResponseEntity<byte[]> image(byte[] bytes) {
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(bytes);
    }
}
