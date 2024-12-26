import hudson.util.Secret
import org.folio.Constants
import org.folio.utilities.RestClient

void deleteOpenSearchIndices(String cluster, String namespace) {
  String opensearch_url = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_URL')
  String opensearch_username = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_USERNAME')
  String opensearch_password = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_PASSWORD')
  //TODO This is unsafe, we should change this approach after Jenkins migration
  String delete_indices_command = "curl -u ${opensearch_username}:${opensearch_password} -X DELETE ${opensearch_url}/${cluster}-${namespace}_*"

  kubectl.runPodWithCommand('curl', 'curlimages/curl:7.88.1')
  kubectl.waitPodIsRunning('curl')
  kubectl.execCommand('curl', delete_indices_command)
  kubectl.deletePod('curl')
}

void deleteKafkaTopics(String cluster, String namespace) {
  String kafka_host = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_HOST')
  String kafka_port = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_PORT')
  String delete_topic_command = "kafka-topics.sh --bootstrap-server ${kafka_host}:${kafka_port} --delete --topic ${cluster}-${namespace}.*"

  kubectl.runPodWithCommand("${namespace}", 'kafka', 'bitnami/kafka:2.8.0')
  kubectl.waitPodIsRunning('kafka')
  retry(3) {
    kubectl.execCommand('kafka', delete_topic_command)
  }
  kubectl.deletePod('kafka')
}

List getGitHubTeamsIds(String teams) {
  withCredentials([usernamePassword(credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID, passwordVariable: 'token', usernameVariable: 'username')]) {
    String url = "https://api.github.com/orgs/folio-org/teams?per_page=100"
    Map headers = ["Authorization": "Bearer ${token}"]
    List response = new RestClient(this).get(url, headers).body

    List ids = []
    teams.replaceAll("\\s", "").tokenize(',').each { team ->
      if (team != 'null') {
        try {
          ids.add(response.find { it["name"] == team }["id"])
        } catch (e) {
          println(e.getMessage())
        }
      }
    }
    return ids
  }
}

/**
 * Validate parameters map
 * @param params
 * @param excludeParams
 */
void validateParams(Map params, List excludeParams) {
  params.each { key, value ->
    def valToCheck
    if (value instanceof Secret) {
      valToCheck = value.getPlainText()
    } else {
      valToCheck = value
    }

    if (!excludeParams.contains(key) && (!valToCheck || valToCheck.trim() == '')) {
      error("Value for key '${key}' is missing or empty.")
    }
  }
}

/**
 * Evaluate groovy expression
 * @param expression groovy expression
 * @param parameters parameters
 * @return result
 */
static def eval(String expression, Map<String, Object> parameters) {
  Binding b = new Binding()
  parameters.each { k, v -> b.setVariable(k, v)
  }
  GroovyShell sh = new GroovyShell(b)
  return sh.evaluate(expression)
}

String generateRandomDigits(int length) {
  (1..length).collect { (int) (Math.random() * 10) }.join()
}

String getPipelineBranch() {
  return scm.branches[0].name - "*/"
}

String generateRandomString(int length) {
  return new Random().with { r ->
    List pool = ('a'..'z') + ('A'..'Z')
    (1..length).collect { pool[r.nextInt(pool.size())] }.join('')
  }
}

def getRancherProjectInfo(String project) {
  withCredentials([string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'RANCHER_TOKEN')]) {
    Map rancher_headers = ["Content-Type": "application/json", "Authorization": "Bearer ${env.RANCHER_TOKEN}"]
    return new RestClient(this).get("${Constants.RANCHER_API_URL}/projects?name=${project.trim()}", rancher_headers).body['data']['id']
  }
}

def addGithubTeamsToRancherProjectMembersList(String teams, String project) {
  if (project.trim() in Constants.AWS_EKS_DEV_NAMESPACES) {
    RestClient client = new RestClient(this)
    def members = getGitHubTeamsIds(teams)
    withCredentials([string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'RANCHER_TOKEN')]) {
      Map rancher_headers = ["Content-Type": "application/json", "Authorization": "Bearer ${env.RANCHER_TOKEN}"]
      def projects = getRancherProjectInfo(project)
      projects.each { projectInfo ->
        members.each { member ->
          client.post(Constants.RANCHER_API_URL + "/projectroletemplatebindings", ["roleTemplateId"  : "project-member",
                                                                                   "projectId"       : projectInfo,
                                                                                   "groupPrincipalId": "github_team://" + member], rancher_headers)
        }
      }
    }
  } else {
    folioPrint.colored("Skipping adding teams to project members list for ${project}\nReason: ${project} is not in ${Constants.AWS_EKS_DEV_NAMESPACES}", "red")
  }
}
