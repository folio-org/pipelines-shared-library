import org.folio.Constants
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiUser

static OkapiUser edgeUser(Map args = [:]) {
  return new OkapiUser(
    username: args.username,
    password: args.password,
    firstName: args.username,
    lastName: 'ADMINISTRATOR',
    email: 'admin@diku.example.org',
    groupName: 'staff',
    permissions: args.permissions
  )
}

static OkapiUser adminUser(Map args = [:]) {
  return new OkapiUser(
    username: args.username,
    password: args.password,
    firstName: 'DIKU',
    lastName: 'ADMINISTRATOR',
    email: 'admin@diku.example.org',
    groupName: 'staff',
    barcode: '88888888',
    permissions: ["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"]
  )
}

def superadmin_user() {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.OKAPI_SUPERADMIN_CREDENTIALS_ID,
                    accessKeyVariable: 'SUPERADMIN_USERNAME',
                    secretKeyVariable: 'SUPERADMIN_PASSWORD']]) {
    return new OkapiUser(
      username: SUPERADMIN_USERNAME,
      password: SUPERADMIN_PASSWORD)
  }
}

def email() {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.EMAIL_SMTP_CREDENTIALS_ID,
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
