package org.folio.models

class LdpConfig {

  String dbHost

  String dbPort = '5432'

  String ldpDbName = 'ldp'

  String ldpDbUserName = 'ldp'

  String ldpDbUserPassword

  String ldpAdminDbUserName = 'ldpadmin'

  String ldpAdminDbUserPassword

  String ldpConfigDbUserName = 'ldpconfig'

  String ldpConfigDbUserPassword

  String sqconfigRepoName = 'ldp-queries'

  String sqconfigRepoOwner = 'RandomOtherGuy'

  String sqconfigRepoToken
}
