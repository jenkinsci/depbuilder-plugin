package eu.royalsloth.depbuilder.jenkins;

import hudson.model.Action;
import hudson.model.InvisibleAction;

import java.util.List;

/**
 * Action used for starting partial pipeline builds (only a small part of
 * the entire pipeline will be built. The chosen jobs define the starting
 * nodes from which the build will progress)
 */
public class PartialBuildAction extends InvisibleAction implements Action {
    public final transient List<String> graphJobsToBuild;

    public PartialBuildAction(List<String> graphJobsToBuild) {
        this.graphJobsToBuild = graphJobsToBuild;
    }
}
