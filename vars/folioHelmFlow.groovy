import groovy.json.JsonOutput
import hudson.AbortException
import org.folio.Constants
import org.folio.models.LdpConfig
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.rest_v2.Authorization
import org.folio.utilities.RestClient

import java.time.LocalDateTime

void deployGreenmail(String namespace) {
  addHelmRepository(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL, false)
  upgrade("greenmail", namespace, '', Constants.FOLIO_HELM_HOSTED_REPO_NAME, "greenmail")
}

void deployMockServer(RancherNamespace ns) {
  Map values = [:]
  String version = "5.15.0"
  String MOCK_SERVER_REPO_NAME = 'mockserver'
  String MOCK_SERVER_REPO_URL = 'https://www.mock-server.com'
  String configFileUrl = Constants.FOLIO_GITHUB_RAW_URL + "/folio-integration-tests/master/mod-inn-reach/" + "src/main/resources/volaris/mod-inn-reach/mocks/general/mockserverInitialization.json"

  // Define file paths
  String valuesFilePath = "./values/mockserver.yaml"
  String configFilePath = "./initializerJson.json"
  String propertiesFilePath = "./mockserver.properties"

  // Loading resource mockserver.properties into Jenkins workspace
  writeFile file: propertiesFilePath, text: libraryResource("mockserver/mockserver.properties")

  // Loading Expectation Initializer JSON into Jenkins workspace
  try {
    def response = httpRequest(url: configFileUrl, httpMode: 'GET')
    writeFile file: configFilePath, text: response.getContent()
  } catch (Exception e) {
    throw new AbortException("Error downloading or writing the file: ${e.getMessage()}")
  }

  // Creating a configMap with mockserver.properties and initializerJson.json
  if (kubectl.checkKubernetesResourceExist('configmap', 'mockserver-config', ns.getNamespaceName())) {
    kubectl.createConfigMap('mockserver-config', ns.getNamespaceName(), [configFilePath, propertiesFilePath])
  } else {
    kubectl.patchConfigMap('mockserver-config', ns.getNamespaceName(), [configFilePath, propertiesFilePath])
  }

  // Generating values file
  values << [image         : [tag: version],
             app           : [mountConfigMap: true],
             podAnnotations: [creationTimestamp: "\"${LocalDateTime.now().withNano(0).toString()}\""],
             ingress       : [enabled    : true,
                              hosts      : [[host : "${ns.getClusterName()}-${ns.getNamespaceName()}-mockserver.ci.folio.org",
                                             paths: [[path    : '/*',
                                                      pathType: "ImplementationSpecific"]]]],
                              annotations: ["alb.ingress.kubernetes.io/group.name"      : "${ns.getClusterName()}.${ns.getNamespaceName()}",
                                            "kubernetes.io/ingress.class"               : "alb",
                                            "alb.ingress.kubernetes.io/healthcheck-path": "/mockserver/dashboard",
                                            "alb.ingress.kubernetes.io/listen-ports"    : '[{"HTTPS":443}]',
                                            "alb.ingress.kubernetes.io/scheme"          : "internet-facing",
                                            "alb.ingress.kubernetes.io/success-codes"   : "200-399"]]]
  writeYaml file: valuesFilePath, data: values

  // Add Helm repository and upgrade
  folioHelm.addHelmRepository(MOCK_SERVER_REPO_NAME, MOCK_SERVER_REPO_URL, false)
  folioHelm.upgrade("mockserver", ns.namespaceName, valuesFilePath, MOCK_SERVER_REPO_NAME, "mockserver")
}

void deployLdp(RancherNamespace namespace) {
  String namespaceName = namespace.getNamespaceName()
  String dbHost = kubectl.getSecretValue(namespaceName, 'db-credentials', 'DB_HOST')
  int dbPort = kubectl.getSecretValue(namespaceName, 'db-credentials', 'DB_PORT').toInteger()
  String dbName = kubectl.getSecretValue(namespaceName, 'db-credentials', 'DB_DATABASE')
  String dbSuperUsername = kubectl.getSecretValue(namespaceName, 'db-credentials', 'DB_USERNAME')
  String dbSuperPassword = kubectl.getSecretValue(namespaceName, 'db-credentials', 'DB_PASSWORD')
  String tenantId = namespace.getDefaultTenantId()
  OkapiTenant tenant = namespace.getTenants()[tenantId]
  LdpConfig ldpConfig = tenant.getOkapiConfig().getLdpConfig()

  Map ldpDatabase = [database_name          : ldpConfig.getLdpDbName(),
                     database_host          : dbHost,
                     database_port          : dbPort,
                     database_user          : ldpConfig.getLdpAdminDbUserName(),
                     database_password      : ldpConfig.getLdpAdminDbUserPassword(),
                     database_super_user    : dbSuperUsername,
                     database_super_password: dbSuperPassword,
                     database_sslmode       : "disable"]

  Map sources = [(tenantId): [okapi_tenant            : tenantId,
                              direct_database_host    : dbHost,
                              direct_database_port    : dbPort,
                              direct_database_name    : dbName,
                              direct_database_user    : dbSuperUsername,
                              direct_database_password: dbSuperPassword]]

  Map ldpConfigContent = [deployment_environment: 'development',
                          anonymize             : false,
                          ldp_database          : ldpDatabase,
                          enable_sources        : [tenantId],
                          sources               : sources]

  Authorization auth = new Authorization(this, namespace.getDomains()['okapi'])
  String url = auth.generateUrl('/_/env')
  Map<String, String> headers = auth.getAuthorizedHeaders(namespace.getSuperTenant())
  Map body = [name : "DB_HOST",
              value: dbHost]

  new RestClient(this).post(url, body, headers)

  writeJSON(file: 'ldpconf.json', json: ldpConfigContent, pretty: 4)

  kubectl.createConfigMap('ldp-config', namespaceName, './ldpconf.json')

  folioHelm.upgrade("ldp", namespaceName, '', Constants.FOLIO_HELM_V2_REPO_NAME, "ldp")

  psqlDumpMethods.metaDbRestore(namespace, ldpDatabase)

}
