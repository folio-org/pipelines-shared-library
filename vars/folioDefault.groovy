import org.folio.Constants
import org.folio.models.*

OkapiUser adminOkapiUser(String username, def password) {
  return new OkapiUser(username, password)
    .withFirstName(username.capitalize())
    .withLastName('ADMINISTRATOR')
    .withEmail("$username@example.org")
    .withPermissions(["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"])
    .withBarcode(folioTools.generateRandomDigits(8))
    .withGroup("staff")
    .withType("staff")
}

Map<String, OkapiTenantConsortia> consortiaTenants(Object installJson = [], InstallRequestParams installQueryParameters = new InstallRequestParams()) {
  SmtpConfig smtp = null
  String kbApiKey = ''
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.EMAIL_SMTP_CREDENTIALS_ID,
                    accessKeyVariable: 'EMAIL_USERNAME',
                    secretKeyVariable: 'EMAIL_PASSWORD'],
                   string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'KB_API_KEY')]) {
    smtp = new SmtpConfig(Constants.EMAIL_SMTP_SERVER, Constants.EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, Constants.EMAIL_FROM)
    kbApiKey = KB_API_KEY
  }
  installQueryParameters.addTenantParameter('centralTenantId', 'consortium')
  return [
    consortium: new OkapiTenantConsortia('consortium', true)
      .withTenantName('Consortium')
      .withTenantDescription('Central office (created via Jenkins)')
      .withTenantCode('MCO')
      .withConsortiaName('Mobius')
      .withAdminUser(adminOkapiUser('consortium_admin', 'admin'))
      .withInstallJson(installJson.collect())
      .withIndex(new Index(true, true))
      .withInstallRequestParams(installQueryParameters.clone())
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    university: new OkapiTenantConsortia('university')
      .withTenantName('University')
      .withTenantDescription('University (created via Jenkins)')
      .withTenantCode('UNI')
      .withConsortiaName('Mobius')
      .withAdminUser(adminOkapiUser('university_admin', 'admin'))
      .withInstallJson(installJson.collect())
      .withInstallRequestParams(installQueryParameters.clone())
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    college   : new OkapiTenantConsortia('college')
      .withTenantName('College')
      .withTenantDescription('College (created via Jenkins)')
      .withTenantCode('COL')
      .withConsortiaName('Mobius')
      .withAdminUser(adminOkapiUser('college_admin', 'admin'))
      .withInstallJson(installJson.collect())
      .withInstallRequestParams(installQueryParameters.clone())
      .withConfiguration(new OkapiConfig().withSmtp(smtp))
  ]
}

Map<String, OkapiTenant> tenants() {
  SmtpConfig smtp = null
  String kbApiKey = ''
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.EMAIL_SMTP_CREDENTIALS_ID,
                    accessKeyVariable: 'EMAIL_USERNAME',
                    secretKeyVariable: 'EMAIL_PASSWORD'],
                   string(credentialsId: Constants.EBSCO_KB_CREDENTIALS_ID, variable: 'KB_API_KEY')]) {
    smtp = new SmtpConfig(Constants.EMAIL_SMTP_SERVER, Constants.EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, Constants.EMAIL_FROM)
    kbApiKey = KB_API_KEY
  }
  return [
    test           : new OkapiTenant('test')
      .withTenantName('test')
      .withTenantDescription('test')
      .withAdminUser(adminOkapiUser('test_admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    diku           : new OkapiTenant('diku')
      .withTenantName('Datalogisk Institut')
      .withTenantDescription('Danish Library Technology Institute')
      .withAdminUser(adminOkapiUser('diku_admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    aqa            : new OkapiTenant('aqa')
      .withTenantName('AQA')
      .withTenantDescription('AQA (created via Jenkins)')
      .withAdminUser(adminOkapiUser('aqa_admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    qa             : new OkapiTenant('qa')
      .withTenantName('QA')
      .withTenantDescription('QA (created via Jenkins)')
      .withAdminUser(adminOkapiUser('aqa_admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    fs09000000     : new OkapiTenant('fs09000000')
      .withTenantName('Bug Fest')
      .withTenantDescription('fs09000000 bug-fest created via Jenkins')
      .withAdminUser(adminOkapiUser('folio', 'folio'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    cs00000int     : new OkapiTenantConsortia('cs00000int', true)
      .withTenantName('Central tenant')
      .withTenantDescription('cs00000int, Central tenant created via Jenkins')
      .withTenantCode('CEN')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECSAdmin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
    cs00000int_0001: new OkapiTenantConsortia('cs00000int_0001')
      .withTenantName('Colleague tenant')
      .withTenantDescription('cs00000int_0001, Colleague tenant created via Jenkins')
      .withTenantCode('COL')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0001Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0002: new OkapiTenantConsortia('cs00000int_0002')
      .withTenantName('Professional tenant')
      .withTenantDescription('cs00000int_0002, Professional tenant created via Jenkins')
      .withTenantCode('PROF')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0002Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0003: new OkapiTenantConsortia('cs00000int_0003')
      .withTenantName('School tenant')
      .withTenantDescription('cs00000int_0003, School tenant created via Jenkins')
      .withTenantCode('SCHO')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0003Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0004: new OkapiTenantConsortia('cs00000int_0004')
      .withTenantName('Special tenant')
      .withTenantDescription('cs00000int_0004, Special tenant created via Jenkins')
      .withTenantCode('SPE')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0004Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0005: new OkapiTenantConsortia('cs00000int_0005')
      .withTenantName('University tenant')
      .withTenantDescription('cs00000int_0005, University tenant created via Jenkins')
      .withTenantCode('UNI')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0005Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0006: new OkapiTenantConsortia('cs00000int_0006')
      .withTenantName('AQA ECS tenant')
      .withTenantDescription('cs00000int_0006, AQA ECS tenant created via Jenkins')
      .withTenantCode('AQA')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0006Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0007: new OkapiTenantConsortia('cs00000int_0007')
      .withTenantName('AQA2 ECS tenant')
      .withTenantDescription('cs00000int_0007, AQA2 ECS tenant created via Jenkins')
      .withTenantCode('AQA2')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0007Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0008: new OkapiTenantConsortia('cs00000int_0008')
      .withTenantName('Public tenant')
      .withTenantDescription('cs00000int_0008, Public tenant created via Jenkins')
      .withTenantCode('PUB')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0008Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0009: new OkapiTenantConsortia('cs00000int_0009')
      .withTenantName('Medical tenant')
      .withTenantDescription('cs00000int_0009, Medical tenant created via Jenkins')
      .withTenantCode('MED')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0009Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0010: new OkapiTenantConsortia('cs00000int_0010')
      .withTenantName('Workflow tenant')
      .withTenantDescription('cs00000int_0010, Workflow tenant created via Jenkins')
      .withTenantCode('WORK')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0010Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp)),
    cs00000int_0011: new OkapiTenantConsortia('cs00000int_0011')
      .withTenantName('Management Division tenant')
      .withTenantDescription('cs00000int_0011, Management Division tenant created via Jenkins')
      .withTenantCode('MGMT')
      .withConsortiaName('CONSORTIA')
      .withAdminUser(adminOkapiUser('ECS0011Admin', 'admin'))
      .withConfiguration(new OkapiConfig().withSmtp(smtp))
  ]
}
