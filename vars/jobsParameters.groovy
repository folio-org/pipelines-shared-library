import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput

void call() {}

static String pgDefaultPassword() {
    return 'postgres_password_123!'
}

static String pgAdminDefaultPassword() {
    return 'SuperSecret'
}

static HashMap defaultTenant() {
    return [id           : 'diku',
            name         : 'Datalogisk Institut',
            description  : 'Danish Library Technology Institute',
            loadReference: true,
            loadSample   : true]
}

static ArrayList repositoriesList() {
    return ['complete',
            'core']
}


static ArrayList rancherClustersList() {
    return ['folio-testing',
            'folio-dev',
            'folio-scratch', //Deprecated
            'folio-perf',
            'folio-tmp']
}

@NonCPS
static ArrayList envTypeList() {
    return ['development',
            'performance',
            'testing']
}


@NonCPS
static ArrayList testingEnvironmentsList() {
    return ['karate',
            'cypress',
            'sprint']
}

@NonCPS
static ArrayList devEnvironmentsList() {
    return ['bama',
            'concorde',
            'core-platform',
            'ebsco-core',
            'falcon',
            'firebird',
            'folijet',
            'metadata',
            'prokopovych',
            'scout',
            'spanish',
            'spitfire',
            'sprint-testing', //Deprecated
            'stripes-force',
            'thor',
            'thunderjet',
            'unam',
            'vega',
            'volaris',
            'volaris-2nd']
}

@NonCPS
static ArrayList perfEnvironmentsList() {
    return []
}

@NonCPS
static ArrayList testEnvironmentsList() {
    return ["test"]
}

@NonCPS
static String generateProjectNamesMap() {
    return JsonOutput.toJson(['folio-testing': testingEnvironmentsList(),
                              'folio-dev'    : devEnvironmentsList(),
                              'folio-scratch': devEnvironmentsList(),
                              'folio-perf'   : perfEnvironmentsList(),
                              'folio-tmp'    : testEnvironmentsList()])
}

static String getRepositoryBranches() {
    return '''import groovy.json.JsonSlurperClassic
def get = new URL('https://api.github.com/repos/folio-org/platform-' + folio_repository + '/branches?per_page=100').openConnection()
if (get.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(get.getInputStream().getText()).name
}
'''
}
static String getJenkinsAgents() {
    return 'return ['jenkins-agent-java11']'
}

static String getUIImagesList() {
    return '''import groovy.json.JsonSlurperClassic
def get = new URL('https://docker.dev.folio.org/v2/platform-complete/tags/list').openConnection()
if (get.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(get.getInputStream().getText()).tags.sort().reverse().findAll{it.startsWith(rancher_cluster_name.trim() + '-' + rancher_project_name.trim())}
}
'''
}

static String getProjectNames() {
    return """import groovy.json.JsonSlurperClassic
def projectNamesList = new JsonSlurperClassic().parseText('${generateProjectNamesMap()}')
return projectNamesList[rancher_cluster_name]
"""
}

static String getOkapiVersions() {
    return '''import groovy.json.JsonSlurperClassic
def installJson = new URL('https://raw.githubusercontent.com/folio-org/platform-' + folio_repository + '/' + folio_branch + '/install.json').openConnection()
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

private def _paramChoice(String name, ArrayList options, String description) {
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

def rancherClusters() {
    return _paramChoice('rancher_cluster_name', rancherClustersList(), '(Required) Select cluster')
}

def envType() {
    return _paramChoice('env_config', envTypeList(), '(Required) Select config file')
}

def projectName() {
    return _paramExtended('rancher_project_name', 'rancher_cluster_name', getProjectNames(), '(Required) Select project to operate')
}


def repository() {
    return _paramChoice('folio_repository', repositoriesList(), '(Required) Select source repository')
}

def enableModules() {
    return _paramBoolean('enable_modules', true, 'True if modules should be registered and enabled in Okapi')
}

def tenantId() {
    return _paramString('tenant_id', defaultTenant().id, '(Required) Id used for tenant creation')
}

def tenantName() {
    return _paramString('tenant_name', defaultTenant().name, '(Optional) Name used for tenant creation')
}

def tenantDescription() {
    return _paramString('tenant_description', defaultTenant().description, '(Optional) Description used for tenant creation')
}

def loadReference() {
    return _paramBoolean('load_reference', defaultTenant().loadReference, 'True if reference data should be applied')
}

def reindexElasticsearch() {
    return _paramBoolean('reindex_elastic_search', true, 'True if need to reindex modules')
}

def recreateindexElasticsearch() {
    return _paramBoolean('recreate_index_elastic_search', false, 'True if need to recreate index modules , default value: false')
}

def loadSample() {
    return _paramBoolean('load_sample', defaultTenant().loadSample, 'True if sample data should be applied')
}

def pgPassword() {
    return _paramPassword('pg_password', pgDefaultPassword(), '(Optional) Password for PostgreSQL database')
}

def pgAdminPassword() {
    return _paramPassword('pgadmin_password', pgAdminDefaultPassword(), '(Optional) Password for pgAdmin login')
}

def folioBranch() {
    return _paramExtended('folio_branch', 'folio_repository', getRepositoryBranches(), '(Required) Choose what platform-core or platform-complete branch to build from')
}

def frontendImageTag() {
    return _paramExtended('frontend_image_tag', 'rancher_cluster_name,rancher_project_name', getUIImagesList(), '(Required) Choose image tag for UI')
}

def okapiVersion() {
    return _paramExtended('okapi_version', 'folio_repository,folio_branch', getOkapiVersions(), '(Required) Choose Okapi version')
}

def restorePostgresqlFromBackup() {
    return _paramBoolean('restore_postgresql_from_backup', false, 'Turn on the option if you would like to restore PostgreSQL DB from backup. Modules versions will be restored up to the same state as at the moment when backup was created')
}

def restorePostgresqlBackupName() {
    return _paramString('restore_postgresql_backup_name', '', 'Provide full path/name of DB backup placed in folio-postgresql-backups AWS s3 bucket (e.g. folio-dev/unam/backup_2022-08-23T09:43:59-volodymyr-kartsev/backup_2022-08-23T09:43:59-volodymyr-kartsev.psql)')
}

def tenantIdToBackupModulesVersions() {
    return _paramString('tenant_id_to_backup_modules_versions', defaultTenant().id, "Choose for which tenant you would like to save modules versions. Default is diku")
}

def tenantIdToRestoreModulesVersions() {
    return _paramString('tenant_id_to_restore_modules_versions', defaultTenant().id, "Choose for which tenant you would like to restore Environment and modules versions. Default is diku. The option is active only when restore_postgresql_from_backup is turned on!")
}
def agents() {
    return _paramExtended('agent','', getJenkinsAgents(), 'Choose for which jenkins agent you want to build from')
}