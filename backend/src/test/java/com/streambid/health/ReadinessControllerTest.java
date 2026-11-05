package com.streambid.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

class ReadinessControllerTest {
    @Test
    void databaseConnectionDeterminesReadinessWithoutExposingFailureDetails() {
        var database = mock(DSLContext.class);
        var controller = new ReadinessController(database);
        var healthy = controller.ready();
        assertThat(healthy.getStatusCode().value()).isEqualTo(200);
        assertThat(healthy.getBody().status()).isEqualTo("UP");
        assertThat(healthy.getHeaders().getCacheControl()).isEqualTo("no-store");
        when(database.fetch("SELECT 1"))
                .thenThrow(new org.jooq.exception.DataAccessException("private database details"));
        var unavailable = controller.ready();
        assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getBody().status()).isEqualTo("DOWN");
        assertThat(unavailable.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
