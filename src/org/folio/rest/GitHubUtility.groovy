package org.folio.rest

import org.folio.http.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class GitHubUtility implements Serializable {
    def steps
    static private String githubUrl = 'https://raw.githubusercontent.com/folio-org'
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    GitHubUtility(steps) {
        this.steps = steps
    }

    /**
     * Get json object of modules from github
     * @param fileName
     */
    @NonCPS
    def getJsonModulesList(String repository, String branch, String fileName) {
        String uri = '/' + repository + '/' + branch + '/' + fileName
        def res = http.request(method: 'GET', url: githubUrl, uri: uri)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Can not get modules list from: ' + githubUrl + uri)
        }
    }

    /**
     * Build discovery list of backend modules
     * @param repository
     * @param branch
     * @return
     */
    @NonCPS
    ArrayList buildDiscoveryList(String repository, String branch) {
        ArrayList discoveryList = []
        getJsonModulesList(repository, branch, 'okapi-install.json').each {
            String version = (it['id'] =~ /\d+\.\d+\.\d+-.*\.\d+|\d+\.\d+.\d+$/).findAll()[0]
            discoveryList << [srvcId: it['id'],
                              instId: it['id'],
                              url   : 'http://' + tools.removeLastChar(it['id'] - version)]
        }
        logger.info('Modules discovery list successfully built from repo: ' + repository + ' branch: ' + branch)
        return discoveryList
    }

    /**
     * Build enable list of backend and frontend modules
     * @param repository
     * @param branch
     * @return
     */
    @NonCPS
    ArrayList buildEnableList(String repository, String branch) {
        ArrayList enableList = []
        enableList.addAll(getJsonModulesList(repository, branch, 'okapi-install.json'))
        enableList.addAll(getJsonModulesList(repository, branch, 'stripes-install.json'))
        logger.info('Modules enable list successfully built from repo: ' + repository + ', branch: ' + branch)
        return enableList
    }
    //TODO Add Edge modules list build
}
