void defaultJobWrapper(Closure stages, boolean checkoutGit = true) {
  try {
    if (checkoutGit) {
      stage('Checkout') {
        checkout scm
      }
    }
    stages()
  } catch (e) {
    println "Caught exception: ${e}"
    println "Stack trace:"
    e.printStackTrace()
    error(e.getMessage())
  } finally {
    stage('Cleanup') {
      cleanWs notFailBuild: true
    }
  }
}
