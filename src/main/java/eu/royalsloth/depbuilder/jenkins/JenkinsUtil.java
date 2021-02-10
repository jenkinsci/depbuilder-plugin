package eu.royalsloth.depbuilder.jenkins;

import hudson.Plugin;
import hudson.console.ModelHyperlinkNote;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.util.*;
import java.util.stream.Collectors;

public class JenkinsUtil {
    private JenkinsUtil() {
    }

    public static Jenkins getJenkins() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins node should not be null");
        }
        return jenkins;
    }

    /**
     * Get jenkins build agents without master node
     *
     * @return jenkins build agents without master node
     */
    public static List<Node> getBuildAgents() {
        Jenkins jenkins = getJenkins();
        return jenkins.getNodes();
    }

    /**
     * Get all build agents that are part of the same CI build swarm.
     *
     * @return all build nodes including master (master is the last node in the list)
     */
    public static List<Node> getAllAgents() {
        Jenkins jenkins = getJenkins();
        List<Node> nodes = getBuildAgents();
        // jenkins represents a master node, if there are no other
        // agents we can only build on master node
        nodes.add(jenkins);
        return nodes;
    }

    public static List<Computer> getOnlineComputers() {
        List<Computer> computers = getAllAgents().stream().map(agent -> agent.toComputer())
                                                 .filter(computer -> computer != null)
                                                 .filter(computer -> computer.isOnline())
                                                 .collect(Collectors.toList());
        return computers;
    }

    public static String getComputerName(Node agent) {
        String name = agent.getSelfLabel().toString();
        if ("".equals(name)) {
            // some jenkins name methods are returning "" name when called on master node.
            // Method getSelfLabel seems to correctly identify the master node as 'master',
            // but I am still leaving this check in, just in case this behaviour changes
            // in the future.
            return "master";
        }
        return name;
    }

    public static String getComputerName(Computer computer) {
        String name = computer.getName();
        if ("".equals(name)) {
            // see the other getComputerName method for an explanation
            return "master";
        }
        return name;
    }

    public static Set<String> getBuildJobs() {
        Jenkins jenkins = getJenkins();
        return new HashSet<>(jenkins.getJobNames());
    }

    /**
     * Get jenkins jobs (projects that you can build) via specified job names. Job name is the unique name of
     * the project, which you specify when you create a new jenkins project (freestyle, pipeline, etc...)
     *
     * @return list of relevant jobs that matches with the specified names.
     */
    public static List<Job<?, ?>> getJobs(AbstractProject<?, ?> project, Collection<String> jobNames) {
        // copied from some internal jenkins class
        List<Job<?, ?>> jobs = Items.fromNameList(project.getParent(), String.join(",", jobNames),
                                                  (Class<Job<?, ?>>) (Class) Job.class);
        return jobs;
    }

    /**
     * Get a jenkins job class (project that you can build). Job name is unique name of the project which you
     * specify when you create a new jenkins project (freestyle, pipeline)
     *
     * @throws IllegalStateException if job with specified name does not exist in Jenkins
     */
    public static Job<?, ?> getJob(AbstractProject<?, ?> project,
            String jobName) throws IllegalStateException {
        List<Job<?, ?>> allJobs = getJobs(project, Arrays.asList(jobName));
        for (Job<?, ?> job : allJobs) {
            // it should have only one job anyway
            //
            // job.getName: A
            // job.getFullName: A          (if A doesn't have a parent)
            // job.getFullName: myFolder/A (if A has a parent)
            if (jobName.equals(job.getFullName())) {
                return job;
            }
        }
        String msg = String.format("Job '%s' does not exist in jenkins", jobName);
        throw new IllegalStateException(msg);
    }

    /**
     * Get the specific build from the chosen project.
     *
     * @return build or optional if the project or the build number do not exist
     */
    public static Optional<Run<?, ?>> getBuild(String projectName, int buildNumber) {
        Job<?, ?> job = getJenkins().getItemByFullName(projectName, Job.class);
        if (job == null) {
            return Optional.empty();
        }

        Run<?, ?> build = job.getBuildByNumber(buildNumber);
        return Optional.ofNullable(build);
    }

    /**
     * Get human readable build duration
     */
    public static String getBuildDurationString(Run<?, ?> build) {
        if (build.isBuilding()) {
            // when the build is building, the duration getter is not calculating
            // the right duration number and we have to calculate it on our own
            long start = build.getStartTimeInMillis();
            long now = System.currentTimeMillis();
            long duration = now - start;
            return DslBuild.durationToString(duration);
        }
        // build is already done, we can use the jenkins api for that
        return DslBuild.durationToString(build.getDuration());
    }

    public static String createConsoleLink(Run<?, ?> build) {
        String consoleLink = ModelHyperlinkNote.encodeTo(
                "/" + build.getUrl() + "console", build.toString());
        return consoleLink;
    }

    public static String getPluginVersion(String pluginName) {
        Plugin plugin = JenkinsUtil.getJenkins().getPlugin(pluginName);
        if (plugin == null) {
            return "";
        }

        // version number looks like (revision-changelist (+ some irrelevant version metadata)
        // by splitting on space we get rid of the irrelevant version metadata
        String versionNumber = plugin.getWrapper().getVersionNumber().toString();
        String[] arr = versionNumber.split(" ");
        String actualVersion = arr[0];
        return actualVersion;
    }
}

