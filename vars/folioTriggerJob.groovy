import org.folio.models.parameters.CreateNamespaceParameters

def deleteNamespace(String jobName, CreateNamespaceParameters namespaceParams) {
  def jobResult = build job: jobName,
    parameters: [
      string(name: 'CLUSTER', value: namespaceParams.getClusterName()),
      string(name: 'NAMESPACE', value: namespaceParams.getNamespaceName()),
      booleanParam(name: 'RW_SPLIT', value: namespaceParams.getRwSplit()),
      string(name: 'POSTGRESQL', value: namespaceParams.getPgType()),
      string(name: 'KAFKA', value: namespaceParams.getKafkaType()),
      string(name: 'OPENSEARCH', value: namespaceParams.getOpensearchType()),
      string(name: 'S3_BUCKET', value: namespaceParams.getS3Type()),
      string(name: 'AGENT', value: namespaceParams.getWorker())]
  return jobResult
}

def createNamespaceFromBranch(String jobName, CreateNamespaceParameters namespaceParams) {
  def jobResult = build job: jobName,
    parameters: [
      string(name: 'CLUSTER', value: namespaceParams.getClusterName()),
      string(name: 'NAMESPACE', value: namespaceParams.getNamespaceName()),
      string(name: 'FOLIO_BRANCH', value: namespaceParams.getFolioBranch()),
      string(name: 'OKAPI_VERSION', value: namespaceParams.getOkapiVersion()),
      string(name: 'CONFIG_TYPE', value: namespaceParams.getConfigType()),
      booleanParam(name: 'LOAD_REFERENCE', value: namespaceParams.getLoadReference()),
      booleanParam(name: 'LOAD_SAMPLE', value: namespaceParams.getLoadSample()),
      booleanParam(name: 'CONSORTIA', value: namespaceParams.getConsortia()),
      booleanParam(name: 'LINKED_DATA', value: namespaceParams.getLinkedData()),
      booleanParam(name: 'SPLIT_FILES', value: namespaceParams.getSplitFiles()),
      booleanParam(name: 'RW_SPLIT', value: namespaceParams.getRwSplit()),
      booleanParam(name: 'GREENMAIL', value: namespaceParams.getGreenmail()),
      booleanParam(name: 'MOCK_SERVER', value: namespaceParams.getMockServer()),
      booleanParam(name: 'RTR', value: namespaceParams.getRtr()),
      booleanParam(name: 'EUREKA', value: namespaceParams.getEureka()),
      string(name: 'POSTGRESQL', value: namespaceParams.getPgType()),
      string(name: 'DB_VERSION', value: namespaceParams.getPgVersion()),
      string(name: 'KAFKA', value: namespaceParams.getKafkaType()),
      string(name: 'OPENSEARCH', value: namespaceParams.getOpensearchType()),
      string(name: 'S3_BUCKET', value: namespaceParams.getS3Type()),
      booleanParam(name: 'RUN_SANITY_CHECK', value: namespaceParams.getRunSanityCheck()),
      string(name: 'MEMBERS', value: namespaceParams.getMembers()),
      string(name: 'AGENT', value: namespaceParams.getWorker())]
  return jobResult
}
