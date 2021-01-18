package eu.royalsloth.depbuilder.dsl.scheduling;

import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuildJob {
    private final String id;
    private final Set<BuildJob> children;
    private final BuildSettings buildSettings;
    private BuildStatus buildStatus = BuildStatus.NONE;

    public BuildJob(String id) {
        this(id, new BuildSettings(id), new ArrayList<>());
    }

    public BuildJob(ParsedBuildJob buildNode) {
        this(buildNode.getId(), buildNode.getBuildSettings(), new ArrayList<>());
    }

    public BuildJob(String id, BuildSettings buildSettings) {
        this(id, buildSettings, new ArrayList<>());
    }

    public BuildJob(String id, BuildSettings buildSettings, List<BuildJob> children) {
        this.id = id;
        this.buildSettings = buildSettings;
        this.children = new HashSet<>(children);
    }

    public String getId() {
        return id;
    }

    public void addChild(BuildJob buildJob) {
        this.children.add(buildJob);
    }

    public Set<BuildJob> getChildren() {
        return children;
    }

    public void setBuildStatus(BuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }

    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    public BuildSettings getBuildSettings() {
        return buildSettings;
    }

    public boolean isReadyToBuild() {
        switch (buildStatus) {
            case NONE:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return true if the build is done (either success, no build or error) and false if the build is in
     * progress or waiting for parents to be built
     */
    public boolean isBuildFinished() {
        switch (buildStatus) {
            case NONE:
            case IN_PROGRESS:
                return false;
            default:
                return true;
        }
    }

    public boolean hasErrors() {
        if (buildStatus == BuildStatus.ERROR ||
                buildStatus == BuildStatus.PARENT_ERROR) {
            return true;
        }
        return false;
    }

    public boolean wasAborted() {
        if (buildStatus == BuildStatus.ABORT) {
            return true;
        }
        return false;
    }
}
