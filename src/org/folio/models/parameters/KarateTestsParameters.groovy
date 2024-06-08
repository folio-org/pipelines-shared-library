package org.folio.models.parameters

import org.folio.testing.teams.TeamAssignment

class KarateTestsParameters {
  String okapiUrl

  String edgeUrl

  String tenant

  String prototypeTenant

  String adminUserName

  String adminPassword

  String modulesToTest = ''

  String threadsCount = '1'

  String gitBranch = 'master'

  String karateConfig = 'folio-testing-karate'

  String javaVerson = 'openjdk-17-jenkins-slave-all'

  String mavenVersion = 'maven3-jenkins-slave-all'

  String mavenSettings = 'folioci-maven-settings'

  String reportPortalProjectName

  String reportPortalProjectId

  TeamAssignment teamAssignment

  boolean syncWithJira = false

  boolean sendSlackNotification = false

  String timeout
}
