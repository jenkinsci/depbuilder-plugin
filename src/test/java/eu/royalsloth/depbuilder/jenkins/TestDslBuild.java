package eu.royalsloth.depbuilder.jenkins;

import hudson.EnvVars;
import hudson.Platform;
import hudson.model.*;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Integration tests that are testing if the {@link DslBuild} scheduling logic is working as expected.
 */
public class TestDslBuild {
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    public TestDslBuild() {
    }

    @Before
    public void setup() throws Exception {
        // number of executors on the master node should be at least 1, otherwise we
        // won't be able to schedule all the jobs, since our scheduler occupies 1 executor
        jenkins.getInstance().setNumExecutors(2);

        // we are testing the build process of the community version of the plugin
        PluginVersion.setCommunity();
    }

    public EnvVars createLinuxVars() {
        EnvVars linux = new EnvVars();
        linux.setPlatform(Platform.UNIX);
        return linux;
    }

    public EnvVars createWindowsVars() {
        EnvVars windows = new EnvVars();
        windows.setPlatform(Platform.WINDOWS);
        return windows;
    }

    /**
     * Checking if offline nodes are still found via Jenkins instance or they completely disappear (they
     * should still be found via Jenkins master controller node even if they are offline).
     */
    @Test
    public void testIfOfflineNodesAreDetected() throws Exception {
        EnvVars linux = createLinuxVars();
        DumbSlave buildNode = jenkins.createSlave("build_1", null, linux);
        jenkins.waitOnline(buildNode);

        Optional<Computer> computer = Arrays.stream(JenkinsUtil.getJenkins().getComputers())
                                            .filter(comp -> "build_1".equals(comp.getName()))
                                            .findFirst();
        if (!computer.isPresent()) {
            Assertions.fail("build_1 was not found in the list of computers");
        }

        assertTrue(computer.get().isOnline(), "Computer build_1 should be online");

        // disconnect and try again
        if (buildNode.getComputer() == null) {
            throw new IllegalStateException("Build node computer in question is null, this should never happen");
        }
        buildNode.getComputer()
                 .setTemporarilyOffline(true, new OfflineCause.UserCause(User.current(), "Maintenance"));
        computer = Arrays.stream(JenkinsUtil.getJenkins().getComputers())
                         .filter(comp -> "build_1".equals(comp.getName()))
                         .findFirst();
        if (!computer.isPresent()) {
            // the computer should be still part of the group of computers even if it's offline
            Assertions.fail("build_1 was not found in the list of computers after disconnect");
        }
        assertFalse(computer.get().isOnline(), "Computer build_1 should be offline, but it's not");
    }

    /**
     * If we assign the job to the specific nodes and they are all offline, we expect the build to immediately
     * fail before we even execute it.
     */
    @Test
    public void assignedNodesAreAllOffline() throws Exception {
        EnvVars linux = createLinuxVars();
        DumbSlave buildNode = jenkins.createSlave("build_1", null, linux);

        FreeStyleProject projectA = jenkins.createFreeStyleProject("A");
        DslProject dslProject = jenkins.createProject(DslProject.class);
        String pipeline = "_BUILD {"
                + "maxDuration: 00:01:30;"
                + "}\n"
                + ""
                + "A {"
                + "agent: [build_1];"
                + "}";
        dslProject.setPipeline(pipeline);

        jenkins.waitOnline(buildNode);
        Optional<Computer> computer = Arrays.stream(JenkinsUtil.getJenkins().getComputers())
                                            .filter(comp -> "build_1".equals(comp.getName()))
                                            .findFirst();
        if (!computer.isPresent()) {
            Assertions.fail("build_1 was not found in the list of computers");
        }

        // using setTemporarilyOffline instead of disconnect method call, as it looks like this
        // one goes into effect immediately and disconnect needs some time to disconnect.
        assertNotNull(buildNode.getComputer(), "Computer does not exist for the build node");
        buildNode.getComputer()
                 .setTemporarilyOffline(true, new OfflineCause.UserCause(User.current(), "Maintenance"));
        assertTrue(buildNode.getComputer().isOffline(), "Computer should be offline but it's not");

        jenkins.buildAndAssertStatus(Result.FAILURE, dslProject);
        assertTrue(buildNode.getComputer()
                            .isOffline(), "Computer after build should be offline but it's not");

        DslBuild lastRun = dslProject.getLastBuild();
        jenkins.assertLogContains("Build job A agents [build_1] are not online.", lastRun);
    }

    @Test
    public void maxBuildShouldTimeout() throws Exception {
        WorkflowJob firstProject = jenkins.createProject(WorkflowJob.class, "project1");
        firstProject.setDefinition(new CpsFlowDefinition("sleep 10s", true));

        DslProject dslProject = jenkins.createProject(DslProject.class);
        String pipeline = "_ALL {\n"
                + "maxDuration: 00:00:02\n"
                + "}\n"
                + "project1";
        dslProject.setPipeline(pipeline);
        jenkins.buildAndAssertStatus(Result.ABORTED, dslProject);
    }

    @Test
    public void schedulerShouldTimeout() throws Exception {
        WorkflowJob firstProject = jenkins.createProject(WorkflowJob.class, "project1");
        firstProject.setDefinition(new CpsFlowDefinition("sleep 10s", true));

        DslProject dslProject = jenkins.createProject(DslProject.class);
        String pipeline = "_BUILD {\n"
                + "maxDuration: 00:00:05\n"
                + "}\n"
                + "\n"
                + "project1 {\n"
                + "maxDuration: 02:10\n"
                + "}\n"
                + "project1";
        dslProject.setPipeline(pipeline);
        jenkins.buildAndAssertStatus(Result.ABORTED, dslProject);
    }

    @Test
    public void syntaxErrorShouldFailTheBuild() throws Exception {
        FreeStyleProject firstProject = jenkins.createFreeStyleProject("project1");
        DslProject job = jenkins.createProject(DslProject.class);

        String pipeline = "project1 {\n"
                + "agent: [build1]\n"
                + "\n" // missing closing brace should return an error
                + "\n"
                + "project1";
        job.setPipeline(pipeline);
        jenkins.buildAndAssertStatus(Result.FAILURE, job);
    }

    /**
     * Check if we can restrict the builds to a specific build agents.
     */
    @Test
    public void testBuildOnComputer() throws Exception {
        EnvVars linux = createLinuxVars();
        DumbSlave buildNode = jenkins.createSlave("build1", null, linux);
        DumbSlave buildNode2 = jenkins.createSlave("build2", null, linux);

        FreeStyleProject firstProject = jenkins.createFreeStyleProject("project1");
        FreeStyleProject secondProject = jenkins.createFreeStyleProject("project2");
        DslProject job = jenkins.createProject(DslProject.class);

        // build one project on specific build node
        String pipeline = "project1 {\n"
                + "agent: [build1]\n"
                + "}\n"
                + "\n"
                + "project1 -> project2";
        job.setPipeline(pipeline);

        jenkins.waitOnline(buildNode);
        jenkins.waitOnline(buildNode2);
        DslBuild build = jenkins.buildAndAssertSuccess(job);

        FreeStyleBuild firstBuild = firstProject.getLastBuild();
        assertNotNull(firstBuild, "myProject last build was null");
        Node firstBuildNode = firstBuild.getBuiltOn();
        assertNotNull(firstBuildNode);
        assertEquals("build1", getOrMaster(firstBuildNode), "Wrong build node for project 1");

        FreeStyleBuild anotherProjectBuild = secondProject.getLastBuild();
        assertNotNull(anotherProjectBuild, "myProject last build was null");
        Node secondBuildNode = anotherProjectBuild.getBuiltOn();
        assertNotNull(secondBuildNode, "Second build agent was null");
        assertEquals("master", getOrMaster(secondBuildNode), "Wrong build node for project2");
    }

    /**
     * Checking if the error build prevents building other projects downstream
     */
    @Test
    public void testErrorBuildPreventsBuilding() throws Exception {
        WorkflowJob firstProject = jenkins.createProject(WorkflowJob.class, "project1");
        firstProject.setDefinition(new CpsFlowDefinition("error 'Fail!'", true));
        FreeStyleProject secondProject = jenkins.createFreeStyleProject("project2");

        DslProject job = jenkins.createProject(DslProject.class);
        job.setPipeline("project1 -> project2");
        jenkins.buildAndAssertStatus(Result.FAILURE, job);

        WorkflowRun firstProjectBuild = firstProject.getLastBuild();
        assertEquals(Result.FAILURE, firstProjectBuild.getResult(), "project1 build should fail, but it didn't");

        FreeStyleBuild secondProjectBuild = secondProject.getLastBuild();
        assertNull(secondProjectBuild, "Build for project2 should not exist");
    }

    /**
     * If onParentFailure setting is set the project should build even if there were errors in the previous
     * steps of the build. The build as a whole should still fail at the end of the whole build
     */
    @Test
    public void testBuildEvenIfThereAreErrors() throws Exception {
        WorkflowJob firstProject = jenkins.createProject(WorkflowJob.class, "project1");
        firstProject.setDefinition(new CpsFlowDefinition("error 'Fail!'", true));
        FreeStyleProject secondProject = jenkins.createFreeStyleProject("project2");

        DslProject job = jenkins.createProject(DslProject.class);
        String pipeline = "project2 {\n"
                + "onParentFailure: BUILD\n"
                + "}\n"
                + "project1 -> project2";

        job.setPipeline(pipeline);
        jenkins.buildAndAssertStatus(Result.FAILURE, job);

        WorkflowRun firstProjectBuild = firstProject.getLastBuild();
        assertEquals(Result.FAILURE, firstProjectBuild.getResult(), "project1 build should fail but it didn't");

        FreeStyleBuild secondProjectBuild = secondProject.getLastBuild();
        assertNotNull(secondProjectBuild, "project2 build should exist");
        assertEquals(Result.SUCCESS, secondProjectBuild.getResult(), "project2 build should succeed but it didn't");
    }

    /**
     * Checking if pulling pipeline data with the SCM works as expected
     */
    @Test
    public void testScmDslBuild() throws Exception {
        DumbSlave buildNode = jenkins.createSlave("agent1", null, createLinuxVars());
        FreeStyleProject project1 = jenkins.createFreeStyleProject("Project1");
        FreeStyleProject project2 = jenkins.createFreeStyleProject("Project2");

        DslProject job = jenkins.createProject(DslProject.class);
        String contents = "// my comment \n"
                + "Project1 {"
                + "agent: [agent1]\n"
                + "}\n"
                + "Project1 -> Project2;\n"
                + "// another comment";
        SingleFileSCM scm = new SingleFileSCM("someFolder/Pipeline", contents);
        job.setScm(scm);
        job.setScmFileLocation("someFolder/Pipeline");
        job.setScriptInputType(DslProject.ScriptInputType.SCM);

        jenkins.waitOnline(buildNode);
        jenkins.buildAndAssertSuccess(job);

        FreeStyleBuild build1 = project1.getLastBuild();
        Node builtOn = build1.getBuiltOn();
        assertEquals(builtOn.getDisplayName(), "agent1", "Project1 was built on the wrong agent");

        FreeStyleBuild build2 = project2.getLastBuild();
        builtOn = build2.getBuiltOn();
        assertEquals(getOrMaster(builtOn), "master", "Project2 was built on the wrong agent");
    }

    /**
     * Wrong scm file should abort the build
     */
    @Test
    public void wrongScmFile() throws Exception {
        FreeStyleProject project1 = jenkins.createFreeStyleProject("Project1");
        FreeStyleProject project2 = jenkins.createFreeStyleProject("Project2");
        DslProject job = jenkins.createProject(DslProject.class);
        String pipeline = "Project1 -> Project2";
        job.setScm(new SingleFileSCM("someFolder/Pipeline", pipeline));
        job.setScriptInputType(DslProject.ScriptInputType.SCM);
        job.setScmFileLocation("Pipeline");

        jenkins.buildAndAssertStatus(Result.FAILURE, job);
        assertNull(project1.getLastBuild(), "Project1 last build should not exist");
        assertNull(project2.getLastBuild(), "Project2 last build should not exist");
    }

    private String getOrMaster(Node node) {
        if ("".equals(node.getNodeName())) {
            return "master";
        }
        return node.getNodeName();
    }
}
