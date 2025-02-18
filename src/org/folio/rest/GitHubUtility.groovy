package org.folio.rest

import org.folio.utilities.HttpClient
import org.folio.utilities.Tools

@Deprecated
class GitHubUtility implements Serializable {

  private Object steps

  private Tools tools = new Tools(steps)

  private HttpClient http = new HttpClient(steps)

  GitHubUtility(Object steps) {
    this.steps = steps
  }

  /**
   * Get json object of modules from github
   * @param fileName
   */
  def getJsonModulesList(String repository, String branch, String fileName) {
    String url = 'https://raw.githubusercontent.com/folio-org/' + repository + '/' + branch + '/' + fileName
    def res = http.getRequest(url, [], true)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content)
    } else {
      throw new Exception("Can not get modules list from: ${url}")
    }
  }

  /**
   * Build enable list of backend and frontend modules
   * @param repository
   * @param branch
   * @return
   */
  List getEnableList(String repository, String branch) {
    return getJsonModulesList(repository, branch, 'install.json')
  }
}
