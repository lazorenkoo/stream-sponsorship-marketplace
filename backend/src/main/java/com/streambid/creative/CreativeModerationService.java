package com.streambid.creative;

import com.streambid.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreativeModerationService {
    private final UserRepository users;
    private final AdCreativeRepository creatives;
    private final CreativeReadService reads;

    public CreativeModerationService(UserRepository users, AdCreativeRepository creatives, CreativeReadService reads) {
        this.users = users;
        this.creatives = creatives;
        this.reads = reads;
    }
    public CreativeReadService.Content content(String email, UUID id) {
        admin(email);
        var creative = creatives.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return reads.content(creative);
    }

    public List<CreativeView> pending(String email, UUID after, int limit) {
        admin(email);
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid moderation limit");
        return creatives.pending(after, limit).stream().map(CreativeView::from).toList();
    }

    @Transactional
    public CreativeView review(String email, UUID id, CreativeReviewRequest request) {
        UUID reviewer = admin(email);
        creatives.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String reason = request.reason() == null ? null : request.reason().strip();
        if (request.status() == CreativeStatus.PENDING
                || (request.status() == CreativeStatus.REJECTED && (reason == null || reason.isEmpty()))
                || (request.status() == CreativeStatus.APPROVED && reason != null)
                || (reason != null && reason.codePoints().anyMatch(Character::isISOControl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid moderation decision or rejection reason");
        }
        return CreativeView.from(creatives.review(id, reviewer, request.status(), reason, request.expectedVersion(), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Creative has changed or has already been reviewed")));
    }

    private UUID admin(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!users.findRoles(user.id()).contains("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user.id();
    }
}
