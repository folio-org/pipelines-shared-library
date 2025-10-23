import hudson.util.Secret
import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.utilities.RestClient

void deleteOpenSearchIndices(String cluster, String namespace) {
  String opensearch_url = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_URL')
  String opensearch_username = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_USERNAME')
  String opensearch_password = kubectl.getSecretValue(namespace, 'opensearch-credentials', 'ELASTICSEARCH_PASSWORD')
  //TODO This is unsafe, we should change this approach after Jenkins migration
  String delete_indices_command = "curl -u ${opensearch_username}:${opensearch_password} -X DELETE ${opensearch_url}/${cluster}-${namespace}_*"

  kubectl.runPodWithCommand("${namespace}", 'curl', Constants.ECR_FOLIO_REPOSITORY + '/curl:7.88.1')
  kubectl.waitPodIsRunning("${namespace}", 'curl')
  kubectl.execCommand("${namespace}", 'curl', delete_indices_command)
  kubectl.deletePod("${namespace}", 'curl')
}

void deleteKafkaTopics(String cluster, String namespace) {
  String kafka_host = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_HOST')
  String kafka_port = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_PORT')
  String delete_topic_command = "kafka-topics.sh --bootstrap-server ${kafka_host}:${kafka_port} --delete --topic ${cluster}-${namespace}.*"

  kubectl.runPodWithCommand("${namespace}", 'kafka', Constants.ECR_FOLIO_REPOSITORY + '/kafka:3.5.0')
  kubectl.waitPodIsRunning("${namespace}", 'kafka')
  retry(3) {
    kubectl.execCommand("${namespace}", 'kafka', delete_topic_command)
  }
  kubectl.deletePod("${namespace}", 'kafka')
}

void stsKafkaLag(String cluster, String namespace, String tenantId) {
  folioHelm.withKubeConfig(cluster) {
    Logger logger = new Logger(this, 'CapabilitiesChecker')
    String kafka_host = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_HOST')
    String kafka_port = kubectl.getSecretValue(namespace, 'kafka-credentials', 'KAFKA_PORT')
    String lag = "kafka-consumer-groups.sh --bootstrap-server ${kafka_host}:${kafka_port} --describe --group ${cluster}-${namespace}-mod-roles-keycloak-capability-group | awk '\$6 ~ /^[0-9]+\$/ {print \$6}' "
    def status = sh(script: "kubectl get pod kafka-sh --ignore-not-found=true --namespace ${namespace}", returnStdout: true).trim()
    if (status == '') {
      kubectl.runPodWithCommand("${namespace}", 'kafka-sh', Constants.ECR_FOLIO_REPOSITORY + '/kafka:3.5.0', 'sleep 60m')
      kubectl.waitPodIsRunning("${namespace}", 'kafka-sh')
    }
    def check = kubectl.execCommand("${namespace}", 'kafka-sh', "${lag}")
    if (check.contains("0\n0")) {
      check = check.tokenize()[0]
    }
    // Check if the result is a valid number (0 or any other digits)
    if (check ==~ /^\d+$/) {
      while (check.toInteger() != 0) {
        logger.debug("Waiting for capabilities to be propagated on tenant: ${tenantId}")
        sleep time: 30, unit: 'SECONDS'
        check = kubectl.execCommand("${namespace}", 'kafka-sh', "${lag}")
        if (check.contains("0\n0")) {
          check = check.tokenize()[0]
        }
      }
    } else {
      logger.debug("Kafka lag value is not a valid number: ${check}")
      kubectl.deletePod("${namespace}", 'kafka-sh', false)
      stsKafkaLag(cluster, namespace, tenantId)
    }
    kubectl.deletePod("${namespace}", 'kafka-sh', false)
  }
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
  Constants.AWS_EKS_DEV_NAMESPACES.add('snapshot')
  Constants.AWS_EKS_DEV_NAMESPACES.add('snapshot2')
  if (project.trim() in Constants.AWS_EKS_DEV_NAMESPACES || project.trim() == 'sprint') {
    RestClient client = new RestClient(this)
    def members = getGitHubTeamsIds(teams)
    withCredentials([string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'RANCHER_TOKEN')]) {
      Map rancher_headers = ["Content-Type": "application/json", "Authorization": "Bearer ${env.RANCHER_TOKEN}"]
      def projects = getRancherProjectInfo(project)
      projects.each { projectInfo ->
        members.each { member ->
          client.post(Constants.RANCHER_API_URL + "/projectroletemplatebindings", ["roleTemplateId"  : project == 'sprint' ? 'read-only' : "project-member",
                                                                                   "projectId"       : projectInfo,
                                                                                   "groupPrincipalId": "github_team://" + member], rancher_headers)
        }
      }
    }
  } else {
    folioPrint.colored("Skipping adding teams to project members list for ${project}\nReason: ${project} is not in ${Constants.AWS_EKS_DEV_NAMESPACES}", "red")
  }
}

void deleteSSMParameters(String cluster, String namespace) {
  folioHelm.withK8sClient {
    def ssm_params = ""
    // Retry logic for AWS SSM describe-parameters to handle throttling
    retry(5) {
      try {
        ssm_params = sh(script: """aws ssm describe-parameters --parameter-filters "Key=Name,Option=Contains,Values=${cluster}-${namespace}_" --query Parameters[].Name --output text --region ${Constants.AWS_REGION}""", returnStdout: true).trim()
      } catch (Exception e) {
        if (e.getMessage().contains('ThrottlingException') || e.getMessage().contains('Rate exceeded')) {
          echo "AWS SSM throttling detected, retrying with exponential backoff..."
          sleep(time: (2 ** (currentBuild.getNumber() % 4)) * 5, unit: 'SECONDS') // Exponential backoff: 5, 10, 20, 40 seconds
          throw e // Re-throw to trigger retry
        } else {
          throw e // Re-throw non-throttling errors immediately
        }
      }
    }
    
    if (ssm_params) {
      int Limit = 5 // Reduced batch size to minimize throttling
      ssm_params.tokenize().collate(Limit).each { ssm_param ->
        def branches = [:]
        ssm_param.each { param ->
          branches[param.toString().trim()] = {
            retry(3) {
              try {
                sh(script: "aws ssm delete-parameter --name ${param.toString().trim()} --region ${Constants.AWS_REGION} || true", returnStdout: true)
              } catch (Exception e) {
                if (e.getMessage().contains('ThrottlingException') || e.getMessage().contains('Rate exceeded')) {
                  sleep(time: 3, unit: 'SECONDS')
                  throw e
                }
                // Ignore other errors (parameter not found, etc.)
              }
            }
          }
        }
        parallel branches
        sleep(10) // Increased sleep between batches to avoid throttling
      }
    } else {
      echo "No SSM parameters found for ${cluster}-${namespace}_"
    }
  }
}

void karateTenantsCleanUp(String cluster, String namespace) {
  String url = "https://${cluster}-${namespace}-keycloak.ci.folio.org/realms/master/protocol/openid-connect/token"
  String body = "grant_type=client_credentials&client_id=folio-backend-admin-client&client_secret=SecretPassword"
  def tokenResp = sh(script: "curl -s -X POST -d \"${body}\" \"${url}\"", returnStdout: true).trim()
  String token = readJSON(text: tokenResp).access_token

  String tenantsUrl = "https://${cluster}-${namespace}-kong.ci.folio.org/tenants?limit=500"
  def tenantsResp = sh(script: "curl -s -H 'Authorization: Bearer ${token}' '${tenantsUrl}'", returnStdout: true).trim()
  def tenantsJson = readJSON(text: tenantsResp)

  tenantsJson.tenants.each { tenantObj ->
    String tenantName = tenantObj.name
    String tenantId = tenantObj.id
    if (tenantName ==~ /(university|central|college|testtenant)[0-9]+$/) {
      println "Deleting tenant: ${tenantName} (ID: ${tenantId})"
      String deleteUrl = "https://${cluster}-${namespace}-kong.ci.folio.org/tenants/${tenantId}?purge=true"
      sh(script: "curl -s -X DELETE -H 'Authorization: Bearer ${token}' '${deleteUrl}'", returnStdout: false)
    }
  }
  folioPrint.colored("Karate tenants cleanup completed for cluster: ${cluster}, namespace: ${namespace}" as String, "green")
}

void karateTenantsCleanUpUnified(String kongURL, String keycloakURL, String clientId, String clientSecret) {
  String url = "${keycloakURL}/realms/master/protocol/openid-connect/token"
  String body = "grant_type=client_credentials&client_id=${clientId}&client_secret=${clientSecret}"
  def tokenResp = sh(script: "curl -s -X POST -d \"${body}\" \"${url}\"", returnStdout: true).trim()
  String token = readJSON(text: tokenResp).access_token

  String tenantsUrl = "${kongURL}/tenants?limit=500"
  def tenantsResp = sh(script: "curl -s -H 'Authorization: Bearer ${token}' '${tenantsUrl}'", returnStdout: true).trim()
  def tenantsJson = readJSON(text: tenantsResp)

  tenantsJson.tenants.each { tenantObj ->
    String tenantName = tenantObj.name
    String tenantId = tenantObj.id
    if (tenantName ==~ /(university|central|college|testtenant)[0-9]+$/) {
      println "Deleting tenant: ${tenantName} (ID: ${tenantId})"
      String deleteUrl = "${kongURL}/tenants/${tenantId}?purge=true"
      sh(script: "curl -s -X DELETE -H 'Authorization: Bearer ${token}' '${deleteUrl}'", returnStdout: false)
    }
  }
  folioPrint.colored("Karate tenants cleanup completed for env: ${kongURL}", "green")
}