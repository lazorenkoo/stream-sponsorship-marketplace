package com.streambid.creative;

import com.streambid.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreativeUploadService {
    private final UserRepository users;
    private final AdCreativeRepository creatives;
    private final CreativeImageValidator images;
    private final CreativeUploadIntentRepository intents;
    private final ObjectProvider<S3CreativeStorage> storage;
    private final Clock clock = Clock.systemUTC();

    public CreativeUploadService(UserRepository users, AdCreativeRepository creatives, CreativeImageValidator images,
            ObjectProvider<S3CreativeStorage> storage, CreativeUploadIntentRepository intents) {
        this.users = users;
        this.creatives = creatives;
        this.images = images;
        this.storage = storage;
        this.intents = intents;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdCreative upload(String email, String title, byte[] bytes) {
        var owner = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!users.findRoles(owner.id()).contains("ADVERTISER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var objects = storage.getIfAvailable();
        if (objects == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Creative storage is unavailable");
        AdCreative creative;
        try {
            var verified = images.validate(bytes);
            creative = new AdCreative(UUID.randomUUID(), owner.id(), title, verified.format(), verified.widthPx(),
                    verified.heightPx(), verified.byteSize(), verified.sha256(), CreativeStatus.PENDING, 0, clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid creative title or image");
        }
        intents.create(creative.id(), creative.format(), creative.createdAt());
        try {
            objects.put(creative.id(), creative.format(), bytes);
        } catch (RuntimeException exception) {
            cleanup(objects, creative, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Creative storage is unavailable");
        }
        try {
            return creatives.insertPending(creative);
        } catch (RuntimeException exception) {
            cleanup(objects, creative, exception);
            throw exception;
        }
    }

    private void cleanup(S3CreativeStorage objects, AdCreative creative, RuntimeException failure) {
        try {
            objects.delete(creative.id(), creative.format());
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
