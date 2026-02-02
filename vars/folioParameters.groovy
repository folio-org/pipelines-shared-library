import hudson.util.Secret
import org.folio.Constants
import org.folio.rest.model.OkapiUser
import org.folio.rest_v2.PlatformType
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

private def _extendedSelect(String name, String reference, String script, String description
                            , boolean filterable = true, boolean useSandBoxFlag = false, String choiceType = "PT_SINGLE_SELECT") {

  return [$class              : 'CascadeChoiceParameter',
          choiceType          : choiceType,
          description         : description,
          filterLength        : 1,
          filterable          : filterable,
          name                : name,
          referencedParameters: reference,
          script              : [$class        : 'GroovyScript',
                                 fallbackScript: [classpath: [],
                                                  sandbox  : useSandBoxFlag,
                                                  script   : 'return ["error"]'],
                                 script        : [classpath: [],
                                                  sandbox  : useSandBoxFlag,
                                                  script   : script]]]
}

private def _extendedDynamicParam(String name, String reference, String script, String description
                                  , boolean omitValueField = false, String choiceType = "ET_FORMATTED_HTML") {

  return [$class              : 'DynamicReferenceParameter',
          choiceType          : choiceType,
          description         : description,
          name                : name,
          referencedParameters: reference,
          omitValueField      : omitValueField,
          script              : [$class        : 'GroovyScript',
                                 fallbackScript: [classpath: [],
                                                  sandbox  : false,
                                                  script   : 'return ["error"]'],
                                 script        : [classpath: [],
                                                  sandbox  : false,
                                                  script   : script]]]
}

private def _paramExtendedMultiSelect(String name, String reference, String script, String description, boolean filterable = true, boolean useSandBoxFlag = false) {
  _extendedSelect(name, reference, script, description, filterable, useSandBoxFlag, "PT_MULTI_SELECT")
}

private def _paramExtendedSingleSelect(String name, String reference, String script, String description, boolean filterable = true, boolean useSandBoxFlag = false) {
  _extendedSelect(name, reference, script, description, filterable, useSandBoxFlag, "PT_SINGLE_SELECT")
}

private def _paramExtendedCheckboxSelect(String name, String reference, String script, String description, boolean filterable = false, boolean useSandBoxFlag = false) {
  _extendedSelect(name, reference, script, description, filterable, useSandBoxFlag, "PT_CHECKBOX")
}

private def _paramHiddenHTML(String script, String reference, boolean omitValue = true, String name = "", String description = "") {
  _extendedDynamicParam(name, reference, script, description, omitValue, "ET_FORMATTED_HIDDEN_HTML")
}

private def _paramFormattedHTML(String script, String reference, boolean omitValue = true, String name = "", String description = "") {
  _extendedDynamicParam(name, reference, script, description, omitValue, "ET_FORMATTED_HTML")
}

@Deprecated
def agent() {
  return _paramChoice('AGENT', [], 'Select Jenkins agent for build')
}

@Deprecated
def cypressAgent() {
  return _paramChoice('AGENT', CypressConstants.JENKINS_CYPRESS_AGENTS, 'Select Jenkins agent for build')
}

def platform(PlatformType defaultValue = null, String paramName = 'PLATFORM') {
  List values = PlatformType.values().collect{it.name() } - [defaultValue?.name()]
  values = defaultValue ? [defaultValue.name()] + values : values

  return _paramChoice(paramName, values, 'Select FOLIO platform')
}

def applicationsFromPlatform(String paramName = 'APPLICATIONS', String reference = 'PLATFORM_BRANCH') {
  return _paramExtendedCheckboxSelect(paramName, reference, folioStringScripts.getApplicationsFromPlatformDescriptor(reference), 'Select env applications', false)
}

def applicationFromPlatform(String paramName = 'APPLICATION', String reference = 'PLATFORM_BRANCH') {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getApplicationsFromPlatformDescriptor(reference), 'Select application')
}

def refreshParameters() {
  return _paramBoolean('REFRESH_PARAMETERS', false, 'Set to true for update pipeline parameters, it will not run a pipeline')
}

def cluster(String reference = null, String paramName = 'CLUSTER') {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getClusters(reference), '(Required) Select cluster for current job')
}

def namespace() {
  return _paramExtendedSingleSelect('NAMESPACE', 'CLUSTER', folioStringScripts.getNamespaces(), '(Required) Select cluster namespace for current job')
}

def repository() {
  return _paramChoice('FOLIO_REPOSITORY', repositoriesList(), 'Platform-complete repository, complete stripes platform consists an NPM package.json that specifies the version of @folio/stripes-core')
}

def branch(String paramName = 'FOLIO_BRANCH', String repository = 'platform-complete', String reference = 'PLATFORM') {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getRepositoryBranches(repository), "(Required) Select what '${repository}' branch use for build")
}

def platformBranch(String paramName = 'PLATFORM_BRANCH', String repository = 'platform-lsp', String reference = 'PLATFORM') {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getRepositoryBranches(repository), "(Required) Select what '${repository}' branch use for build")
}

def branchWithRef(String paramName = 'FOLIO_BRANCH', String reference = "") {
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

def tenantId(String tenant_id = 'diku') {
  return _paramString('TENANT_ID', tenant_id, 'Input Tenant ID')
}

def referenceTenantId(String tenant_id = 'diku') {
  return _paramString('REFERENCE_TENANT_ID', tenant_id, 'Reference Id used for tenant creation')
}

def moduleName(String reference = 'PLATFORM', String paramName = 'MODULE_NAME') {
  return _paramExtendedSingleSelect(paramName, reference, folioStringScripts.getModulesList("\${${reference}}"), 'Select module name to install')
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

def hideParameters(Map valueParams, String keyParameter, String reference = keyParameter) {
  return _paramHiddenHTML(folioStringScripts.getHideHTMLScript(valueParams, keyParameter), reference)
}

def hideParameters(List params) {
  return _paramHiddenHTML(folioStringScripts.getHideHTMLScript(['hide': params], '"hide"'), '')
}

def groupCheckboxes(List checkboxes, String reference = "") {
  return _paramFormattedHTML(folioStringScripts.groupCheckBoxes(checkboxes), reference)
}

def groupParameters(String title, List groupedParams, String reference = "") {
  return _paramFormattedHTML(folioStringScripts.getGroupHTMLScript(title, groupedParams), reference)
}

def imageRepositoryName() {
  return _paramChoice('IMAGE_REPO_NAME', Constants.DOCKERHUB_REPO_NAMES_LIST, 'Docker Hub image repository name')
}

def containerImageTag(String paramName = 'CONTAINER_IMAGE_TAG', String referencedParams) {
  return _paramExtendedSingleSelect(paramName, referencedParams, folioStringScripts.getContainerImageTags(), "(Required) Get Container Image Tags from Docker Hub", true, true)
}

def moduleSource() {
  return _paramChoice('MODULE_SOURCE', Constants.EUREKA_MODULE_SOURCES, 'Select Eureka module source')
}

def consortiaSecureMemberTenant(
      String paramName = 'SECURE_TENANT'
      , List value =
        folioDefault.consortiaTenants()
          .findAll {!(it.value.isCentralConsortiaTenant)}
          .collect{it.value.tenantId}
      , String description = 'Select secure tenant'
) {
  return _paramChoice(paramName, value, description)
}

def testGroup(String paramName = 'TEST_GROUP') {
  String script = "return ${Constants.TEST_GROUP.inspect()}"
  return _paramExtendedCheckboxSelect(paramName, '', script, 'Select test groups to run', false)
}
