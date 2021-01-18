package eu.royalsloth.depbuilder.dsl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO for performing validity checks of user set build nodes
 */
public class SettingsVerifier {
    private Set<String> buildNodes = new HashSet<>();
    private Set<String> knownAgents = new HashSet<>();

    /**
     * Flag that determines whether or not we should check for the existence of data.
     */
    private boolean verify = true;

    /**
     * Determines whether we should strictly check the provided settings (e.g: unknown settings are not
     * allowed)
     */
    private boolean strictMode = true;

    public SettingsVerifier(Set<String> knownAgents, Set<String> buildNodes) {
        this.buildNodes = buildNodes;
        this.knownAgents = knownAgents;
        addAnyKnownAgent();
    }

    public SettingsVerifier() {
        addAnyKnownAgent();
    }

    public void setVerify(boolean verify) {
        this.verify = verify;
    }

    public boolean buildNodeExists(String buildNode) {
        if (verify) {
            return this.buildNodes.contains(buildNode);
        }

        return true;
    }

    public boolean agentExists(String agentToVerify) {
        if (verify) {
            return this.knownAgents.contains(agentToVerify);
        }

        return true;
    }

    public void setBuildNodes(String... nodes) {
        Set<String> newBuildNodes = new HashSet<>();
        Collections.addAll(newBuildNodes, nodes);
        this.buildNodes = newBuildNodes;
    }

    public void setBuildNodes(Set<String> buildNodes) {
        this.buildNodes = buildNodes;
    }

    public Set<String> getBuildNodes() {
        return buildNodes;
    }

    public Set<String> getKnownAgents() {
        return knownAgents;
    }

    public void setKnownAgents(Set<String> knownAgents) {
        this.knownAgents = knownAgents;
        addAnyKnownAgent();
    }

    private void addAnyKnownAgent() {
        this.knownAgents.add("any");
    }

    public void setKnownAgents(String... agents) {
        Set<String> newAgents = new HashSet<>();
        Collections.addAll(newAgents, agents);
        this.knownAgents = newAgents;
    }

    public void setStrictMode(boolean usingStrictMode) {
        this.strictMode = usingStrictMode;
    }

    public boolean getStrictMode() {
        return strictMode;
    }
}
