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

  String javaVerson = '21'

  String javaToolName = 'amazoncorretto-jdk'

  String mavenToolName = 'maven-3.9.9'

  String mavenSettings = 'folioci-maven-settings'

  String reportPortalProjectName

  String reportPortalProjectId

  TeamAssignment teamAssignment

  boolean syncWithJira = false

  boolean sendSlackNotification = false

  boolean sendTeamsSlackNotification = false

  boolean lsdi = false

  String timeout
}
