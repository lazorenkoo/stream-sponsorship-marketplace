package com.streambid.error;

import com.streambid.platform.ChannelNotFoundException;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.PlatformRateLimitException;
import com.streambid.platform.StreamingPlatform;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class PlatformErrorTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void mapsMissingChannelTo404() throws Exception {
        mvc.perform(get("/test/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Channel not found"));
    }

    @Test
    void mapsUnavailableToSafe503() throws Exception {
        mvc.perform(get("/test/unavailable")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Streaming platform unavailable"));
    }

    @Test
    void rateLimitIncludesRetryInformation() throws Exception {
        mvc.perform(get("/test/limited")).andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryAt").exists());
    }

    @RestController
    static class ErrorController {
        @GetMapping("/test/{kind}")
        void error(@PathVariable String kind) {
            switch (kind) {
                case "missing" -> throw new ChannelNotFoundException(StreamingPlatform.TWITCH);
                case "limited" -> throw new PlatformRateLimitException(StreamingPlatform.TWITCH, Instant.now().plusSeconds(60));
                default -> throw new PlatformUnavailableException(StreamingPlatform.TWITCH, new RuntimeException("private"));
            }
        }
    }
}
