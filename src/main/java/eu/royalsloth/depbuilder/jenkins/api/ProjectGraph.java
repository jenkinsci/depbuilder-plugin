package eu.royalsloth.depbuilder.jenkins.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.List;

@SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "this is public api used by the frontend")
public class ProjectGraph {
    public String projectName = "";
    public long buildNumber = -1;

    public List<FinishedBuildJob> graphNodes = new ArrayList<>();
    public String duration = "";
    public String status = "";
    public boolean finished = false;

    public String error;
}
