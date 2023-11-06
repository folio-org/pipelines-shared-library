import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.plugins.jira.JiraSite
import org.folio.Constants
import org.folio.utilities.RestClient

/*
***folioRancher general library for processing existing and new entries***
* Functionality:
* CRUD operations for Jira: tickets & comments.
*/

JsonOutput JsonOutput = new JsonOutput()
JsonSlurperClassic JsonSlurperClassic = new JsonSlurperClassic()

JiraSite jira = new JiraSite(Constants.FOLIO_JIRA_URL)






//Working variant for searching existing tickets.
void createJiraTicket(String summary, String DevTeam, String projectName){
  withCredentials([usernamePassword(credentialsId: 'jenkins-jira', passwordVariable: 'jira_password', usernameVariable: 'jira_user')]) {
    RestClient restWorker = new RestClient(this)
    Map headers = [
      "Content-Type": "application/json",

    ]
    restWorker.post(Constants.FOLIO_JIRA_ISSUE_URL)
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
