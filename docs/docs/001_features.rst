.. include:: subs.rst

Features
====================


The |Product| plugin is currently available in 2 versions:

* **Community**: open source version with basic set of features.

* **Pro**: commercial version with additional features and support.

.. note:: For more info about the pricing of the |Product| Pro version, see |PricePage|


Overview
--------------------

.. |fa-tick| raw:: html

    <i class="fa fa-check"></i>

.. |fa-x| raw:: html

    <i class="fa fa-times"></i>


.. list-table::
   :widths: 20 5 5 70
   :header-rows: 1

   * - Feature
     - Community
     - Pro
     - Description
   * - DSL
     - |fa-tick|
     - |fa-tick|
     - Domain specific language for defining the build pipeline.
   * - SCM
     - |fa-tick|
     - |fa-tick|
     - Version control your pipeline build scripts.
   * - Cycle detection
     - |fa-tick|
     - |fa-tick|
     - Job dependency cycles in the pipeline are automatically detected and
       have to be resolved before the build is able to start.
   * - Build Visualization
     - |fa-tick|
     - |fa-tick|
     - Live visualization of the build pipeline
   * - Agent Selection
     - |fa-tick|
     - |fa-tick|
     - Define the Jenkins agent on which a specific job build will be executed.
   * - Build Termination
     - |fa-tick|
     - |fa-tick|
     - Terminate the build if it exceeds the specified duration.
   * - Parent Failure Action
     - |fa-tick|
     - |fa-tick|
     - When the parent job fails to build, it determines whether or not to continue the build.
   * - Parallel Builds
     - |fa-x|
     - |fa-tick|
     - Automatically build jobs in parallel
   * - Build Throttle
     - |fa-x|
     - |fa-tick|
     - Throttle the parallel builds depending on the time of the day.
   * - Support
     - |fa-x|
     - |fa-tick|
     - Support via email.


.. contents:: Contents:

.. _DSLSection:

DSL
----------

.. note:: If you prefer to learn through an example, feel free to skip to
   the :ref:`ExampleSection` section.

The |Product|'s domain specific language (DSL) is the central part of the plugin for
defining the build order of Jenkins jobs. The DSL is very strict and will
warn you about syntax errors, invalid build agents, typos in job names, cycles in your
build pipeline while nudging you into the right direction with the helpful error messages.


**Terminology:**

.. list-table::
   :widths: 15 85
   :header-rows: 1

   * - Term
     - Description

   * - Job
     - A build unit in Jenkins. A typical example of a job would be the FreeStyle build.

   * - Pipeline/Workflow
     - Output of the |Product|'s DSL that defines the specific order in which the
       jobs will be built.

   * - Jenkins Pipeline
     - A plugin that is part of the Jenkins core that allows you to create a complicated
       build process that consists of many steps.

   * - Agent
     - A build computer/node that is usually not a Jenkins master node.
       Jenkins build infrastructure consists of one or more master nodes that control
       the rest of non-master build computers/agents.


|

The |Product| DSL is usually split into 3 sections:

* **Pipeline settings (_BUILD)**: defines the settings of the pipeline build scheduler.
* **Job build settings**: defines the settings of the build jobs in the pipeline.
* **Build order**: defines the order of the build.


.. code-block:: cpp

    // Pipeline settings
    _BUILD {
       ...
    }

    // Job build settings
    backendProject {
        ...
    }

    // Build order
    //
    // The jobs on the left side of the arrow are parents of the projects
    // on the right side of the arrow:
    //
    // parent_project -> child_project
    backendProject -> integrationTests
    frontendProject -> integrationTests




.. note:: |Product| DSL allows comments that follow the C-style comment convention:

   .. code-block:: cpp

       // single line comment

       /*
          multi line comment
       */


.. _PipelineSettings:

Pipeline Settings (_BUILD)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Pipeline settings are defined within the ``_BUILD`` section in the DSL script.
The following table presents the possible options of the pipeline settings.

.. list-table:: Pipeline Settings (_BUILD section)
   :widths: 10 10 70
   :header-rows: 1

   * - Setting
     - Default
     - Description

   * - maxDuration
     - 2:00
     - Maximum duration of the pipeline build in ``hh:mm`` format. If the pipeline build
       duration exceeds the chosen maxDuration, the pipeline build will be aborted.

   * - buildThrottle :guilabel:`Pro`
     - 00:00|-1
     - In the case of parallel builds it may be desired to throttle the build at the
       certain hours of the day in order to give priority to other builds.

       The buildThrottle setting follows the ``hh:mm|#allowed weight`` format.
       Each build job defines its weight factor via job settings (see :ref:`jobBuildSettings` section).
       The weight factors sum of concurrently building jobs is always ``<= #allowed weight``

       Build scheduler will always build at least one job, even if:
       ``job weight factor > #allowed weight.``

       To disable the buildThrottling, set the number of executors to -1.


**Example**:

.. code-block:: cpp

    _BUILD {
        // the pipeline build will terminated after 5 hours and 30 minutes
        maxDuration: 5:30

        /*
         (PRO only):
         00:00 - at the start of the day the build throttling is turned off until
                 the first build throttling definition. In this case the builds are
                 are not throttled up until 12:15. You can turn it on by adding
                 00:00|<number> throttle definition.

         12:15 - we can build as many projects in parallel as long as the sum of
                 their weights is <= 3. That is true up until 16:30

         16:30 - we can build as many projects in parallel as long as the sum of
                 their weights is <= 12. That is true up until 20:00

         20:00 - after 20:00 there are no restrictions and we can run as many builds
                 as possible as long as we have free build executors on Jenkins.
                 That is true until 23:00.

         23:00 - we can build as many projects in parallel as long as the sum of
                 their weights is <= 5. That is true up until 24:00. At the start
                 of the new day (00:00), the build throttling is turned off.
        */
        buildThrottle: [12:15|3, 16:30|12, 20:00|-1, 23:00|5]
    }



.. _jobBuildSettings:

Job Build Settings
~~~~~~~~~~~~~~~~~~~~~~


Build settings for each build job in the pipeline is defined with the ``jobName {}`` section.
You can use the special ``_ALL`` identifier to define the build settings for all the build jobs
in the pipeline.

.. list-table:: Job Build Settings
   :widths: 10 10 70
   :header-rows: 1

   * - Setting
     - Default
     - Description

   * - agent
     - [any]
     - Defines the Jenkins build runner on which the build should be executed on.
       This option is usually used when we have a certain build constraints,
       such as the operating system on which the build should be ran on.

       It's possible to define more than one build runner::

        agent: [runner_1, runner_2, runner_3]

       If the agent is set to ``any``, the job will be executed on any
       Jenkins build node that has free executors (by default the agent with
       the highest number of free executors will be picked).

       If there is no build agent with free executors, the job will not be scheduled until
       one of the specified build agents finishes one task and
       at least one build executor becomes free.

   * - maxDuration
     - 2:00
     - Maximum duration of the job build in ``hh:mm`` format. If the duration of
       the job build exceeds the maxDuration, the job build will be automatically aborted.

   * - onParentFailure
     - ABORT
     - When the parent job of the job with this setting fails to build, the pipeline build
       could either continue building (``BUILD``) or abort the pipeline build (``ABORT``).

       Possible options::

        ABORT, BUILD

   * - weight :guilabel:`Pro`
     - 1
     - Defines how demanding building this specific job is. The weight factor is used when
       the jobs are build in parallel in order to throttle the pipeline build during
       the specific hours of the day where you may need more resources for other projects.

       If you know that a certain job is computationally expensive (e.g: you
       are building a large C++ project), you may want to increase its weight factor
       in order to throttle this specific pipeline build and give the rest of the jobs
       more resources.

       This weight factor is an arbitrary number that you can pick as you see fit.


**Example:**

.. code-block:: cpp

   // override the default build settings for all the build jobs
   // defined in the build order section
   _ALL {
      // if any parent job of the build job fails, the build should
      // continue building. This option could be used when the build
      // jobs in the pipeline do not depend on one another
      onParentFailure: BUILD

      // every job build in the pipeline should finish in less than 30 minutes,
      // otherwise that specific job will be aborted (just one job and not the
      // entire pipeline)
      maxDuration: 0:30
   }

   // override the default and _ALL build settings
   backendBuild {
      // any build agent with free executors will be able to build this job
      agent: [any]

      // the build of backendBuild should finish in less than 1h and 30 minutes
      // otherwise it will be aborted
      maxDuration: 1:30

      // if any parent job fails, this build will not run. The entire pipeline
      // build will be aborted once the builds that don't have a dependency on
      // the aborted parent project are done building.
      onParentFailure: ABORT

      // (PRO): defines how heavy the build is in the case the parallel
      // build throttling was turned on. It's an arbitrary number that
      // you can pick depending on your estimate of how demanding
      // building this job is.
      weight: 10
   }

   // defines the maxDuration for all 3 jobs overriding what was set
   // in the sections above
   frontendBuild, linterCheck, integrationTests {
       maxDuration: 1:30
   }

Build Order
~~~~~~~~~~~~~~~~~~~~~~

This section of |Product| DSL defines the build order of dependencies - existing Jenkins
build jobs. The |Product| does not restrict builds to a special Build type; any of the
existing Jenkins build types are suitable, such as:

* FreeStyleBuild
* Jenkins Pipeline
* Any other job created via your own custom plugin

**Example**:

.. code-block:: cpp

    // The build order in this case would be:
    //
    // 1. backendBuild, frontendBuild  (built in parallel if using PRO version)
    // 2. integrationTests
    // 3. deployment
    backendBuild     -> integrationTests
    frontendBuild    -> integrationTests
    integrationTests -> deployment

    // The order of job definitions is not important. The DepBuilder will
    // figure out the relations between the build jobs. The following
    // declaration would build the jobs in the same order as
    // in the example above:
    frontendBuild    -> integrationTests
    integrationTests -> deployment
    backendBuild     -> integrationTests


    // if the build job contains a space in the name, make sure to put
    // the quotes around the name, e.g:
    "my job name with spaces" -> integrationTests



.. note:: If the job's name contains spaces or special non-ascii characters, you have to declare it in quotes:

      .. code-block:: cpp

         "my name with spaces" -> jobWithNoSpaces

         "myFolder/myJobName"  -> jobWithNoSpaces


.. note:: Semicolons are optional end line delimiters, but are necessary in case
   you would like to put two unrelated statements on the same line, e.g:

      .. code-block:: cpp

         backendBuild -> integrationTests; frontendBuild -> integrationTests



.. _ExampleSection:

Examples
----------------------

.. code-block:: cpp

   _BUILD {
      maxDuration: 00:30
      buildThrottle: [09:00|1, 12:00|-1]
   }

   _ALL {
      // all jobs should be built either on runner_1 or runner_2
      agent: [runner_1, runner_2]
      // all jobs in the pipeline should be built in less than 10 minutes
      maxDuration: 00:10
   }

   backendBuild {
      // overrides settings from _ALL settings
      // overrides the default weight factor
      agent: [windows_runner]
      maxDuration: 00:20
      weight: 3;
   }

   frontendBuild, integrationTests {
      // inherits agents from _ALL settings (runner_1, runner_2)
      // inherits maxDuration from _ALL settings
      onParentFailure: ABORT
      weight: 2
   }

   backendBuild     -> integrationTests;
   frontendBuild    -> integrationTests;
   integrationTests -> deployment;

    /*
      -------------------------------------
      The following is true for our build:
      -------------------------------------

      1. backendBuild will be built on windows_runner

      2. If either backendBuild or frontendBuild fails, the build will
         fail and the integrationTests will never run

      3. The entire pipeline should be built in less than 30 minutes.
         The backend build should finish in less than 20 minutes.

      4. The frontendBuild and integrationTests will run on either runner_1
         or runner_2 as this setting is inherited from the _ALL block

      5. Each of the following [frontendBuild, integrationTests, deployment]
         should finish in less than 10 minutes.

      6. deployment job will have the weight factor of 1 (default) and will
         run on either runner_1 or runner_2 (inherited from _ALL block)

      (PRO):
      7. If using PRO account and the build ran 09:00 < time < 12:00 the
         backendBuild and frontendBuild wouldn't run in parallel due to
         buildThrottling set in place (the sum of parallel job weights
         should be <= 1).

         If the build started before 09:00 the backendBuild and frontendBuild
         would run in parallel.

         If the build started after 12:00 the backendBuild and frontendBuild
         would run in parallel.
    */



SCM
-----------------------

|Product| supports using source control management systems for storing your pipeline build scripts.
See :ref:`ui-scm` page for more info on how to correctly set up a build that uses SCM.

.. note:: SCM is the recommended way of storing your build pipelines.


Parallel Builds
----------------------------------------

:guilabel:`Pro` The |Product| Pro version contains a build scheduler that will try to automatically schedule
new job builds in parallel in order to make the most out of your infrastructure. The number of
jobs builds that can run in parallel are only limited by:


* **Number of free Jenkins executors**: since every job is built by one Jenkins executor,
  the maximum number of builds running in parallel is the number of all executors
  provided by Jenkins build agents. To increase the number of
  possible parallel builds you have to either increase the number of executors of existing
  Jenkins build agents or deploy additional Jenkins build agents.

* **Build throttle defined in scheduler settings**: number of allowed build weights that
  can run in parallel during the specified hour of the day. This number is defined by the
  user in order to waste all build executors just for one pipeline.

  For example: during the day you might want to allocate more resources for jobs that are
  being built after every commit as opposed to the builds of the entire pipeline
  which are more suitable for nightly builds.

For more info about build parallelization you can read the documentation and the example
in the :ref:`PipelineSettings` section.



Sharing the Build Artifacts
--------------------------------------

Large projects usually consists of complicated build pipelines and libraries that
are shared across the build jobs in the pipeline. Since we would often like
to share the build artifacts between the parent-child jobs in the pipeline,
this situation is best explained with a specific example.

**Example:** We would like to build the job A and job B in the specified order below.
The build of job A also produces a library file which is a prerequisite for building
the project B.

.. code-block:: cpp

    // A is the parent job of B (B is a child of A, since it depends on the
    // library artifact produced by the job A)
    //
    // In other words, first we build the project A and after the A has
    // successfully finished building, we can start building the project B
    A -> B


Currently, there are two ways of sharing the build artifacts between the jobs in
the build pipeline:

* **Use the same agent**: you can store the build artifacts directly on the build agent's
  filesystem. In our case the job A would store the build artifacts at the end of the build
  and the job B would pull the artifacts from the same file system location before the start
  of the build. The |Product| pipeline would look like:

  .. code-block:: cpp

      A, B {
          agent: [runner_1]
      }

      A -> B


* **Use the artifactory**: if you have the access to the build artifactory or a private
  "cloud" instance the build agents do not have to be defined in the script. Each parent job
  has to publish the build artifacts to the artifactory at the end of the build and
  each child job has to download the artifacts before the start of the build (you can
  use the Jenkins build/post-build actions). In this case the |Product| pipeline would look like:

  .. code-block:: cpp

    A -> B


  By default the |Product| is terminating the build if one of the parents for the child
  job were not built successfully, so there is no fear of building the project B with the
  old artifacts coming from the project A.

  If you would prefer to build the projects in the pipeline even if they use the old artifacts,
  you can change the default ``onParentFailure`` behaviour:

  .. code-block:: cpp

    _ALL {
        onParentFailure: BUILD
    }

    A -> B


