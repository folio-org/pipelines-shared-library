package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import groovy.text.GStringTemplateEngine
import org.folio.Constants
import org.folio.rest.model.LdpConfig
import org.folio.rest.model.OkapiUser
import org.folio.utilities.model.Project

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

  String copyResourceFileToCurrentDirectory(String srcPath) {
    String currentDir = steps.sh(script: "pwd", returnStdout: true,).trim()
    String destPath = currentDir + File.separator + new File(srcPath).getName()
    steps.writeFile file: destPath, text: steps.libraryResource(srcPath)
    logger.info("Copied ${srcPath} to ${currentDir}")
    return destPath
  }

  List getGitHubTeamsIds(List teams) {
    steps.withCredentials([steps.usernamePassword(credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID, passwordVariable: 'token', usernameVariable: 'username')]) {
      String url = "https://api.github.com/orgs/folio-org/teams?per_page=100"
      ArrayList headers = [[name: "Authorization", value: "Bearer " + steps.token]]
      def response = new HttpClient(steps).getRequest(url, headers)
      List ids = []
      if (response.status == HttpURLConnection.HTTP_OK) {
        teams.each { team ->
          try {
            ids.add(jsonParse(response.content).find { it["name"] == team }["id"])
          } catch (ignored) {
            logger.warning("Unable to get GitHub id for team ${team}")
          }
        }
      } else {
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
    Binding b = new Binding()
    parameters.each { k, v ->
      b.setVariable(k, v)
    }
    GroovyShell sh = new GroovyShell(b)
    return sh.evaluate(expression)
  }

  List findAllRegex(String list, String regex) {
    return new JsonSlurperClassic().parseText(list).findAll { s -> s ==~ /${regex}/ }
  }

  void createFileFromString(String filePathName, String fileContent) {
    steps.writeFile file: filePathName, text: """${fileContent}"""
    logger.info("Created file in ${filePathName}")
  }

  String build_ldp_setting_json(Project project_config, OkapiUser admin_user, String template_name, LdpConfig ldpConfig,
                                db_host, folio_db_name, folio_db_user, folio_db_password) {
    def binding = [
      tenant_id             : project_config.getTenant().getId(),
      tenant_admin_user     : admin_user.getUsername(),
      tenant_admin_password : admin_user.getPassword(),
      okapi_url             : "https://${project_config.getDomains().okapi}",
      deployment_environment: project_config.getConfigType(),
      db_host               : db_host,
      db_port               : 5432,
      folio_db_name         : folio_db_name,
      folio_db_user         : folio_db_user,
      folio_db_password     : folio_db_password,
      ldp_db_name           : ldpConfig.getLdp_db_name(),
      ldp_db_user_name      : ldpConfig.getLdp_db_user_name(),
      ldp_db_user_password  : ldpConfig.getLdp_db_user_password(),
      sqconfig_repo_name    : ldpConfig.getSqconfig_repo_name(),
      sqconfig_repo_owner   : ldpConfig.getSqconfig_repo_owner(),
      sqconfig_repo_token   : ldpConfig.getSqconfig_repo_token()
    ]


    def content = steps.readFile this.copyResourceFileToWorkspace("okapi/configurations/" + template_name)
    String body = new GStringTemplateEngine().createTemplate(content).make(binding).toString()
    return body
  }

  def retry(times, delayMillis, closure) {
    int attempt = 0
    while (attempt < times) {
      try {
        return closure()
      } catch (Exception e) {
        attempt++
        if (attempt >= times) {
          throw e // rethrow the last exception if max attempts reached
        }
        logger.warning("Attempt ${attempt} failed, retrying in ${delayMillis}ms...")
        sleep(delayMillis)
      }
    }
  }
}
