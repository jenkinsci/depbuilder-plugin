package eu.royalsloth.depbuilder.dsl.scheduling;

public class BuildCycleException extends Exception {
    public BuildCycleException(String msg) {
        super(msg);
    }

    public BuildCycleException(String msg, Throwable e) {
        super(msg, e);
    }
}
