import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.rest_v2.PlatformType
import org.folio.rest_v2.Constants as RestConstants

static String getClusters(String platform) {
  return """return ${platform} && ${PlatformType.values().collect{it.name() }.inspect()}.contains(${platform}.trim()) ?
${Constants.AWS_EKS_PLATFORM_CLUSTERS().inspect()}[${platform}.trim()] :
${Constants.AWS_EKS_CLUSTERS_LIST.inspect()}
""".stripIndent()
}

static String getNamespaces() {
  return """def namespacesList = ${Constants.AWS_EKS_NAMESPACE_MAPPING.inspect()}
return namespacesList[CLUSTER]
""".stripIndent()
}

static String getApplications(String applicationSet) {
  return "return ${RestConstants.APPLICATION_SETS_APPLICATIONS.inspect()}[${applicationSet}.trim()]"
}

static String getRepositoryBranches(String repository) {
  return """import groovy.json.JsonSlurperClassic
import com.cloudbees.plugins.credentials.CredentialsProvider
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import jenkins.model.Jenkins

def getGithubToken(String credentialId) {
  def credential = CredentialsProvider.lookupCredentials(
    StringCredentials.class,
    Jenkins.instance,
    null,
    null
  ).find { it.id == credentialId }

  if (!credential) {
    throw new IllegalStateException("Credential with ID '\${credentialId}' not found!")
  }

  return credential.secret.plainText
}

def fetchAllBranches(String initialUrl, String token) {
  def branches = []
  def jsonSlurper = new JsonSlurperClassic()

  def fetchPage
  fetchPage = { url ->
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("User-Agent", "Jenkins-Groovy-Script")
    connection.setRequestProperty("Authorization", "Bearer \${token}")
    connection.setConnectTimeout(5000)
    connection.setReadTimeout(10000)

    if (connection.responseCode == 200) {
      def responseText = connection.inputStream.getText('UTF-8')
      def json = jsonSlurper.parseText(responseText)
      branches.addAll(json.collect { it.name })

      def linkHeader = connection.getHeaderField('Link')
      def nextPageUrl = (linkHeader =~ /<([^>]+)>; rel="next"/)?.with { matcher -> matcher.find() ? matcher.group(1) : null }

      if (nextPageUrl) {
        fetchPage(nextPageUrl)
      }
    } else {
      throw new IOException("Failed to fetch branches: HTTP \${connection.responseCode}")
    }
  }

  fetchPage(initialUrl)

  return branches
}

def apiUrl = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches"
def perPage = 100
def credentialId = "github-jenkins-service-user-token"

try {
  def token = getGithubToken(credentialId)
  def branches = fetchAllBranches("\${apiUrl}?per_page=\${perPage}", token)

  return branches
} catch (Exception e) {
  println "Error: \${e.message}"
  throw e
}
""".stripIndent()
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
""".stripIndent()
}

static String getModuleId(String moduleName) {
  URLConnection registry = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${moduleName}&preRelease=only&latest=1").openConnection()
  if (registry.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
  } else {
    throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
  }
}

static String getModulesList(String reference){
  return """import groovy.json.JsonSlurperClassic
import com.cloudbees.plugins.credentials.CredentialsProvider
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import jenkins.model.Jenkins

def getGithubToken(String credentialId) {
  def credential = CredentialsProvider.lookupCredentials(
    StringCredentials.class,
    Jenkins.instance,
    null,
    null
  ).find { it.id == credentialId }

  if (!credential) {
    throw new IllegalStateException("Credential with ID '\${credentialId}' not found!")
  }

  return credential.secret.plainText
}

def fetchAllModules(String initialUrl, String token) {
  def modules = []
  def jsonSlurper = new JsonSlurperClassic()

  def fetchPage
  fetchPage = { url ->
  def connection = new URL(url).openConnection()
  connection.setRequestProperty("User-Agent", "Jenkins-Groovy-Script")
  connection.setRequestProperty("Authorization", "Bearer \${token}")
  connection.setConnectTimeout(5000)
  connection.setReadTimeout(10000)

  if (connection.responseCode == 200) {
    def responseText = connection.inputStream.getText('UTF-8')
    def json = jsonSlurper.parseText(responseText)
    modules.addAll(json*.name)

    def linkHeader = connection.getHeaderField('Link')
    def nextPageUrl = (linkHeader =~ /<([^>]+)>; rel="next"/)?.with { matcher -> matcher.find() ? matcher.group(1) : null }

    if (nextPageUrl) {
      fetchPage(nextPageUrl)
    }
  } else {
    throw new IOException("Failed to fetch modules: HTTP \${connection.responseCode}")
  }
}

  fetchPage(initialUrl)

  return modules
}

def filterModules(List<String> modules, String platform) {
  modules.findAll { name ->
    def isStandardModule = name.startsWith('mod-') || name.startsWith('edge-')
    def isOkapiSpecific = name == 'okapi'
    def isEurekaSpecific = ['folio-kong', 'folio-keycloak', 'folio-module-sidecar'].contains(name) || name.startsWith('mgr-')

    def result = isStandardModule
    if (platform?.toUpperCase() == 'OKAPI') {
      result = result || isOkapiSpecific
    }
    if (platform?.toUpperCase() == 'EUREKA') {
      result = result || isEurekaSpecific
    }
    return result
  }.sort()
}

def apiUrl = "https://api.github.com/orgs/folio-org/repos"
def perPage = 100
def credentialId = "github-jenkins-service-user-token"
def platform = "${reference}" // Provided at runtime

try {
  def token = getGithubToken(credentialId)
  def modules = fetchAllModules("\${apiUrl}?per_page=\${perPage}", token)
  def filteredModules = filterModules(modules, platform)

  return filteredModules
} catch (Exception e) {
  println "Error: \${e.message}"
  throw e
}
""".stripIndent()
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
  return '''def versions = ["16.8", "16.9", "16.10"]

List pgVersions = versions.findAll { it != '16.8' }
pgVersions.add(0, '16.8')

return pgVersions'''
}

static String getUIImagesList() {
  return """import com.amazonaws.services.ecr.AmazonECR
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
""".stripIndent()
}

static String getHideHTMLScript(Map hideMap, String reference) {
  return """
def selectors = ${hideMap.inspect()}[${reference}.toString()]?.collect {
    "div.jenkins-form-item:has(input[value='\$it']):not(:has([id^=hiddenPanel]))"
  }?.join(", \\n")

return selectors ? \"\"\"
  <style>
    \$selectors {
      display: none !important;
    }
  </style>
  \"\"\" : ""
"""
}

static String groupCheckBoxes(List checkboxes){
  def selectors = checkboxes.collect { "div.jenkins-form-item:has(input[value='$it']):not(:has([id^=hiddenPanel]))" }.join(", ")

  return """
return \"\"\"
  <script>
    document.addEventListener("DOMContentLoaded", function() {
      var checkbox_divs = document.querySelectorAll("${selectors}");

      var checkboxes = [...checkbox_divs].map(function(checkbox_div) {
          return checkbox_div.querySelector('input[name=value]');
      });

      console.debug(checkboxes);

      // For each checkbox, attach a change listener.
      checkboxes.forEach(function(checkbox) {
        checkbox.addEventListener("change", function() {
          if (checkbox.checked) {
            checkboxes.forEach(function(other) {
              if (other !== checkbox) {
                other.checked = false;
              }
            });
          }
        });
      });
    });
  </script>
\"\"\"
  """
}

static String getGroupHTMLScript(String title, List params) {
  int id = Math.abs(title.hashCode())

  return """
return \"\"\"
<style>
  #toggleHeader${id} {
    background-color: #ddd;
    padding: 0.5em;
    margin: 1em 0;
    cursor: pointer;
    font-weight: var(--form-label-font-weight);
    border-radius: 4px;
    transition: background-color 0.2s;
  }

  #toggleHeader${id}::after{
    content: "- ";
  }

  #toggleHeader${id}.closed::after{
    content: "+ ";
  }

  #toggleHeader${id}.closed {
    background-color: #ccc;
  }

  div:has(#toggleHeader${id}.closed) #hiddenPanel${id} {
    display: none !important;
  }

  div.jenkins-form-item:has(#toggleHeader${id}) {
    margin-bottom: 0;
  }

  #hiddenPanel${id} {
    display: block;
    border: 1px solid #ccc;
    padding: 1em;
    background: #f9f9f9;
    border-radius: 4px;
    margin-bottom: var(--section-padding);
  }
</style>

<script>
  document.addEventListener('DOMContentLoaded', function() {
    const hiddenPanel = document.getElementById('hiddenPanel${id}');
    const hiddenPanelParent = hiddenPanel.closest('div.jenkins-form-item');

    if (hiddenPanelParent && hiddenPanel) {
      hiddenPanelParent.parentNode.insertBefore(hiddenPanel, hiddenPanelParent.nextSibling);
    }

    // Move each named parameter's DOM node into the hiddenPanel <div>
    ${params.inspect()}.forEach(name => {
      const input = document.querySelector('div.jenkins-form-item input[value="' + name + '"]');

      if (input) {
        const paramDiv = input.closest('div.jenkins-form-item');
        hiddenPanel.appendChild(paramDiv);
      }
    });
  });
  </script>

<div id="toggleHeader${id}" class="closed" onclick="this.classList.toggle('closed');">
  ${title}
</div>

<div id="hiddenPanel${id}"></div>
\"\"\"
"""
}

static String getContainerImageTags(String numOfTagsToShow = '100') {
  return """
    if (MODULE_SOURCE.contains('DockerHub/')) {
      def getContainerImageTags = "curl -s -X GET '${Constants.DOCKERHUB_URL}/repositories/\${MODULE_SOURCE.split('/')[1]}/\${MODULE_NAME}/tags?page_size=${numOfTagsToShow}' | jq -r '.results[].name'"
      def process = ['sh', '-c', getContainerImageTags].execute()
      return process.text.readLines().sort().reverse()
    } else {
      return ["N/A"]
    }
  """.stripIndent().trim()
}
