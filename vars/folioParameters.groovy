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
    return _paramChoice('AGENT', Constants.JENKINS_AGENTS, 'Select build agent')
}

def refreshParameters() {
    return _paramBoolean('REFRESH_PARAMETERS', false, 'Set to true for job parameters refresh')
}

def cluster() {
    return _paramChoice('CLUSTER', Constants.AWS_EKS_CLUSTERS, '(Required) Select cluster')
}

def namespace() {
    return _paramExtendedSingleSelect('NAMESPACE', 'CLUSTER', folioStringScripts.getNamespaces(), '(Required) Select cluster namespace')
}

def repository() {
    return _paramChoice('FOLIO_REPOSITORY', repositoriesList(), 'Select source repository')
}

def branch(String paramName = 'FOLIO_BRANCH', String repository = 'platform-complete') {
    return _paramExtendedSingleSelect(paramName, '', folioStringScripts.getRepositoryBranches(repository), "(Required) Select what '${repository}' branch use for build")
}

def okapiVersion() {
    return _paramExtendedSingleSelect('OKAPI_VERSION', 'FOLIO_BRANCH', folioStringScripts.getOkapiVersions(), 'Select okapi version')
}

def loadReference(boolean value = false) {
    return _paramBoolean('LOAD_REFERENCE', value, 'Set to true to load reference data during install')
}

def loadSample(boolean value = false) {
    return _paramBoolean('LOAD_SAMPLE', value, 'Set to true to load sample data during install')
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
    return _paramChoice('CONFIG_TYPE', Constants.AWS_EKS_NAMESPACE_CONFIGS, 'Select deployment config type')
}

def pgType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('POSTGRESQL', value, 'Select built-in PostgreSQL or AWS RDS')
}

def kafkaType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('KAFKA', value, 'Select built-in Kafka or AWS MSK')
}

def opensearchType(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE.reverse()) {
    return _paramChoice('OPENSEARCH', value, 'Select built-in OpenSearch or AWS OpenSearch')
}

def s3Type(List value = Constants.AWS_INTEGRATED_SERVICE_TYPE) {
    return _paramChoice('S3_BUCKET', value, 'Select built-in Minio or AWS S3')
}

def uiBundleBuild(){
  return _paramBoolean('UI_BUNDLE_BUILD', false, 'True if build new ui bundle, false if choose from existing one in ui_bundle_tag parameter')
}

def uiBundleTag() {
  return _paramExtendedSingleSelect('UI_BUNDLE_TAG', 'CLUSTER,NAMESPACE', getUIImagesList(), 'Choose image tag for UI')
}

def tenantId(String tenant_id = folioDefault.tenants()['diku'].tenantId) {
  return _paramString('TENANT_ID', tenant_id, 'Id used for tenant creation')
}

static List repositoriesList() {
    return ['platform-complete',
            'platform-core']
}

def pgVersion(){
  return _paramChoice('DB_VERSION', Constants.PGSQL_VERSION, 'Select PostgreSQL version')
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
  return _paramExtendedSingleSelect('MODULE_NAME', '', folioStringScripts.getBackendModulesList(), 'Select module')
}
def moduleType(){
  return _paramChoice('VERSION_TYPE', ['release', 'preRelease'], 'Select version type')
}
def moduleVersion(){
  return _paramExtendedSingleSelect('MODULE_VERSION', 'MODULE_NAME, VERSION_TYPE', folioStringScripts.getModuleVersion(), 'Select module version')
}
