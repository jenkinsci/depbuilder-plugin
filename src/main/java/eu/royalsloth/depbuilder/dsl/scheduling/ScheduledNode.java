package eu.royalsloth.depbuilder.dsl.scheduling;

public class ScheduledNode {
    private static final BuildJob emptyBuildJob = new BuildJob("WAIT_BUILD_JOB");
    public static final ScheduledNode WAIT_NODE = new ScheduledNode(emptyBuildJob, ScheduledNodeStatus.WAIT);
    public static final ScheduledNode ABORT_NODE = new ScheduledNode(emptyBuildJob, ScheduledNodeStatus.ABORT);
    public static final ScheduledNode FINISHED_NODE = new ScheduledNode(emptyBuildJob, ScheduledNodeStatus.FINISHED);

    private final ScheduledNodeStatus status;
    private final BuildJob buildJob;

    public ScheduledNode(BuildJob buildJob, ScheduledNodeStatus status) {
        this.status = status;
        this.buildJob = buildJob;
    }

    public ScheduledNodeStatus getStatus() {
        return status;
    }

    public BuildJob getBuildJob() {
        return buildJob;
    }

    public enum ScheduledNodeStatus {
        OK,
        WAIT,
        ABORT,
        FINISHED;
    }
}
