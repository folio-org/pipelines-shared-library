import org.folio.Constants

String getOpenKarateTcikets(String moduleName){
  String jql = "summary ~ ${moduleName} AND labels in (karateRegressionPipeline)"
  return jql
}

String getOpenTickets(String summary, String team){
  String jql = """
summary ~ ${summary} AND "Development Team" = ${team} AND status in (Active,"In progress","In Review","In Code Review") AND project = ${Constants.DM_JIRA_PROJECT}"
  """
  return jql
}

String getTicketInfo(String ticketNumber){
  String jql = """
issue=${ticketNumber}
"""
  return jql
}
