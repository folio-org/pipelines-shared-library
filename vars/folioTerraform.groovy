import org.folio.Constants

void client(Closure closure) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_CREDENTIALS_ID,
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                   string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')]) {
    closure.call()
  }
}

def init(String path, String opts = '') {
  stage('[TF] Init') {
    dir(path) {
      sh "terraform init ${opts}"
    }
  }
}

def selectWorkspace(String path, String name) {
  stage('[TF] Select workspace') {
    dir(path) {
      sh "terraform workspace select ${name} || (terraform workspace new ${name} && terraform workspace select ${name})"
    }
  }
}

def plan(String path, String opts) {
  stage('[TF] Plan') {
    dir(path) {
      sh "terraform plan -input=false -out tfplan ${opts}"
    }
  }
}

def planSingleResource(String path, String resource, String opts) {
  stage('[TF] Plan') {
    dir(path) {
      sh "terraform plan -input=false -target=${resource} -out tfplan ${opts}"
    }
  }
}

def planApprove(String path) {
  stage('[TF] Approve') {
    dir(path) {
      def plan = sh(script: "terraform show -no-color tfplan", returnStdout: true).trim()
      input message: "Do you want to apply the plan?",
        parameters: [text(name: 'Plan',
          description: 'Please review the plan',
          defaultValue: plan)]
    }
  }
}

def statePull(String path) {
  stage('[TF] State pull') {
    dir(path) {
      sh 'terraform state pull>/dev/null 2>&1'
    }
  }
}

def removeFromState(String path, String resource) {
  stage('[TF] Remove from state') {
    dir(path) {
      sh "terraform state rm ${resource}"
    }
  }
}

def cleanUpPostgresResources(String path) {
  stage('[TF] Clean up postgres resources') {
    dir(path) {
      def postgresql_resources = sh(script: "terraform state list | grep postgresql_ || true", returnStdout: true).trim()
      if (postgresql_resources.contains('postgresql_')) {
        postgresql_resources.tokenize().each { sh "terraform state rm ${it}" }
      }
    }
  }
}

def apply(String path) {
  stage('[TF] Apply') {
    dir(path) {
      sh 'terraform apply -input=false tfplan'
    }
  }
}

def destroy(String path, String opts) {
  stage('[TF] Destroy') {
    dir(path) {
      sh "terraform destroy -auto-approve ${opts}"
    }
  }
}

def output(String path, String variable) {
  stage('[TF] Output') {
    dir(path) {
      def output = sh(script: "terraform output -raw ${variable}", returnStdout: true).trim()
      return output
    }
  }
}
