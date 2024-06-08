import org.folio.Constants

void client(Closure closure) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_CREDENTIALS_ID,
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                   string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')]) {
    docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
      /*Temporary solution*/
      sh '''
                apk add --no-cache python3 py3-pip
                pip3 install --upgrade pip
                pip3 install --no-cache-dir awscli
                rm -rf /var/cache/apk/*
                aws --version
                export TF_REGISTRY_CLIENT_TIMEOUT=20
            '''
      closure.call()
    }
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
