package eu.royalsloth.depbuilder.dsl.scheduling;

import eu.royalsloth.depbuilder.dsl.DslParser;
import eu.royalsloth.depbuilder.dsl.ParseException;
import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static eu.royalsloth.depbuilder.dsl.scheduling.ScheduledNode.ScheduledNodeStatus;
import static org.junit.jupiter.api.Assertions.*;

public class TestScheduler {
    @Test
    public void testScheduler() throws ParseException {
        /*
           A
          / \
         B   C
         */
        Scheduler scheduler = createScheduler("A -> B; A -> C");

        assertTrue(scheduler.hasNext(), "We should have next build node, but we did not");
        ScheduledNode node = scheduler.getNext();
        assertEquals("A", node.getBuildJob().getId(), "Wrong build node id fetched");

        assertTrue(scheduler.hasNext(), "We should have next build node, but we did not");
        scheduler.finishBuild(node.getBuildJob(), BuildStatus.SUCCESS);

        node = scheduler.getNext();
        assertEquals("B", node.getBuildJob().getId(), "Wrong build node id fetched");
        scheduler.finishBuild(node.getBuildJob(), BuildStatus.SUCCESS);

        assertTrue(scheduler.hasNext(), "We should have next build node, but we did not");
        node = scheduler.getNext();
        assertEquals("C", node.getBuildJob().getId(), "Wrong build node id fetched");
        scheduler.finishBuild(node.getBuildJob(), BuildStatus.SUCCESS);

        // next node should not be found in the array
        assertFalse(scheduler.hasNext(), "We shouldn't have next build node");
        node = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.FINISHED, node.getStatus());
    }

    /**
     * Test a slow build where we are building node from another layer in case the first layer is not
     * finished
     */
    @Test
    public void testScheduler_slowBuild() throws Exception {
        /*
            A  C
            |  |
            B  D
               |
               E

            Here we simulate A build as slow, to verify that we can build the branch
            from another layer
         */
        Scheduler scheduler = createScheduler("A -> B; C -> D -> E");

        assertTrue(scheduler.hasNext(), "We should have next build node");
        BuildJob nodeA = scheduler.getNext().getBuildJob();
        assertEquals("A", nodeA.getId(), "Wrong build node");

        // since the A is not finished, the next node should be the next in the first
        // layer, that is C.
        assertTrue(scheduler.hasNext(), "We should have next build node");
        BuildJob nodeC = scheduler.getNext().getBuildJob();
        assertEquals("C", nodeC.getId(), "Wrong build node");

        // we simulate A as a slow build => we finish C node
        scheduler.finishBuild(nodeC, BuildStatus.SUCCESS);

        assertTrue(scheduler.hasNext(), "We should have a next build node");
        BuildJob nodeD = scheduler.getNext().getBuildJob();
        assertEquals("D", nodeD.getId(), "Wrong build node");

        scheduler.finishBuild(nodeD, BuildStatus.SUCCESS);
        assertTrue(scheduler.hasNext(), "We should have a next build node");

        // testing that the build layer traversal in scheduler works as expected.
        // E should be built before the other node, as A is still building.
        BuildJob nodeE = scheduler.getNext().getBuildJob();
        assertEquals("E", nodeE.getId(), "Wrong build node is next");
        scheduler.finishBuild(nodeE, BuildStatus.SUCCESS);

        // A is still not finished, getNext should return empty optional
        ScheduledNode nodeB = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, nodeB.getStatus(), "Wrong status, we should wait until A is built");

        scheduler.finishBuild(nodeA, BuildStatus.SUCCESS);
        nodeB = scheduler.getNext();
        assertEquals("B", nodeB.getBuildJob().getId(), "Wrong build node fetched");
        assertEquals(ScheduledNodeStatus.OK, nodeB.getStatus());

        ScheduledNode waitForB = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, waitForB.getStatus(), "We should wait for B to finish");

        scheduler.successBuild(nodeB.getBuildJob());
        ScheduledNode finishedBuild = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.FINISHED, finishedBuild.getStatus(), "Build should be finished");
    }

    @Test
    public void schedulingABuild() throws Exception {
        /*
              A
            /  \
           B    C
         */
        Scheduler scheduler = createScheduler("A -> B; A -> C");

        // A node
        ScheduledNode node = scheduler.getNext();
        assertEquals("A", node.getBuildJob().getId());
        assertEquals(ScheduledNodeStatus.OK, node.getStatus());
        scheduler.successBuild(node.getBuildJob());

        // B node
        ScheduledNode nodeB = scheduler.getNext();
        assertEquals("B", nodeB.getBuildJob().getId());
        assertEquals(ScheduledNodeStatus.OK, nodeB.getStatus());

        ScheduledNode nodeC = scheduler.getNext();
        assertEquals("C", nodeC.getBuildJob().getId());
        assertEquals(ScheduledNodeStatus.OK, nodeC.getStatus());

        // next node should report as wait until B and C are built
        ScheduledNode waitNode = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, waitNode.getStatus());

        // finish build C, scheduler should still report wait
        scheduler.errorBuild(nodeB.getBuildJob());
        waitNode = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, waitNode.getStatus());

        // finish build B, scheduler should report finished
        scheduler.successBuild(nodeC.getBuildJob());

        // we should get finished
        ScheduledNode finished = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.FINISHED, finished.getStatus());

        // trying to get another node, should return finished as well
        finished = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.FINISHED, finished.getStatus());
    }

    @Test
    public void schedulingPartialBuild() throws Exception {
        /*
              A
            /   \
         B(x)    C(ok)
         |       |
         D       E(ok)
           \   /
            F (abort)
         */
        String input = "A -> B -> D -> F; A -> C -> E -> F;";
        Scheduler scheduler = createScheduler(input);

        // A node
        ScheduledNode node = scheduler.getNext();
        assertEquals("A", node.getBuildJob().getId(), "Wrong build node id");
        assertEquals(ScheduledNodeStatus.OK, node.getStatus());
        scheduler.successBuild(node.getBuildJob());

        // B node
        node = scheduler.getNext();
        assertEquals("B", node.getBuildJob().getId(), "Wrong build node id");
        assertEquals(ScheduledNodeStatus.OK, node.getStatus());
        scheduler.errorBuild(node.getBuildJob());

        final ScheduledNode nodeC = scheduler.getNext();
        assertEquals("C", nodeC.getBuildJob().getId(), "Wrong build node id");
        assertEquals(ScheduledNodeStatus.OK, nodeC.getStatus());

        // let's say that C is a long build, we try to get next and we should get wait status
        node = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, node.getStatus());

        // we finish the node C build, now E node should be available
        scheduler.successBuild(nodeC.getBuildJob());

        node = scheduler.getNext();
        assertEquals("E", node.getBuildJob().getId(), "Wrong build node id");
        assertEquals(ScheduledNodeStatus.OK, node.getStatus());
        scheduler.successBuild(node.getBuildJob());

        // calling getNext() should return abort build
        node = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.ABORT, node.getStatus(), "Wrong node build status");
    }

    /**
     * Checks if we can build only a small chunk of the graph that was selected
     * by the user (from node B downwards).
     */
    @Test
    public void testPartialGraphBuild() throws Exception {
        /* Starting the build with node B. Nodes (B, C, D) should have status NONE,
           while the rest of the nodes should have status NOT_BUILD
           A      E
           |      |
          (B)      F
            \   /
              C
              |
              D
         */
        String input = "A -> B -> C -> D; E -> F -> C -> D";
        Scheduler scheduler = createScheduler(input, Arrays.asList("B"));
        BuildLayers layers = scheduler.buildLayers;
        for (List<BuildJob> layer : layers.getLayers()) {
            for (BuildJob job : layer) {
                switch (job.getId()) {
                    case "B":
                    case "C":
                    case "D":
                        assertEquals(job.getBuildStatus(), BuildStatus.NONE,
                                     "Wrong build status for job: " + job.getId());
                        break;
                    default:
                        assertEquals(job.getBuildStatus(), BuildStatus.NO_BUILD,
                                     "Wrong build status for job: " + job.getId());
                }
            }
        }

        ScheduledNode nodeB = scheduler.getNext();
        assertEquals("B", nodeB.getBuildJob().getId());
        ScheduledNode waitNode = scheduler.getNext();
        assertEquals(ScheduledNodeStatus.WAIT, waitNode.getStatus());

        // finish B, expecting next element to appear
        scheduler.successBuild(nodeB);
        ScheduledNode nodeC = scheduler.getNext();
        assertEquals("C", nodeC.getBuildJob().getId());

        // next node should be wait, until we finish building C
        waitNode = scheduler.getNextNode();
        assertEquals(ScheduledNodeStatus.WAIT, waitNode.getStatus());
        scheduler.successBuild(nodeC);

        ScheduledNode nodeD = scheduler.getNextNode();
        assertEquals("D", nodeD.getBuildJob().getId());

        // next node should be wait, until we finish building D
        waitNode = scheduler.getNextNode();
        assertEquals(ScheduledNodeStatus.WAIT, waitNode.getStatus());
        scheduler.successBuild(nodeD);

        ScheduledNode finished = scheduler.getNextNode();
        assertEquals(ScheduledNodeStatus.FINISHED, finished.getStatus());
    }

    private Scheduler createScheduler(String input) throws ParseException {
        List<ParsedBuildJob> nodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        SchedulerSettings defaultSettings = new SchedulerSettings();
        return new Scheduler(layers, defaultSettings);
    }

    private Scheduler createScheduler(String input, List<String> startNodes) throws ParseException {
        List<ParsedBuildJob> nodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        SchedulerSettings defaultSettings = new SchedulerSettings();
        return new Scheduler(layers, defaultSettings, Instant.now(), startNodes);
    }
}
