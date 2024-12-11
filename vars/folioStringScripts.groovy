import groovy.json.JsonSlurperClassic
import org.folio.Constants

static String getNamespaces() {
  return """def namespacesList = ${Constants.AWS_EKS_NAMESPACE_MAPPING.inspect()}
return namespacesList[CLUSTER]
"""
}

static String getRepositoryBranches(String repository) {
  return """import groovy.json.JsonSlurperClassic
def apiUrl = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches"
def perPage = 100
def fetchBranches(String url) {
def credentialId = "id-jenkins-github-personal-token"
def credential = com.cloudbees.plugins.credentials.SystemCredentialsProvider.getInstance().getStore().getCredentials(com.cloudbees.plugins.credentials.domains.Domain.global()).find { it.getId().equals(credentialId) }
def secret_value = credential.getSecret().getPlainText()
    def branches = []
  def jsonSlurper = new JsonSlurperClassic()
  def getNextPage
  def processResponse = { connection ->
    connection.setRequestProperty("Authorization", "Bearer \${secret_value}")
    if (connection.responseCode == 200) {
      def responseText = connection.getInputStream().getText()
      branches += jsonSlurper.parseText(responseText).collect { it.name }
      def linkHeader = connection.getHeaderField('Link')
      if (linkHeader?.find('rel="next"')) {
        def nextUrlMatcher = linkHeader =~ /<(http[^>]+)>; rel="next"/
        if (nextUrlMatcher.find()) {
          getNextPage(nextUrlMatcher[0][1])
        }
      }
    } else {
      println("Error fetching data: HTTP \${connection.responseCode}")
    }
  }
  getNextPage = { nextPageUrl ->
        def nextConn = new URL(nextPageUrl).openConnection()
    processResponse(nextConn)
            }
  processResponse(new URL(url).openConnection())
    return branches
}
fetchBranches("\${apiUrl}?per_page=\${perPage}")
"""
}

static String getOkapiVersions() {
  return """import groovy.json.JsonSlurperClassic
def installJson = new URL("${Constants.FOLIO_GITHUB_RAW_URL}/platform-complete/\${FOLIO_BRANCH}/install.json").openConnection()
if (installJson.getResponseCode().equals(200)) {
    String okapi = new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.find{it ==~ /okapi-.*/}
    if(okapi){
        return [okapi - 'okapi-']
    }else {
        String repository = FOLIO_BRANCH.contains("snapshot") ? "folioci" : "folioorg"
        def dockerHub = new URL("${Constants.DOCKERHUB_URL}/repositories/\${repository}/okapi/tags?page_size=100&ordering=last_updated").openConnection()
        if (dockerHub.getResponseCode().equals(200)) {
            return new JsonSlurperClassic().parseText(dockerHub.getInputStream().getText()).results*.name - 'latest'
        }
    }
}
"""
}

static String getModuleId(String moduleName) {
  URLConnection registry = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${moduleName}&preRelease=only&latest=1").openConnection()
  if (registry.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
  } else {
    throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
  }
}

static String getBackendModulesList() {
  return '''import groovy.json.JsonSlurperClassic
def apiUrl = "https://api.github.com/orgs/folio-org/repos"
def perPage = 100
def fetchModules(String url) {
  def credentialId = "id-jenkins-github-personal-token"
  def credential = com.cloudbees.plugins.credentials.SystemCredentialsProvider.getInstance().getStore().getCredentials(com.cloudbees.plugins.credentials.domains.Domain.global()).find { it.getId().equals(credentialId) }
  def secret_value = credential.getSecret().getPlainText()
  def modules = []
  def jsonSlurper = new JsonSlurperClassic()
  def getNextPage
  def processResponse = { connection ->
    connection.setRequestProperty("Authorization", "Bearer ${secret_value}")
    if (connection.responseCode == 200) {
      def responseText = connection.getInputStream().getText()
      def json = jsonSlurper.parseText(responseText)
      modules.addAll(json*.name)
      def linkHeader = connection.getHeaderField('Link')
      if (linkHeader) {
        def nextPageUrl = (linkHeader =~ /<([^>]+)>; rel="next"/)?.with { matcher -> matcher.find() ? matcher.group(1) : null }
        if (nextPageUrl) {
          getNextPage(nextPageUrl)
        }
      }
    } else {
      println("Error fetching data: HTTP ${connection.responseCode}")
    }
  }
  getNextPage = { nextPageUrl ->
    def nextConn = new URL(nextPageUrl).openConnection()
    processResponse(nextConn)
  }
  processResponse(new URL(url).openConnection())
  return modules.findAll { it == 'okapi' || it.startsWith('mod-') || it.startsWith('edge-') || it.startsWith('mgr-') }.sort()
}
fetchModules("${apiUrl}?per_page=${perPage}")'''
}

static String getModuleVersion() {
  return '''import groovy.json.JsonSlurperClassic
def versionType = ''
switch(VERSION_TYPE){
  case 'release':
    versionType = 'false'
    break
  case 'preRelease':
    versionType = 'only'
    break
  default:
    versionType = 'only'
    break
}
def moduleVersionList = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${MODULE_NAME}&preRelease=${versionType}&order=desc&orderBy=id").openConnection()
if (moduleVersionList.getResponseCode().equals(200)) {
  return new JsonSlurperClassic().parseText(moduleVersionList.getInputStream().getText())*.id.collect{id -> return id - "${MODULE_NAME}-"}
}'''
}

static String getPostgresqlVersion() {
  return '''def versions = ["12.12", "12.14", "13.13", "14.10", "15.5", "16.1"]

List pgVersions = versions.findAll { it != '16.1' }
pgVersions.add(0, '16.1')

return pgVersions'''
}

static String getUIImagesList() {
  return """
import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.ListImagesRequest

AmazonECR client = AmazonECRClientBuilder.standard().withRegion("us-west-2").build()

String repositoryName = "ui-bundle"

def result = []
def final_result = []
String nextToken = null

while (nextToken != '') {
    ListImagesRequest request = new ListImagesRequest()
            .withRepositoryName(repositoryName)
            .withNextToken(nextToken)

    def res = client.listImages(request)
    result.addAll(res.imageIds.collect { it.imageTag })
    result.each {
        if (!(it == null)) {
            final_result.add(it)
        }
    }
    nextToken = res.nextToken ?: ''
}

result = final_result.findAll { it.startsWith(CLUSTER + '-' + NAMESPACE + '.') }
        .sort()
        .reverse()

return result
"""
}
