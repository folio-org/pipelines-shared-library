static String getOpenTicketsByTeam(String teamName, String projectName, String summary) {
  String jql = "Team = ${teamName} AND summary ~ ${summary}  AND status in (Open, 'In Progress')"
  return jql
}

static String getOpenKarateTcikets(String moduleName){
  String jql = "summary ~ "${moduleName}" AND labels in (karateRegressionPipeline)"
  return jql
}

static String getOpenKitFoxTickets(String summary){
  String jql = "summary ~ "${summary}" AND 'Development Team' = Kitfox AND status in (Backlog, Active, 'In progress')"
  return jql
}
