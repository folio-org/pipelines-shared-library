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

def branch(String paramName = 'FOLIO_BRANCH', String repository = 'platform-complete') {
    return _paramExtendedSingleSelect(paramName, '', folioStringScripts.getRepositoryBranches(repository), "(Required) Select what '${repository}' branch use for build")
}

def okapiVersion() {
    return _paramExtendedSingleSelect('OKAPI_VERSION', 'FOLIO_BRANCH', folioStringScripts.getOkapiVersions(), 'Select okapi version')
}

def loadReference(boolean value = true) {
    return _paramBoolean('LOAD_REFERENCE', value, 'Set to true to load reference data during install')
}

def loadSample(boolean value = true) {
    return _paramBoolean('LOAD_SAMPLE', value, 'Set to true to load sample data during install')
}

def configType() {
    return _paramChoice('CONFIG_TYPE', Constants.AWS_EKS_NAMESPACE_CONFIGS, 'Select deployment config type')
}

def pgType() {
    return _paramChoice('POSTGRESQL', Constants.AWS_INTEGRATED_SERVICE_TYPE, 'Select build agent')
}

def kafkaType() {
    return _paramChoice('KAFKA', Constants.AWS_INTEGRATED_SERVICE_TYPE, 'Select build agent')
}

def opensearchType() {
    return _paramChoice('OPENSEARCH', Constants.AWS_INTEGRATED_SERVICE_TYPE.reverse(), 'Select build agent')
}

def s3Type() {
    return _paramChoice('S3_BUCKET', Constants.AWS_INTEGRATED_SERVICE_TYPE, 'Select build agent')
}
