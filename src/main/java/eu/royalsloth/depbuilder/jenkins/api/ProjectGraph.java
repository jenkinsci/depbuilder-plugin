package eu.royalsloth.depbuilder.jenkins.api;

import java.util.ArrayList;
import java.util.List;

public class ProjectGraph {
    public String projectName = "";
    public long buildNumber = -1;

    public List<FinishedBuildJob> graphNodes = new ArrayList<>();
    public String duration = "";
    public String status = "";
    public boolean finished = false;

    public String error;
}
