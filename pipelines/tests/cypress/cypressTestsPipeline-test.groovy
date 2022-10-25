import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library') _

properties([
    parameters([
        password(name: 'password', defaultValueAsSecret: Secret.fromString('admin'), description: 'User password'),
    ])
])

node('jenkins-agent-java11') {
    println(params.password)
}