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

boolean isEcrRepoExist(String repo_name, String region) {
    return sh(script: "aws ecr describe-repositories --region ${region} --repository-names ${repo_name}", returnStatus: true)
}

void createEcrRepo(String repo_name, String region) {
    sh "aws ecr create-repository --region ${region} --repository-name ${repo_name}"
}
