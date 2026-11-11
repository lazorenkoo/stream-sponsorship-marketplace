package com.streambid.health;

import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {
    private final DSLContext database;

    public ReadinessController(DSLContext database) {
        this.database = database;
    }

    @GetMapping("/api/health/ready")
    public ResponseEntity<Readiness> ready() {
        try {
            database.resultQuery("SELECT 1").queryTimeout(2).fetch();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Readiness("UP"));
        } catch (DataAccessException | org.jooq.exception.DataAccessException exception) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new Readiness("DOWN"));
        }
    }

    public record Readiness(String status) { }
}
