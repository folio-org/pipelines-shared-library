import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools


// A function that returns the last commit hash of a given repository and branch.
String getLastCommitHash(String repository, String branch) {
    String url = "https://api.github.com/repos/${Constants.FOLIO_ORG}/${repository}/branches/${branch}"
    def response = new HttpClient(this).getRequest(url)
    if (response.status == HttpURLConnection.HTTP_OK) {
        return new Tools(this).jsonParse(response.content).commit.sha
    } else {
        new Logger(this, 'common').error(new HttpClient(this).buildHttpErrorMessage(response))
    }
}

// A function that returns the Jenkins user id and group id.
String jenkinsUidGid() {
    return sh(script: "id -u", returnStdout: true).trim() + ":" + sh(script: "id -g", returnStdout: true).trim()
}

// A function that returns a string.
static String generateDomain(String cluster_name, String project_name, String prefix = '', String domain) {
    return "${cluster_name}-${project_name}${prefix.isEmpty() ? '' : '-' + prefix}.${domain}"
}

// A function that waits for a service to be available.
void healthCheck(String url, String status_codes='200,403'){
    timeout(15) {
        waitUntil(initialRecurrencePeriod: 20000, quiet: true) {
            try {
                httpRequest ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', timeout: 1000, url: url, validResponseCodes: status_codes
                return true
            } catch (exception) {
                println(exception.getMessage())
                return false
            }
        }
    }
}

static String getOkapiVersion(List install_json){
   return install_json*.id.find{it ==~ /okapi-.*/} - 'okapi-'
}
