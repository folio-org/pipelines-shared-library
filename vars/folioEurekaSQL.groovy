import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.RancherNamespace
import org.folio.utilities.Logger
import org.folio.utilities.Tools
import groovy.json.JsonSlurperClassic

void initSQL(RancherNamespace namespace, List DBs = ['keycloak', 'kong'], String pgMajorVersion = '13') {
  Logger logger = new Logger(this, 'initSQL')
  new Tools(this).copyResourceFileToWorkspace("eureka/eureka_db.tpl")
  DBs.each { db ->
    String tpl = readFile file: "./eureka_db.tpl"
    LinkedHashMap data = [db_name: "${db}", db_username: "${if (db == 'keycloak') { 'keycloak_admin' } else { 'kong_admin' }}", db_password: Constants.PG_ROOT_DEFAULT_PASSWORD]
    writeFile encoding: 'utf-8', file: "${db}.sql", text: (new StreamingTemplateEngine().createTemplate(tpl).make(data)).toString()
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      String pgadmin_pod = sh(script: "kubectl get pod --namespace ${namespace.getNamespaceName()} --no-headers | grep pgadmin | awk '{print \$1}'", returnStdout: true).trim()
      logger.info(sh(script: "ls -la", returnStdout: true))
      sh(script: "kubectl cp ./${db}.sql ${namespace.getNamespaceName()}/${pgadmin_pod}:/tmp/${db}.sql", returnStdout: true)
      def connInfo = sh(script: "kubectl exec ${pgadmin_pod} --namespace ${namespace.getNamespaceName()} -- /bin/cat /pgadmin4/servers.json", returnStdout: true)
      def connStr = new JsonSlurperClassic().parseText("${connInfo}")
        try {
          logger.info("Trying to init Eureka DBs...")
          sh(script: "kubectl exec pod/${pgadmin_pod} --namespace ${namespace.getNamespaceName()} -- /usr/local/pgsql-${pgMajorVersion}/psql " +
            "--host=${connStr["Servers"]["pg"]["Host"]} --port=\"5432\" --username=\"postgres\" --echo-all --file=/tmp/${db}.sql", returnStdout: true)
        } catch (Exception e) {
          logger.error("Error: ${e.getMessage()}")
        }
    }
  }
}
