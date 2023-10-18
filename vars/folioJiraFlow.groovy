import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.folio.utilities.RestClient
/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
 */

RestClient RestClient = new RestClient(this)
JsonOutput JsonOutput = new JsonOutput()
JsonSlurperClassic JsonSlurperClassic = new JsonSlurperClassic()

withCredentials([usernamePassword(credentialsId: 'jenkins-jira', passwordVariable: 'jira_password', usernameVariable: 'jira_username')]) {
  Map JiraCredentials = [
    username: "${env.jira_username}",
    password: "${env.jira_password}"
  ]
  return JiraCredentials
}
