<p align="center">
    <img src="docs/_static/logo_dark_min.png" height="64">
</p>


## Introduction

DepBuilder is a Jenkins plugin for building project dependencies in a specific
order. The main idea behind the project is not to replace the currently
available plugins, but to improve the experience of defining the Jenkins build
pipelines that consist of existing jobs and plugins, speeding up your build
via automatic job parallelization and making the build process glancable
through build visualization. 

**Example:**

Imagine having 4 jobs (A, B, C, D) that you would like to build in a certain 
order. The build itself shouldn't take more than 30 minutes and the job C
should be built on a Windows machine called "windows_runner".

<p align="center">
    <img src="docs/docs/images/initialBuildDefinition_min.png" alt="Desired build pipeline definition" />
</p>

These requirements could be easily fulfilled through the use of DepBuilder DSL: 

```cpp

_BUILD {
    maxDuration: 00:30
}

C {
    agent: [windows_runner]
}

A -> B
B -> C
C -> D
```

After building the DepBuilder pipeline, you should be able to see a 
build graph on the pipeline's dashboard:

<p align="center">
    <img src="docs/docs/images/buildSuccess_min.png" alt="Graph of the projects that are part of the successfully built pipeline"/>
</p>


## Documentation

Documentation is located [here](https://docs.royalsloth.eu/depbuilder/latest/docs/000_intro.html).


## Installation 

For the installation instructions, check out the [Installation](https://docs.royalsloth.eu/depbuilder/latest/docs/004_installation.html) page.


## Pro

[DepBuilder Pro](https://www.royalsloth.eu/products/depbuilder/) is an extension of DepBuilder Community 
plugin that contains additional features (such as automatic parallel build of dependencies in the pipeline) 
and priority email support from the DepBuilder creators in case of any unforeseen
issues.

For a full list of features, see the [Features](https://docs.royalsloth.eu/depbuilder/latest/docs/001_features.html) page.


## License

DepBuilder Community version is open source, released under the AGPL-3.0 license.
See [License](LICENSE) for licensing details.
