import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentialsBinding
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials
import javax.mail.*
import javax.mail.internet.*

List KitFoxMembers = ["oleksii_petrenko1@epam.com",
                      "guram_jalaghonia@epam.com",
                      "oleksandr_haimanov@epam.com",
                      "renat_safiulin@epam.com",
                      "vasili_kapylou@epam.com",
                      "eldiiar_duishenaliev@epam.com"]

withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'ses-smtp-rancher']]) {
  def host = "email-smtp.us-west-2.amazonaws.com"
  def port = 587
  def username = "${AWS_ACCESS_KEY_ID}"
  def password = "${AWS_SECRET_ACCESS_KEY}"

  def props = new Properties()
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.starttls.enable", "true")
  props.put("mail.smtp.host", host)
  props.put("mail.smtp.port", port)

  def session = Session.getInstance(props, new Authenticator() {
    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(username, password)
    }
  })

  try {
    def message = new MimeMessage(session)
    message.setFrom(new InternetAddress("folio-jenkins@indexdata.com"))
    message.setRecipients(Message.RecipientType.TO, "Eldiiar_Duishenaliev@epam.com")
    message.setSubject("JiraFlow tests v1.")
    message.setText("Hello this is start of testing...")

    Transport.send(message)
    println("Email sent successfully.")
  } catch (MessagingException e) {
    println("Email could not be sent. Error: " + e.getMessage())
  }
}
