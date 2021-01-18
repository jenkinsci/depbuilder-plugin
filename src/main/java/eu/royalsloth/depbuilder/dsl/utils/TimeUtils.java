package eu.royalsloth.depbuilder.dsl.utils;

import eu.royalsloth.depbuilder.dsl.ParseException;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings;

import java.time.Duration;
import java.time.LocalTime;

public class TimeUtils {
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours > 0) {
            long minutesPart = duration.toMinutes() - hours * 60;
            return String.format("%dh:%02dm", hours, minutesPart);
        }

        long minutes = duration.toMinutes();
        if (minutes > 0) {
            long secondsPart = (duration.toMillis() / 1000) - minutes * 60;
            return String.format("%dm:%02ds", minutes, secondsPart);
        }

        long seconds = duration.toMillis() / 1000;
        return String.format("%ds", seconds);
    }

    public static LocalTime parseTime(String hourStr, String minutesStr) throws ParseException {
        try {
            int hours = Integer.parseInt(hourStr);
            int minutes = Integer.parseInt(minutesStr);
            if (hours < 0 || hours > 23) {
                throw new ParseException(String.format("'%s:%s' is not a valid time, expected hours range: [0, 23]", hourStr, minutesStr));
            }
            if (minutes < 0 || minutes > 59) {
                throw new ParseException(String.format("'%s:%s' is not a valid time, expected minutes range: [0, 59]", hourStr, minutesStr));
            }
            return LocalTime.of(hours, minutes);
        } catch (NumberFormatException e) {
            throw new ParseException(String.format("%s:%s is not a valid time (expected hh:mm)", hourStr, minutesStr));
        }
    }

    public static Duration parseDuration(String input) throws ParseException {
        if (input == null || input.isEmpty()) {
            // right now it's only used for build node settings, but this may cause
            // problems in case somebody reuses this code for non build settings
            return BuildSettings.INFINITE_DURATION;
        }

        String[] timeArr = input.split(":");
        switch (timeArr.length) {
            case 2: {
                int hours;
                int minutes;
                try {
                    hours = Integer.parseInt(timeArr[0]);
                    minutes = Integer.parseInt(timeArr[1]);
                } catch (Exception e) {
                    String msg = String.format("'%s' is not a valid duration (expected hh:mm)", input);
                    throw new ParseException(msg);
                }
                return Duration.ofHours(hours).plusMinutes(minutes);
            }
            case 1: {
                int minutes;
                try {
                    minutes = Integer.parseInt(timeArr[0]);
                } catch (Exception e) {
                    String msg = String.format("'%s' is not a valid duration (expected mm)", input);
                    throw new ParseException(msg);
                }

                int hours = minutes % 60;
                minutes = minutes - hours * 60;
                return Duration.ofHours(hours).plusMinutes(minutes);
            }

            default:
                String msg = String.format("'%s' is not a valid duration (expected hh:mm)", input);
                throw new ParseException(msg);
        }
    }
}
