package eu.royalsloth.depbuilder.dsl;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildLayers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static eu.royalsloth.depbuilder.dsl.TestDslParser.parseBuildNodes;
import static org.junit.jupiter.api.Assertions.*;

class TestParsedBuildJob {

    ////////////////
    // Build order
    ////////////////

    @Test
    public void topologicalSortTest_diamond() {
        /*
             A
           /   \
          B     C
           \   /
             D
         */
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B", "C"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("D"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList("D"));
        ParsedBuildJob D = new ParsedBuildJob("D", Arrays.asList());

        BuildLayers buildLayers = BuildLayers.topologicalSort(Arrays.asList(B, C, A, D));

        List<String> layerOne = Arrays.asList("A");
        List<String> layerTwo = Arrays.asList("B", "C");
        List<String> layerThree = Arrays.asList("D");
        List<List<String>> expectedLayers = Arrays.asList(layerOne, layerTwo, layerThree);
        assertEquals(expectedLayers, buildLayers.getOrderedBuildLayers(), "Build order layers are wrong");
        assertFalse(buildLayers.hasCycle(), "Detected build cycle where there should be none");
    }

    @Test
    public void topologicalSort_transitiveDependency() throws ParseException {
        /* Checking if A being parent of D affects our build layers
                A
              / | \
             B  |  C
              \ | /
                D
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B -> D\n A -> C -> D\n A -> D");
        System.out.println("parsedNodes = " + parsedNodes);
        BuildLayers buildLayers = BuildLayers.topologicalSort(parsedNodes);
        assertFalse(buildLayers.hasCycle(), "Detected cycle where there should be none");

        List<String> layerOne = Arrays.asList("A");
        List<String> layerTwo = Arrays.asList("B", "C");
        List<String> layerThree = Arrays.asList("D");
        List<List<String>> expectedLayers = Arrays.asList(layerOne, layerTwo, layerThree);
        assertEquals(expectedLayers, buildLayers.getOrderedBuildLayers(), "Wrong build layers detected");
    }

    @Test
    public void topologicalSort_twoGraphs() throws ParseException {
        /*
               A      D
              / \     |
             B   C    E
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B; A -> C; D -> E");
        BuildLayers layers = BuildLayers.topologicalSort(parsedNodes);
        List<String> firstLayer = Arrays.asList("A", "D");
        List<String> secondLayer = Arrays.asList("B", "C", "E");
        assertEquals(Arrays.asList(firstLayer, secondLayer), layers.getOrderedBuildLayers(),
                     "Wrong build layers detected");
    }

    @Test
    public void topologicalSort_twoGraphs_loneNode() throws ParseException {
        /*
               A      D
              / \
             B   C
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B; A -> C; D");
        BuildLayers layers = BuildLayers.topologicalSort(parsedNodes);
        List<String> firstLayer = Arrays.asList("A", "D");
        List<String> secondLayer = Arrays.asList("B", "C");
        assertEquals(Arrays.asList(firstLayer, secondLayer), layers.getOrderedBuildLayers(),
                     "Wrong build layers detected");
    }

    @Test
    public void parseNodesDsl_twoConnectedGraphs() throws ParseException {
        /*
            A    D
           / \   |
          B   C  |
               \ |
                 E
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B; A -> C; D -> E; C -> E");
        BuildLayers layers = BuildLayers.topologicalSort(parsedNodes);
        List<String> firstLayer = Arrays.asList("A", "D");
        List<String> secondLayer = Arrays.asList("B", "C");
        List<String> thirdLayer = Arrays.asList("E");
        assertEquals(Arrays.asList(firstLayer, secondLayer, thirdLayer), layers.getOrderedBuildLayers(),
                     "Wrong layers parsed");
    }

    ////////////////
    // Cycle detection
    ///////////////
    @Test
    public void topologicalSort_cycleDetection() throws ParseException {
        /* Cycle between components
               A
              / \
             B - C
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B -> C -> A");
        BuildLayers buildLayers = BuildLayers.topologicalSort(parsedNodes);
        assertTrue(buildLayers.hasCycle(), "Build cycle was not detected, but it should");
        assertEquals(Arrays.asList(), buildLayers.getOrderedBuildLayers(), "Build layers should be empty");
        assertEquals(Arrays.asList("A", "B", "C", "A"), buildLayers.getBuildCycle(), "Wrong build cycle detected");
    }

    @Test
    public void topologicalSort_cycleDetection2() throws ParseException {
        /* This is a cycle in a way where B depends on D and D depends on B.
                 A
                 |
                 B
               /  \\
              C    D
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B -> C; B -> D -> B");
        BuildLayers buildLayers = BuildLayers.topologicalSort(parsedNodes);
        assertTrue(buildLayers.hasCycle(), "Build cycle was not detected, but it should");
        assertEquals(Arrays.asList(), buildLayers.getOrderedBuildLayers(), "Build layers should be empty");
        assertEquals(Arrays.asList("D", "B", "D"), buildLayers.getBuildCycle(), "Wrong build cycle detected");
    }

    @Test
    public void topologicalSort_sameNodeCycle() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> A");
        BuildLayers buildLayers = BuildLayers.topologicalSort(parsedNodes);
        assertTrue(buildLayers.hasCycle(), "Build cycle was not detected");
    }

    @Test
    public void topologicalSort_twoWaysCycle() throws ParseException {
        /*
           A   E  <-+
           |   |    |
           B   X    |
           \  /     |
            C ------+
         */
        List<ParsedBuildJob> nodes = parseBuildNodes("A -> B -> C; E -> X -> C; C -> E");
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        assertTrue(layers.hasCycle(), "Build cycle was not detected");
        assertEquals(Arrays.asList("E", "X", "C", "E"), layers.getBuildCycle(), "Wrong build cycle detected");
    }

    @Test
    public void topologicalSort_firstCycleDetected() throws ParseException {
        /*
             A
           /   \
          B     C
           \   //
             D
           /   \
          E     F
           \\  /
             G
         */
        List<ParsedBuildJob> nodes = parseBuildNodes(
                "A -> B -> D; A -> C -> D;"
                        + "D -> E -> G; D -> F -> G;"
                        + "D -> C; G -> F;");
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        assertTrue(layers.hasCycle(), "Build cycle was not detected");
        assertEquals(Arrays.asList("C", "D", "C"), layers.getBuildCycle(), "Wrong build cycle detected");
    }

    @Test
    public void topologicalSort_circle() throws ParseException {
        /*
             A
            / \
           B  C
           |  |
           D  F = E
         */
        List<ParsedBuildJob> nodes = parseBuildNodes(
                "A -> B -> D; A -> C -> F -> E; E -> F;");
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        assertTrue(layers.hasCycle(), "Build cycle was not detected");
        assertEquals(Arrays.asList("E", "F", "E"), layers.getBuildCycle(), "Wrong build order detected");
    }

    @Test
    public void topologicalSort_circleWithoutCycle() throws ParseException {
        /*
            A
         /  |
        B   |
        \   |
         D  |
          \ |
            C
         */
        List<ParsedBuildJob> nodes = parseBuildNodes("A -> B -> D; A -> C; D -> C");
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        assertFalse(layers.hasCycle(), "Build cycle was detected when it shouldn't be");
    }

    @Test
    public void topologicalSort_cycleDetect() throws Exception {
        /*
            A +-------<----\
               \            \
                +---- C ----> D
            B -------->-----/

            Cycle:
            A -> C -> D -> A
         */
        String input = "A -> C -> D;"
                + "B -> C;"
                + "B -> D;"
                + "D -> A;";
        List<ParsedBuildJob> nodes = parseBuildNodes(input);
        BuildLayers layers = BuildLayers.topologicalSort(nodes);
        assertTrue(layers.hasCycle(), "Build has a cycle but it was not detected");
        List<String> cycle = layers.getBuildCycle();
        assertEquals(Arrays.asList("A", "C", "D", "A"), cycle, "Wrong cycle detected");
    }
}
