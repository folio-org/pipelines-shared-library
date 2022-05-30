def tfInit(String path, String opts) {
    stage('TF init') {
        dir(path) {
            sh "terraform init ${opts}"
        }
    }
}

def tfPlan(String path, String opts) {
    stage('TF plan') {
        dir(path) {
            sh "terraform plan -var 'env_type=[\"local.module_configs_dev\", \"local.module_configs_perf\", \"local.module_configs_test\"]'  -input=false -out tfplan ${opts}"
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

def tfWorkspaceSelect(String path, String name){
    stage('TF workspace'){
        dir(path){
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
