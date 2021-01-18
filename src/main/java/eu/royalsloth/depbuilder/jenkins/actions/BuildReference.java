package eu.royalsloth.depbuilder.jenkins.actions;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Simple DTO that is used for storing build data into project build.xml file. XStreamAlias is used to avoid
 * persisting full class path name for every element in the build.
 */
@XStreamAlias("RSBuildReference")
class BuildReference {
    public String projectName;
    public int buildNumber;
}
