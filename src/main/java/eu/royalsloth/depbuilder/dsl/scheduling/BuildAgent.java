package eu.royalsloth.depbuilder.dsl.scheduling;

public class BuildAgent {
    public static final String ANY = "any";

    public final String agentName;
    public final BuildAgentType agentType;

    public BuildAgent(String agentName, BuildAgentType agentType) {
        this.agentName = agentName;
        this.agentType = agentType;
    }

    /**
     * Copy constructor
     */
    public BuildAgent(BuildAgent buildAgent) {
        this.agentName = buildAgent.agentName;
        this.agentType = buildAgent.agentType;
    }

    /**
     * Checks if the build agent could be any build server (not bound to specific name)
     *
     * @return true if any build node could represent this agent and false if a specific build node with a
     * specific name should represent this agent.
     */
    public boolean isAny() {
        boolean isAny = ANY.equalsIgnoreCase(agentName);
        return isAny;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildAgent that = (BuildAgent) o;

        if (!agentName.equals(that.agentName)) {
            return false;
        }
        return agentType == that.agentType;
    }

    @Override
    public int hashCode() {
        int result = agentName.hashCode();
        result = 31 * result + agentType.hashCode();
        return result;
    }
}
