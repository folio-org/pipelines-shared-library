package org.folio.pipelines.modules.buildPush

import org.folio.rest_v2.PlatformType
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

class ModuleBuildPushPipeline {
  static String JOB_NAME = "/folioDevTools/moduleDeployment/buildAndPushModule"

  List parameters = []
  def context

  ModuleBuildPushPipeline(def context) {
    this.context = context
  }

  ModuleBuildPushPipeline withPlatformType(PlatformType type) {
    parameters.add(context.string(name: 'PLATFORM', value: type.name()))
    return this
  }

  ModuleBuildPushPipeline withModuleName(String name) {
    parameters.add(context.string(name: 'MODULE_NAME', value: name))
    return this
  }

  ModuleBuildPushPipeline withModuleBranch(String branch) {
    parameters.add(context.string(name: 'MODULE_BRANCH', value: branch))
    return this
  }

  ModuleBuildPushPipeline withMavenArgs(String args) {
    parameters.add(context.string(name: 'MAVEN_ARGS', value: args))
    return this
  }

  ModuleBuildPushPipeline doPushImage(boolean isPush) {
    parameters.add(context.booleanParam(name: 'PUSH_IMAGE_TO_ECR', value: isPush))
    return this
  }

  ModuleBuildPushPipeline doPushDescriptor(boolean isPush) {
    parameters.add(context.booleanParam(name: 'PUSH_DESCRIPTOR_TO_ECR', value: isPush))
    return this
  }

  ModuleBuildPushPipeline withAgent(String agent) {
    parameters.add(context.string(name: 'AGENT', value: agent))
    return this
  }

  RunWrapper run(boolean waitJobComplete = true, boolean propagateJobRunStatus = true) {
    return context.build(job: JOB_NAME, wait: waitJobComplete, propagate: propagateJobRunStatus, parameters: parameters)
  }
}
