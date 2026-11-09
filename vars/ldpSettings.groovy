import org.folio.Constants
import org.folio.models.LdpConfig

static org.folio.rest.model.LdpConfig ldpConfig(Map args = [:]) {
  return new org.folio.rest.model.LdpConfig(
    ldp_db_name: 'ldp',
    ldp_db_user_name: 'ldpadmin',
    ldp_db_user_password: args.ldp_db_user_password,
    ldpadmin_db_user_name: 'ldpadmin',
    ldpadmin_db_user_password: args.ldp_db_user_password,
    ldpconfig_db_user_name: 'ldpconfig',
    ldpconfig_db_user_password: args.ldp_db_user_password,
    sqconfig_repo_name: 'ldp-queries',
    sqconfig_repo_owner: 'RandomOtherGuy',
    sqconfig_repo_token: args.ldp_queries_gh_token
  )
}

/**
 * Builds the Reporting app (mod-reporting/mod-ldp) database configuration for the shared MetaDB instance.
 * Credentials are read from the Jenkins credential store, so the password is never kept in the repository.
 *
 * @return LdpConfig with the MetaDB connection details, or null when the credential is not available.
 */
LdpConfig metadbConfig() {
  LdpConfig config = null

  try {
    withCredentials([usernamePassword(credentialsId: Constants.METADB_CREDENTIALS_ID,
                                      usernameVariable: 'METADB_USERNAME',
                                      passwordVariable: 'METADB_PASSWORD')]) {
      config = new LdpConfig(
        dbHost            : Constants.METADB_HOST,
        dbPort            : Constants.METADB_PORT,
        ldpDbName         : Constants.METADB_DB_NAME,
        ldpDbUserName     : METADB_USERNAME,
        ldpDbUserPassword : METADB_PASSWORD
      )
    }
  } catch (Exception e) {
    echo "WARNING: Jenkins credential '${Constants.METADB_CREDENTIALS_ID}' is not available, " +
      "Reporting app will be deployed without database configuration: ${e.getMessage()}"
  }

  return config
}

/**
 * Checks whether the Reporting app backend is a part of the provided module list.
 *
 * @param modules Install JSON of an application, tenant or namespace.
 * @return true when mod-reporting or mod-ldp is present.
 */
static boolean isReportingEnabled(def modules) {
  return Constants.REPORTING_MODULES.any { moduleName -> modules?.getModuleByName(moduleName) }
}

def get_ldp_queries_gh_token() {
  withCredentials([string(credentialsId: 'ldp-queries-gh-token', variable: 'ldp_queries_gh_token')]) {
    return ldp_queries_gh_token
  }
}
