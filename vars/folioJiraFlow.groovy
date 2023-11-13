import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.rest_v2.Common
import org.folio.utilities.RestClient
import org.folio.utilities.Tools

/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
*/

void createJiraTicket(String summary, String DevTeamId, String type) {
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    String body = """{
    "fields": {
       "project": {"key": "${Constants.DM_JIRA_PROJECT}"},
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
  def issuesData
  String search_url = "https://issues.folio.org/rest/api/2/search?jql=${jql}"
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    def response = new RestClient(this).get(search_url, headers)
    issuesData = response["body"]
  }
  return issuesData
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

void moveJiraTicket(List ticketNumbers, String moveId){
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
    ticketNumbers.each { ticket ->
      String labelUrl = Constants.FOLIO_JIRA_ISSUE_URL + "${ticket}/transitions"
      String body = """
      {"transition": {"id": "${moveId}"}}
      """
      def body_json = new JsonSlurperClassic().parseText(body)
      new RestClient(this).put(labelUrl, headers, body_json)
    }
  }
}

void processJiraTicketStatus(String summary) {
  new Tools(this).copyResourceFileToWorkspace('jiraFlow/teamsInfo.json')
  def teamsInfo = new JsonSlurperClassic().parseText(readJSON file: "teamsInfo.json")
  withCredentials([string(credentialsId: 'JiraFlow', variable: 'JiraToken')]) {
    Map headers = [
      "Content-Type" : "application/json",
      "Authorization": "Bearer ${env.JiraToken}"
    ]
//Below map is written for future use. PLEASE DO NOT DELETE!
    Map status = [
      "Open"          : 81,
      "Closed"        : 61,
      "Blocked"       : 71,
      "In progress"   : 21,
      "In review"     : 31,
      "In code review": 91
    ]
    String updateUrl = Constants.FOLIO_JIRA_ISSUE_URL + "${ticket}/transitions"
    teamsInfo.each { -> team
      String body
      def ticketNumbers = new JsonSlurperClassic().parseText(searchForExistingJiraTickets(jiraFlowJQL.getOpenTickets(summary, "${team.keySet().first()}")))
      if (ticketNumbers["total"] != 0) {
        ticketNumbers["issues"].each { ticket ->
          switch (ticket["fields"]["status"]["name"]) {
            case "Open":
              new Common(this, "https://fakeUrl").logger.info("Similar ticket already exists, summary: ${summary}")
              addJiraComment("${ticket[key]}","This issue is still active as of " + new Date().format("MM/dd/YYYY"))
              break
            case "Closed":
              if(ticket["fields"]["summary"].contains(summary)) {
                createJiraTicket(summary, team["${team.keySet().first()}"]["info"]["id"], "Task")
                addJiraComment("${ticket[key]}", "This issue opened on " + new Date().format("MM/dd/YYYY"))
              }
              break
            case "In progress":
              addJiraComment("${ticket[key]}","This issue is still active as of " + new Date().format("MM/dd/YYYY"))
              break
            case "In review":
              addJiraComment("${ticket[key]}","This issue is still active as of " + new Date().format("MM/dd/YYYY"))
              break
            default:
              new Common(this, "https://fakeUrl").logger.warning("No suitable state was supplied to flow...\nClosing this ${ticket[key]} ticket...")
              moveJiraTicket("${ticket[key]}", "61")
              break
          }
        }
      } else {
        new Common(this, "https://fakeUrl").logger.info("No active Jira ticket found for ${ticket["fields"]["customfield_10501"]["value"]} team.")
      }
    }
  }
}
