package eu.royalsloth.depbuilder.jenkins.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu.royalsloth.depbuilder.jenkins.DslBuild;
import eu.royalsloth.depbuilder.jenkins.JenkinsUtil;
import hudson.model.Run;


/**
 * DTO for holding a job build information for one build of the DslBuild
 */
@SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "this is public api used by the frontend")
public class JobBuildStatus {
    public String projectName = "";
    public String buildStatus = "";
    public int buildNumber = -1;
    /**
     * Relative build uri that looks like job/foo/32 (without the trailing slash). This uri has to
     * be concatenated together on the client side.
     */
    public String buildUri = "";
    public String duration = DslBuild.durationToString(0);

    /**
     * Parse all the relevant data from the Jenkins build info class
     */
    public static JobBuildStatus from(Run<?, ?> build) {
        if (build == null) {
            return new JobBuildStatus();
        }

        JobBuildStatus info = new JobBuildStatus();
        info.projectName = parseProjectName(build);
        info.buildStatus = DslBuild.convertBuildResult(build.getResult());
        info.buildNumber = build.getNumber();

        info.duration = JenkinsUtil.getBuildDurationString(build);
        info.buildUri = build.getUrl();
        return info;
    }

    public static String parseProjectName(Run<?, ?> build) {
        if (build == null) {
            // this should never happen
            return "MISSING_BUILD";
        }

        // If the build is part of another parent (e.g: the user is using cloudbees-folder plugin)
        // we have to store the unique name which is fullName (name: A, fullName: myFolder/A)
        String parentName = build.getParent().getFullName();
        if (parentName == null) {
            // this should never happen
            return "MISSING_PARENT_NAME";
        }
        return parentName;
    }
}
