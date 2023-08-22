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
def secretText = credential.getSecret().getPlainText()
def get = new URL("${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches?per_page=100&Authorization=${secretText}").openConnection()
if (get.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(get.getInputStream().getText()).name
}
"""
}

static String getOkapiVersions(){
    return """import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurperClassic
def credentialId = "id-jenkins-github-personal-token"
def credential = com.cloudbees.plugins.credentials.SystemCredentialsProvider.getInstance().getStore().getCredentials(com.cloudbees.plugins.credentials.domains.Domain.global()).find { it.getId().equals(credentialId) }
def secretText = credential.getSecret().getPlainText()
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
