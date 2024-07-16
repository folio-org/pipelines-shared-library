#!groovy
import org.folio.Constants

void call(Map params, boolean releaseVersion = false) {
  stage('Checkout') {
    sh(script: "git clone --branch ${params.branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/platform-complete.git" as String)
  }

  stage('Cleanup') {
    //common.removeImage(image.getImageName()) TODO clean fresh image.
  }
}
