package org.folio.jenkins

import org.folio.utilities.Logger

class PodTemplates {
  static final String BASE_AGENT = "base-agent"

  private Object steps

  private boolean debug

  private Logger logger

  PodTemplates(context, debug = false) {
    this.steps = context
    this.debug = debug
    this.logger = new Logger(context, 'PodTemplates')
  }

  void javaTemplate(String javaVersion, Closure body) {
    steps.podTemplate(inheritFrom: BASE_AGENT, label: 'java-agent', showRawYaml: debug,
      containers: [steps.containerTemplate(name: 'java', image: "amazoncorretto:${javaVersion}-alpine-jdk",
        command: 'sleep', args: '99d')]) {
      body.call()
    }
  }

  void stripesTemplate(Closure body) {
    steps.podTemplate(inheritFrom: BASE_AGENT, label: 'stripes-agent', showRawYaml: debug,
      containers: [steps.containerTemplate(name: 'jnlp', resourceRequestMemory: '8Gi', resourceLimitMemory: '9Gi')]) {
      body.call()
    }
  }

  /**
   * To be implemented in scope of cypress tests adoption
   */
  void cypressTemplate(Closure body) {}
}
