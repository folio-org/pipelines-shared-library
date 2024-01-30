import org.folio.Constants
import hudson.util.Secret

private def _paramChoice(String name, List value, String description) {
    return choice(name: name, choices: value, description: description)
}

private def _paramString(String name, String value, String description) {
    return string(name: name, defaultValue: value, description: description)
}

private def _paramBoolean(String name, Boolean value, String description) {
    return booleanParam(name: name, defaultValue: value, description: description)
}

private def _paramPassword(String name, String value, String description) {
    return password(name: name, defaultValueAsSecret: new Secret(value), description: description)
}

private def _paramExtendedSingleSelect(String name, String reference, String script, String description) {
    return [$class              : 'CascadeChoiceParameter',
            choiceType          : 'PT_SINGLE_SELECT',
            description         : description,
            filterLength        : 1,
            filterable          : true,
            name                : name,
            referencedParameters: reference,
            script              : [$class        : 'GroovyScript',
                                   fallbackScript: [classpath: [],
                                                    sandbox  : false,
                                                    script   : 'return ["error"]'],
                                   script        : [classpath: [],
                                                    sandbox  : false,
                                                    script   : script]]]
}

def agent() {
    return _paramChoice('AGENT', Constants.JENKINS_AGENTS, 'Select Jenkins agent for build')
}

def refreshParameters() {
    return _paramBoolean('REFRESH_PARAMETERS', false, 'Set to true for update pipeline parameters, it will not run a pipeline')
}

def cluster() {
    return _paramChoice('CLUSTER', Constants.AWS_EKS_CLUSTERS, '(Required) Select cluster for current job')
}

def namespace() {
    return _paramExtendedSingleSelect('NAMESPACE', 'CLUSTER', folioStringScripts.getNamespaces(), '(Required) Select cluster namespace for current job')
}

def repository() {
    return _paramChoice('FOLIO_REPOSITORY', repositoriesList(), 'Platform-complete repository, complete stripes platform consists an NPM package.json that specifies the version of @folio/stripes-core')
}

def branch(String paramName = 'FOLIO_BRANCH', String repository = 'platform-complete') {
    return _paramExtendedSingleSelect(paramName, '', folioStringScripts.getRepositoryBranches(repository), "(Required) Select what '${repository}' branch use for build")
}

def okapiVersion() {
    return _paramExtendedSingleSelect('OKAPI_VERSION', 'FOLIO_BRANCH', folioStringScripts.getOkapiVersions(), 'Select what Okapi version use for build')
}

def loadReference(boolean value = true) {
    return _paramBoolean('LOAD_REFERENCE', value, 'Select true to load initial module reference data (instances, holdings etc.) for automated tests')
}

def loadSample(boolean value = true) {
    return _paramBoolean('LOAD_SAMPLE', value, 'Select true to load initial module sample data (instances, holdings etc.) for automated tests')
}

def simulate(boolean value = false) {
    return _paramBoolean('SIMULATE', value, 'Set to true to simulate installation before install')
}

def ignoreErrors(boolean value = false) {
    return _paramBoolean('IGNORE_ERRORS', value, 'Set to true to ignore errors during install')
}

def reinstall(boolean value = false) {
    return _paramBoolean('REINSTALL', value, 'Set to true to re-enable modules during install')
}

def configType() {
    return _paramChoice('CONFIG_TYPE', Constants.AWS_EKS_NAMESPACE_CONFIGS, 'Select EKS deployment configuration')
}

def pgType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('POSTGRESQL', value, 'Select database type, built-in PostgreSQL or AWS RDS')
}

def pgVersion(){
  return _paramExtendedSingleSelect('DB_VERSION', '', folioStringScripts.getPostgresqlVersion(), 'Select PostgreSQL database version')
}

def kafkaType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('KAFKA', value, 'Select Kafka type, built-in Kafka or AWS MSK')
}

def opensearchType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE.reverse()) {
    return _paramChoice('OPENSEARCH', value, 'Select OpenSearch type, built-in OpenSearch or AWS OpenSearch')
}

def s3Type(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('S3_BUCKET', value, 'Select object storage type, built-in Minio or AWS S3')
}

def uiBundleBuild(){
  return _paramBoolean('UI_BUNDLE_BUILD', false, 'True if build new ui bundle, false if choose from existing one in ui_bundle_tag parameter')
}

def uiBundleTag() {
  return _paramExtendedSingleSelect('UI_BUNDLE_TAG', 'CLUSTER,NAMESPACE', getUIImagesList(), 'Select image tag/version for UI which will be deployed')
}

def tenantId(String tenant_id = folioDefault.tenants()['diku'].tenantId) {
  return _paramString('TENANT_ID', tenant_id, 'Id used for tenant creation/deletion')
}

def referenceTenantId(String tenant_id = 'diku') {
  return _paramString('REFERENCE_TENANT_ID', tenant_id, 'Reference Id used for tenant creation')
}

static List repositoriesList() {
    return ['platform-complete',
            'platform-core']
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

def moduleName(){
  return _paramExtendedSingleSelect('MODULE_NAME', '', folioStringScripts.getBackendModulesList(), 'Select module name to install')
}
def moduleType(){
  return _paramChoice('VERSION_TYPE', ['release', 'preRelease'], 'Select module version type')
}
def moduleVersion(){
  return _paramExtendedSingleSelect('MODULE_VERSION', 'MODULE_NAME, VERSION_TYPE', folioStringScripts.getModuleVersion(), 'Select module version to install')
}
