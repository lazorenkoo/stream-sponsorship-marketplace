package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentialsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"twitch.enabled", "twitch.validation.enabled"}, havingValue = "true")
public class TwitchValidationJob {

    private static final Logger LOG = LoggerFactory.getLogger(TwitchValidationJob.class);
    private static final int BATCH_SIZE = 50;
    private final ChannelCredentialsRepository credentials;
    private final TwitchValidationCycle cycle;
    private final Clock clock;
    private boolean firstRun = true;

    public TwitchValidationJob(ChannelCredentialsRepository credentials, TwitchValidationCycle cycle, Clock twitchClock) {
        this.credentials = credentials;
        this.cycle = cycle;
        this.clock = twitchClock;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${twitch.validation.interval-ms:300000}")
    public synchronized void validateDue() {
        var now = OffsetDateTime.now(clock);
        var cutoff = firstRun ? now : now.minusMinutes(55);
        UUID after = null;
        while (true) {
            var batch = credentials.dueForValidation(cutoff, after, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (UUID channelId : batch) {
                try {
                    cycle.check(channelId);
                } catch (RuntimeException exception) {
                    LOG.warn("Twitch validation failed for channel {} ({})", channelId, exception.getClass().getSimpleName());
                }
            }
            after = batch.getLast();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        firstRun = false;
    }
}
