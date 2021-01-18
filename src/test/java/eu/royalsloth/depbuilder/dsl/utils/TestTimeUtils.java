package eu.royalsloth.depbuilder.dsl.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTimeUtils {
    @Test
    public void testHoursMinutes() {
        Duration d = Duration.ofHours(1).plusMinutes(20);
        assertEquals("1h:20m", TimeUtils.formatDuration(d));
    }

    @Test
    public void testMinutesSeconds() {
        Duration d = Duration.ofMinutes(9).plusSeconds(1);
        assertEquals("9m:01s", TimeUtils.formatDuration(d));
    }

    @Test
    public void testSeconds() {
        Duration d = Duration.ofSeconds(12);
        assertEquals("12s", TimeUtils.formatDuration(d));
    }
}
