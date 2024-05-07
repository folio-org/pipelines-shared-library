import groovy.json.JsonSlurperClassic
import org.folio.Constants

static String getNamespaces() {
    return """def namespacesList = ${Constants.AWS_EKS_NAMESPACE_MAPPING.inspect()}
return namespacesList[CLUSTER]
"""
}

static String getRepositoryBranches(String repository){
    return """import groovy.json.JsonSlurperClassic
def credentialId = "id-jenkins-github-personal-token"
def credential = com.cloudbees.plugins.credentials.SystemCredentialsProvider.getInstance().getStore().getCredentials(com.cloudbees.plugins.credentials.domains.Domain.global()).find { it.getId().equals(credentialId) }
def secret_value = credential.getSecret().getPlainText()
def apiUrl = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches"
def perPage = 500
def fetchBranches = { String url ->
    def branches = []
    def getNextPage = { nextPageUrl ->
        def nextConn = new URL(nextPageUrl).openConnection()
        nextConn.setRequestProperty("Authorization", "Bearer \${secret_value}")
        if (nextConn.responseCode.equals(200)) {
            def nextResponseText = nextConn.getInputStream().getText()
            branches += new JsonSlurperClassic().parseText(nextResponseText).name
            def nextLinkHeader = nextConn.getHeaderField("Link")
            if (nextLinkHeader && nextLinkHeader.contains('rel="next"')) {
                def nextUrl = nextLinkHeader =~ /<(.*?)>/
                if (nextUrl) {
                    getNextPage(nextUrl[0][1])
                }
            }
        }
    }
    def conn = new URL(url).openConnection()
    conn.setRequestProperty("Authorization", "Bearer \${secret_value}")
    if (conn.responseCode.equals(200)) {
        def responseText = conn.getInputStream().getText()
        branches += new JsonSlurperClassic().parseText(responseText).name
        def linkHeader = conn.getHeaderField("Link")
        if (linkHeader && linkHeader.contains('rel="next"')) {
            def nextPageUrl = linkHeader =~ /<(.*?)>/
            if (nextPageUrl) {
                getNextPage(nextPageUrl[0][1])
            }
        }
    }
    return branches
}
fetchBranches("\$apiUrl?per_page=\$perPage")
"""
}

static String getOkapiVersions(){
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

static String getBackendModulesList(){
    return '''import groovy.json.JsonSlurperClassic
String nameGroup = "moduleName"
String patternModuleVersion = /^(?<moduleName>.*)-(?<moduleVersion>(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*).*)$/
def installJson = new URL('https://raw.githubusercontent.com/folio-org/platform-complete/snapshot/install.json').openConnection()
if (installJson.getResponseCode().equals(200)) {
    List modules_list = ['okapi']
    new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.findAll { it ==~ /mod-.*|edge-.*/ }.each { value ->
        def matcherModule = value =~ patternModuleVersion
        assert matcherModule.matches()
        modules_list.add(matcherModule.group(nameGroup))
    }
    return modules_list.sort()
}'''
}

static String getModuleVersion(){
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

static String getPostgresqlVersion(){
    return '''def versions = ["12.12", "12.14", "13.13", "14.10", "15.5", "16.1"]
List pg_versions = []
versions.each {version ->
if(version == '13.13') {
  pg_versions.add(0, version)
} else{
  pg_versions.add(version)
 }
}
return (pg_versions)'''
}
