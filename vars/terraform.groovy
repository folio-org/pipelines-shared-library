import org.folio.Constants

def tfInit(String path, String opts = '') {
    stage('TF init') {
        dir(path) {
            sh "terraform init ${opts}"
        }
    }
}

def tfPlan(String path, String opts) {
    stage('TF plan') {
        dir(path) {
            sh "terraform plan -input=false -out tfplan ${opts}"
        }
    }
}

def tfApply(String path) {
    stage('TF apply') {
        dir(path) {
            sh 'terraform apply -input=false tfplan'
        }
    }
}

def tfPostgreSQLPlan(String path, String opts) {
    stage('TF plan') {
        dir(path) {
            sh "terraform plan -target=rancher2_app_v2.postgresql -out tfplan ${opts}"
        }
    }
}

def tfDestroy(String path, String opts) {
    stage('TF destroy') {
        dir(path) {
            sh "terraform destroy -auto-approve ${opts}"
        }
    }
}

def tfRemoveElastic(String path) {
    stage('TF remove elastic policy') {
        dir(path) {
            sh "terraform state rm elasticstack_elasticsearch_index_lifecycle.index_policy"
        }
    }
}

def tfStatePull(String path) {
    stage('TF state pull') {
        dir(path) {
            sh 'terraform state pull>/dev/null 2>&1'
        }
    }
}

def tfOutput(String path, String variable) {
    stage('TF output') {
        dir(path) {
            def output = sh(script: "terraform output -raw ${variable}", returnStdout: true).trim()
            return output
        }
    }
}

def tfWorkspaceSelect(String path, String name) {
    stage('TF workspace') {
        dir(path) {
            sh "terraform workspace select ${name} || (terraform workspace new ${name} && terraform workspace select ${name})"
        }
    }
}

static String generateTfVars(Map variables) {
    return variables.collect { "-var '${it.key}=${it.value}'" }.join(" ")
}

def tfPlanApprove(String path) {
    dir(path) {
        def plan = sh(script: "terraform show -no-color tfplan", returnStdout: true).trim()
        input message: "Do you want to apply the plan?",
            parameters: [text(name: 'Plan',
                description: 'Please review the plan',
                defaultValue: plan)]
    }
}

void tfWrapper(Closure body) {
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
            '''
            body()
        }
    }
}

void tfApplyFlow(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    tfInit(config.working_dir)
    tfWorkspaceSelect(config.working_dir, config.workspace_name)
    tfStatePull(config.working_dir)
    if (config.preAction) {
        config.preAction.delegate = this
        config.preAction.resolveStrategy = Closure.DELEGATE_FIRST
        config.preAction.call()
    }
    retry(2) {
        sleep(60)
        tfPlan(config.working_dir, config.tf_vars)
        tfApply(config.working_dir)
    }
    if (config.postAction) {
        config.postAction.delegate = this
        config.postAction.resolveStrategy = Closure.DELEGATE_FIRST
        config.postAction.call()
    }
}

void tfDestroyFlow(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    tfInit(config.working_dir)
    tfWorkspaceSelect(config.working_dir, config.workspace_name)
    tfStatePull(config.working_dir)
    tfDestroy(config.working_dir, config.tf_vars)
}
