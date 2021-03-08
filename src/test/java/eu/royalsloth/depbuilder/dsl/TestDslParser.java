package eu.royalsloth.depbuilder.dsl;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildLayers;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings;
import eu.royalsloth.depbuilder.dsl.scheduling.SchedulerSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings.ParentFailureMode;
import static eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings.UnknownSetting;
import static org.junit.jupiter.api.Assertions.*;

public class TestDslParser {

    private static final SettingsVerifier settingsVerifier = new SettingsVerifier();

    @Test
    public void oneLineComment() throws ParseException {
        String input = "///////// A -> B\n"
                + "C -> D // E -> F";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        assertEquals(2, buildNodes.size());
        assertEquals("C", buildNodes.get(0).getId());
        assertEquals("D", buildNodes.get(1).getId());
    }

    @Test
    public void parseMultiLineComment_oneLine() throws ParseException {
        String input = "/**A -> B**/ C -> D";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        assertEquals(2, buildNodes.size());
        assertEquals("C", buildNodes.get(0).getId());
        assertEquals("D", buildNodes.get(1).getId());
    }

    @Test
    public void parseMultiLineComment_multiLine() throws ParseException {
        String input = "/* A -> B\n"
                + "C -> D\n"
                + "*/E -> F\n";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        assertEquals(2, buildNodes.size());
        assertEquals("E", buildNodes.get(0).getId());
        assertEquals("F", buildNodes.get(1).getId());
    }

    ///////////////////////////////
    // Parse settings builds
    ///////////////////////////////
    @Test
    public void parseSetting() throws ParseException {
        String input = "A {\n"
                + "name: \"custom name\"\n"
                + "agent: [any] \n"
                + "weight: 2\n"
                + "onParentFailure: BUILD\n"
                + "}\n";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildSettings settings = buildNodes.get(0).getBuildSettings();
        assertEquals("A", settings.getJobName(), "Wrong job name");
        assertEquals("custom name", settings.getDisplayName(), "Wrong custom name");
        assertEquals(Arrays.asList("any"), settings.getAgentNames(), "Wrong agent setting parsed");
        assertEquals(ParentFailureMode.BUILD, settings.getOnParentFailure(), "Wrong parent failure");
        Duration defaultDuration = Duration.ofHours(2);
        assertEquals(defaultDuration, settings.getMaxDuration(), "Default build time is wrong");
        assertEquals(2, settings.getWeight(), "Wrong build weight parsed");
    }

    @Test
    public void parseInvalidSetting_separateLines() throws ParseException {
        String input = "A {\n"
                + "agent: \n"
                + "[any]\n"
                + "weight: \n"
                + "2\n"
                + "}\n";
        ParseException ex = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        });
        System.out.println(ex.getMessage());
        assertEquals(2, ex.getLine(), "Error detected in the wrong line");
    }

    @Test
    public void parseInvalidSetting() {
        ParseException ex = assertThrows(ParseException.class, () -> {
            String input = "A {\n"
                    + "agent: \n"
                    + "weight: 2\n"
                    + "}";
            List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        });

        System.out.println(ex.getMessage());
        assertEquals(2, ex.getLine(), "Error detected in the wrong line");
    }

    @Test
    public void parseArraySetting() throws ParseException {
        String input = "A {\n"
                + "agent: [\"build1\", build2] \n"
                + "onParentFailure: ABORT\n"
                + "maxDuration: \"00:15\"\n"
                + "}";
        settingsVerifier.setBuildNodes("A");
        settingsVerifier.setKnownAgents("build1", "build2");
        List<ParsedBuildJob> buildNodes = DslParser.parseBuild(input, settingsVerifier).parsedJobs;
        BuildSettings settings = buildNodes.get(0).getBuildSettings();
        assertEquals("A", settings.getJobName());
        assertEquals("A", settings.getDisplayName());
        assertEquals(Arrays.asList("build1", "build2"), settings.getAgentNames(), "Wrong agent parsed");
        assertEquals(ParentFailureMode.ABORT, settings.getOnParentFailure(), "Wrong on parent failure setting");
        assertEquals(Duration.ofMinutes(15), settings.getMaxDuration(), "Wrong build time parsed");
    }

    @Test
    public void parseArraySetting_multiLine() throws ParseException {
        String input = "A {\n"
                + "agent: [build-1,\n"
                + "build_2\n"
                + ",]\n"
                + "maxDuration: 1:20\n"
                + "}";

        settingsVerifier.setKnownAgents("build-1", "build_2");
        List<ParsedBuildJob> buildNodes = DslParser.parseBuild(input, settingsVerifier).parsedJobs;
        BuildSettings settings = buildNodes.get(0).getBuildSettings();
        assertEquals(Arrays.asList("build-1", "build_2"), settings.getAgentNames(), "Wrong agents parsed");
        assertEquals(80, settings.getMaxDuration().toMinutes(), "Wrong max build time parsed");
    }

    @Test
    public void parseArraySetting_unclosedArray() {
        String input = "A {\n"
                + "weight: 2\n"
                + "agent: [build-1\n"
                + "}";

        ParseException ex = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
            BuildSettings settings = buildNodes.get(0).getBuildSettings();
        });
        assertEquals(4, ex.getLine(), "Error detected in the wrong line");
        System.out.println(ex);
    }

    @Test
    public void parseInvalidSetting_invalidTime_hhmm() {
        String input = "A {\n"
                + "maxDuration: 12:\n"
                + "}";
        ParseException ex = assertThrows(ParseException.class, () -> {
            DslParser.parseBuildNoVerify(input);
        });
        assertEquals("Line(2): invalid maxDuration value expected mm or hh:mm, got: '12:\\n'", ex.getMessage());
    }

    @Test
    public void parseInvalidSetting_invalidTime_mm() {
        String input = "A {\n"
                + "maxDuration: 12.0\n"
                + "}";
        ParseException ex = assertThrows(ParseException.class, () -> {
            DslParser.parseBuildNoVerify(input);
        });
        assertEquals("Line(2): invalid maxDuration value, '12.0' is not a valid duration (expected mm)", ex.getMessage());
    }

    /**
     * Unknown settings in strict mode are not allowed
     */
    @Test
    public void parseSettings_unknownSettingsInStrictMode() throws ParseException {
        String input = "A {\n"
                + "weight: 1\n"
                + "agent: [any]\n"
                + "unknownSetting: 2.0\n"
                + "onParentFailure: [\"a\",b]\n"
                + "}";

        ParseException ex = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        });
        assertEquals(4, ex.line, "Unknown setting was detected in the wrong line");
    }

    /**
     * Unknown settings in non strict mode should be allowed
     */
    @Test
    public void parseSettings_unknownSettingsInNonStrictMode() throws ParseException {
        String input = "A {\n"
                + "first: whatever\n"
                + "second: \"whatever\"\n"
                + "third: 2.0 \n"
                + "fourth: [\"a\",b]\n"
                + "}";
        // if strict setting is disabled, the unknown settings are allowed
        SettingsVerifier verifier = new SettingsVerifier();
        verifier.setVerify(false);
        verifier.setStrictMode(false);
        List<ParsedBuildJob> buildNodes = DslParser.parseBuild(input, verifier).parsedJobs;

        ParsedBuildJob node = buildNodes.get(0);
        BuildSettings settings = node.getBuildSettings();
        assertEquals("A", settings.getJobName());

        List<UnknownSetting> unknownSettings = settings.getUnknownSettings();
        assertEquals(4, unknownSettings.size(), "Wrong size of unknown settings");

        UnknownSetting firstSetting = unknownSettings.get(0);
        assertEquals("first", firstSetting.getSettingField());
        assertEquals("whatever", firstSetting.getSettingValue());
        assertEquals(2, firstSetting.getLine());

        // third option
        UnknownSetting thirdSetting = unknownSettings.get(2);
        assertEquals("third", thirdSetting.getSettingField());
        assertEquals("2.0", thirdSetting.getSettingValue());
        assertEquals(4, thirdSetting.getLine());

        // fourth
        UnknownSetting fourthSetting = unknownSettings.get(3);
        assertEquals("fourth", fourthSetting.getSettingField());
        assertEquals(Arrays.asList("a", "b"), fourthSetting.getSettingValue());
        assertEquals(5, fourthSetting.getLine());
    }

    // test if we can override the same setting by introducing another block
    @Test
    public void overrideSettingsTest() throws ParseException {
        String input = "A {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + "\n"
                + "A {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + "\n"
                + "A -> B";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        assertEquals(2, buildNodes.size(), "Wrong number of build settings parsed");
        BuildSettings settings = buildNodes.get(0).getBuildSettings();

        assertEquals(2, settings.getWeight(), "Wrong weight parsed");
        assertEquals(Arrays.asList("agent2"), settings.getAgentNames(), "Wrong weight parsed");

        // verify build dependency parsing
        ParsedBuildJob jobA = buildNodes.get(0);
        List<String> childrenA = jobA.getChildren().stream().sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("B"), childrenA, "Wrong children parsed for job A");

        ParsedBuildJob jobB = buildNodes.get(1);
        List<String> childrenB = jobB.getChildren().stream().sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList(), childrenB, "Wrong children parsed for job B");
    }

    @Test
    public void multipleSettingsTest() throws ParseException {
        // check parsing multiple tests at once
        String input = "A, B, C {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + "\n"
                + " B {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + "\n"
                + "A -> B -> C";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        assertEquals(3, buildNodes.size(), "Wrong number of build nodes");

        // first we verify that node settings are properly parsed
        BuildSettings settingsA = buildNodes.get(0).getBuildSettings();
        assertEquals(2, settingsA.getWeight(), "Wrong weight parsed for setting A");
        assertEquals(Arrays.asList("agent1"), settingsA.getAgentNames(), "Wrong agent parsed for setting A");

        BuildSettings settingsB = buildNodes.get(1).getBuildSettings();
        assertEquals(2, settingsB.getWeight(), "Wrong weight parsed for setting B");
        assertEquals(Arrays.asList("agent2"), settingsB.getAgentNames(), "Wrong agent parsed for setting B");

        BuildSettings settingsC = buildNodes.get(2).getBuildSettings();
        assertEquals(2, settingsC.getWeight(), "Wrong weight parsed for setting C");
        assertEquals(Arrays.asList("agent1"), settingsC.getAgentNames(), "Wrong agent parsed for setting C");

        // verify build dependency parsing
        ParsedBuildJob jobA = buildNodes.get(0);
        List<String> childrenA = jobA.getChildren().stream().sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("B"), childrenA, "A has wrong children");

        ParsedBuildJob jobB = buildNodes.get(1);
        List<String> childrenB = jobB.getChildren().stream().sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("C"), childrenB, "B has wrong children");

        ParsedBuildJob jobC = buildNodes.get(2);
        List<String> childrenC = jobC.getChildren().stream().sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList(), childrenC, "C has wrong children");
    }

    @Test
    public void testAllSettingsBlock() throws Exception {
        // block A should inherit weight properties from global configuration
        String input = "_ALL {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + ""
                + "A {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + "A -> B";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildSettings aSettings = buildNodes.get(0).getBuildSettings();
        assertEquals(2, aSettings.getWeight(), "Node A has wrong weight");
        assertEquals(Arrays.asList("agent2"), aSettings.getAgentNames(), "Wrong agents for node A");

        BuildSettings bSettings = buildNodes.get(1).getBuildSettings();
        assertEquals(2, bSettings.getWeight(), "Node B has wrong weight");
        assertEquals(Arrays.asList("agent1"), bSettings.getAgentNames(), "Wrong agents for node B");
    }

    @Test
    public void testAllTestSettingsBlock_deferred() throws Exception {
        // _ALL nodes will only affect the build settings that come after
        // the _ALL declaration. In this case A should not inherit anything
        // from the _ALL settings block
        String input = "A {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + ""
                + "_ALL {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + ""
                + "A -> B";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildSettings aSettings = buildNodes.get(0).getBuildSettings();
        assertEquals(1, aSettings.getWeight(), "Node A has wrong weight");
        assertEquals(Arrays.asList("agent2"), aSettings.getAgentNames(), "Wrong agents for node A");

        BuildSettings bSettings = buildNodes.get(1).getBuildSettings();
        assertEquals(2, bSettings.getWeight(), "Node B has wrong weight");
        assertEquals(Arrays.asList("agent1"), bSettings.getAgentNames(), "Wrong agents for node B");
    }

    @Test
    public void testAllTestSettingsBlock_deferred2() throws Exception {
        // if the _ALL setting is after the build definition, the B should
        // still grab the global configuration. The A configuration should
        // not be affected, because _ALL is declared after the A settings block
        // C settings should inherit weight from _ALL block
        String input = "A {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + ""
                + "A -> B -> C\n"
                + ""
                + "_ALL {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + ""
                + "C {\n"
                + "agent: [agent3]\n"
                + "}\n";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildSettings aSettings = buildNodes.get(0).getBuildSettings();
        assertEquals(1, aSettings.getWeight(), "Node A has wrong weight");
        assertEquals(Arrays.asList("agent2"), aSettings.getAgentNames(), "Wrong agents for node A");

        BuildSettings bSettings = buildNodes.get(1).getBuildSettings();
        assertEquals(2, bSettings.getWeight(), "Node B has wrong weight");
        assertEquals(Arrays.asList("agent1"), bSettings.getAgentNames(), "Wrong agents for node B");

        BuildSettings cSettings = buildNodes.get(2).getBuildSettings();
        assertEquals(2, cSettings.getWeight(), "Node C has wrong weight");
        assertEquals(Arrays.asList("agent3"), cSettings.getAgentNames(), "Wrong agents for node C");
    }

    @Test
    public void testAllTestSettingsBlock_UnwieldyInheritance() throws Exception {
        // Settings should only inherit from the _ALL setting blocks that were
        // declared above them. In this case A should inherit from the first _ALL
        // block while B should inherit from both _ALL blocks.
        String input = "_ALL {\n"
                + "weight: 2\n"
                + "agent: [agent1]\n"
                + "}\n"
                + ""
                + "A {\n"
                + "agent: [agent2]\n"
                + "}\n"
                + ""
                + "_ALL {\n"
                + "weight: 3\n"
                + "}\n"
                + ""
                + "B {\n"
                + "onParentFailure: BUILD\n"
                + "}\n"
                + ""
                + "A -> B";
        List<ParsedBuildJob> buildNodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        BuildSettings aSettings = buildNodes.get(0).getBuildSettings();
        assertEquals(2, aSettings.getWeight(), "Node A has wrong weight");
        assertEquals(Arrays.asList("agent2"), aSettings.getAgentNames(), "Wrong agents for node A");
        assertEquals(ParentFailureMode.ABORT, aSettings.getOnParentFailure(), "Wrong parent failure mode for node A");

        BuildSettings bSettings = buildNodes.get(1).getBuildSettings();
        assertEquals(3, bSettings.getWeight(), "Node B has wrong weight");
        assertEquals(Arrays.asList("agent1"), bSettings.getAgentNames(), "Wrong agents for node B");
        assertEquals(ParentFailureMode.BUILD, bSettings.getOnParentFailure(), "Wrong parent failure mode for node B");
    }

    ///////////////////////
    // Scheduler settings - community version
    ///////////////////////
    @Test
    public void parseSchedulerSettings() throws ParseException {
        String input = "_BUILD {\n"
                + "maxDuration: 12:15\n"
                + "buildThrottle: [12:00|5]\n"
                + "}";
        ParsedBuild parsedBuild = DslParser.parseBuildNoVerify(input);
        SchedulerSettings settings = parsedBuild.schedulerSettings;
        assertEquals(Duration.ofHours(12).plusMinutes(15), settings.maxDuration, "Wrong max build time");
        assertEquals(new ArrayList<>(), settings.buildThrottle, "Build throttle is not supported in community version");
    }

    @Test
    public void unknownSettingsField() {
        String pipeline = "A {\n"
                + "maxDuration: 02:10\n"
                + "A";

        ParseException ex = assertThrows(ParseException.class, () -> {
            DslParser.parseBuildNoVerify(pipeline);
        });
        assertEquals("Line(3): unknown setting A, supported settings: [agent, maxDuration, onParentFailure, weight]", ex.getMessage());
    }

    @Test
    public void missingClosingBraceInSettings() {
        String pipeline = "A {\n"
                + "maxDuration: 1:15\n"
                + "weight";
        ParseException ex = assertThrows(ParseException.class, () -> {
            DslParser.parseBuildNoVerify(pipeline);
        });
        assertEquals("Line(3): A 'weight' field is missing a value, expected ':', got ''", ex.getMessage());
    }

    @Test
    public void missingClosingBraceSettings() {
        String pipeline = "A {\n"
                + "maxDuration: 1:15\n";
        ParseException ex = assertThrows(ParseException.class, () -> {
            DslParser.parseBuildNoVerify(pipeline);
        });
        assertEquals("Line(2): 'A' settings are missing a closing brace '}'", ex.getMessage());
    }

    //////////////////////
    // Strict verifier
    //////////////////////
    @Test
    public void integrationTest() throws ParseException {
        String input = "_BUILD {\n"
                + "maxDuration: 12:15"
                + "}\n"
                + " "
                + "_ALL {"
                + "agent: [any];"
                + "weight: 3;"
                + "maxDuration: 01:15;"
                + "}\n"
                + "\n"
                + "A {"
                + "agent: [agent1]"
                + "maxDuration: 00:00:10"
                + "}\n"
                + "\n"
                + "A -> B;";

        Set<String> agents = new HashSet<>();
        agents.add("agent1");
        Set<String> buildNodes = new HashSet<>();
        buildNodes.add("A");
        buildNodes.add("B");
        SettingsVerifier verifier = new SettingsVerifier(agents, buildNodes);

        ParsedBuild parsedBuild = DslParser.parseBuild(input, verifier);
        SchedulerSettings settings = parsedBuild.schedulerSettings;
        assertEquals(Duration.ofHours(12).plusMinutes(15), settings.maxDuration);

        // verify the build jobs.
        List<ParsedBuildJob> parsedBuildJobs = parsedBuild.parsedJobs;
        parsedBuildJobs.sort(Comparator.comparing(ParsedBuildJob::getId));

        ParsedBuildJob nodeA = parsedBuildJobs.get(0);
        assertEquals(Arrays.asList("agent1"), nodeA.getBuildSettings().getAgentNames());
        assertEquals(Arrays.asList("B"), new ArrayList<>(nodeA.getChildren()));
        assertEquals(Duration.ofSeconds(10), nodeA.getBuildSettings().getMaxDuration());

        ParsedBuildJob nodeB = parsedBuildJobs.get(1);
        assertEquals(Arrays.asList("any"), nodeB.getBuildSettings().getAgentNames());
        assertEquals(Arrays.asList(), new ArrayList<>(nodeB.getChildren()));
        assertEquals(Duration.ofHours(1).plusMinutes(15), nodeB.getBuildSettings().getMaxDuration());
    }

    ///////////////////////////////
    // Parsing DSL into build nodes, ready for dependency resolution
    ///////////////////////////////
    @Test
    public void parseNodesDsl_test() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B -> C");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseBuild() throws ParseException {
        String input = "A -> B; A -> C";
        List<ParsedBuildJob> nodes = parseBuildNodes(input);
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B", "C"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList());
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), nodes, "Wrong build nodes parsed");
    }

    @Test
    public void parseNodesDsl_newLineTest() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("\n A->B ->C    ");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_quotedString() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("\"A x\"->B ->C");
        ParsedBuildJob A = new ParsedBuildJob("A x", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_multilineString() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("B -> C\nA -> B");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_semicolonSeparator() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes(";;A->B ->C  ;  ");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_semicolonSeparator2() throws ParseException {
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("B -> C;;A -> B");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("C"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_diamondParse() throws ParseException {
        /*
             A
           /   \
          B     C
           \   /
             D
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B -> D\n A -> C -> D");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B", "C"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList("D"));
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList("D"));
        ParsedBuildJob D = new ParsedBuildJob("D", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C, D), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_twoUnconnectedGraphs() throws ParseException {
        /*
            A      D  <-- lone node
           / \
          B   C
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B; A->C; D");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B", "C"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList());
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        ParsedBuildJob D = new ParsedBuildJob("D", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C, D), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_twoUnconnectedGraphs2() throws ParseException {
        /*
            A      D
           / \     |
          B   C    E
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> B; A->C; D->E");
        ParsedBuildJob A = new ParsedBuildJob("A", Arrays.asList("B", "C"));
        ParsedBuildJob B = new ParsedBuildJob("B", Arrays.asList());
        ParsedBuildJob C = new ParsedBuildJob("C", Arrays.asList());
        ParsedBuildJob D = new ParsedBuildJob("D", Arrays.asList("E"));
        ParsedBuildJob E = new ParsedBuildJob("E", Arrays.asList());
        assertEquals(Arrays.asList(A, B, C, D, E), parsedNodes, "Nodes were not parsed correctly");
    }

    @Test
    public void parseNodesDsl_sameNode() throws ParseException {
        /*
         A - A
         */
        List<ParsedBuildJob> parsedNodes = parseBuildNodes("A -> A");
        BuildLayers layers = BuildLayers.topologicalSort(parsedNodes);
        assertTrue(layers.hasCycle(), "Cycle should be detected");
    }

    /////////////////////////////////
    // parsing invalid DLS section
    /////////////////////////////////
    @Test
    public void parseInvalidDsl_doubleArrow() {
        Exception exception = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> parseNodes = parseBuildNodes("A -> -> B");
        });

        System.out.println("exception = " + exception.getMessage());
    }

    @Test
    public void parseInvalidDsl_identifierProblem() {
        Exception exception = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> nodes = parseBuildNodes("A -> B D");
        });
        System.out.println("exception = " + exception.getMessage());
    }

    @Test
    public void parseInvalidDsl_arrowAtTheStart() {
        Exception exception = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> nodes = parseBuildNodes("-> A -> B");
        });
        System.out.println("exception = " + exception.getMessage());
    }

    @Test
    public void parseInvalidDsl_arrowAtTheEndOfTheLine() {
        ParseException exception = assertThrows(ParseException.class, () -> {
            List<ParsedBuildJob> nodes = parseBuildNodes("A -> B -> \nC");
        });
        System.out.println("exception = " + exception.getMessage());
    }

    ///////////////
    // Helpers
    ///////////////
    public static List<ParsedBuildJob> parseBuildNodes(String input) throws ParseException {
        List<ParsedBuildJob> nodes = DslParser.parseBuildNoVerify(input).parsedJobs;
        // sort alphabetically
        nodes.sort(Comparator.comparing(ParsedBuildJob::getId));
        return nodes;
    }
}
