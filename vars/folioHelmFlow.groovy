import hudson.AbortException
import org.folio.Constants
import org.folio.models.RancherNamespace

import java.time.LocalDateTime

void deployEureka(String namespace) {
  folioHelm.addHelmRepository(Constants.BITNAMI_HELM_PROXY_REPO_NAME, Constants.BITNAMI_HELM_PROXY_REPO_URL, false)
  folioHelm.upgrade("keycloak", namespace, '', Constants.BITNAMI_HELM_PROXY_REPO_URL, "keycloak")
  folioHelm.upgrade("kong", namespace, '', Constants.BITNAMI_HELM_PROXY_REPO_URL, "kong")
}

void deployGreenmail(String namespace) {
  addHelmRepository(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL, false)
  upgrade("greenmail", namespace, '', Constants.FOLIO_HELM_HOSTED_REPO_NAME, "greenmail")
}

void deployMockServer(RancherNamespace ns) {
  Map values = [:]
  String version = "5.15.0"
  String MOCK_SERVER_REPO_NAME = 'mockserver'
  String MOCK_SERVER_REPO_URL = 'https://www.mock-server.com'
  String configFileUrl = Constants.FOLIO_GITHUB_RAW_URL +
    "/folio-integration-tests/master/mod-inn-reach/" +
    "src/main/resources/volaris/mod-inn-reach/mocks/general/mockserverInitialization.json"

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
  values << [
    image         : [tag: version],
    app           : [mountConfigMap: true],
    podAnnotations: [creationTimestamp: "\"${LocalDateTime.now().withNano(0).toString()}\""],
    ingress       : [
      enabled    : true,
      hosts      : [[
                      host : "${ns.getClusterName()}-${ns.getNamespaceName()}-mockserver.ci.folio.org",
                      paths: [[
                                path    : '/*',
                                pathType: "ImplementationSpecific"
                              ]]
                    ]],
      annotations: [
        "alb.ingress.kubernetes.io/group.name"      : "${ns.getClusterName()}.${ns.getNamespaceName()}",
        "kubernetes.io/ingress.class"               : "alb",
        "alb.ingress.kubernetes.io/healthcheck-path": "/mockserver/dashboard",
        "alb.ingress.kubernetes.io/listen-ports"    : '[{"HTTPS":443}]',
        "alb.ingress.kubernetes.io/scheme"          : "internet-facing",
        "alb.ingress.kubernetes.io/success-codes"   : "200-399"
      ]
    ]
  ]
  writeYaml file: valuesFilePath, data: values

  // Add Helm repository and upgrade
  folioHelm.addHelmRepository(MOCK_SERVER_REPO_NAME, MOCK_SERVER_REPO_URL, false)
  folioHelm.upgrade("mockserver", ns.namespaceName, valuesFilePath, MOCK_SERVER_REPO_NAME, "mockserver")
}
