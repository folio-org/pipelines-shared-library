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
    return ['core',
            'complete']
}

static ArrayList rancherClustersList() {
    return ['folio-testing',
            'folio-scratch',
            'folio-perf']
}

@NonCPS
static ArrayList testingEnvironmentsList() {
    return ['karate',
            'cypress',
            'test']
}

@NonCPS
static ArrayList scratchEnvironmentsList() {
    return ['bama',
            'concorde',
            'core-platform',
            'ebsco-core',
            'falcon',
            'firebird',
            'folijet',
            'metadata',
            'metadata-spitfire',
            'prokopovych',
            'scout',
            'spanish',
            'spitfire',
            'stripes-force',
            'template',
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
static String generateProjectNamesMap() {
    return JsonOutput.toJson([
        'folio-testing': testingEnvironmentsList(),
        'folio-scratch': scratchEnvironmentsList(),
        'folio-perf'   : perfEnvironmentsList()
    ])
}

static String getRepositoryBranches() {
    return '''import groovy.json.JsonSlurperClassic
def get = new URL('https://api.github.com/repos/folio-org/platform-' + folio_repository + '/branches?per_page=100').openConnection();
def responseCode = get.getResponseCode();
if (responseCode.equals(200)) {
    def branchesList = new JsonSlurperClassic().parseText(get.getInputStream().getText())
    return branchesList.name
}
'''
}

static String getDockerImagesList() {
    return '''import groovy.json.JsonSlurperClassic
def get = new URL('https://docker.dev.folio.org/v2/platform-complete/tags/list').openConnection();
def responseCode = get.getResponseCode();
if (responseCode.equals(200)) {
    def imagesTagsList = new JsonSlurperClassic().parseText(get.getInputStream().getText())
    return imagesTagsList.tags.sort().reverse().findAll {it ==~ project_name + '.*'}
}
'''
}

static String getProjectNames() {
    return """import groovy.json.JsonSlurperClassic
    def projectNamesList = new JsonSlurperClassic().parseText('${generateProjectNamesMap()}')
    return projectNamesList[rancher_cluster_name]
    """
}

static String getOkapiVersion() {
    return '''import groovy.json.JsonSlurperClassic
def get = new URL('https://api.github.com/repos/folio-org/okapi/tags').openConnection();
def responseCode = get.getResponseCode();
if (responseCode.equals(200)) {
    def versionsList = new JsonSlurperClassic().parseText(get.getInputStream().getText())
    return versionsList*.name.collect{it -> return it - 'v'}
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
    return [
        $class              : 'CascadeChoiceParameter',
        choiceType          : 'PT_SINGLE_SELECT',
        description         : description,
        filterLength        : 1,
        filterable          : true,
        name                : name,
        referencedParameters: reference,
        script              : [
            $class        : 'GroovyScript',
            fallbackScript: [
                classpath: [],
                sandbox  : false,
                script   : 'return ["error"]'
            ],
            script        : [classpath: [],
                             sandbox  : false,
                             script   : script
            ]
        ]
    ]
}

def rancherClusters() {
    return _paramChoice('rancher_cluster_name', rancherClustersList(), '(Required) Select cluster')
}

def projectName() {
    return _paramExtended('project_name', 'rancher_cluster_name', getProjectNames(), '(Required) Select project to operate')
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

def stripesImageTag() {
    return _paramExtended('stripes_image_tag', 'project_name', getDockerImagesList(), '(Required) Choose image tag for UI')
}

def okapiVersion() {
    return _paramExtended('okapi_version', '', getOkapiVersion(), '(Required) Choose Okapi version')
}
