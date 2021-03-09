package eu.royalsloth.depbuilder.dsl.scheduling;

import eu.royalsloth.depbuilder.dsl.utils.TimeUtils;
import eu.royalsloth.depbuilder.jenkins.BuildFuture;
import eu.royalsloth.depbuilder.jenkins.JenkinsUtil;
import eu.royalsloth.depbuilder.jenkins.actions.PersistBuildInfoAction;
import hudson.model.Result;
import hudson.model.Run;

import javax.annotation.CheckForNull;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static eu.royalsloth.depbuilder.dsl.scheduling.ScheduledNode.ScheduledNodeStatus;

public class Scheduler {

    protected final Instant startTime;
    protected final BuildLayers buildLayers;
    protected final Set<String> finished = new HashSet<>();
    protected final SchedulerSettings settings;

    protected boolean buildHasErrors = false;
    protected boolean buildWasAborted = false;
    private int operatingLayer = 0;

    @CheckForNull
    private volatile BuildFuture futureBuild;

    public Scheduler(BuildLayers buildLayers, SchedulerSettings settings) {
        this(buildLayers, settings, Instant.now());
    }

    public Scheduler(BuildLayers buildLayers, SchedulerSettings settings, Instant startTime) {
        this(buildLayers, settings, startTime, new ArrayList<>());
    }

    public Scheduler(BuildLayers buildLayers, SchedulerSettings settings, Instant startTime,
            List<String> startBuildWithNodes) {
        this.startTime = startTime;
        this.settings = settings;
        this.buildLayers = buildLayers;
        if (buildLayers.hasCycle()) {
            throw new IllegalStateException(
                    "Provided build layers contain a cycle: " + buildLayers.getBuildCycle());
        }

        // check if the user selected only one part of the graph for building
        final boolean partialGraphBuild = !startBuildWithNodes.isEmpty();
        if (partialGraphBuild) {
            // user wanted to build only a small chunk of the graph, steps:
            // 1. Set the status of all jobs to NO_BUILD
            // 2. Find the children nodes of the user selected nodes and
            //    set their status to NONE (only NONE nodes are scheduled)
            for (List<BuildJob> layer : buildLayers.getLayers()) {
                for (BuildJob job : layer) {
                    job.setBuildStatus(BuildStatus.NO_BUILD);
                    finished.add(job.getId());
                }
            }

            // find the chosen nodes from which the build process should start
            // and mark all their childs as ready to build (NONE status)
            for (String startingJob : startBuildWithNodes) {
                BuildJob job = buildLayers.getBuildNode(startingJob);
                final boolean startingJobWasNotFound = job == null;
                if (startingJobWasNotFound) {
                    throw new IllegalStateException(String.format("Can't start the partial build, the selected job '%s' does not exist in the build graph", startingJob));
                }
                markNodesAsReadyToBuild(job);
            }
        }
    }

    protected void markNodesAsReadyToBuild(BuildJob job) {
        job.setBuildStatus(BuildStatus.NONE);
        // we have to remove such jobs from finished set, otherwise the
        // scheduler will think there are still nodes to build and won't finish
        finished.remove(job.getId());
        for (BuildJob child : job.getChildren()) {
            child.setBuildStatus(BuildStatus.NONE);
            finished.remove(child.getId());
            boolean isLeafNode = child.getChildren().isEmpty();
            if (isLeafNode) {
                return;
            }
            markNodesAsReadyToBuild(child);
        }
    }

    public List<BuildFuture> getQueuedBuilds() {
        BuildFuture build = this.futureBuild;
        if (build != null) {
            return Arrays.asList(build);
        }

        return new ArrayList<>();
    }

    public void addQueuedBuild(BuildFuture future) {
        BuildFuture build = this.futureBuild;
        if (build == null) {
            this.futureBuild = future;
            return;
        }

        // this should never happen
        this.errorBuild(build.buildJob);
        this.futureBuild = null;
        throw new IllegalStateException(String.format("Can't add another build %s while another build %s is building", future.buildJob
                .getId(), build.buildJob.getId()));
    }

    /**
     * Eject the build if the build has finished building.
     *
     * @return true if the build was ejected and false otherwise.
     */
    public boolean ejectFinishedBuilds(PrintStream logger, PersistBuildInfoAction persistBuildInfo) {
        final BuildFuture build = this.futureBuild;
        if (build == null) {
            return false;
        }

        BuildSettings settings = build.buildJob.getBuildSettings();
        long maxNodeBuildTime = settings.getMaxDuration().toMillis();
        long maxProjectBuildTime = this.settings.maxDuration.toMillis();
        long maxBuildTime = Math.min(maxNodeBuildTime, maxProjectBuildTime);
        Instant end = startTime.plusMillis(maxBuildTime);
        long maxWaitingTime = end.toEpochMilli() - Instant.now().toEpochMilli();

        final boolean timeBudgetWasSpent = maxWaitingTime < 0;
        if (timeBudgetWasSpent) {
            this.abortBuild(build.buildJob);
            if (build.getScheduledBuild().isPresent()) {
                persistBuildInfo.addBuild(build.getScheduledBuild().get());
            } else {
                persistBuildInfo.addCancelledBuild(build.buildJob.getId());
            }
            return true;
        }

        try {
            // we still have enough time to build the project
            build.future.get(maxWaitingTime, TimeUnit.MILLISECONDS);
            // at this point the project is considered as built. The build could
            // either finish, was aborted or failed due to some build error.
            this.futureBuild = null;
            if (!build.getScheduledBuild().isPresent()) {
                // this should never happen
                logger.println(String.format("Project %s: last build is not present", build.buildJob.getId()));
                persistBuildInfo.addCancelledBuild(build.buildJob.getId());
                this.errorBuild(build.buildJob);
                return true;
            }

            // build exists, process it and log it to the console
            Run<?, ?> lastBuild = build.getScheduledBuild().get();
            Result result = lastBuild.getResult();
            if (result == null) { // this should never happen
                logger.println(String.format("Project %s: last build result is not present", build.buildJob.getId()));
                persistBuildInfo.addBuild(lastBuild);
                this.errorBuild(build.buildJob);
                return true;
            }

            final boolean buildHasFailed = result.isWorseThan(Result.SUCCESS);
            if (buildHasFailed) {
                this.errorBuild(build.buildJob);
            } else {
                this.successBuild(build.buildJob);
            }

            String projectLink = JenkinsUtil.createConsoleLink(lastBuild);
            logger.println(String.format("Project %s: %s", projectLink, result));
            persistBuildInfo.addBuild(lastBuild);
            return true;
        } catch (InterruptedException e) {
            // the user has interrupted the build process through jenkins UI
            this.futureBuild = null;
            this.abortBuild(build.buildJob);

            AbortReason reason = createAbortReason(build, persistBuildInfo);
            String msg = String.format("Project %s: %s, due to user action", reason.projectId, reason.result);
            logger.println(msg);
            return true;
        } catch (ExecutionException e) {
            // not sure when this happens
            this.futureBuild = null;
            this.abortBuild(build.buildJob);

            AbortReason reason = createAbortReason(build, persistBuildInfo);
            String msg = String.format("Project %s: %s", reason.projectId, reason.result);
            logger.println(msg);
            return true;
        } catch (TimeoutException e) {
            // the build takes too long to execute, terminate the build
            this.futureBuild = null;
            this.abortBuild(build.buildJob);

            AbortReason reason = createAbortReason(build, persistBuildInfo);
            String msg = String.format("Project %s: %s, max build time %s exceeded", reason.projectId, reason.result,
                                       TimeUtils.formatDuration(Duration.ofMillis(maxBuildTime)));
            logger.println(msg);
            return true;
        }
    }

    protected static class AbortReason {
        public String projectId;
        public String result;

        public AbortReason(String projectId, Result result) {
            this.projectId = projectId;
            if (result == null) {
                this.result = "ONGOING";
            } else {
                this.result = result.toString();
            }
        }
    }

    protected AbortReason createAbortReason(BuildFuture build, PersistBuildInfoAction persistBuildInfo) {
        if (build.getScheduledBuild().isPresent()) {
            Run<?, ?> lastBuild = build.getScheduledBuild().get();
            persistBuildInfo.addBuild(lastBuild);

            String projectLink = JenkinsUtil.createConsoleLink(lastBuild);
            Result result = lastBuild.getResult();
            return new AbortReason(projectLink, result);
        }

        // we don't have a build result, maybe the build hasn't even started
        // and was just waiting in queue. We cannot link to the project build
        String projectLink = build.buildJob.getId();
        Result result = Result.ABORTED;
        persistBuildInfo.addCancelledBuild(build.buildJob.getId());
        return new AbortReason(projectLink, result);
    }

    public void successBuild(BuildJob node) {
        this.finishBuild(node, BuildStatus.SUCCESS);
    }

    public void successBuild(ScheduledNode node) {
        this.successBuild(node.getBuildJob());
    }

    public void abortBuild(BuildJob node) {
        this.finishBuild(node, BuildStatus.ABORT);
    }

    public void abortBuild(ScheduledNode node) {
        this.abortBuild(node.getBuildJob());
    }

    public void errorBuild(BuildJob node) {
        this.finishBuild(node, BuildStatus.ERROR);
    }

    public void errorBuild(ScheduledNode node) {
        this.errorBuild(node.getBuildJob());
    }

    public void finishBuild(BuildJob buildJob, BuildStatus status) {
        switch (status) {
            case NO_BUILD:
            case SUCCESS: {
                buildJob.setBuildStatus(status);
                finished.add(buildJob.getId());
                // check if all nodes in the build layer are completed, then we can
                // move to the next step


                final List<List<BuildJob>> layers = this.buildLayers.getLayers();
                if (operatingLayer < layers.size()) {
                    // check if we can go down one layer in the graph. We can go down
                    // if all the build nodes in the layer are finished
                    List<BuildJob> layer = layers.get(operatingLayer);
                    boolean allNodeBuildFinished = true;
                    for (BuildJob node : layer) {
                        if (!node.isBuildFinished()) {
                            allNodeBuildFinished = false;
                        }
                    }
                    if (allNodeBuildFinished) {
                        // increase the operating layer by 1 as all the nodes in the layer
                        // are finished
                        operatingLayer++;
                    }
                }
            }
            break;
            case ABORT:
                this.buildWasAborted = true;
                buildJob.setBuildStatus(status);
                finished.add(buildJob.getId());
                break;
            case ERROR:
                // @FUTURE here we may distinguish between error and abort if necessary
                this.buildHasErrors = true;
                buildJob.setBuildStatus(status);
                finished.add(buildJob.getId());
                break;
            default:
                String msg = String.format("You can't finish a build for a node %s with status %s", buildJob, status);
                throw new IllegalStateException(msg);
        }
    }

    public boolean hasNext() {
        if (wasAborted()) {
            // if the user aborted the build (as far as I know that is currently
            // only possible through Jenkins UI or API), there is no point in
            // trying to build the rest of the nodes in the project.
            //
            // If there is an error in the build, it still makes sense to try
            // and build the rest of the projects and not finish early so we
            // get more info about which projects go through and which do not.
            // It also depends on the specific project (they may allow parent
            // builds to fail and continue building until the end)
            return false;
        }

        if (finished.size() == this.buildLayers.getNumberOfBuildNodes()) {
            return false;
        }

        return true;
    }

    /**
     * Checks if any build node in the scheduler has errors. This method should only be called once the
     * scheduler returns ABORT node, so we know if that is due to a build being aborted, or because a certain
     * build didn't build (or has parent build error)
     *
     * @return if any node in the build layers has error and false if it does not.
     */
    public boolean hasBuildErrors() {
        return this.buildHasErrors;
    }

    /**
     * @return true if the build was aborted and false otherwise
     */
    public boolean wasAborted() {
        return this.buildWasAborted;
    }

    /**
     * @return next node used for building
     */
    public ScheduledNode getNext() {
        if (this.buildWasAborted) {
            return ScheduledNode.ABORT_NODE;
        }

        if (this.futureBuild == null) {
            return getNextNode();
        }
        return ScheduledNode.WAIT_NODE;
    }

    protected ScheduledNode getNextNode() {
        if (finished.size() == this.buildLayers.getNumberOfBuildNodes()) {
            return ScheduledNode.FINISHED_NODE;
        }

        final List<List<BuildJob>> layers = buildLayers.getLayers();
        // What we want to achieve is:
        // A   C
        // |   |
        // B   D
        //
        // Layers:
        // [A, C]
        // [B, D]
        //
        // If build A is slow and build C is fast, we should be able to build D instead of waiting for A.
        // before we finish building the 1. layer.
        boolean shouldWaitForParentBuild = false;
        boolean parentsHaveError = false;

        int currentLayer = operatingLayer;
        while (currentLayer < layers.size()) {
            for (BuildJob node : layers.get(currentLayer)) {
                if (node.isReadyToBuild()) {
                    // check if node is available, by having all parents resolved
                    ParentBuildStatus status = checkParentBuildStatus(node);
                    switch (status) {
                        case OK:
                            node.setBuildStatus(BuildStatus.IN_PROGRESS);
                            return new ScheduledNode(node, ScheduledNodeStatus.OK);
                        case NOT_BUILT:
                            // parents of this node were not yet built but they also
                            // do not have errors, we have to build all the parents
                            // of this node first, before we can build this node.
                            shouldWaitForParentBuild = true;
                            continue;
                        case ERROR:
                            // parents of this node have a build error, this node
                            // or its children shouldn't be built
                            node.setBuildStatus(BuildStatus.PARENT_ERROR);
                            this.buildHasErrors = true;
                            parentsHaveError = true;
                            continue;
                        default:
                            throw new IllegalStateException("Unknown build state: " + status);
                    }
                } else {
                    // node is not ready to be build, check if the node has errors.
                    // this extra check is necessary, so we can abort the build
                    BuildStatus status = node.getBuildStatus();
                    if (status == BuildStatus.PARENT_ERROR) {
                        parentsHaveError = true;
                    }
                }
            }

            currentLayer++;
        }

        final boolean buildShouldWait = parentsHaveError && shouldWaitForParentBuild;
        if (buildShouldWait) {
            // there are some nodes with an error
            // and some nodes which are still waiting for the parent build
            //
            // we should wait for the next finished build, before scheduling next build node
            return ScheduledNode.WAIT_NODE;
        }

        final boolean abortBuild = parentsHaveError;
        if (abortBuild) {
            // there is no other child that we can build, as all non built children
            // have parents with an error. We should abort the build.
            return ScheduledNode.ABORT_NODE;
        }

        // otherwise we can only wait for the next node build to finish
        return ScheduledNode.WAIT_NODE;
    }

    enum ParentBuildStatus {
        /**
         * All parent nodes were built successfully
         */
        OK,
        /**
         * One of the parent nodes were not built
         */
        NOT_BUILT,
        /**
         * One of the parent nodes has build error
         */
        ERROR,
    }

    private ParentBuildStatus checkParentBuildStatus(BuildJob childNode) {
        Set<BuildJob> parentNodesOfChild = this.buildLayers.getParents(childNode);

        // check for parent errors depending on the build node settings
        // if onParentFailure.NO_BUILD is selected, we shouldn't build this node
        // in case there is any error upstream.
        //
        // if onParentFailure.BUILD is selected, this block will be skipped and
        // the node will be scheduled for building even if there were errors upstream
        BuildSettings settings = childNode.getBuildSettings();
        if (settings.getOnParentFailure() == BuildSettings.ParentFailureMode.ABORT) {
            // check if any of the parents has build errors. In such case we should report
            // back as build errors, so we can't build the child node.
            for (BuildJob parentNode : parentNodesOfChild) {
                if (parentNode.hasErrors() || parentNode.wasAborted()) {
                    // TODO: compare with node settings in case of an error.
                    //  If that particular parent has error, we might want to abort the build.
                    //  We may provide an option:
                    //  - build if immediate parent fails
                    //  - build if any parent fails
                    return ParentBuildStatus.ERROR;
                }
            }
        }

        // none of the parent nodes has build errors, check if there are any parents
        // on which we are waiting for.
        for (BuildJob parentNode : parentNodesOfChild) {
            final boolean parentStillHasToBeBuilt = !parentNode.isBuildFinished();
            if (parentStillHasToBeBuilt) {
                return ParentBuildStatus.NOT_BUILT;
            }
        }

        // All the parents were successfully built and do not have any build errors
        return ParentBuildStatus.OK;
    }
}
