import org.folio.Constants
import org.folio.models.InstallQueryParameters
import org.folio.models.SmtpConfig
import org.folio.models.OkapiTenant
import org.folio.models.OkapiTenantConsortia
import org.folio.models.OkapiUser

static OkapiUser createAdminOkapiUser(String username, def password) {
    return new OkapiUser(username, password)
        .withFirstName(username.capitalize())
        .withLastName('ADMINISTRATOR')
        .withEmail("$username@example.org")
        .withPermissions(["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"])
        .withBarcode("88888888")
        .withGroup("staff")
}

Map<String, OkapiTenantConsortia> consortiaTenants(String okapiVersion, Object installJson, InstallQueryParameters installQueryParameters = new InstallQueryParameters()) {
    SmtpConfig smtp = null
    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.EMAIL_SMTP_CREDENTIALS_ID,
                      accessKeyVariable: 'EMAIL_USERNAME',
                      secretKeyVariable: 'EMAIL_PASSWORD']]) {
        smtp = new SmtpConfig(Constants.EMAIL_SMTP_SERVER, Constants.EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, Constants.EMAIL_FROM)
    }
    return [
        consortium: new OkapiTenantConsortia('consortium', true)
            .withTenantName('Consortium')
            .withTenantDescription('Central office (created via Jenkins)')
            .withTenantCode('MCO')
            .withConsortiaName('Mobius')
            .withAdminUser(createAdminOkapiUser('consortium_admin', 'admin'))
            .withOkapiVersion(okapiVersion)
            .withInstallJson(installJson)
            .withIndex(true, true)
            .withInstallQueryParameters(installQueryParameters)
            .withOkapiSmtp(smtp),
        university: new OkapiTenantConsortia('university')
            .withTenantName('University')
            .withTenantDescription('University (created via Jenkins)')
            .withTenantCode('UNI')
            .withConsortiaName('Mobius')
            .withOkapiVersion(okapiVersion)
            .withInstallJson(installJson)
            .withInstallQueryParameters(installQueryParameters),
        college   : new OkapiTenantConsortia('college')
            .withTenantName('College')
            .withTenantDescription('College (created via Jenkins)')
            .withTenantCode('COL')
            .withConsortiaName('Mobius')
            .withOkapiVersion(okapiVersion)
            .withInstallJson(installJson)
            .withInstallQueryParameters(installQueryParameters)
    ]
}

Map<String, OkapiTenant> tenants() {
    SmtpConfig smtp = null
    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.EMAIL_SMTP_CREDENTIALS_ID,
                      accessKeyVariable: 'EMAIL_USERNAME',
                      secretKeyVariable: 'EMAIL_PASSWORD']]) {
        smtp = new SmtpConfig(Constants.EMAIL_SMTP_SERVER, Constants.EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, Constants.EMAIL_FROM)
    }
    return [
        diku      : new OkapiTenant('diku')
            .withTenantName('Datalogisk Institut')
            .withTenantDescription('Danish Library Technology Institute')
            .withAdminUser(createAdminOkapiUser('diku_admin', 'admin'))
            .withOkapiSmtp(smtp),
        aqa       : new OkapiTenant('aqa')
            .withTenantName('AQA')
            .withTenantDescription('AQA (created via Jenkins)')
            .withAdminUser(createAdminOkapiUser('aqa_admin', 'admin'))
            .withOkapiSmtp(smtp),
        qa        : new OkapiTenant('qa')
            .withTenantName('QA')
            .withTenantDescription('QA (created via Jenkins)')
            .withAdminUser(createAdminOkapiUser('aqa_admin', 'admin'))
            .withOkapiSmtp(smtp),
        fs09000000: new OkapiTenant('fs09000000')
            .withTenantName('Bug Fest')
            .withTenantDescription('fs09000000 bug-fest created via Jenkins')
            .withAdminUser(createAdminOkapiUser('folio', 'folio'))
            .withOkapiSmtp(smtp)
    ]

}
