package org.folio.jenkins

import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge
import org.folio.utilities.Logger

/**
 * PodTemplates class is responsible for defining pod templates for Jenkins pipeline.
 * It provides a set of methods to define pod templates for different types of agents.
 * The class is intended to be used in Jenkinsfile to define pod templates for different stages of the pipeline.
 * Based on Jenkins documentation:
 * https://plugins.jenkins.io/kubernetes/#plugin-content-nesting-pod-templates
 * https://www.jenkins.io/doc/pipeline/steps/kubernetes/
 * https://github.com/jenkinsci/kubernetes-plugin/tree/master/src/main/java/org/csanchez/jenkins/plugins/kubernetes
 **/
class PodTemplates {
  private static final String CLOUD_NAME = 'folio-tmp'
  private static final String NAMESPACE = 'jenkins-agents'
  private static final String SERVICE_ACCOUNT = 'jenkins-agent-sa'

  private Object steps

  private boolean debug

  private Logger logger

  PodTemplates(context, debug = false) {
    this.steps = context
    this.debug = debug
    this.logger = new Logger(context, 'PodTemplates')
  }

  void defaultTemplate(Closure body) {
    steps.podTemplate(
      cloud: CLOUD_NAME,
      label: 'default-agent',
      namespace: NAMESPACE,
      serviceAccount: SERVICE_ACCOUNT,
      nodeUsageMode: 'EXCLUSIVE',
      showRawYaml: debug,
      yamlMergeStrategy: new Merge(),
      podRetention: new Never(),
      workspaceVolume: steps.emptyDirWorkspaceVolume(),
      inheritYamlMergeStrategy: true,
      slaveConnectTimeout: 300,
      hostNetwork: false,
      containers: [steps.containerTemplate(
        name: 'jnlp',
        image: '732722833398.dkr.ecr.us-west-2.amazonaws.com/folio-jenkins-agent:latest',
        ttyEnabled: true,
        alwaysPullImage: true,
        workingDir: '/home/jenkins/agent'
      )]
    ) {
      body.call()
    }
  }

  void javaTemplate(String javaVersion, Closure body) {
    defaultTemplate {
      steps.podTemplate(label: 'java-agent', showRawYaml: debug,
        containers: [
          steps.containerTemplate(
            name: 'java',
            image: "amazoncorretto:${javaVersion}-alpine-jdk",
            command: 'sleep',
            args: '99d')]
      ) {
        logger.info("Java version: ${javaVersion}")
        body.call()
      }
    }
  }

  void stripesTemplate(Closure body) {
    defaultTemplate {
      steps.podTemplate(label: 'stripes-agent', showRawYaml: debug,
        containers: [
          steps.containerTemplate(
            name: 'jnlp',
            image: '732722833398.dkr.ecr.us-west-2.amazonaws.com/folio-jenkins-agent:latest',
            resourceRequestMemory: '8Gi',
            resourceLimitMemory: '9Gi')]
      ) {
        body.call()
      }
    }
  }

  void kanikoTemplate(Closure body) {
    defaultTemplate {
      steps.podTemplate(label: 'kaniko-agent', showRawYaml: debug,
        containers: [
          steps.containerTemplate(
            name: 'kaniko',
            image: 'gcr.io/kaniko-project/executor:debug',
            command: 'sleep',
            args: '99d')]
      ) {
        body.call()
      }
    }
  }

  /**
   * To be implemented in scope of cypress tests adoption
   */
  void cypressTemplate(Closure body) {}
}
