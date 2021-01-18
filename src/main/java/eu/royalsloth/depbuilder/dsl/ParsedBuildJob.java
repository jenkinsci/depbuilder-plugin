package eu.royalsloth.depbuilder.dsl;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO for build node parsed data
 */
public class ParsedBuildJob {

    private final String id;
    private final Set<String> children = new HashSet<>();
    private final BuildSettings buildSettings;

    /**
     * @param id
     * @param children      - dependency is the known parent node of this build node
     * @param buildSettings - settings of this build node
     */
    public ParsedBuildJob(String id, Collection<String> children, BuildSettings buildSettings) {
        this.id = id;
        this.children.addAll(children);
        this.buildSettings = buildSettings;
    }

    public ParsedBuildJob(String id, Collection<String> children) {
        this(id, children, new BuildSettings(id));
    }

    public String getId() {
        return id;
    }

    public Set<String> getChildren() {
        return children;
    }

    public BuildSettings getBuildSettings() {
        return this.buildSettings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ParsedBuildJob buildNode = (ParsedBuildJob) o;

        if (!id.equals(buildNode.id)) {
            return false;
        }
        return children.equals(buildNode.children);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + children.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", id, children);
    }
}
