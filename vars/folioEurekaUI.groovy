#!groovy
import org.folio.Constants

void call(Map params, boolean releaseVersion = false) {
  stage('Checkout') {
    sh(script: "git clone --branch ${params.branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/platform-complete.git" as String)
  }
  stage('Prepare') {
    dir('platform-complete') {
      sh(script: "cp -R -f eureka-tpl/* .")
      //templating goes here...
      input("Paused for review...")
    }
  }
  stage('Build and Push') {
    dir('platform-complete') {
      // Docker & Helm stuff goes here
    }
  }
  stage('Cleanup') {
    //common.removeImage(image.getImageName()) TODO clean fresh image.
  }
}
