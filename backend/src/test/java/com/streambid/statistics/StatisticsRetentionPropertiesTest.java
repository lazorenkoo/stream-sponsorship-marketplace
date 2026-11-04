package com.streambid.statistics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsRetentionPropertiesTest {
    @Test
    void validatesRetentionDays() {
        assertThat(new StatisticsRetentionProperties(false, 90).enabled()).isFalse();
        assertThatThrownBy(() -> new StatisticsRetentionProperties(true, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatisticsRetentionProperties(true, 3651)).isInstanceOf(IllegalArgumentException.class);
    }
}
