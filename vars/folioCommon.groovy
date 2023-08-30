import groovy.json.JsonSlurperClassic

void defaultJobWrapper(Closure stages, boolean checkoutGit = true) {
    try {
        if (checkoutGit) {
            stage('Checkout') {
                checkout scm
            }
        }
        stages()
    } catch (e) {
        println "Caught exception: ${e}"
        println "Stack trace:"
        e.printStackTrace()
        error(e.getMessage())
    } finally {
        stage('Cleanup') {
            cleanWs notFailBuild: true
        }
    }
}

String getOkapiVersion(folio_repository, folio_branch) {
    def installJson = new URL('https://raw.githubusercontent.com/folio-org/' + folio_repository + '/' + folio_branch + '/install.json').openConnection()
    if (installJson.getResponseCode().equals(200)) {
        String okapi = new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.find{it ==~ /okapi-.*/}
        if(okapi){
            return okapi - 'okapi-'
        } else {
            error("Can't get okapi version from install.json in ${folio_branch} branch of ${folio_repository} repository!" )
        }
    }
    error("There is no install.json in ${folio_branch} branch of ${folio_repository} repository!" )
}
