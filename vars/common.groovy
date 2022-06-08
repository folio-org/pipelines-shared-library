import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools


String getLastCommitHash(String repository, String branch){
    String url = "https://api.github.com/repos/${Constants.FOLIO_ORG}/${repository}/branches/${branch}"
    def response = new HttpClient(this).getRequest(url)
    if (response.status == HttpURLConnection.HTTP_OK) {
        return new Tools(this).jsonParse(response.content).commit.sha
    }else{
        new Logger(this, 'common').error(new HttpClient(this).buildHttpErrorMessage(response))
    }
}
