package eu.royalsloth.depbuilder.dsl;

import eu.royalsloth.depbuilder.dsl.scheduling.SchedulerSettings;

import java.util.List;

/**
 * DTO for holding parsed build data
 */
public class ParsedBuild {
    public final List<ParsedBuildJob> parsedJobs;
    public final SchedulerSettings schedulerSettings;

    public ParsedBuild(SchedulerSettings schedulerSettings, List<ParsedBuildJob> parsedJobs) {
        this.parsedJobs = parsedJobs;
        this.schedulerSettings = schedulerSettings;
    }
}
