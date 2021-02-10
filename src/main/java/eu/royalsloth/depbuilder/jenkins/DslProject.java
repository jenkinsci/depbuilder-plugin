package eu.royalsloth.depbuilder.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import eu.royalsloth.depbuilder.dsl.DslParser;
import eu.royalsloth.depbuilder.dsl.ParseException;
import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildCycleException;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildLayers;
import eu.royalsloth.depbuilder.jenkins.api.ConfigGraphNode;
import hudson.Extension;
import hudson.model.*;
import hudson.util.*;
import jenkins.model.Jenkins;
import jenkins.model.item_category.StandaloneProjectsCategory;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class creates a custom project in the jenkins new project view
 */
public class DslProject extends Project<DslProject, DslBuild> implements TopLevelItem {

    public DslProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<DslBuild> getBuildClass() {
        return DslBuild.class;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        DescriptorImpl descriptor = (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
        return descriptor;
    }

    private volatile String pipeline = "";

    @DataBoundSetter
    public void setPipeline(String value) {
        this.pipeline = value;
    }

    public String getPipeline() {
        if (this.pipeline == null) {
            return "";
        }
        return pipeline;
    }

    private volatile ScriptInputType scriptInputType = ScriptInputType.SCRIPT;

    @DataBoundSetter
    public void setScriptInputType(ScriptInputType value) {
        this.scriptInputType = value;
    }

    public ScriptInputType getScriptInputType() {
        return this.scriptInputType;
    }

    private volatile String scmFileLocation = "";

    @DataBoundSetter
    public void setScmFileLocation(String value) {
        this.scmFileLocation = value;
    }

    public String getScmFileLocation() {
        return this.scmFileLocation;
    }

    /**
     * Number of build graphs that should be displayed in the history of build graphs.
     */
    private int numberOfGraphs = 5;

    @Exported
    public int getNumberOfGraphs() {
        return numberOfGraphs;
    }

    @DataBoundSetter
    public void setNumberOfGraphs(int numberOfGraphs) {
        this.numberOfGraphs = numberOfGraphs;
    }

    /**
     * Jenkins has changed the UI from table based rendering to div based rendering. The old jenkins versions
     * (like current LTS 2.263) are using tables, and if we want to dynamically render sections of the dom
     * tree, we can not do that without introducing a table with a specific id.
     * <p>
     * In the newer Jenkins versions, tables were replaced with divs and to render sections dynamically we
     * have to use divs (table solution no longer works)
     */
    private static final VersionNumber versionWhenTablesWereRemoved = new VersionNumber("2.264");

    @Exported
    public boolean getTableBasedRendering() {
        VersionNumber currentVersion = Jenkins.getVersion();
        if (currentVersion == null) {
            return true;
        }

        // earlier versions should be using table based rendering
        if (currentVersion.isOlderThan(versionWhenTablesWereRemoved)) {
            return true;
        }

        // versions after the specified version should be using div rendering
        return false;
    }

    /**
     * Temporary pipeline which is set when the user updates the pipeline through UI the build graph should
     * update as well, but we don't want to store their decision until the user presses the apply/save
     * button.
     * <p>
     * We are using this to prevent the following edge case:
     * <ul>
     * - the user loads the /config page
     * - the user changes pipeline on /config page, but doesn't press save button
     * - the user runs the build, the build should run whatever is currently saved in the db
     * and not what the user has just inserted without saving.
     * </ul>
     */
    @CheckForNull
    private transient volatile String temporaryPipeline = null;

    // SCM related info will be parsed through the received json within the super.doConfigSubmit() code.
    // For some reason I cannot get it to work via our custom doCheck method in the project descriptor.
    @POST
    @Override
    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        JenkinsUtil.getJenkins().checkPermission(Jenkins.ADMINISTER);

        JSONObject json = req.getSubmittedForm();
        int graphs = json.optInt("numberOfGraphs", 5);
        this.numberOfGraphs = graphs;

        final String inputTypeStr = json.optString("scriptInputType", "");
        final Optional<ScriptInputType> inputType = ScriptInputType.parseInputType(json.optString("scriptInputType", ""));
        if (!inputType.isPresent()) {
            throw new IllegalStateException(String.format("Invalid script input type %s, expected: %s. This is probably a bug in the code.", inputTypeStr, Arrays
                    .toString(ScriptInputType.values())));
        }

        if (inputType.get() == ScriptInputType.SCRIPT) {
            // verify pipeline
            final String pipelineStr = json.optString("pipeline", "");
            FormValidation pipelineValidated = getDescriptor().doCheckPipeline(pipelineStr, this);
            if (pipelineValidated.kind == FormValidation.Kind.ERROR) {
                // this is the jenkins way of showing the error notification bar. It's horrible string
                // concatenation, but apparently this is the way to go
                if (FormApply.isApply(req)) {
                    FormApply.applyResponse(
                            "notificationBar.show('Pipeline Error',notificationBar.ERROR)")
                             .generateResponse(req, rsp, null);
                } else {
                    // the user pressed 'save' button which by default redirects them on the
                    // main project page (with links to workspace and permalinks)
                    String continueWithRequest = ".";
                    String referToPreviousPage = req.getHeader("Referer");
                    if (referToPreviousPage == null) {
                        referToPreviousPage = ".";
                    }

                    // success button was clicked despite the project having errors
                    // Hard coding metadata like that is horrible, but that's how jenkins works
                    String msg = String.format("<h2>Pipeline error</h2>"
                                                       + "<p>%s</p></br>"
                                                       + "<p>You have to fix the errors before building the project</p>"
                                                       + "<div>"
                                                       + "<a style='padding-right: 10px' href='%s'><button>Back To Project</button></a>"
                                                       + "</div>",
                                               pipelineValidated.getMessage(), referToPreviousPage);
                    FormValidation.errorWithMarkup(msg).generateResponse(req, rsp, null);
                }

                // if the user decides to not store their changes, their changes
                // won't be persisted in the presence of errors in the config
                // (we are handling error branch here)
                return;
            }

            // if this point is reached, everything related to the user provided pipeline
            // script is fine and we can verify the rest of the project related info
            this.pipeline = pipelineStr;
            this.temporaryPipeline = null;
            setScriptInputType(inputType.get());
        } else if (inputType.get() == ScriptInputType.SCM) {
            // if the user has pipeline set while selecting SCM, this ensures
            // their pipeline script is cleared. We don't want to store SCM
            // and random junk that may be left in the pipeline textarea.
            //
            // Note: the right SCM class (implementation not our enum type, we know our enum type)
            // is actually parsed within the super.doConfigSubmit() method call, so at this point
            // we still don't know which SCM implementation the user selected.
            this.pipeline = "";
            this.temporaryPipeline = null;
            setScriptInputType(inputType.get());
        }

        // an apply button was pressed (the user is not redirected). Even if the form
        // has errors, that is okay as we are validating the input forms
        // via client calls anyway (they display red error message under form)
        //
        // this config submit ensures that the properties of the build are saved
        save();
        super.doConfigSubmit(req, rsp);
    }

    // exported fields are shown in api, see /jenkins/job/<jobName>/api/json
    // This method is called from the /jenkins/job/<jobName>/configure page
    // in order to display the graph of jobs in the UI
    @Exported
    public JSONObject getBuildConfiguration() {
        String pipelineStr;
        if (this.temporaryPipeline == null) {
            // this will only happen when the project is loaded and the temporaryPipeline is
            // not set. When the user changes the pipeline through editor if it's valid it
            // will fill the temporary value and we will show the graph as presented in the
            // UI. The user will have to press the apply button if they wanted to persist
            // the changes. See also the comment for temporaryPipeline.
            pipelineStr = this.pipeline;
        } else {
            // BUG:
            // 1. The user writes something into the text area (but doesn't save)
            // 2. Refresh page
            // 3. Graph will be showing the previously typed things (stored in temporaryPipeline)
            //    even though the actual textarea is empty.
            //
            // I am not sure if problem has a solution, since on one hand we don't want to persist
            // the user data while they are typing, but on the other hand we also don't want to
            // display a graph when the pipeline is empty. We could fix it on the frontend by checking
            // for empty text area (if empty text area don't show the graph)
            pipelineStr = this.temporaryPipeline;
        }

        try {
            List<ParsedBuildJob> parsedNodes = DslParser.parseBuildNoVerify(pipelineStr).parsedJobs;
            List<ConfigGraphNode> jobs = createSerializedJobs(parsedNodes);
            BuildLayers layers = BuildLayers.topologicalSort(parsedNodes);

            JSONObject o = new JSONObject();
            o.put("build", jobs);
            o.put("cycle", layers.getBuildCycle());
            return o;
        } catch (ParseException e) {
            JSONObject o = new JSONObject();
            o.put("error", e.getMessage());
            return o;
        }
    }

    public static List<ConfigGraphNode> createSerializedJobs(List<ParsedBuildJob> parsedNodes) {
        // get uris for every job on jenkins, so we can link to projects on frontend
        Iterable<Job> jenkinsJobs = JenkinsUtil.getJenkins().allItems(Job.class);
        Map<String, String> uris = new HashMap<>();
        for (Job j : jenkinsJobs) {
            // @FUTURE: getAbsoluteUrl represents a potential problem with reverse proxies.
            uris.put(j.getFullName(), j.getAbsoluteUrl());
        }
        return parsedNodes.stream().map(node -> {
            String uri = uris.getOrDefault(node.getId(), "");
            return new ConfigGraphNode(node, uri);
        }).collect(Collectors.toList());
    }

    /**
     * Determines the type of user input for the DepBuilder pipeline
     */
    public enum ScriptInputType {
        SCRIPT,
        SCM;

        public static Optional<ScriptInputType> parseInputType(String input) {
            if (input == null || input.isEmpty()) {
                return Optional.empty();
            }

            switch (input.toUpperCase()) {
                case "SCM":
                    return Optional.of(SCM);
                case "SCRIPT":
                    return Optional.of(SCRIPT);
                default:
                    return Optional.empty();
            }
        }
    }

    /**
     * This class is used to describe the plugin (name, description)
     */
    @Extension(ordinal = 999)
    @Symbol({"depBuilderProject"})
    public static class DescriptorImpl extends AbstractProjectDescriptor {

        public DescriptorImpl() {
            // This descriptor is loaded immediately on Jenkins start,
            // the jenkins configuration pages are loaded later once the user
            // first refreshes the page.
        }

        @Override
        public String getDisplayName() {
            // this is the name displayed on the new job page (where the freestyle project is)
            return "DepBuilder";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new DslProject(parent, name);
        }

        @NonNull
        @Override
        public String getDescription() {
            // this is the description on the new job page (where the freestyle project is)
            return "Build project dependencies in a specific order by combining existing Freestyle jobs and Pipelines together into a large build workflow.";
        }

        @NonNull
        @Override
        public String getCategoryId() {
            return StandaloneProjectsCategory.ID;
        }

        @Override
        public String getIconClassName() {
            return "icon-depbuilder";
        }

        public static final String iconUri = "plugin/depbuilder/icons/depbuilder.png";

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-depbuilder icon-sm", iconUri, Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-depbuilder icon-md", iconUri, Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-depbuilder icon-lg", iconUri, Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-depbuilder icon-xlg", iconUri, Icon.ICON_XLARGE_STYLE));
        }

        public FormValidation doCheckScriptInputType(@QueryParameter String value,
                @AncestorInPath DslProject project) {
            if (value == null) {
                return FormValidation.error("Invalid value null is not allowed");
            }

            Optional<ScriptInputType> inputType = ScriptInputType.parseInputType(value);
            if (inputType.isPresent()) {
                return FormValidation.ok();
            }
            // we have an error
            return FormValidation.error(String.format("Invalid value provided: %s, allowed %s",
                                                      value, Arrays.toString(ScriptInputType.values())));
        }

        public ListBoxModel doFillScriptInputTypeItems(@AncestorInPath ItemGroup context) {
            ListBoxModel model = new ListBoxModel();
            model.add("Script", "SCRIPT");
            model.add("SCM", "SCM");
            return model;
        }

        // this method is automatically triggered on page load, but for some reason
        // it's not triggered on save action (we call it manually via onFormSubmit action)
        // Apparently there is an open bug for exactly this behaviour and provided
        // workaround seem to work for now: https://issues.jenkins-ci.org/browse/JENKINS-15604
        @POST
        public FormValidation doCheckPipeline(@QueryParameter String value,
                @AncestorInPath DslProject project) {
            project.checkPermission(Item.CONFIGURE);
            if (value == null) {
                return FormValidation.error("Pipeline should not be empty");
            }

            try {
                String projectName = project.getDisplayName();
                DslBuild.verifyPipeline(projectName, value);
                // save into internal variable so we can render graph in getBuildLayers method
                // in case of parsing error an old graph will be shown
                project.temporaryPipeline = value;
                return FormValidation.ok();
            } catch (BuildCycleException e) {
                project.temporaryPipeline = value;
                return FormValidation.error(e.getMessage());
            } catch (ParseException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        @POST
        public FormValidation doCheckScmFileLocation(@QueryParameter String value,
                @AncestorInPath DslProject project) {
            project.checkPermission(Item.CONFIGURE);
            if (value == null || value.isEmpty()) {
                return FormValidation.error("File location should not be empty");
            }

            project.setScmFileLocation(value);
            return FormValidation.ok();
        }

        public FormValidation doCheckNumberOfGraphs(@QueryParameter String value, @AncestorInPath DslProject project) {
            try {
                int parsedNumber = Integer.parseInt(value);
                if (parsedNumber < 1 || parsedNumber > 20) {
                    // we don't want to have more than 20 graphs displayed as this might
                    // cause performance problems
                    return FormValidation.error(String.format("number should be in range [1, 20]"));
                }

                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error("%s is not a valid number");
            }
        }

        // see https://reports.jenkins.io/core-taglib/jelly-taglib-ref.html for more on jenkins UI
        public ComboBoxModel doFillProjectsItems(@AncestorInPath ItemGroup context) {
            ComboBoxModel model = new ComboBoxModel();
            List<Job> jobs = Jenkins.get().getAllItems(Job.class);
            for (Job job : jobs) {
                model.add(job.getFullName());
            }
            return model;
        }

        public ComboBoxModel doFillAgentsItems(@AncestorInPath ItemGroup context) {
            ComboBoxModel model = new ComboBoxModel();
            model.add("any");
            model.add("master");
            List<Node> nodes = Jenkins.get().getNodes();
            for (Node n : nodes) {
                model.add(n.getDisplayName());
            }
            return model;
        }
    }
}
