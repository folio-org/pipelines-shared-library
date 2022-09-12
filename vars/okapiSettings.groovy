import org.folio.Constants
import org.folio.rest.model.OkapiUser
import org.folio.rest.model.Email

static OkapiUser adminUser(Map args = [:]) {
    return new OkapiUser(
        username: args.username,
        password: args.password,
        firstName: 'DIKU',
        lastName: 'ADMINISTRATOR',
        email: 'admin@diku.example.org',
        groupName: 'staff',
        permissions: ["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"]
    )
}

def email() {
    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.AWS_CREDENTIALS_ID,
                      accessKeyVariable: 'EMAIL_USERNAME',
                      secretKeyVariable: 'EMAIL_PASSWORD']]) {
        return new Email(
            smtpHost: Constants.EMAIL_SMTP_SERVER,
            smtpPort: Constants.EMAIL_SMTP_PORT,
            from: Constants.EMAIL_FROM,
            username: EMAIL_USERNAME,
            password: EMAIL_PASSWORD
        )
    }
}
