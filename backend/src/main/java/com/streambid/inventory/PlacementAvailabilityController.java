package com.streambid.inventory;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlacementAvailabilityController {
    private final AdPlacementService placements;

    public PlacementAvailabilityController(AdPlacementService placements) {
        this.placements = placements;
    }

    @PostMapping("/api/placements/{id}/availability")
    public ResponseEntity<PlacementAvailability> add(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(placements.addAvailability(principal.getName(), id, request));
    }

    @GetMapping("/api/placements/{id}/availability")
    public ResponseEntity<List<PlacementAvailability>> list(@PathVariable UUID id, Principal principal,
            @RequestParam(required = false) Instant after, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(placements.availability(principal.getName(), id, after, limit));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return ResponseEntity.status(409).cacheControl(CacheControl.noStore())
                        .body(ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.CONFLICT,
                                "Availability overlaps an existing window"));
            }
        }
        throw exception;
    }
}
