static String getOpenKarateTcikets(String moduleName){
  String jql = "summary ~ ${moduleName} AND labels in (karateRegressionPipeline)"
  return jql
}

static String getOpenTickets(String summary, String project, String team){
  String jql = """
  summary ~ ${summary} AND "Development Team" = ${team} AND status in (Active,"In progress","In Review","In Code Review") AND project = ${project}"
  """
  return jql
}
