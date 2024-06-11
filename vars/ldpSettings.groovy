import org.folio.rest.model.LdpConfig

static LdpConfig ldpConfig(Map args = [:]) {
  return new LdpConfig(
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

def get_ldp_queries_gh_token() {
  withCredentials([string(credentialsId: 'ldp_queries_gh_token', variable: 'ldp_queries_gh_token')]) {
    return ldp_queries_gh_token
  }
}
