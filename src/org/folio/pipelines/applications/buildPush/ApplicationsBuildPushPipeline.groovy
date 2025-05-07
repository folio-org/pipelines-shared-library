package org.folio.pipelines.applications.buildPush

import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

class ApplicationsBuildPushPipeline {
  static String JOB_NAME = "/folioDevTools/moduleDeployment/buildAndPushApplication"

  enum SourceType {
    REPOSITORY,
    DESCRIPTOR,
    TEMPLATE
  }

  List parameters = []
  def context

  ApplicationsBuildPushPipeline(def context) {
    this.context = context
  }

  ApplicationsBuildPushPipeline doFromRepository(boolean doFrom) {
    parameters.add(context.booleanParam(name: 'FROM_REPOSITORY', value: doFrom))
    return this
  }

  ApplicationsBuildPushPipeline withApplicationSet(String set) {
    parameters.add(context.string(name: 'APPLICATION_SET', value: set))
    return this
  }

  ApplicationsBuildPushPipeline withApplication(String name) {
    parameters.add(context.string(name: 'APPLICATION', value: name))
    return this
  }

  ApplicationsBuildPushPipeline withApplicationBranch(String branch) {
    parameters.add(context.string(name: 'APPLICATION_BRANCH', value: branch))
    return this
  }

  ApplicationsBuildPushPipeline doFromDescriptor(boolean doFrom) {
    parameters.add(context.booleanParam(name: 'FROM_DESCRIPTOR', value: doFrom))
    return this
  }

  ApplicationsBuildPushPipeline doRecreateDescriptor(boolean isRecreate) {
    parameters.add(context.booleanParam(name: 'RECREATE', value: isRecreate))
    return this
  }

  ApplicationsBuildPushPipeline doUpgradeDescriptor(boolean isUpgrade) {
    parameters.add(context.booleanParam(name: 'UPGRADE', value: isUpgrade))
    return this
  }

  ApplicationsBuildPushPipeline doFromTemplate(boolean doFrom) {
    parameters.add(context.booleanParam(name: 'FROM_TEMPLATE', value: doFrom))
    return this
  }

  ApplicationsBuildPushPipeline withAppName(String name) {
    parameters.add(context.string(name: 'APP_NAME', value: name))
    return this
  }

  ApplicationsBuildPushPipeline withAppDescription(String description) {
    parameters.add(context.string(name: 'APP_DESCRIPTION', value: description))
    return this
  }

  ApplicationsBuildPushPipeline withVersion(String version) {
    parameters.add(context.string(name: 'VERSION', value: version))
    return this
  }

  ApplicationsBuildPushPipeline withDescriptorURL(String url) {
    parameters.add(context.string(name: 'DESCRIPTOR_URL', value: url))
    return this
  }

  ApplicationsBuildPushPipeline withTemplate(String json) {
    parameters.add(context.text(name: 'TEMPLATE', value: json))
    return this
  }

  ApplicationsBuildPushPipeline withInstallJson(String json) {
    parameters.add(context.text(name: 'INSTALL_JSON', value: json))
    return this
  }

  ApplicationsBuildPushPipeline doPushDescriptor(boolean isPush) {
    parameters.add(context.booleanParam(name: 'PUSH_DESCRIPTOR_TO_REGISTRY', value: isPush))
    return this
  }

  ApplicationsBuildPushPipeline withAgent(String agent) {
    parameters.add(context.string(name: 'AGENT', value: agent))
    return this
  }

  RunWrapper run(boolean waitJobComplete = true, boolean propagateJobRunStatus = true) {
    return context.build(job: JOB_NAME, wait: waitJobComplete, propagate: propagateJobRunStatus, parameters: parameters)
  }
}
