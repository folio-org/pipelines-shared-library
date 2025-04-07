package org.folio.rest

import org.folio.utilities.HttpClient
import org.folio.utilities.Tools

@Deprecated
/**
 * Functions from this class should be or already moved to other places
 */
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
  List buildDiscoveryList(Map isntall_map) {
    List discoveryList = []
    isntall_map.findAll { it.key.startsWith("mod-") }.collect {
      discoveryList << [srvcId: "${it.key}-${it.value}",
                        instId: "${it.key}-${it.value}",
                        url   : "http://${it.key}"]
    }
    return discoveryList
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

  /**
   * List of additional Eureka modules
   * @param repository
   * @param branch
   * @return
   */
  List getEurekaList(String repository, String branch) {
    return getJsonModulesList(repository, branch, 'eureka-platform.json')
  }

  /**
   *  Parsing the install.json file and creating a map of module names and versions.
   * @param install_json
   * @return
   */
  static Map getModulesVersionsMap(List install_json) {
    Map modules_versions_map = [:]
    install_json*.id.each {
      def (_, module_name, version) = (it =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)[0]
      modules_versions_map << [(module_name): version]
    }
    return modules_versions_map
  }

  static Map getBackendModulesMap(Map install_map) {
    return install_map.findAll { it.key.startsWith("mod-") }
  }

  static Map getEdgeModulesMap(Map install_map) {
    return install_map.findAll { it.key.startsWith("edge-") }
  }

  static Map getMgrModulesMap(Map install_map) {
    return install_map.findAll { it.key.startsWith("mgr-*") }
  }
}
