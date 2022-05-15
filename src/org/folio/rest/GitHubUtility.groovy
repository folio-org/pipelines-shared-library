package org.folio.rest

import org.folio.utilities.Logger
import org.folio.utilities.HttpClient
import org.folio.utilities.Tools

class GitHubUtility implements Serializable {

    private Object steps

    private Tools tools = new Tools(steps)

    private HttpClient http = new HttpClient(steps)

    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    GitHubUtility(Object steps) {
        this.steps = steps
    }

    /**
     * Get json object of modules from github
     * @param fileName
     */
    def getJsonModulesList(String repository, String branch, String fileName) {
        String url = OkapiConstants.RAW_GITHUB_URL + '/' + repository + '/' + branch + '/' + fileName
        def res = http.getRequest(url, [], true)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return tools.jsonParse(res.content)
        } else {
            throw new Exception("Can not get modules list from: ${url}")
        }
    }

    /**
     * Build discovery list of backend modules
     * @param repository
     * @param branch
     * @return
     */
    List buildDiscoveryList(String repository, String branch) {
        List discoveryList = []
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
    List buildEnableList(String repository, String branch) {
        return getJsonModulesList(repository, branch, 'install.json')
    }
}
