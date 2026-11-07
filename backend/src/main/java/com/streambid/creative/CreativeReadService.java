package com.streambid.creative;

import com.streambid.user.UserRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreativeReadService {
    private final UserRepository users;
    private final AdCreativeRepository creatives;
    private final ObjectProvider<S3CreativeStorage> storage;

    public CreativeReadService(UserRepository users, AdCreativeRepository creatives, ObjectProvider<S3CreativeStorage> storage) {
        this.users = users;
        this.creatives = creatives;
        this.storage = storage;
    }

    public AdCreative find(String email, UUID id) {
        return creatives.findOwned(id, owner(email)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<CreativeView> list(String email, UUID after, int limit) {
        UUID owner = owner(email);
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid creative limit");
        return creatives.listOwned(owner, after, limit).stream().map(CreativeView::from).toList();
    }

    public Content content(String email, UUID id) {
        var creative = find(email, id);
        var objects = storage.getIfAvailable();
        if (objects == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Creative storage is unavailable");
        try {
            byte[] bytes = objects.read(id, creative.format());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            if (bytes.length != creative.byteSize()
                    || !MessageDigest.isEqual(digest, java.util.HexFormat.of().parseHex(creative.sha256()))) {
                throw new IOException("Stored creative integrity check failed");
            }
            return new Content(creative.format(), bytes);
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Creative content is unavailable");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID owner(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).id();
    }

    public record Content(CreativeFormat format, byte[] bytes) { }
}
