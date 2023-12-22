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

def configType() {
    return _paramChoice('CONFIG_TYPE', Constants.AWS_EKS_NAMESPACE_CONFIGS, 'Select EKS deployment configuration')
}

def pgType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('POSTGRESQL', value, 'Select database type, built-in PostgreSQL or AWS RDS')
}

def pgVersion(){
  return _paramChoice('DB_VERSION', Constants.PGSQL_VERSION, 'Select PostgreSQL database version')
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
  return _paramString('TENANT_ID', tenant_id, 'Id used for tenant creation')
}

static List repositoriesList() {
    return ['platform-complete',
            'platform-core']
}

static String getUIImagesList() {
  return """
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AbstractAmazonECR;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import jenkins.model.*

AmazonECR client = AmazonECRClientBuilder.standard().withRegion("us-west-2").build();
ListImagesRequest request = new ListImagesRequest().withRepositoryName("ui-bundle");
res = client.listImages(request);


def result = []
for (image in res) {
   result.add(image.getImageIds());
}

return result[0].imageTag.sort().reverse().findAll().findAll{it.startsWith(CLUSTER.trim() + '-' + NAMESPACE.trim())};
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
