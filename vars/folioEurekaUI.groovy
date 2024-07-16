#!groovy
import org.folio.Constants

void call(Map params, boolean releaseVersion = false) {
  stage('Checkout') {
    sh(script: "git clone ${Constants.FOLIO_GITHUB_URL}/platform-complete.git --branch ${params.branch} --single-branch ${params.branch}" as String)
  }
}
