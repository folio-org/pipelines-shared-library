#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.Constants

void call(Map params) {
  stage('Checkout') {
    sh(script: "git clone --branch ${params.branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/platform-complete.git" as String)
  }

  stage('Prepare') {
    dir('platform-complete') {
      sh(script: "cp -R -f eureka-tpl/* .")
      println("Parameters for UI:\n${JsonOutput.prettyPrint(JsonOutput.toJson(params))}")
      def data = readFile(file: 'stripes.config.js', encoding: "UTF-8")
      make_tpl(data as String, params)
      input("Test review...")
    }
  }

  stage('Build and Push') {
    dir('platform-complete') {
      // Docker & Helm stuff goes here
      //common.removeImage(image.getImageName()) TODO: clean fresh image.
    }
  }

}

@NonCPS
def make_tpl(String tpl, Map data) {
  def ui_tpl = (new StreamingTemplateEngine().createTemplate(tpl)).make(data)
  println(ui_tpl)
}
