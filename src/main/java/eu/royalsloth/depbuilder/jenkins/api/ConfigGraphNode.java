package eu.royalsloth.depbuilder.jenkins.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;

import java.util.Set;

/**
 * Helper class used for serializing build jobs in order to display them on the graph in the Jenkins UI job
 * configuration page.
 */
@SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "this is public api used by the frontend")
public class ConfigGraphNode {
    public final String projectName;
    public final Set<String> children;
    public final String projectUri;

    public ConfigGraphNode(ParsedBuildJob j, String projectUri) {
        this.projectName = j.getId();
        this.children = j.getChildren();
        this.projectUri = projectUri;
    }
}
