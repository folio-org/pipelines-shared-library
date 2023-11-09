import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.RestClient

/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
*/

Map status = [
  "Open"          : 81,
  "Closed"        : 61,
  "Blocked"       : 71,
  "In progress"   : 21,
  "In review"     : 31,
  "In code review": 91
]

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
  def issues
  String search_url = "https://issues.folio.org/rest/api/2/search?jql=${jql}"
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    def response = new RestClient(this).get(search_url, headers)
    issues = response.body.issues.key
  }
  return issues
}

void addJiraComment(String ticketNumber, String comment) {
  String commentUrl = Constants.FOLIO_JIRA_ISSUE_URL + "${ticketNumber}/comment"
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    String body = """
      body: ${comment}
      """
    def comment_body = new JsonSlurperClassic().parseText(body)
    new RestClient(this).post(commentUrl, headers, comment_body)
  }
}

void addLabelJiraTicket(String ticketNumber, List labels) {
  String labelUrl = Constants.FOLIO_JIRA_ISSUE_URL + "${ticketNumber}"
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    labels.each { label ->
      String body = """
      { "update": { "labels": [{ "add" : "${label}"}]}}
      """
      def body_json = new JsonSlurperClassic().parseText(body)
      new RestClient(this).put(labelUrl, headers, body_json)
    }
  }
}

void updateStatusJiraTicket(List ticketNumbers) {


}

void closeJiraTicket(String ticketNumber) {

}
