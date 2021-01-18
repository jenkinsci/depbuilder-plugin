package eu.royalsloth.depbuilder.jenkins;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildJob;
import eu.royalsloth.depbuilder.jenkins.actions.BuildAddedCause;
import hudson.model.Run;

import java.util.Optional;
import java.util.concurrent.Future;

/**
 * Simple DTO that contains the data about the job that was scheduled to Jenkins
 */
public class BuildFuture {
    public final Future<?> future;
    public final BuildJob buildJob;
    public final BuildAddedCause cause;

    public BuildFuture(BuildJob buildJob, Future<?> buildFuture, BuildAddedCause cause) {
        this.future = buildFuture;
        this.buildJob = buildJob;
        this.cause = cause;
    }

    public Optional<Run<?, ?>> getScheduledBuild() {
        return cause.getBuild();
    }
}
