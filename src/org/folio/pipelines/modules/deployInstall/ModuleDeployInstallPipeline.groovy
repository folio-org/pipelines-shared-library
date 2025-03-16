package org.folio.pipelines.modules.deployInstall


import org.folio.rest_v2.PlatformType
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

class ModuleDeployInstallPipeline {
  static String JOB_NAME = "/v2/DevOps_Tools/modules/deploy_install"

  List parameters = []
  def context

  ModuleDeployInstallPipeline(def context) {
    this.context = context
  }

  ModuleDeployInstallPipeline withPlatformType(PlatformType type) {
    parameters.add(context.string(name: 'PLATFORM', value: type.name()))
    return this
  }

  ModuleDeployInstallPipeline withCluster(String cluster) {
    parameters.add(context.string(name: 'CLUSTER', value: cluster))
    return this
  }

  ModuleDeployInstallPipeline withNamespace(String namespace) {
    parameters.add(context.string(name: 'NAMESPACE', value: namespace))
    return this
  }

  ModuleDeployInstallPipeline withInstallJson(String json) {
    parameters.add(context.text(name: 'INSTALL_JSON', value: json))
    return this
  }

  ModuleDeployInstallPipeline withConfigType(String type) {
    parameters.add(context.string(name: 'CONFIG_TYPE', value: type))
    return this
  }

  ModuleDeployInstallPipeline doDeploy(boolean isDeploy) {
    parameters.add(context.booleanParam(name: 'DEPLOY', value: isDeploy))
    return this
  }

  ModuleDeployInstallPipeline doInstall(boolean isInstall) {
    parameters.add(context.booleanParam(name: 'INSTALL', value: isInstall))
    return this
  }

  ModuleDeployInstallPipeline withAgent(String agent = 'rancher') {
    parameters.add(context.string(name: 'AGENT', value: agent))
    return this
  }

  RunWrapper run(boolean waitJobComplete = true, boolean propagateJobRunStatus = true) {
    return context.build(job: JOB_NAME, wait: waitJobComplete, propagate: propagateJobRunStatus, parameters: parameters)
  }
}
