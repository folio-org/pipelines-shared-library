void deleteOpenSearchIndices(String cluster, String namespace) {
    String opensearch_url = kubectl.getSecretValue(namespace, 'db-connect-modules', 'ELASTICSEARCH_URL')
    String opensearch_username = kubectl.getSecretValue(namespace, 'db-connect-modules', 'ELASTICSEARCH_USERNAME')
    String opensearch_password = kubectl.getSecretValue(namespace, 'db-connect-modules', 'ELASTICSEARCH_PASSWORD')
    //TODO This is unsafe, we should change this approach after Jenkins migration
    String delete_indices_command = "curl -u ${opensearch_username}:${opensearch_password} -X DELETE ${opensearch_url}/${cluster}-${namespace}_*"

    kubectl.runPodWithCommand('curl', 'curlimages/curl:7.88.1')
    kubectl.waitPodIsRunning('curl')
    kubectl.execCommand('curl', delete_indices_command)
    kubectl.deletePod('curl')
}

void deleteKafkaTopics(String cluster, String namespace) {
    String kafka_host = kubectl.getSecretValue(namespace, 'db-connect-modules', 'KAFKA_HOST')
    String kafka_port = kubectl.getSecretValue(namespace, 'db-connect-modules', 'KAFKA_PORT')
    String delete_topic_command = "kafka-topics.sh --bootstrap-server ${kafka_host}:${kafka_port} --delete --topic ${cluster}-${namespace}.*"

    kubectl.runPodWithCommand('kafka', 'bitnami/kafka:2.8.0')
    kubectl.waitPodIsRunning('kafka')
    kubectl.execCommand('kafka', delete_topic_command)
    kubectl.deletePod('kafka')
}
