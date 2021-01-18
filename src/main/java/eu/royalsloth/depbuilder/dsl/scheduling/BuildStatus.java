package eu.royalsloth.depbuilder.dsl.scheduling;

public enum BuildStatus {
    /**
     * An error occurred during the build process
     */
    ERROR,

    /**
     * The build node has a parent with build errors
     */
    PARENT_ERROR,

    /**
     * The build node was aborted during the build (usually manual abort)
     */
    ABORT,

    /**
     * The node was successfully built
     */
    SUCCESS,

    /**
     * The build is in progress
     */
    IN_PROGRESS,

    /**
     * This node should not be built
     */
    NO_BUILD,

    /**
     * The node was not yet built, but it is on the list of build nodes
     */
    NONE,
}
