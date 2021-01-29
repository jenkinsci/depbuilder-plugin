package eu.royalsloth.depbuilder.jenkins.api;

import eu.royalsloth.depbuilder.jenkins.DslBuild;
import eu.royalsloth.depbuilder.jenkins.JenkinsUtil;
import hudson.model.Run;
import org.graalvm.compiler.core.common.SuppressFBWarnings;


/**
 * DTO for holding a job build information for one build of the DslBuild
 */
@SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "this is public api used by the frontend")
public class JobBuildStatus {
    public String projectName = "";
    public String buildStatus = "";
    public int buildNumber = -1;
    public String duration = DslBuild.durationToString(0);

    /**
     * Parse all the relevant data from the Jenkins build info class
     *
     * @param build
     * @return
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
        return info;
    }

    public static String parseProjectName(Run<?, ?> build) {
        if (build == null) {
            // this should never happen
            return "MISSING_BUILD";
        }

        String parentName = build.getParent().getName();
        if (parentName == null) {
            // this should never happen
            return "MISSING_PARENT_NAME";
        }
        return parentName;
    }
}
