package eu.royalsloth.depbuilder.dsl.scheduling;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settings container used for managing plugin build scheduler
 */
public class SchedulerSettings {
    public static final Duration DEFAULT_MAX_BUILD_TIME = Duration.ofHours(2);
    public static final int NO_RESTRICTION = Integer.MAX_VALUE;

    public Duration maxDuration = DEFAULT_MAX_BUILD_TIME;
    public List<Throttle> buildThrottle = new ArrayList<>();

    public void addThrottle(Throttle... throttles) {
        buildThrottle.addAll(Arrays.asList(throttles));
        throttleSort();
    }

    public void setThrottles(List<Throttle> throttles) {
        buildThrottle.clear();
        buildThrottle.addAll(throttles);
        throttleSort();
    }

    private void throttleSort() {
        // sort in ascending order by time
        buildThrottle.sort((lhs, rhs) -> {
            LocalTime lhsTime = lhs.time;
            LocalTime rhsTime = rhs.time;

            if (lhsTime.isBefore(rhsTime)) {
                return -1;
            }

            if (lhsTime.isAfter(rhsTime)) {
                return 1;
            }

            return 0;
        });
    }

    /**
     * Get number of allowed executors based on current time or -1 if the number of current executors is not
     * restricted.
     */
    public int getAllowedExecutors(LocalTime currentTime) {
        // assuming the list of build throttles is sorted and small
        if (buildThrottle.isEmpty()) {
            return NO_RESTRICTION;
        }

        Throttle currentThrottle = buildThrottle.get(0);
        if (currentThrottle.time.isAfter(currentTime)) {
            // the first restriction happens before the current time
            // this means we placed no restrictions on the build
            return NO_RESTRICTION;
        }

        // find the most suitable throttle build in the list of throttles
        for (Throttle throttle : buildThrottle) {
            LocalTime time = throttle.time;
            if (time.isAfter(currentTime)) {
                // the throttle selected in the previous loop was the most appropriate,
                break;
            }

            // throttle time appears before the current time, keep iterating
            // to select the most appropriate time
            currentThrottle = throttle;
        }
        return currentThrottle.executors;
    }

    public static class Throttle {
        public final LocalTime time;
        public final int executors;

        public Throttle(LocalTime time, int numOfExecutors) {
            this.time = time;
            this.executors = numOfExecutors;
        }


        @Override
        public String toString() {
            return String.format("%s|%d", time, executors);
        }
    }
}
