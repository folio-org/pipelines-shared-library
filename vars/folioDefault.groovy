import org.folio.Constants
import org.folio.models.Index
import org.folio.models.InstallRequestParams
import org.folio.models.SmtpConfig
import org.folio.models.OkapiTenant
import org.folio.models.OkapiTenantConsortia
import org.folio.models.OkapiUser
import org.folio.models.OkapiConfig

static OkapiUser adminOkapiUser(String username, def password) {
    return new OkapiUser(username, password)
        .withFirstName(username.capitalize())
        .withLastName('ADMINISTRATOR')
        .withEmail("$username@example.org")
        .withPermissions(["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"])
        .withBarcode("88888888")
        .withGroup("staff")
}

Map<String, OkapiTenantConsortia> consortiaTenants(Object installJson, InstallRequestParams installQueryParameters = new InstallRequestParams()) {
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
    installQueryParameters.addTenantParameter('centralTenantId','consortium')
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
        diku      : new OkapiTenant('diku')
            .withTenantName('Datalogisk Institut')
            .withTenantDescription('Danish Library Technology Institute')
            .withAdminUser(adminOkapiUser('diku_admin', 'admin'))
            .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
        aqa       : new OkapiTenant('aqa')
            .withTenantName('AQA')
            .withTenantDescription('AQA (created via Jenkins)')
            .withAdminUser(adminOkapiUser('aqa_admin', 'admin'))
            .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
        qa        : new OkapiTenant('qa')
            .withTenantName('QA')
            .withTenantDescription('QA (created via Jenkins)')
            .withAdminUser(adminOkapiUser('aqa_admin', 'admin'))
            .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey)),
        fs09000000: new OkapiTenant('fs09000000')
            .withTenantName('Bug Fest')
            .withTenantDescription('fs09000000 bug-fest created via Jenkins')
            .withAdminUser(adminOkapiUser('folio', 'folio'))
            .withConfiguration(new OkapiConfig().withSmtp(smtp).withKbApiKey(kbApiKey))
    ]

}
