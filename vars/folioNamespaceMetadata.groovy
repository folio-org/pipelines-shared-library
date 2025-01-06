import org.folio.Constants
import org.folio.models.parameters.CreateNamespaceParameters

void create(CreateNamespaceParameters param) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def allowedKeys = Constants.METADATA_KEYS
  def namespace = param.namespaceName
  tempFile = "configmap-data"
  try {
    def fileContent = allowedKeys.collect { key ->
      "${key}=${param."${key}"}"
    }.join("\n")

    writeFile(file: tempFile, text: fileContent)

    kubectl.createConfigMap(configMapName, namespace, tempFile)

    println "ConfigMap '${configMapName}' created successfully in namespace '${namespace}'"

  } catch (Exception e) {
    println "Error while creating ConfigMap: ${e.message}"
    e.printStackTrace()
  } finally {
    sh "rm -f ${tempFile}"
  }

}

