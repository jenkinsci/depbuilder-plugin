package eu.royalsloth.depbuilder.jenkins.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Set;

/**
 * DTO for transfering build related data to frontend
 */
@SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "this is public api used by the frontend")
public class FinishedBuildJob {
    // project related properties
    public String projectName;
    /**
     * The name that will be displayed in the UI (if set)
     */
    public String displayName;
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
