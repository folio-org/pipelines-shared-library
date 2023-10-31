static String getOpenTicketsByTeam(String teamName, String projectName, String summary) {
  String jql = "Team = ${teamName} AND summary ~ ${summary}  AND status in (Open, 'In Progress')"
  return jql
}
