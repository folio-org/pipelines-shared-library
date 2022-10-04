package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import org.folio.Constants

class Tools {
    Object steps

    Tools(Object steps) {
        this.steps = steps
    }

    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    String copyResourceFileToWorkspace(String srcPath) {
        String destPath = steps.env.WORKSPACE + File.separator + new File(srcPath).getName()
        steps.writeFile file: destPath, text: steps.libraryResource(srcPath)
        logger.info("Copied ${srcPath} to ${steps.env.WORKSPACE}")
        return destPath
    }

    List getGitHubTeamsIds(List teams) {
        steps.withCredentials([steps.usernamePassword(credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID, passwordVariable: 'token', usernameVariable: 'username')]) {
            String url = "https://api.github.com/orgs/folio-org/teams?per_page=100"
            ArrayList headers = [[ name:"Authorization", value: "Bearer " + steps.token]]
            def response = new HttpClient(steps).getRequest(url, headers)
            List ids = []
            if (response.status == HttpURLConnection.HTTP_OK) {
                teams.each { team ->
                    try {
                        ids.add("github_team://" + jsonParse(response.content).find { it["name"] == team }["id"])
                    } catch (ignored) {
                        logger.warning("Unable to get GitHub id for team ${team}")
                    }
                }
            }else{
                logger.warning(new HttpClient(steps).buildHttpErrorMessage(response))
            }
            return ids
        }
    }

    /**
     * Parse json object to groovy map
     * @param json
     * @return
     */
    @NonCPS
    static def jsonParse(String json) {
        new JsonSlurperClassic().parseText(json)
    }

    /**
     * Remove last char from string
     * @param str
     * @return
     */
    @NonCPS
    static def removeLastChar(String str) {
        return (str == null || str.length() == 0) ? null : (str.substring(0, str.length() - 1))
    }

    /**
     * Evaluate groovy expression
     * @param expression groovy expression
     * @param parameters parameters
     * @return result
     */
    static def eval(String expression, Map<String, Object> parameters) {
        Binding b = new Binding();
        parameters.each { k, v ->
            b.setVariable(k, v);
        }
        GroovyShell sh = new GroovyShell(b);
        return sh.evaluate(expression);
    }

    List findAllRegex(String list, String regex) {
        return new JsonSlurperClassic().parseText(list).findAll{ s -> s ==~ /${regex}/ }
    }

}
