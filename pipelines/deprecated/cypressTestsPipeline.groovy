package deprecated

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library
import hudson.util.Secret

@Library('pipelines-shared-library') _

def cypressRepositoryUrl = "${Constants.FOLIO_GITHUB_URL}/stripes-testing.git"

def testsBranchesScript = """
def gettags = ("git ls-remote -t -h ${cypressRepositoryUrl}").execute()
return gettags.text.readLines().collect {
  it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\\\^\\\\{\\\\}", '')
}"""

properties([
    parameters([
      [
            $class      : 'CascadeChoiceParameter',
            choiceType  : 'PT_SINGLE_SELECT',
            description : 'Cypress tests repository branch to checkout',
            filterLength: 1,
            filterable  : false,
            name        : 'branch',
            script      : [
                $class        : 'GroovyScript',
                fallbackScript: [
                    classpath: [],
                    sandbox  : false,
                    script   : 'return ["error"]'
                ],
                script        : [classpath: [],
                                 sandbox  : false,
                                 script   : testsBranchesScript
                ]
            ]
        ],
      string(name: 'uiUrl', defaultValue: "https://folio-testing-cypress-diku.ci.folio.org", description: 'Target environment UI URL', trim: true),
      string(name: 'okapiUrl', defaultValue: "https://folio-testing-cypress-okapi.ci.folio.org", description: 'Target environment OKAPI URL', trim: true),
      string(name: 'tenant', defaultValue: "diku", description: 'Tenant name'),
      string(name: 'user', defaultValue: "diku_admin", description: 'User name'),
      password(name: 'password', defaultValueAsSecret: Secret.fromString('admin'), description: 'User password'),
      jobsParameters.agents(),
      //string(name: 'cypressParameters', defaultValue: "--spec cypress/integration/finance/funds/funds.search.spec.js", description: 'Cypress execution parameters'),
      string(name: 'cypressParameters', defaultValue: "--env grepTags=\"smoke criticalPth extendedPath\",grepFilterSpecs=true", description: 'Cypress execution parameters'),
      string(name: 'customBuildName', defaultValue: "", description: 'Custom name for build'),
      string(name: 'timeout', defaultValue: "4", description: 'Custom timeout for build. Set in hours'),
      string(name: 'testrailProjectID', defaultValue: "", description: 'To enable TestRail integration, enter ProjectID from TestRail, ex. 22', trim: true),
      string(name: 'testrailRunID', defaultValue: "", description: 'To enable TestRail integration, enter RunID from TestRail, ex. 2048', trim: true),
      choice(name: 'numberOfWorkers', defaultValue: "1", description: "Numbers of parallel cypress workers", choices: ["1", "2", "3", "4", "5", "6", "7", "8"].join("\n"))
    ])
])

node {
    cypressFlow(params)
}
