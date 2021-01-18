package eu.royalsloth.depbuilder.dsl.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSchedulerSettings {
    private final SchedulerSettings settings;

    public TestSchedulerSettings() {
        settings = new SchedulerSettings();

        SchedulerSettings.Throttle throttle1 = new SchedulerSettings.Throttle(LocalTime.of(10, 0), 5);
        SchedulerSettings.Throttle throttle2 = new SchedulerSettings.Throttle(LocalTime.of(15, 0), 2);
        SchedulerSettings.Throttle throttle3 = new SchedulerSettings.Throttle(LocalTime.of(20, 0), 10);
        settings.addThrottle(throttle1, throttle3, throttle2);
    }

    @Test
    public void testNumOfExecutors_startOfDay() {
        // the start of the day should reset the evening restriction
        LocalTime time = LocalTime.of(0, 0);
        assertEquals(SchedulerSettings.NO_RESTRICTION, settings.getAllowedExecutors(time), "Wrong number of executors");
    }

    @Test
    public void testNumOfExecutors_beforeRestriction() {
        LocalTime time = LocalTime.of(9, 59, 59);
        assertEquals(SchedulerSettings.NO_RESTRICTION, settings.getAllowedExecutors(time));
    }

    @Test
    public void testNumOfExecutors_early() {
        LocalTime time = LocalTime.of(10, 0);
        assertEquals(5, settings.getAllowedExecutors(time));
    }

    @Test
    public void testNumOfExecutors_restrictionApply() {
        LocalTime time = LocalTime.of(12, 12, 15);
        assertEquals(5, settings.getAllowedExecutors(time));
    }

    @Test
    public void testNumOfExecutors_afternoon() {
        LocalTime time = LocalTime.of(15, 0, 10);
        assertEquals(2, settings.getAllowedExecutors(time));
    }

    @Test
    public void testNumOfExecutors_evening() {
        LocalTime time = LocalTime.of(23, 59);
        assertEquals(10, settings.getAllowedExecutors(time));
    }
}
