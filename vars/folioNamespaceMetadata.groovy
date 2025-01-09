import groovy.json.JsonSlurper
import org.folio.Constants
import org.folio.models.parameters.CreateNamespaceParameters


void create(CreateNamespaceParameters param) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def allowedKeys = Constants.METADATA_KEYS
  def namespace = param.namespaceName
  def jsonString = JsonOutput.toJson(param)
  tempFile = "metadata.json"

// Сохраняем в файл (имитируем ConfigMap)
  def fileName = "my-config.json"
  new File(tempFile).text = jsonString

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

boolean isExist(String namespace) {
  def name = Constants.AWS_EKS_NS_METADATA
  try {
    sh "kubectl get configmap $name --namespace=${namespace}"
    return true
  } catch (Exception e) {
    return false
  }
}

def getMetadata() {

}

//def parseConfigMapData(String rawData) {
//  rawData.split("\n").collectEntries { line ->
//    def (key, value) = line.split("=", 2)
//    [(key): value]
//  }


//temp method to print createNamespaceParameters
  void printParams(CreateNamespaceParameters param) {
    println "*******************METADATA******************"
    param.properties.each { key, value ->
      if (value instanceof String || value instanceof Boolean) {
        println "$key: $value"
      } else if (value instanceof List && value.every { it instanceof String }) {
        println "$key: ${value.join(', ')}"
      }
    }
    println "*****************************************"
  }

//compare with existing metadata
  void compare(CreateNamespaceParameters param) {
    def configMapName = Constants.AWS_EKS_NS_METADATA
    def namespace = param.namespaceName
    def configMapRawData = kubectl.getConfigMap(configMapName, namespace, 'configmap-data')
    def configMapData = configMapRawData.split("\n").collectEntries { line ->
      def (key, value) = line.split("=", 2)
      [(key): value]
    }

// 2: compare
    def changes = [:]
    param.properties.each { key, value ->
      if (key in ['class', 'metaClass']) return

      def configValue = configMapData[key]
      def objectValue = value instanceof List ? value.join("\n") : value.toString()

      if (configValue != objectValue) {
        changes[key] = [current: configValue, expected: objectValue]
      }
    }

// 3: output diff
    if (changes) {
      println("Found Differences in Existing Metadata and Desired Parameters:")
      changes.each { key, diff ->
        println("Key '${key}': Existing Value '${diff.current}', New Value  '${diff.expected}'")
      }
      def userInput = input(
        message: 'There are in ConfigMap. Continue?',
        ok: 'YES',
        parameters: [
          string(defaultValue: 'Yes', description: 'Type "yes" to continue, name: 'response')
        ]
      )

      if (userInput.toLowerCase() != 'yes') {
        error "Pipeline Cancelled by User."
      }

    }
  }


  void updateConfigMap(String configMapName, String namespace, Map changes) {
    if (!changes) {
      println("Нет изменений для обновления.")
      return
    }

    def updatedData = changes.collect { key, diff -> "${key}=${diff.expected}" }.join("\n")

    def tempFile = "updated-configmap-data.txt"
    writeFile(file: tempFile, text: updatedData)

    //update CM
    try {

      sh """
        kubectl create configmap ${configMapName} --namespace=${namespace} \
            --from-file=configmap-data=${tempFile} --dry-run=client -o yaml | \
            kubectl apply -f -
        """
      println("ConfigMap '${configMapName}' успешно обновлён.")
    } catch (Exception e) {
      println("Ошибка при обновлении ConfigMap: ${e.message}")
    } finally {
      // Удаляем временный файл
      sh "rm -f ${tempFile}"
    }
  }
