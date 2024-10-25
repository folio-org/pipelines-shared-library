import hudson.util.Secret
import org.folio.Constants
import org.folio.rest.model.OkapiUser
import org.folio.testing.cypress.CypressConstants

static List repositoriesList() {
  return ['platform-complete',
          'platform-core']
}

static OkapiUser defaultAdminUser() {
  return new OkapiUser(
    username: 'diku_admin',
    password: 'admin'
  )
}

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

def cypressAgent() {
  return _paramChoice('AGENT', CypressConstants.JENKINS_CYPRESS_AGENTS, 'Select Jenkins agent for build')
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

def branchWithRef(String paramName = 'FOLIO_BRANCH', String reference) {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getRepositoryBranches("\${${reference}}"), "(Required) Select what '${reference}' branch use for build")
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

def pgVersion() {
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

def uiBundleBuild() {
  return _paramBoolean('UI_BUNDLE_BUILD', false, 'True if build new ui bundle, false if choose from existing one in ui_bundle_tag parameter')
}

def uiBundleTag() {
  return _paramExtendedSingleSelect('UI_BUNDLE_TAG', 'CLUSTER,NAMESPACE', folioStringScripts.getUIImagesList(), 'Select image tag/version for UI which will be deployed')
}

def tenantId(String tenant_id = folioDefault.tenants()['diku'].tenantId) {
  return _paramString('TENANT_ID', tenant_id, 'Input Tenant ID')
}

def referenceTenantId(String tenant_id = 'diku') {
  return _paramString('REFERENCE_TENANT_ID', tenant_id, 'Reference Id used for tenant creation')
}

def moduleName() {
  return _paramExtendedSingleSelect('MODULE_NAME', '', folioStringScripts.getBackendModulesList(), 'Select module name to install')
}

def moduleType() {
  return _paramChoice('VERSION_TYPE', ['release', 'preRelease'], 'Select module version type')
}

def moduleVersion() {
  return _paramExtendedSingleSelect('MODULE_VERSION', 'MODULE_NAME, VERSION_TYPE', folioStringScripts.getModuleVersion(), 'Select module version to install')
}

def adminUsername(String admin_username = defaultAdminUser().username) {
  return _paramString('ADMIN_USERNAME', admin_username, 'Admin user name')
}

def adminPassword(String admin_password = defaultAdminUser().password, String description = 'Password for admin user') {
  return _paramPassword('ADMIN_PASSWORD', admin_password, description)
}

def eurekaModules() {
  return _paramChoice('MODULE_NAME', Constants.EUREKA_MODULES, 'Eureka module name to build')
}

def runSanityCheck(boolean value = true) {
  return _paramBoolean('RUN_SANITY_CHECK', value, 'Set to false, to disable cypress sanity check')
}
