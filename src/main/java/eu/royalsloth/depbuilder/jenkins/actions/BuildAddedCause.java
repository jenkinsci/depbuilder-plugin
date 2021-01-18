package eu.royalsloth.depbuilder.jenkins.actions;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.Run;

import java.util.Optional;

/**
 * Custom cause that is used in order to get the build callback just before the project/job is added into the
 * queue (at least I think this is the order of actions based on the debug logs).
 * <p>
 * Without this callback there was a problem with scheduling the project and getting back their latest build.
 * For some reason that was often returned as null during automated tests, and this callback fixes that
 * problem.
 */
public class BuildAddedCause extends Cause {

    private transient volatile Run<?, ?> build;

    @Override
    public String getShortDescription() {
        // we don't care about descriptions
        return "";
    }

    @Override
    public void onAddedTo(@NonNull Run build) {
        // this method is called before the items appear in jenkins queue
        // figured out via debug messages
        this.build = build;
    }

    public Optional<Run<?, ?>> getBuild() {
        return Optional.ofNullable(build);
    }
}
