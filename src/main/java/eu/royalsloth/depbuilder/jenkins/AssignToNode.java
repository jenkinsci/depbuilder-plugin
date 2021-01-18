package eu.royalsloth.depbuilder.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import eu.royalsloth.depbuilder.dsl.ParseException;
import eu.royalsloth.depbuilder.dsl.ParsedBuild;
import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildAgent;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildAgentType;
import hudson.Platform;
import hudson.Util;
import hudson.model.*;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class that is assigning builds to nodes
 */
public class AssignToNode extends InvisibleAction implements LabelAssignmentAction, Queue.QueueAction {

    private transient final Node node;

    // we could use node, but I guess it's better to use Label as label represent
    // a group of nodes...
    public AssignToNode(Node node) {
        this.node = node;
    }

    @Override
    public Label getAssignedLabel(@NonNull SubTask task) {
        // TODO: this is interesting, we could try different things here
        // SubTask t = task;
        // t.getSameNodeConstraint()
        return node.getSelfLabel();
    }

    @Override
    public boolean shouldSchedule(List<Action> actions) {
        // TODO: I am not sure if this action is necessary. This part was only
        // added because it was present in another plugin, but it might be useless for our case
        System.out.println("====== Should schedule trigger: " + actions);
        List<AssignToNode> assignments = Util.filter(actions, AssignToNode.class);
        for (AssignToNode assign : assignments) {
            if (assign.node.getSelfLabel().equals(this.node.getSelfLabel())) {
                return true;
            }
        }

        return false;
    }

    public static AssignToNode createAction(List<BuildAgent> allPossibleBuildAgents) {
        // TODO: move jenkins calls outside of this action?
        final Jenkins jenkins = JenkinsUtil.getJenkins();
        final List<Node> buildNodes = JenkinsUtil.getAllAgents();

        if (allPossibleBuildAgents.isEmpty()) {
            return new AssignAnyNode();
        }

        Node node = findJenkinsAgent(allPossibleBuildAgents, buildNodes);
        return new AssignToNode(node);
    }

    /**
     * Checks if all jobs from the build have online build agent or throws an exception if one node has all
     * build agents offline.
     * <p>
     * In a large company the build agents might be coming and going depending on the time of the day where
     * more processing power is needed. In a long build it's possible that an agent will be present by the
     * time our job is available for building and calling this method at the start of the build will throw an
     * exception.
     * <p>
     * For now, I still think it's better to be fail fast over hoping that the agents will appear one day and
     * be stuck with a build that does not progresses.
     */
    public static boolean allJobsShouldHaveOnlineAgent(ParsedBuild build) throws ParseException {
        // Q: sometimes the computers are put offline, because they were idle and could be launched
        // when their time comes. Unfortunately this behavior is not documented anywhere
        // and I don't know how it really works. For now we just ignore the nodes that
        // are not currently online, but there may be a better solution.
        Set<String> onlineComputerNames = JenkinsUtil.getOnlineComputers().stream()
                                                     .map(JenkinsUtil::getComputerName)
                                                     .collect(Collectors.toSet());

        List<ParsedBuildJob> buildJobsWithNoAgents = new ArrayList<>();
        for (ParsedBuildJob job : build.parsedJobs) {
            // at least one build agent should be present and online
            int numberOfOnlineJobs = 0;
            List<BuildAgent> allowedAgents = job.getBuildSettings().getAgents();
            if (allowedAgents.isEmpty()) {
                // allowed agents are not specified, in such case any agent is
                // allowed (e.g: master) and for this reason we can increase
                // the number of jobs
                numberOfOnlineJobs++;
            }

            for (BuildAgent agent : allowedAgents) {
                if (agent.isAny()) {
                    // if any node will do, this means we can execute on master
                    // node which is always online.
                    numberOfOnlineJobs++;
                    break;
                }

                // build node is not any, we should find at least one node that is online
                // for the given job
                if (onlineComputerNames.contains(agent.agentName)) {
                    numberOfOnlineJobs++;
                }
            }

            final boolean jobHasNoAgent = numberOfOnlineJobs <= 0;
            if (jobHasNoAgent) {
                buildJobsWithNoAgents.add(job);
            }
        }

        final boolean allJobsHaveOnlineAgent = buildJobsWithNoAgents.isEmpty();
        if (allJobsHaveOnlineAgent) {
            return true;
        }

        // at least one build job does not have online agent
        StringBuilder builder = new StringBuilder();
        for (ParsedBuildJob job : buildJobsWithNoAgents) {
            List<BuildAgent> allowedAgents = job.getBuildSettings().getAgents();
            List<String> agentNames = allowedAgents.stream().map(agent -> agent.agentName)
                                                   .collect(Collectors.toList());
            String msg = String.format("Build job %s agents %s are not online. Make sure you have at least one agent online before starting the build",
                                       job.getId(), agentNames);
            builder.append(msg);
            builder.append("\n");
        }

        throw new ParseException(builder.toString());
    }

    /**
     * Find appropriate jenkins agent node, based on the needs of the build job. Right now the job is assigned
     * to agent with the most free executors.
     *
     * @FUTURE: we might want to allow different node assign strategies: - assign to last executor (where the
     * job was being executed before) - assign to node with the most free executors (spread the load across
     * cluster)
     */
    public static Node findJenkinsAgent(List<BuildAgent> allPossibleBuildAgents, List<Node> buildNodes) {
        if (allPossibleBuildAgents.isEmpty()) {
            throw new IllegalArgumentException("Provided possible list of build agents is empty");
        }

        BuildAgent desiredAgent = allPossibleBuildAgents.get(0);
        // TODO: right now, we are optimizing for the least used node, but we could also prefer
        // the node on which the project was already built (or node on which the parent of the
        // project was already built). Check for the lastBuild for builtOn node and prefer that
        // node?
        if (desiredAgent.isAny()) {
            // schedule to the computer with the most free executors
            Node freeNode = null;
            int maxFreeExecutors = 0;
            for (Node node : buildNodes) {
                if (!node.isAcceptingTasks()) {
                    continue;
                }

                // assign to node with the most free executors
                Computer buildComputer = node.toComputer();
                final boolean computerHasNoExecutors = buildComputer == null;
                if (computerHasNoExecutors) {
                    continue;
                }

                if (desiredAgent.agentType == BuildAgentType.ANY) {
                    int idleExecutors = buildComputer.countIdle();
                    if (idleExecutors >= maxFreeExecutors) {
                        freeNode = node;
                        maxFreeExecutors = idleExecutors;
                    }
                    continue;
                }

                // we want to target specific platform
                try {
                    Platform platform = buildComputer.getEnvironment().getPlatform();
                    if (platform == null) {
                        // platform for node does not exist, we cannot assign such
                        // build node to non any agent type that specified a specific
                        // platform.
                        continue;
                    }

                    // computer platform is windows and our agent type is windows,
                    // assign this node
                    final boolean platformIsWindows =
                            platform == Platform.WINDOWS && (desiredAgent.agentType
                                    == BuildAgentType.WINDOWS);
                    // TODO: we cannot differentiate between mac or linux computers
                    // you have to explicitly specify the build agent.
                    // Might want to open up a feature request on Jenkins
                    // TODO: decide if we support any:mac feature.
                    final boolean platformIsUnix =
                            platform == Platform.UNIX && (desiredAgent.agentType != BuildAgentType.WINDOWS);
                    if (platformIsWindows || platformIsUnix) {
                        int idleExecutors = buildComputer.countIdle();
                        if (idleExecutors >= maxFreeExecutors) {
                            freeNode = node;
                            maxFreeExecutors = idleExecutors;
                        }
                        continue;
                    }
                } catch (Exception e) {
                    String msg = String.format("Failed to get environment for build computer: %s",
                                               buildComputer.getName());
                    System.out.println(msg + "\n" + e);
                    // we can't do anything about this? Why would we even throw this exception??
                    continue;
                }
            }

            // the most appropriate build node was selected
            if (freeNode == null) {
                // this should never happen
                assert !buildNodes.isEmpty() : "Build agents are empty, we have a bug in code";
                final Node masterNode = buildNodes.get(buildNodes.size() - 1);
                return masterNode;
            }

            return freeNode;
        }

        // the user wants a specific build agent
        for (Node node : buildNodes) {
            if (node.getDisplayName().equals(desiredAgent.agentName)) {
                return node;
            }
            throw new IllegalStateException(
                    String.format("Desired agent %s does not exist in CI process", desiredAgent.agentName));
        }

        // this should never happen, but if it does, return master build node
        Node masterNode = JenkinsUtil.getJenkins();
        return masterNode;
    }

    public static class AssignAnyNode extends AssignToNode {
        public AssignAnyNode() {
            this(null);
        }

        public AssignAnyNode(Node node) {
            super(node);
        }

        @Override
        public Label getAssignedLabel(@NonNull SubTask task) {
            return null;
        }
    }
}
