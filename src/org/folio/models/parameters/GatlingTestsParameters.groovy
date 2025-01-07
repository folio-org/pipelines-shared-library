package org.folio.models.parameters

class GatlingTestsParameters {
  String modulesToTest = ''

  String envType = 'dev'

  String gitBranch = 'master'

  String javaVersion = 'openjdk-17-jenkins-slave-all'

  String mavenVersion = 'maven3-jenkins-slave-all'

  String mavenSettings = 'folioci-maven-settings'

  String timeout
}
