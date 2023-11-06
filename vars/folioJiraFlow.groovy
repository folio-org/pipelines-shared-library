import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.RestClient

/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
*/

void createJiraTicket(String summary, String DevTeamId, String projectName, String type) {
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    String body = """{
    "fields": {
       "project": {"key": "${projectName}"},
       "summary": "${summary}",
       "description": "Automatic Jira ticket created via Jenkins",
       "customfield_10501": {"id":"${DevTeamId}"},
       "issuetype": {"name": "${type}"}
        }
    }"""
    def body_data = new JsonSlurperClassic().parseText(body)
    new RestClient(this).post(Constants.FOLIO_JIRA_ISSUE_URL, body_data, headers)
  }
}

def searchForExistingJiraTickets(String jql) {
  def existing_tickets = jiraIssueSelector(issueSelector: [$class: 'JqlIssueSelector', jql: "${jql}"])
  return existing_tickets
}

void addJiraComment(HashSet ticketNumbers, String body) {
  ticketNumbers.each { ticketNumber ->
    jiraComment body: "${body}", issueKey: "${ticketNumber}"
  }
}

void updateJiraTicket(String ticketNumber) {

}

void closeJiraTicket(String ticketNumber) {

}
