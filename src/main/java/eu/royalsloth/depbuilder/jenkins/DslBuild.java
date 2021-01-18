package eu.royalsloth.depbuilder.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import eu.royalsloth.depbuilder.dsl.*;
import eu.royalsloth.depbuilder.dsl.scheduling.*;
import eu.royalsloth.depbuilder.jenkins.actions.BuildAddedCause;
import eu.royalsloth.depbuilder.jenkins.actions.PersistBuildInfoAction;
import eu.royalsloth.depbuilder.jenkins.api.*;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Queue;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static hudson.model.Result.FAILURE;

/**
 * {@link hudson.tasks.BuildTrigger}
 */
public class DslBuild extends Build<DslProject, DslBuild> {

    /**
     * Should only exist if the build is in progress. This is currently used to propagate the scheduler
     * instance back to the code that is handling build progress requests
     */
    private transient volatile BuildExecution buildExecution;

    // BuildPlugin constructors are used via reflection deep within the Jenkins
    public DslBuild(DslProject project) throws IOException {
        super(project);
    }

    public DslBuild(DslProject project, Calendar timestamp) {
        super(project, timestamp);
    }

    // Currently it seems that only this constructor is being called
    public DslBuild(DslProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    // we could do this transformation on frontend
    public static String durationToString(long milliseconds) {
        if (milliseconds < 0) {
            return "";
        }

        // this avoids the problems of very short build time frames. For the end
        // user it looks weird to see 0s written in the gui for very short builds
        if (milliseconds < 1000) {
            double inSeconds = milliseconds / 1000.0;
            return String.format("%.1fs", inSeconds);
        }

        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        seconds = seconds - hours * 3600;
        long minutes = seconds / 60;
        seconds = seconds - minutes * 60;

        if (hours > 0) {
            return String.format("%dh:%02dm", hours, minutes);
        }

        // hours don't exist
        if (minutes > 0) {
            return String.format("%dm", minutes);
        }

        // hours and minutes don't exist return only seconds
        return String.format("%ds", seconds);
    }

    public static String convertBuildResult(Result buildResult) {
        if (buildResult == null) {
            return BuildStatus.IN_PROGRESS.toString();
        } else if (buildResult == FAILURE) {
            return BuildStatus.ERROR.toString();
        } else if (buildResult == Result.UNSTABLE) {
            // the build was successful but some tests has failed
            // for now we just report this as an error
            return BuildStatus.ERROR.toString();
        } else if (buildResult == Result.ABORTED) {
            return BuildStatus.ABORT.toString();
        } else if (buildResult == Result.SUCCESS) {
            return BuildStatus.SUCCESS.toString();
        } else if (buildResult == Result.NOT_BUILT) {
            return BuildStatus.NONE.toString();
        } else {
            return BuildStatus.NONE.toString();
        }
    }

    public boolean isFinished() {
        boolean isFinished = !isBuilding();
        return isFinished;
    }

    ///////////////////
    // API for build overview pages
    ///////////////////
    @Exported
    @CheckForNull
    public String getPipeline() {
        PersistBuildInfoAction action = getAction(PersistBuildInfoAction.class);
        final boolean buildWasPersisted = action != null;
        if (buildWasPersisted) {
            return action.getPipeline();
        }

        // the action was not found which means either:
        // - the build was not yet put into queue
        // - the build has failed before the pipeline was even saved
        //
        // We should check the build result before returning the pipeline string
        Result result = getResult();
        final boolean buildInProgress = result == null;
        if (buildInProgress) {
            DslProject.ScriptInputType typeOfDownload = getProject().getScriptInputType();
            if (typeOfDownload == DslProject.ScriptInputType.SCM) {
                // the build is in progress, but we don't know the current pipeline
                return null;
            } else {
                // the build is using pipeline and is still running, that's why we can
                // return the current configuration of the pipeline (as seen in /configure url)
                String pipeline = getProject().getPipeline();
                return pipeline;
            }
        }

        // the action was not found, and the build is not in progress
        // (the build has either aborted or was terminated)
        return null;
    }

    /**
     * Endpoint that is called by the frontend when we want to get the current build status report.
     */
    @Exported
    public JSONObject getStatus() {
        ProjectBuildStatus info = getBuildStatus();
        return JSONObject.fromObject(info);
    }

    /**
     * Endpoint to get the structure of the build graph. All graph nodes are returned (even the ones that are
     * waiting in the build queue). This endpoint is accessible under specific job build:
     * /jenkins/job/<jobName>/<buildNumber/api/json
     */
    @Exported
    public JSONObject getDslBuild() {
        ProjectGraph graph = getBuildGraph();
        return JSONObject.fromObject(graph);
    }

    /**
     * Get build status of the job builds (which job was success, which failed which was aborted). If the
     * build is still in progress, only partial status will be reported (see finished flag).
     */
    private ProjectBuildStatus getBuildStatus() {
        PersistBuildInfoAction action = getAction(PersistBuildInfoAction.class);
        final boolean actionDoesNotExist = action == null;
        if (actionDoesNotExist) {
            // build either hasn't started or the action was not persisted in the build.xml.
            // or we are fetching data for some old build that didn't use/persist this action.
            // This event has happened during build with SCM input type where we couldn't
            // find the user's file and an exception was thrown before the action was stored.
            ProjectBuildStatus info = new ProjectBuildStatus();
            info.buildStatus = convertBuildResult(getResult());
            info.finished = isFinished();
            info.duration = JenkinsUtil.getBuildDurationString(this);
            return info;
        }

        // this set is only here to prevent a multithreading issue that is present
        // due to this method being accessed from the different thread than our
        // build scheduling thread. It's possible that the same build job might
        // be present both in the action queue as in the scheduledBuilds queue.
        // If that is the case, then the build from the action queue should be
        // taken into account and the build in the scheduledBuilds should
        // be ignored (because the scheduledBuilds queue contains slightly older data)
        Set<String> buildJobNames = new HashSet<>();

        // builds that are finished,error or aborted are in this struct
        // builds that are in progress or haven't started are not in this struct
        List<Run<?, ?>> pastBuilds = action.getBuildInfo();
        List<JobBuildStatus> outputInfo = new ArrayList<>(pastBuilds.size());
        for (Run<?, ?> build : pastBuilds) {
            JobBuildStatus info = JobBuildStatus.from(build);
            outputInfo.add(info);
            buildJobNames.add(info.projectName);
        }

        // builds that were scheduled (put into queue) or are still building (IN_PROGRESS) are
        // added to the list here.
        final boolean theBuildHasFinished = getResult() != null;
        if (this.buildExecution != null) {
            Optional<Scheduler> scheduler = this.buildExecution.getScheduler();
            if (scheduler.isPresent()) {
                List<BuildFuture> scheduledBuilds = scheduler.get().getQueuedBuilds();
                for (BuildFuture buildFuture : scheduledBuilds) {
                    Optional<Run<?, ?>> build = buildFuture.getScheduledBuild();
                    if (!build.isPresent()) {
                        // Immediately after the project is scheduled, the build might not exist.
                        // I guess the project only receives Run object after it is executed by
                        // the Executor. If the object is still in the queue, it won't have a
                        // Run object. Still we would like to show to the end user that something
                        // is going on with the build.
                        String projectName = buildFuture.buildJob.getId();
                        if (!buildJobNames.contains(projectName)) {
                            JobBuildStatus info = new JobBuildStatus();
                            if (theBuildHasFinished) {
                                // Edge case: if the user aborted the build while the build
                                // is in progress, the scheduler might still have the objects
                                // in it's build queue that has not changed it's state to aborted
                                // (multithreading problem). In such case we would show
                                // the user IN_PROGRESS state indefinitely, while the correct
                                // state is actually aborted.
                                //
                                // In some rare case the build might actually finish just before
                                // this branch has executed and we would show the wrong state.
                                info.buildStatus = BuildStatus.ABORT.toString();
                            } else {
                                // the build has not yet finished and the run object does
                                // not exist, this means the build is still in progress
                                info.buildStatus = BuildStatus.IN_PROGRESS.toString();
                            }
                            info.projectName = projectName;
                            outputInfo.add(info);
                        } else {
                            // if the build jobs contains the name, that means that the build
                            // has finished, but it was not yet ejected from the scheduledBuild
                            // queue. In that case we already added the build info from the
                            // build jobs so we don't have to do anything at this point.
                        }
                        continue;
                    }

                    // Run object exists for the scheduled build
                    Run<?, ?> run = build.get();
                    String projectName = buildFuture.buildJob.getId();
                    if (!buildJobNames.contains(projectName)) {
                        JobBuildStatus info = JobBuildStatus.from(run);
                        outputInfo.add(info);
                    } else {
                        // build job was already added to the final output via action queue,
                        // but the same build was not yet removed from the queue of scheduledBuilds.
                        // This is possible due to multithreading and nothing to worry about.
                    }
                }
            }
        }

        // if the user cancelled the build from the queue (before the build was executed)
        // we only know the names of the canceled builds. Mark such builds as aborted.
        for (String projectName : action.getCancelledBuilds()) {
            if (buildJobNames.contains(projectName)) {
                // this should never happen. If it does it means that canceled project
                // was also the build that finished successfully (was loaded from disk)
                LOGGER.log(Level.WARNING, String.format("%s(%d): build job '%s' that was canceled is already present in the health check output. This should never happen", getDisplayName(), getNumber(), projectName));
                continue;
            }

            JobBuildStatus info = new JobBuildStatus();
            info.projectName = projectName;
            info.buildStatus = BuildStatus.ABORT.toString();
            outputInfo.add(info);
        }

        ProjectBuildStatus info = new ProjectBuildStatus();
        info.jobBuildStatus = outputInfo;
        info.finished = isFinished();
        info.buildStatus = convertBuildResult(getResult());
        info.duration = JenkinsUtil.getBuildDurationString(this);
        return info;
    }

    /**
     * Get the structure of the build pipeline (how are jobs connected).
     */
    public ProjectGraph getBuildGraph() {
        PersistBuildInfoAction action = getAction(PersistBuildInfoAction.class);
        final boolean actionDoesNotExist = action == null;
        if (actionDoesNotExist) {
            // build either hasn't started or the action was not persisted in the build.xml.
            // or we are fetching data for some old build that didn't use/persist this action.
            // This event has happened when we couldn't find the file specified in the SCM
            // section. The build has failed and the action was not persisted.
            ProjectGraph graph = new ProjectGraph();
            graph.projectName = getProject().getName();
            graph.buildNumber = getNumber();
            graph.graphNodes = new ArrayList<>();
            graph.duration = JenkinsUtil.getBuildDurationString(this);
            graph.status = convertBuildResult(this.getResult());
            graph.finished = isFinished();
            return graph;
        }

        // action was found for this build, create an api for the frontend
        // if the build is still running, this method will return different results
        // as new builds are finished and get appended to the finished build array
        try {
            // define the structure of the build nodes, this structure is not going to change
            String pipeline = action.getPipeline();

            // If the action was not persisted correctly in the build.xml file
            // the received action fields might be null.
            if (pipeline == null) {
                LOGGER.log(Level.WARNING, String.format("Build %s#%d, pipeline input was not stored properly in the build.xml file.", getDisplayName(), getNumber()));
                pipeline = "";
            }

            List<ParsedBuildJob> parsedNodes = DslParser.parseBuildNoVerify(pipeline).parsedJobs;
            List<ConfigGraphNode> graphNodes = DslProject.createSerializedJobs(parsedNodes);

            ProjectBuildStatus projectBuildStatus = getBuildStatus();
            Map<String, JobBuildStatus> jobBuildInfo = new HashMap<>(projectBuildStatus.jobBuildStatus.size());
            for (JobBuildStatus info : projectBuildStatus.jobBuildStatus) {
                jobBuildInfo.put(info.projectName, info);
            }

            List<FinishedBuildJob> jobs = new ArrayList<>(graphNodes.size());
            for (ConfigGraphNode graphNode : graphNodes) {
                FinishedBuildJob job = new FinishedBuildJob();
                job.projectName = graphNode.projectName;
                job.projectUri = graphNode.projectUri;
                job.children = graphNode.children;

                JobBuildStatus buildStatus = jobBuildInfo.get(job.projectName);
                final boolean buildDoesNotExist = buildStatus == null;
                if (buildDoesNotExist) {
                    // if the job pipeline was aborted (or the pipeline is still in progress)
                    // some builds will be missing (it's a normal behavior)
                    job.buildNumber = -1;
                    job.buildUri = "";
                    job.buildStatus = BuildStatus.NONE.toString();
                    job.buildDuration = durationToString(0);
                } else {
                    job.buildNumber = buildStatus.buildNumber;
                    job.buildUri = job.projectUri + job.buildNumber;
                    job.buildStatus = buildStatus.buildStatus;
                    job.buildDuration = buildStatus.duration;
                }
                jobs.add(job);
            }

            ProjectGraph graph = new ProjectGraph();
            graph.projectName = getProject().getName();
            graph.buildNumber = getNumber();
            graph.graphNodes = jobs;
            graph.duration = projectBuildStatus.duration;
            graph.status = projectBuildStatus.buildStatus;
            graph.finished = isFinished();
            return graph;
        } catch (ParseException e) {
            // there was a syntax error in the pipeline - no build happened
            ProjectGraph graph = new ProjectGraph();
            graph.error = "Build pipeline has a syntax error: " + e.getMessage();
            ProjectBuildStatus projectBuildStatus = getBuildStatus();
            graph.projectName = getProject().getName();
            graph.buildNumber = getNumber();
            graph.duration = projectBuildStatus.duration;
            graph.status = projectBuildStatus.buildStatus;
            graph.finished = isFinished();
            return graph;
        }
    }

    /**
     * Parse the DslProject pipeline and verify the build settings.
     *
     * @param projectName - name of the project for which the pipeline is verified
     * @param pipeline    - pipeline DSL that will be validated
     * @return parsed pipeline into build nodes
     * @throws ParseException in case of any parse error or setting that does not exist
     */
    public static ParsedBuild verifyPipeline(String projectName,
            String pipeline) throws ParseException, BuildCycleException {
        Set<String> agentNames = JenkinsUtil.getAllAgents()
                                            .stream()
                                            .map(agent -> JenkinsUtil.getComputerName(agent))
                                            .collect(Collectors.toSet());

        // The user might not know how the plugin works, but they shouldn't be starting the build with
        // the DslProject as that may cause cyclic build problems (DslBuild starts DslBuild
        // which triggers another DslBuild until stack overflow happens). To avoid this
        // problem we filter out the job names of this project.
        Set<String> jobsOnJenkins = JenkinsUtil.getBuildJobs();
        Set<String> allowedJobs = jobsOnJenkins.stream().filter(job -> !projectName.equals(job))
                                               .collect(Collectors.toSet());

        SettingsVerifier settingsVerifier = new SettingsVerifier(agentNames, allowedJobs);
        ParsedBuild build = DslParser.parseBuild(pipeline, settingsVerifier);
        List<ParsedBuildJob> buildJobs = build.parsedJobs;
        BuildLayers layers = BuildLayers.topologicalSort(buildJobs);
        if (layers.hasCycle()) {
            throw new BuildCycleException(
                    "Provided graph has a cycle: " + String.join(" 🠖 ", layers.getBuildCycle()));
        }

        return build;
    }

    public static Scheduler schedulerFactory(PrintStream logger, BuildLayers layers, SchedulerSettings settings, Instant startTime) {
        logger.println("");
        if (PluginVersion.isCommunity()) {
            logger.println("Building with DepBuilder Community plugin, v: " + PluginVersion.version);
            return new Scheduler(layers, settings, startTime);
        } else {
            // @PRO: scheduler with more features
            //
            // this should never happen, since the licensing features are not present
            // in the community plugin, but we are still handling it just in case.
            logger.println("You are using the Community DepBuilder plugin with a valid Pro version.\n"
                                   + "If you would like to use Pro features, please download the Pro"
                                   + "plugin version.");
            return new Scheduler(layers, settings, startTime);
        }
    }

    @Override
    public void run() {
        // by default the run method calls new BuildExecution() within the extended BuildClass
        this.buildExecution = new BuildExecution();
        execute(this.buildExecution);
    }

    /**
     * Implementation copied from freestyle project. Check {@link hudson.model.Run.RunExecution} which is the
     * non deprecated run execution.
     */
    protected class BuildExecution extends AbstractRunner {

        private final Pattern PROHIBITED_DOUBLE_DOT = Pattern.compile(".*[\\\\/]\\.\\.[\\\\/].*");
        private volatile Scheduler scheduler;

        /**
         * Some plugins might depend on this instance castable to Runner, so we need to use deprecated class
         * here.
         */
        public BuildExecution() {
        }

        public Optional<Scheduler> getScheduler() {
            return Optional.ofNullable(this.scheduler);
        }

        @Override
        protected Result doRun(@NonNull BuildListener listener) throws Exception {
            final Node masterNode = JenkinsUtil.getJenkins();
            final Node projectBuildAgent = getBuild().getBuiltOn();
            if (projectBuildAgent == null) {
                // this should never happen, but we are still checking it just in case
                throw new IllegalStateException(String.format("Build agent on which the project %s is running is null. This should never happen!",
                                                              getProject().getName()));
            }

            final boolean projectIsBuiltOnMaster = projectBuildAgent.getSelfLabel().equals(
                    masterNode.getSelfLabel());
            if (projectIsBuiltOnMaster) {
                // this avoids the problem of infinite build. If the user has only one master
                // node executor and the project is running on such executor the child projects
                // will never be built and we have to inform the user of this problem
                // (they should increase number of executors on master node).
                // In theory it's possible to have 1 master node running the build and a few
                // other build agents on which the projects are assigned to, but that would demand
                // more complex logic for no good reason. When one thread/executor is mostly sleeping,
                // adding another thread doesn't cost us much more.
                int masterExecutorCount = JenkinsUtil.getJenkins().getNumExecutors();
                if (masterExecutorCount <= 1) {
                    listener.getLogger().println(
                            "ERROR: you are running the build with only 1 executor on master node. Please increase "
                                    + "the number of executors on master node to at least 2 or more. Alternatively "
                                    + "you could add additional build agents");
                    return FAILURE;
                }
            }

            final DslProject project = getProject();
            final Instant buildStart = Instant.now();

            // @FUTURE: we don't want to build if there are any problems with the
            // build script, however the question is how to handle
            // the agent verification. By default if the agent is offline, the master node
            // still knows about it and you can refer to it (so our verification process is fine).
            // Q1: how do we handle the case where all specified build node agents are offline
            // Q2: if the user is creating/deleting agents in a dynamic fashion the
            // build script might be totally fine, but due to dynamic build creation
            // this might cause the build to fail (a specific agent is missing)
            // This is the type of a comment that stays in the codebase forever :)
            final String pipeline = getPipeline(listener, project);
            // adding persistence action so we know which projects were built
            // with this specific DslBuild build number. We are adding pipeline info
            // before we verified the build, just to persist the pipeline. This way the
            // user can see the whole pipeline which they used and not just the error message
            // in the build logs.
            final PersistBuildInfoAction persistBuildInfo = new PersistBuildInfoAction(pipeline);
            addAction(persistBuildInfo);

            // verify if the build job is valid, checking with strict parser
            // and verifying that all jobs have at least one online agent. Without
            // this verification test, that specific build would simply wait until
            // the online agent appears. If it never appears then the build would
            // be terminated by our scheduler.
            ParsedBuild verifiedBuild = verifyPipeline(project.getDisplayName(), pipeline);
            AssignToNode.allJobsShouldHaveOnlineAgent(verifiedBuild);

            BuildLayers layers = BuildLayers.topologicalSort(verifiedBuild.parsedJobs);
            this.scheduler = schedulerFactory(listener.getLogger(), layers, verifiedBuild.schedulerSettings, buildStart);
            final Duration maxProjectBuildDuration = verifiedBuild.schedulerSettings.maxDuration;

            // main scheduler loop that keeps checking if the
            while (scheduler.hasNext()) {
                ScheduledNode node = scheduler.getNext();
                LOGGER.log(Level.FINE, String.format("Building projects, scheduler state: %s", node.getStatus()));
                if (node.getStatus() == ScheduledNode.ScheduledNodeStatus.FINISHED) {
                    // all dependencies were built
                    break;
                }

                if (node.getStatus() == ScheduledNode.ScheduledNodeStatus.ABORT) {
                    // It's a bit confusing that the scheduler is returning abort
                    // but in this case it means terminate the build due to errors
                    // in the build.
                    //
                    // The build could either stop due to build errors or
                    // timing out (aborted build). We have to distinguish between the
                    // two in order to display the right light/color in the UI.
                    listener.getLogger().println("");
                    if (scheduler.wasAborted()) {
                        return Result.ABORTED;
                    }

                    if (scheduler.hasBuildErrors()) {
                        return Result.FAILURE;
                    }

                    // we don't know exactly what went wrong, (maybe it's a programming bug)
                    // so we are returning aborted state
                    return Result.ABORTED;
                }

                if (node.getStatus() == ScheduledNode.ScheduledNodeStatus.WAIT) {
                    final boolean buildDurationIsSet = !maxProjectBuildDuration.isNegative();
                    if (buildDurationIsSet) {
                        Duration buildDuration = Duration.between(buildStart, Instant.now());
                        final boolean buildDurationExceeded =
                                maxProjectBuildDuration.compareTo(buildDuration) < 0;
                        if (buildDurationExceeded) {
                            listener.getLogger().println("");
                            listener.getLogger()
                                    .println(String.format("Max build duration %s exceeded, terminating build", maxProjectBuildDuration));
                            return Result.ABORTED;
                        }
                    }

                    // when we have no other projects to schedule due to waiting for previous
                    // project to build, we try to remove the finished builds in order to be able
                    // to schedule new nodes
                    boolean buildsEjected = scheduler.ejectFinishedBuilds(listener.getLogger(), persistBuildInfo);
                    if (buildsEjected) {
                        // at least one build was removed, we can try to schedule a new
                        // build right away (without waiting for 1 second)
                        continue;
                    }

                    // no builds were ejected, wait for a while and try again
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        // The user has canceled the build via the UI while this thread
                        // was sleeping. If any build has finished in the meantime, eject it.
                        // All other builds should be terminated and their status persisted.
                        scheduler.ejectFinishedBuilds(listener.getLogger(), persistBuildInfo);
                        for (BuildFuture future : scheduler.getQueuedBuilds()) {
                            Optional<Run<?, ?>> run = future.getScheduledBuild();
                            future.future.cancel(true);
                            if (run.isPresent()) {
                                persistBuildInfo.addBuild(run.get());
                            } else {
                                persistBuildInfo.addCancelledBuild(future.buildJob.getId());
                            }
                        }

                        listener.getLogger().println("");
                        return Result.ABORTED;
                    }

                    // after sleep, build was not interrupted, continue with the build
                    continue;
                }

                // if this point is reached, we have a new build to schedule
                BuildJob buildJob = node.getBuildJob();
                final String buildId = buildJob.getId();

                // assign node to a specific build agent as defined per settings block.
                // Build agents might be missing, in such case it's irrelevant on which
                // node the build will be scheduled and we can use the default jenkins behavior
                List<BuildAgent> buildAgents = buildJob.getBuildSettings().getAgents();
                AssignToNode assignToNodeAction = AssignToNode.createAction(buildAgents);

                // if the job no longer exists, this will throw an exception. The only reason why
                // that might happen in the middle of the build is if somebody deleted the job
                // while the build is running. Since we can't predict how to handle such case,
                // it's better to just throw an exception and fail the build right away.
                final Job<?, ?> jenkinsJob = JenkinsUtil.getJob(getProject(), buildId);
                final Cause cause = new Cause.UpstreamCause((Run) getBuild());
                int quietPeriod = ((ParameterizedJobMixIn.ParameterizedJob) jenkinsJob).getQuietPeriod();
                // instead of AbstractProject.resolveForCli(), a ParameterizedJobMixIn is used for scheduling the
                // build, as otherwise we cannot run "pipeline" jobs through our plugin as pipeline job does
                // not extend the AbstractProject class.
                final BuildAddedCause buildAddedCause = new BuildAddedCause();
                final CauseAction causeAction = new CauseAction(cause, buildAddedCause);
                final Queue.Item item = ParameterizedJobMixIn.scheduleBuild2(jenkinsJob, quietPeriod,
                                                                             causeAction, assignToNodeAction);

                final boolean queueRefusedTheItem = item == null;
                if (queueRefusedTheItem) {
                    // job was refused for scheduling for whatever reason, see scheduleBuild2 documentation
                    scheduler.errorBuild(buildJob);
                    LOGGER.log(Level.INFO, String.format("Project %s: REFUSED", buildId));
                    continue;
                }

                // the jenkins api does not allow to register callback when the job is finished building,
                // so we have to do this manually. The necessary parts are put into the queue, which
                // is being iterated and finished jobs ejected every time the scheduler determines it can't
                // scheduler new jobs.
                BuildFuture buildInFuture = new BuildFuture(buildJob, item.getFuture(), buildAddedCause);
                scheduler.addQueuedBuild(buildInFuture);
            }
            // add an empty line at the end of the report
            listener.getLogger().println("");

            ////////////////////////////
            // This is old code for building the project, it's probably irrelevant for our
            // custom project, because we don't support pre post build options...
            ////////////////////////////
            if (!preBuild(listener, project.getBuilders())) {
                return FAILURE;
            }
            if (!preBuild(listener, project.getPublishersList())) {
                return FAILURE;
            }

            Result r = null;
            try {
                List<BuildWrapper> wrappers = new ArrayList<>(project.getBuildWrappers().values());

                ParametersAction parameters = getAction(ParametersAction.class);
                if (parameters != null) {
                    parameters.createBuildWrappers(DslBuild.this, wrappers);
                }

                for (BuildWrapper w : wrappers) {
                    Environment e = w.setUp((AbstractBuild<?, ?>) DslBuild.this, launcher, listener);
                    if (e == null) {
                        return (r = FAILURE);
                    }
                    buildEnvironments.add(e);
                }


                // (REVIEW): at this point the build of this project actually happened
                if (!build(listener, project.getBuilders())) {
                    r = FAILURE;
                }
            } catch (InterruptedException e) {
                r = Executor.currentExecutor().abortResult();
                // not calling Executor.recordCauseOfInterruption here. We do that where this exception is consumed.
                throw e;
            } finally {
                if (r != null) {
                    setResult(r);
                }
                // tear down in reverse order
                boolean failed = false;
                for (int i = buildEnvironments.size() - 1; i >= 0; i--) {
                    if (!buildEnvironments.get(i).tearDown(DslBuild.this, listener)) {
                        failed = true;
                    }
                }
                // WARNING The return in the finally clause will trump any return before
                if (failed) {
                    return FAILURE;
                }
            }

            // our custom build check if any of the scheduled builds has failed
            if (scheduler.wasAborted()) {
                return Result.ABORTED;
            }

            boolean buildFailed = this.scheduler.hasBuildErrors();
            if (buildFailed) {
                return FAILURE;
            }

            return r;
        }

        /**
         * Get the pipeline string based on the project setting (SCM or direct string)
         *
         * @return pipeline string as specified in the configuration of the project (/config page)
         */
        private String getPipeline(@NonNull BuildListener listener,
                DslProject project) throws IOException, InterruptedException {
            DslProject.ScriptInputType inputType = getProject().getScriptInputType();
            if (inputType == DslProject.ScriptInputType.SCRIPT) {
                return project.getPipeline();
            } else if (inputType == DslProject.ScriptInputType.SCM) {
                SCM scm = project.getScm();
                FilePath workspace = getWorkspace();
                if (workspace == null) {
                    throw new IllegalStateException("Workspace assigned for this build is null, this should never happen");
                }

                String buildFileLocation = project.getScmFileLocation();
                if (buildFileLocation == null || "".equals(buildFileLocation)) {
                    throw new IllegalStateException("Provided input file with a build script is missing");
                }

                scm.checkout(getBuild(), launcher, workspace, listener, null, null);

                // We have to ensure that we are not reading the data outside of the
                // current build workspace. We could use FilePath.isDescendant, but
                // unfortunately it's prohibited to use outside of its package
                // (otherwise we cannot compile our software into plugin hpi file).
                //
                // A very simple solution is to simply prevent the ".." pattern
                if (PROHIBITED_DOUBLE_DOT.matcher(buildFileLocation).matches()) {
                    throw new IllegalStateException(String.format("Provided build file path '%s' is using forbidden characters (..)!", buildFileLocation));
                }

                // everything is fine, read the pipeline file
                FilePath pipelineFile = workspace.child(buildFileLocation);
                if (!pipelineFile.exists()) {
                    throw new IllegalStateException(String.format("Provided pipeline file %s does not exist in the SCM repository", pipelineFile
                            .getRemote()));
                }
                if (pipelineFile.isDirectory()) {
                    throw new IllegalStateException(String.format("Provided pipeline file %s is a directory", pipelineFile
                            .getRemote()));
                }
                return pipelineFile.readToString();
            } else {
                // unknown input type, this should never happen
                throw new IllegalStateException(String.format("Provided project input type is %s which is not handled. This is probably a programming bug.", inputType));
            }
        }

        @Override
        public void post2(@NonNull BuildListener listener) throws IOException, InterruptedException {
            if (!performAllBuildSteps(listener, project.getPublishersList(), true)) {
                setResult(FAILURE);
            }
            if (!performAllBuildSteps(listener, project.getProperties(), true)) {
                setResult(FAILURE);
            }
        }

        @Override
        public void cleanUp(@NonNull BuildListener listener) throws Exception {
            // at this point it's too late to mark the build as a failure, so ignore return value.
            try {
                performAllBuildSteps(listener, project.getPublishersList(), false);
                performAllBuildSteps(listener, project.getProperties(), false);
            } catch (Exception x) {
                String error = "Post build steps failed";
                Functions.printStackTrace(x, listener.error(error));
            }
            super.cleanUp(listener);
        }

        private boolean build(@NonNull BuildListener listener,
                @NonNull Collection<Builder> steps) throws IOException, InterruptedException {
            for (BuildStep bs : steps) {
                if (!perform(bs, listener)) {
                    LOGGER.log(Level.FINE, "{0} : {1} failed", new Object[]{DslBuild.this, bs});
                    return false;
                }

                Executor executor = getExecutor();
                if (executor != null && executor.isInterrupted()) {
                    // someone asked build interruption, let stop the build before trying to run another build step
                    throw new InterruptedException();
                }
            }
            return true;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DslBuild.class.getName());
}
