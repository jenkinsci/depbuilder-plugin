package eu.royalsloth.depbuilder.jenkins.api;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildStatus;
import eu.royalsloth.depbuilder.jenkins.DslBuild;

import java.util.ArrayList;
import java.util.List;

public class ProjectBuildStatus {
    /**
     * Build status of jobs that were built as part of the project
     */
    public List<JobBuildStatus> jobBuildStatus = new ArrayList<>();

    // project specifics
    public String buildStatus = BuildStatus.NONE.toString();
    public String duration = DslBuild.durationToString(0);
    public boolean finished = true;
}
