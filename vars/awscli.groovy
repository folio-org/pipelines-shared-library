import org.folio.Constants

// Getting the kubeconfig file from the AWS EKS cluster.
String getKubeConfig(String region, String cluster_name) {
  sh "aws eks update-kubeconfig --region ${region} --name ${cluster_name}"
}

// Using the AWS CLI to get a list of RDS cluster snapshots.
List getRdsClusterSnapshots(String region) {
  return Eval.me(sh(script: "aws rds describe-db-cluster-snapshots " +
    "--region ${region} " +
    "--snapshot-type manual " +
    "--query 'DBClusterSnapshots[*].DBClusterSnapshotIdentifier'",
    returnStdout: true).trim())
}

String getRdsClusterSnapshotEngineVersion(String region, String snapshot_name) {
  return Eval.me(sh(script: "aws rds describe-db-cluster-snapshots " +
    "--region ${region} " +
    "--snapshot-type manual " +
    "--db-cluster-snapshot-identifier ${snapshot_name} " +
    "--query 'DBClusterSnapshots[*].EngineVersion'",
    returnStdout: true).trim())[0]
}

String getRdsClusterSnapshotMasterUsername(String region, String snapshot_name) {
  return Eval.me(sh(script: "aws rds describe-db-cluster-snapshots " +
    "--region ${region} " +
    "--snapshot-type manual " +
    "--db-cluster-snapshot-identifier ${snapshot_name} " +
    "--query 'DBClusterSnapshots[*].MasterUsername'",
    returnStdout: true).trim())[0]
}

String getS3FileContent(String path) {
  return sh(script: "aws s3 cp s3://${path} -", returnStdout: true).trim()
}

boolean isEcrRepoExist(String region, String repo_name) {
  return sh(script: "aws ecr describe-repositories --region ${region} --repository-names ${repo_name}", returnStatus: true)
}

void createEcrRepo(String region, String repo_name) {
  sh "aws ecr create-repository --region ${region} --repository-name ${repo_name} --tags Key=Team,Value=kitfox && \
        aws ecr put-lifecycle-policy --region ${region} --repository-name ${repo_name} \
        --lifecycle-policy-text '{\"rules\":[{\"rulePriority\":1,\"description\":\"Remove untagged images older than 1 day\",\"selection\":{\"tagStatus\":\"untagged\",\"countType\":\"sinceImagePushed\",\"countUnit\":\"days\",\"countNumber\":1},\"action\":{\"type\":\"expire\"}}]}'"
}

String listEcrImages(String region, String repo_name) {
  return sh(script: "aws ecr describe-images --region ${region} --repository-name ${repo_name} --query 'sort_by(imageDetails,& imagePushedAt)[*].imageTags[0]' --output json", returnStdout: true)
}

void deleteEcrImage(String region, String repo_name, String image_tag) {
  sh(script: "aws ecr batch-delete-image --region ${region} --repository-name ${repo_name} --image-ids imageTag=${image_tag}")
}

void startRdsCluster(String cluster_name, String region) {
  sh(script: "aws rds start-db-cluster --db-cluster-identifier ${cluster_name} --region ${region} > /dev/null")
}

void stopRdsCluster(String cluster_name, String region) {
  sh(script: "aws rds stop-db-cluster --db-cluster-identifier ${cluster_name} --region ${region} > /dev/null")
}

void waitRdsClusterAvailable(String cluster_name, String region) {
  sh(script: "aws rds wait db-cluster-available --db-cluster-identifier ${cluster_name} --region ${region}")
}


/**
 * @param awsRegion The aws region where located ssm parameter
 * @param awsParameterName parameter where we are saving the commit hash value
 * @param latestCommitHash latest commit hash value

 */
void updateSsmParameter(String awsRegion, String awsParameterName, String latestCommitHash) {
  sh(script: "aws ssm put-parameter --name ${awsParameterName} --region ${awsRegion} --value ${latestCommitHash} --type String --overwrite")
}

/**
 * @param awsRegion The aws region where located ssm parameter
 * @param awsParameterName parameter where we are saving the commit hash value
 * @return AWS SSM parameter value as a String
 */
String getSsmParameterValue(String awsRegion, String awsParameterName, boolean withDecryption = false) {
  println("I'm in awscli.getSsmParameterValue")
  return sh(script: "aws ssm get-parameter --name ${awsParameterName} --region ${awsRegion} --query 'Parameter.Value' --output text ${withDecryption ? '--with-decryption' : ''}", returnStdout: true).trim()
}


void withAwsClient(Closure closure) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_CREDENTIALS_ID,
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
    closure()
  }
}
