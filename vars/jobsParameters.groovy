import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

void call() {}

static String pgDefaultPassword() {
    return 'postgres_password_123!'
}

static String pgAdminDefaultPassword() {
    return 'SuperSecret'
}

static String pgLdpUserDefaultPassword() {
    return 'diku_ldp9367'
}

static List dbList() {
    return ['rds', 'postgresql']
}

static OkapiUser defaultAdminUser() {
    return new OkapiUser(
        username: 'diku_admin',
        password: 'admin'
    )
}

static OkapiTenant defaultTenant() {
    return new OkapiTenant(
        id: 'diku',
        name: 'Datalogisk Institut',
        description: 'Danish Library Technology Institute',
        tenantParameters: [
            loadReference: false,
            loadSample   : false
        ],
        queryParameters: [
            reinstall: false
        ]
    )
}

static List repositoriesList() {
    return ['platform-complete',
            'platform-core']
}

static List clustersList() {
    return ['folio-testing',
            'folio-dev',
            'folio-perf',
            'folio-tmp']
}

static List jenkinsAgentsList() {
    return ['rancher',
            'jenkins-agent-java11',
            'jenkins-agent-java11-test',
            'jenkins-agent-java17',
            'jenkins-agent-java17-test',
            'rancher||jenkins-agent-java11'
    ]
}

@NonCPS
static List configTypeList() {
    return ['development',
            'performance',
            'testing']
}


@NonCPS
static List testingEnvironmentsList() {
    return ['karate',
            'cypress',
            'sprint']
}

@NonCPS
static List devEnvironmentsList() {
    return ['aggies',
            'bama',
            'bulk-edit',
            'concorde',
            'consortia',
            'core-platform',
            'data-migration',
            'falcon',
            'firebird',
            'folijet',
            'folijet-lotus',
            'metadata',
            'nest-es',
            'nla',
            'prokopovych',
            'scout',
            'spanish',
            'spitfire',
            'spitfire-2nd',
            'stripes-force',
            'task-force',
            'task-force-2nd',
            'thor',
            'thunderjet',
            'unam',
            'vega',
            'volaris',
            'volaris-2nd',
            'rtr']
}

@NonCPS
static List crudOperationsList () {
    return ['create', 'delete', 'update']
}

@NonCPS
static List perfEnvironmentsList() {
    return devEnvironmentsList()
}

@NonCPS
static List testEnvironmentsList() {
    return ['test', 'test-1', 'test-2', 'tdspora']
}

@NonCPS
static String generateProjectNamesMap() {
    return JsonOutput.toJson(['folio-testing': testingEnvironmentsList().sort(),
                              'folio-dev'    : devEnvironmentsList().sort(),
                              'folio-perf'   : perfEnvironmentsList().sort(),
                              'folio-tmp'    : testEnvironmentsList().sort()])
}

static String getRepositoryBranches(String repository) {
    return """import groovy.json.JsonSlurperClassic
def credentialId = "id-jenkins-github-personal-token"
def credential = com.cloudbees.plugins.credentials.SystemCredentialsProvider.getInstance().getStore().getCredentials(com.cloudbees.plugins.credentials.domains.Domain.global()).find { it.getId().equals(credentialId) }
def secret_value = credential.getSecret().getPlainText()
def get = new URL('https://api.github.com/repos/folio-org/' + ${repository} + '/branches?per_page=100')
HttpURLConnection conn = (HttpURLConnection) get.openConnection()
conn.setRequestProperty("Authorization","Bearer "+" \${secret_value}");
if(conn.responseCode.equals(200)){
  return new JsonSlurperClassic().parseText(conn.getInputStream().getText()).name
}
"""
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

static String getProjectNames() {
    return """import groovy.json.JsonSlurperClassic
def projectNamesList = new JsonSlurperClassic().parseText('${generateProjectNamesMap()}')
return projectNamesList[rancher_cluster_name]
"""
}

static String getOkapiVersions() {
    return '''import groovy.json.JsonSlurperClassic
def installJson = new URL('https://raw.githubusercontent.com/folio-org/' + folio_repository + '/' + folio_branch + '/install.json').openConnection()
if (installJson.getResponseCode().equals(200)) {
    String okapi = new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.find{it ==~ /okapi-.*/}
    if(okapi){
        return [okapi - 'okapi-']
    }else {
        String repository = folio_branch.contains("snapshot") ? "folioci" : "folioorg"
        def dockerHub = new URL('https://hub.docker.com/v2/repositories/' + repository + '/okapi/tags?page_size=100&ordering=last_updated').openConnection()
        if (dockerHub.getResponseCode().equals(200)) {
            return new JsonSlurperClassic().parseText(dockerHub.getInputStream().getText()).results*.name - 'latest'
        }
    }
}
'''
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

static String getEdgeModulesList() {
    return '''import groovy.json.JsonSlurperClassic
String nameGroup = "moduleName"
String patternModuleVersion = /^(?<moduleName>.*)-(?<moduleVersion>(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*).*)$/
def installJson = new URL('https://raw.githubusercontent.com/folio-org/platform-complete/snapshot/install.json').openConnection()
if (installJson.getResponseCode().equals(200)) {
    List modules_list = []
    new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.findAll { it ==~ "edge-.*" }.each { value ->
        def matcherModule = value =~ patternModuleVersion
        assert matcherModule.matches()
        modules_list.add(matcherModule.group(nameGroup))
    }
    return modules_list.sort()
}'''
}

private def _paramChoice(String name, List options, String description) {
    return choice(name: name, choices: options, description: description)
}

private def _paramString(String name, String value, String description) {
    return string(name: name, defaultValue: value, description: description)
}

private def _paramBoolean(String name, Boolean value, String description) {
    return booleanParam(name: name, defaultValue: value, description: description)
}

private def _paramPassword(String name, String value, String description) {
    return password(name: name, defaultValueAsSecret: new hudson.util.Secret(value), description: description)
}

private def _paramExtended(String name, String reference, String script, String description) {
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

def refreshParameters() {
    return _paramBoolean('refresh_parameters', false, 'Do a dry run and refresh pipeline configuration')
}

def agents() {
    return _paramChoice('agent', jenkinsAgentsList(), 'Choose for which jenkins agent you want to build from')
}

def clusterName() {
    return _paramChoice('rancher_cluster_name', clustersList(), '(Required) Select target cluster')
}

def projectName() {
    return _paramExtended('rancher_project_name', 'rancher_cluster_name', getProjectNames(), '(Required) Select target project')
}

def projectDevName() {
    return _paramChoice('rancher_project_name', devEnvironmentsList().sort(), '(Required) Select target project')
}

def repository() {
    return _paramChoice('folio_repository', repositoriesList(), 'Select source repository')
}

def branch(String reference_parameter = 'folio_repository', String parameter_name = 'folio_branch') {
    return _paramExtended(parameter_name, reference_parameter, getRepositoryBranches(reference_parameter), 'Choose what platform-core or platform-complete branch to build from')
}

def configType() {
    return _paramChoice('config_type', configTypeList(), 'Select config file')
}

def enableModules() {
    return _paramBoolean('enable_modules', true, 'True if modules should be registered and enabled in Okapi')
}

def tenantId(String tenant_id = defaultTenant().id) {
    return _paramString('tenant_id', tenant_id, 'Id used for tenant creation')
}

def tenantName(String tenant_name = defaultTenant().name) {
    return _paramString('tenant_name', tenant_name, 'Name used for tenant creation')
}

def tenantDescription(String tenant_description = defaultTenant().description) {
    return _paramString('tenant_description', tenant_description, 'Description used for tenant creation')
}

def referenceTenantId(String reference_tenant_id = defaultTenant().id) {
    return _paramString('reference_tenant_id', reference_tenant_id, 'Id used to extract list of installed modules. For Rancher environments it\'s usually diku')
}

def loadReference() {
    return _paramBoolean('load_reference', defaultTenant().tenantParameters.loadReference, 'True if reference data should be applied')
}

def loadSample() {
    return _paramBoolean('load_sample', defaultTenant().tenantParameters.loadSample, 'True if sample data should be applied')
}

def reinstall() {
    return _paramBoolean('reinstall', defaultTenant().queryParameters.reinstall, 'True if force modules install')
}

def adminUsername(String admin_username = defaultAdminUser().username) {
    return _paramString('admin_username', admin_username, 'Admin user name')
}

def adminPassword(String admin_password = defaultAdminUser().password, String description = 'Password for admin user') {
    return _paramPassword('admin_password', admin_password, description)
}

def reindexElasticsearch() {
    return _paramBoolean('reindex_elastic_search', false, 'True if need to reindex modules')
}

def recreateIndexElasticsearch() {
    return _paramBoolean('recreate_elastic_search_index', false, 'True if need to recreate index')
}

def pgPassword() {
    return _paramPassword('pg_password', pgDefaultPassword(), 'Password for PostgreSQL database')
}

def pgAdminPassword() {
    return _paramPassword('pgadmin_password', pgAdminDefaultPassword(), 'Password for pgAdmin login')
}

def uiBundleBuild(){
    return _paramBoolean('ui_bundle_build', false, 'True if build new ui bundle, false if choose from existing one in ui_bundle_tag parameter')
}

def uiBundleTag() {
    return _paramExtended('ui_bundle_tag', 'rancher_cluster_name,rancher_project_name', getUIImagesList(), 'Choose image tag for UI')
}

def okapiVersion() {
    return _paramExtended('okapi_version', 'folio_repository,folio_branch', getOkapiVersions(), 'Choose Okapi version')
}

def backendModule() {
    return _paramExtended('backend_module', '', getBackendModulesList(), 'Choose backend module')
}

def edgeModule() {
    return _paramExtended('edge_module', '', getEdgeModulesList(), 'Choose edge module')
}

def restoreFromBackup() {
    return _paramBoolean('restore_from_backup', false, 'Turn on the option if you would like to restore PostgreSQL DB from backup. Modules versions will be restored up to the same state as at the moment when backup was created')
}

def backupType() {
    return _paramChoice('backup_type', dbList(), 'Select type of db')
}

def backupName() {
    return _paramString('backup_name', '', 'Provide full path/name of DB backup placed in folio-postgresql-backups AWS s3 bucket (e.g. folio-dev/unam/backup_2022-08-23T09:43:59-volodymyr-kartsev/backup_2022-08-23T09:43:59-volodymyr-kartsev.psql)')
}

def tenantIdToBackupModulesVersions() {
    return _paramString('tenant_id_to_backup_modules_versions', defaultTenant().id, "Choose for which tenant you would like to save modules versions. Default is diku")
}

def crudOperations(){
    return _paramChoice('operation_type', crudOperationsList(), "Choose secret operation")
}

def mvnOptions(String options = '') {
    return _paramString('mvn_options', options, 'Put additional maven options if needed')
}
