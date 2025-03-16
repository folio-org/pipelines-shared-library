package org.folio.pipelines.modules.deployInstall


import org.folio.rest_v2.PlatformType
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

class Pipeline {
  static String JOB_NAME = "/v2/DevOps_Tools/modules/build_push"

  List parameters = []
  def context

  Pipeline(def context) {
    this.context = context
  }

  Pipeline withPlatformType(PlatformType type) {
    parameters.add(context.string(name: 'PLATFORM', value: type.name()))
    return this
  }

  Pipeline withModuleName(String name) {
    parameters.add(context.string(name: 'MODULE_NAME', value: name))
    return this
  }

  Pipeline withModuleBranch(String branch) {
    parameters.add(context.string(name: 'MODULE_BRANCH', value: branch))
    return this
  }

  Pipeline withMavenArgs(String args) {
    parameters.add(context.string(name: 'MAVEN_ARGS', value: args))
    return this
  }

  Pipeline doPushImage(boolean isPush) {
    parameters.add(context.booleanParam(name: 'PUSH_IMAGE_TO_ECR', value: isPush))
    return this
  }

  Pipeline doPushDescriptor(boolean isPush) {
    parameters.add(context.booleanParam(name: 'PUSH_DESCRIPTOR_TO_ECR', value: isPush))
    return this
  }

  Pipeline withAgent(String agent) {
    parameters.add(context.string(name: 'AGENT', value: agent))
    return this
  }

  RunWrapper run(boolean waitJobComplete = true, boolean propagateJobRunStatus = true) {
    return context.build(job: JOB_NAME, wait: waitJobComplete, propagate: propagateJobRunStatus, parameters: parameters)
  }
}
