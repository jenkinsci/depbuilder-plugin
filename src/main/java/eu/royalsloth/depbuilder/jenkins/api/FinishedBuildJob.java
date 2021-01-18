package eu.royalsloth.depbuilder.jenkins.api;

import java.util.Set;

/**
 * DTO for transfering build related data to frontend
 */
public class FinishedBuildJob {
    // project related properties
    public String projectName;
    public Set<String> children;
    public String projectUri;

    // build related properties
    public int buildNumber;
    public String buildUri;
    public String buildStatus;
    public String buildDuration;

    public FinishedBuildJob() {
    }
}
