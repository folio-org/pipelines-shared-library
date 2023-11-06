import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.RestClient

import groovy.json.JsonOutput

JsonOutput.prettyPrint()

/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
*/

void createJiraTicket(String summary, String DevTeam, String projectName, String type){
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type": "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    String body = """{
    "fields": {
       "project":
       {"key": "${projectName}"},
       "summary": "${summary}",
       "description": "Automatic Jira ticket created via Jenkins",
       "Development Team": "${DevTeam}",
       "issuetype": {
          "name": "${type}"
       }
   }
}"""
    new RestClient(this).post(Constants.FOLIO_JIRA_ISSUE_URL, body, headers)
  }
}

def searchForExistingJiraTickets(String jql){
  def existing_tickets = jiraIssueSelector(issueSelector: [$class: 'JqlIssueSelector', jql: "${jql}"])
  return existing_tickets
}

void addJiraComment(HashSet ticketNumbers, String body){
  ticketNumbers.each { ticketNumber ->
    jiraComment body: "${body}", issueKey: "${ticketNumber}"
  }
}

void updateJiraTicket(String ticketNumber){

}

void closeJiraTicket(String ticketNumber){

}
