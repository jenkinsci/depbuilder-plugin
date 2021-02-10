package eu.royalsloth.depbuilder.jenkins.actions;

import eu.royalsloth.depbuilder.jenkins.JenkinsUtil;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Action that is used for persisting build information: exact pipeline string that was used and build info
 * about the projects that this plugin has triggered. By adding this action to the build process and filling
 * it with data, this data will be stored in the build.xml file.
 *
 * <p>
 * By default all private non transient fields are persisted (with or without getters) Getters are not
 * persisted, only fields are.
 */
public class PersistBuildInfoAction extends InvisibleAction implements Action {

    private volatile String pipeline = "";

    private final Queue<BuildReference> buildReferences = new ConcurrentLinkedQueue<>();

    /**
     * Builds of projects that were queued, but not executed by the Jenkins Executors. For this reason such
     * build build does not have a build number and we are only storing project name of such build. Builds
     * mentioned here are later displayed as aborted in the UI.
     * <p>
     * Set cannot be concurrent, due to XStream security related reasons so we are synchronizing the access
     * through getters and setters, since this class is used through different threads.
     */
    private final Set<String> cancelledBuilds = new HashSet<>();

    public PersistBuildInfoAction(String pipeline) {
        this.pipeline = pipeline;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public String getPipeline() {
        return this.pipeline;
    }

    public void addBuild(Run<?, ?> build) {
        BuildReference ref = new BuildReference();
        ref.projectName = build.getParent().getFullName();
        ref.buildNumber = build.getNumber();
        buildReferences.add(ref);
    }

    /**
     * Add a build that was cancelled during the build or while it staid in the queue. Such builds are later
     * shown in the UI as canceled build.
     */
    public synchronized void addCancelledBuild(String projectName) {
        this.cancelledBuilds.add(projectName);
    }

    public synchronized Set<String> getCancelledBuilds() {
        return this.cancelledBuilds;
    }

    public List<Run<?, ?>> getBuildInfo() {
        // If the user selected a very small number of projects to persist
        // it is possible that the referenced build no longer exists in the
        // history. In such case the returned build object will be Optional.empty
        // and won't be returned in the list of builds.
        List<Run<?, ?>> builds = buildReferences.stream()
                                                .map(ref -> JenkinsUtil.getBuild(ref.projectName, ref.buildNumber))
                                                .filter(Optional::isPresent).map(Optional::get)
                                                .collect(Collectors.toList());
        return builds;
    }
}
