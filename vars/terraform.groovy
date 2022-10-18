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

static def generateTfVar(String key, def value) {
    return ' -var \'' + key + '=' + value + '\''
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
                     [$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                      accessKeyVariable: 'TF_VAR_s3_access_key',
                      secretKeyVariable: 'TF_VAR_s3_secret_key'],
                     [$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.AWS_S3_POSTGRES_BACKUPS,
                      accessKeyVariable: 'TF_VAR_s3_postgres_backups_access_key',
                      secretKeyVariable: 'TF_VAR_s3_postgres_backups_secret_key'],
                     string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key'),
                     usernamePassword(credentialsId: Constants.DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID,
                         passwordVariable: 'TF_VAR_folio_docker_registry_password',
                         usernameVariable: 'TF_VAR_folio_docker_registry_username')]) {
        docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
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
    if(config.preAction){
        config.preAction.delegate = this
        config.preAction.resolveStrategy = Closure.DELEGATE_FIRST
        config.preAction.call()
    }
    retry(2) {
        sleep(60)
        tfPlan(config.working_dir, config.tf_vars)
        tfApply(config.working_dir)
    }
    if(config.postAction){
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
