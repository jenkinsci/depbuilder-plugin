package eu.royalsloth.depbuilder.dsl.scheduling;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Build settings of one build node in the graph of build nodes.
 *
 * <pre>
 * MyNode {
 *    agent: [name of agents]
 *    onParentFailure: (BUILD_ANYWAY, NO_BUILD)
 *    maxBuildTime: 1:00 (hh:mm, 00:00 don't limit)
 * }
 * </pre>
 */
public class BuildSettings {
    public static final Duration INFINITE_DURATION = Duration.ofDays(10_000);
    public static final Duration DEFAULT_BUILD_DURATION = Duration.ofHours(2);

    // settable fields
    private final String buildNodeName;
    private List<BuildAgent> agents = new LinkedList<>();
    private ParentFailureMode onParentFailure = ParentFailureMode.ABORT;
    private Duration maxDuration = DEFAULT_BUILD_DURATION;
    /**
     * Defines how heavy the build process is. In case we know there is a heavy compilation build that is more
     * expensive than a simple report job.
     **/
    private int weight = 1;
    /**
     * In case we need to report the invalid or deprecated settings to the end user
     */
    private List<UnknownSetting> unknownSettings = new LinkedList<>();

    public BuildSettings(String buildNodeName) {
        this.buildNodeName = buildNodeName;
    }

    /**
     * Since Java doesn't support deep object copy without magic tricks, we are manually copying all the
     * fields over. If any of the fields in the settings are changed, this method should change as well.
     */
    public BuildSettings(BuildSettings settingsToCopy) {
        this.buildNodeName = settingsToCopy.getName();
        this.weight = settingsToCopy.weight;

        // copy an array of build agents
        for (BuildAgent agentToCopy : settingsToCopy.getAgents()) {
            this.agents.add(new BuildAgent(agentToCopy));
        }

        this.onParentFailure = settingsToCopy.onParentFailure;
        this.maxDuration = Duration.ofMillis(settingsToCopy.maxDuration.toMillis());

        // copy unknown settings
        for (UnknownSetting unknownSetting : settingsToCopy.unknownSettings) {
            UnknownSetting setting = (UnknownSetting) unknownSetting.clone();
            this.unknownSettings.add(setting);
        }
    }

    public static BuildSettings copy(BuildSettings settingsToCopy) {
        return new BuildSettings(settingsToCopy);
    }

    ////////////////////////////
    // getters & setters
    ///////////////////////////
    public String getName() {
        return buildNodeName;
    }

    public List<BuildAgent> getAgents() {
        return agents;
    }

    public List<String> getAgentNames() {
        return agents.stream().map(agent -> agent.agentName).collect(Collectors.toList());
    }

    public void setAgents(List<BuildAgent> agents) {
        this.agents = agents;
    }

    public ParentFailureMode getOnParentFailure() {
        return onParentFailure;
    }

    public void setOnParentFailure(ParentFailureMode onParentFailure) {
        this.onParentFailure = onParentFailure;
    }

    public Duration getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }


    public void addUnknownSetting(UnknownSetting unknownSetting) {
        this.unknownSettings.add(unknownSetting);
    }

    public List<UnknownSetting> getUnknownSettings() {
        return unknownSettings;
    }

    ////////////////////////
    // additional classes
    ////////////////////////

    public enum ParentFailureMode {
        /**
         * Build child node even if there were errors upstream
         */
        BUILD,

        /**
         * Don't build child node if one of the parents build failed
         */
        ABORT;

        public static Optional<ParentFailureMode> parse(String input) {
            for (ParentFailureMode mode : ParentFailureMode.values()) {
                if (mode.toString().equals(input)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }

        public static String allModes() {
            return Arrays.toString(ParentFailureMode.values());
        }
    }

    // this is a bit wonky, currently the numbers are still presented as string,
    // but other than that it's fine
    public static class UnknownSetting implements Cloneable {
        private final String settingField;
        private final Object settingValue;
        private final int line;

        public UnknownSetting(String settingField, Object settingValue, int line) {
            this.settingField = settingField;
            this.settingValue = settingValue;
            this.line = line;
        }

        private UnknownSetting(UnknownSetting settingToCopy) {
            this.settingField = settingToCopy.settingField;
            this.line = settingToCopy.line;
            // settingValue is actually a shallow copy in this case, but for unknown setting
            // is not that important, since once the object is created it won't be modified
            this.settingValue = settingToCopy.settingValue;
        }

        @Override
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                System.out.println("Clone is not supported for unknown settings: " + e);
                return new UnknownSetting(this);
            }
        }

        public String getSettingField() {
            return settingField;
        }

        public Object getSettingValue() {
            return settingValue;
        }

        public int getLine() {
            return line;
        }

        @Override
        public String toString() {
            return String.format("line %d; %s:%s", getLine(), settingField, settingValue);
        }
    }
}
