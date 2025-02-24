package org.folio.jenkins

import org.folio.utilities.Logger

class PodTemplates {
  static final String BASE_AGENT = "base-agent"

  private Object steps

  private Logger logger

  PodTemplates(context) {
    this.steps = context
    this.logger = new Logger(context, 'PodTemplates')
  }

  void javaTemplate(String javaVersion, Closure body) {
    steps.podTemplate(inheritFrom: BASE_AGENT, label: 'java-agent',
      containers: [steps.containerTemplate(name: 'java', image: "amazoncorretto:${javaVersion}-alpine-jdk",
        command: 'sleep', args: '99d')]) {
      logger.info('\n' + '-' * 30 + "\nJava ${javaVersion} pod template in use!\n" + '-' * 30)
      body.call()
    }
  }

  void stripesTemplate(Closure body) {
    steps.podTemplate(inheritFrom: BASE_AGENT,
      containers: [steps.containerTemplate(name: 'jnlp', resourceRequestMemory: '8Gi', resourceLimitMemory: '9Gi')]) {
      body.call()
    }
  }
}
