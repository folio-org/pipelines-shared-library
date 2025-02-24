package org.folio.jenkins

class PodTemplates {
  static final String BASE_AGENT = "base-agent"

  void java11Template(Closure body) {
//    body.setDelegate(this)
//    body.setResolveStrategy(Closure.DELEGATE_FIRST)

    podTemplate(inheritFrom: BASE_AGENT, label: 'java11-agent', containers: [
      containerTemplate(name: 'java', image: 'amazoncorretto:11-alpine-jdk')
    ]) {
      body.call()
    }
  }

  void java17Template(Closure body) {
    body.setDelegate(this)
    body.setResolveStrategy(Closure.DELEGATE_FIRST)

    podTemplate(inheritFrom: BASE_AGENT, label: 'java17-agent', containers: [
      containerTemplate(name: 'java', image: 'amazoncorretto:17-alpine-jdk')
    ]) {
      body.call()
    }
  }

  void java21Template(Closure body) {
    body.setDelegate(this)
    body.setResolveStrategy(Closure.DELEGATE_FIRST)

    podTemplate(inheritFrom: BASE_AGENT, label: 'java21-agent', containers: [
      containerTemplate(name: 'java', image: 'amazoncorretto:21-alpine-jdk')
    ]) {
      body.call()
    }
  }

  void stripesTemplate(Closure body) {
    body.setDelegate(this)
    body.setResolveStrategy(Closure.DELEGATE_FIRST)

    podTemplate(inheritFrom: BASE_AGENT, label: 'stripes-agent', containers: [
      containerTemplate(name: 'jnlp', resourceRequestMemory: '8Gi', resourceLimitMemory: '9Gi')
    ]) {
      body.call()
    }
  }
}
