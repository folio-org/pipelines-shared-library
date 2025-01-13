import groovy.json.JsonSlurper
import org.folio.Constants
import org.folio.models.parameters.CreateNamespaceParameters
import groovy.json.JsonOutput


void create(CreateNamespaceParameters params) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonString = JsonOutput.toJson(params)
  def tempFile = "metadataJson"
  writeFile(file: tempFile, text: jsonString)

  try {
    if (folioNamespaceMetadata.isMetadataExist(namespace)) {
      println "ConfigMap ${configMapName} already exist in namespace ${namespace} and will be overrited"
      kubectl.recreateConfigMap(configMapName, namespace, tempFile)
    } else {
      kubectl.createConfigMap(configMapName, namespace, tempFile)

      println "ConfigMap '${configMapName}' created successfully in namespace '${namespace}'"
    }
  } catch (Exception e) {
    println "Error while creating ConfigMap: ${e.message}"
    e.printStackTrace()
  } finally {
    sh "rm -f ${tempFile}"
  }
}

boolean isMetadataExist(String namespace) {
  def name = Constants.AWS_EKS_NS_METADATA
  try {
    sh "kubectl get configmap $name --namespace=${namespace}"
    return true
  } catch (Exception e) {
    return false
  }
}

void recreate(CreateNamespaceParameters params) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonString = JsonOutput.toJson(params)
  def tempFile = "metadataJson"
  writeFile(file: tempFile, text: jsonString)

  kubectl.recreateConfigMap(configMapName, namespace, tempFile)
}

def getMetadataAll(CreateNamespaceParameters params) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName

  def jsonData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson').trim()
  return jsonData
}

def getMetadataKey(CreateNamespaceParameters params, String key) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def value = kubectl.getConfigMap(configMapName, namespace, "metadataJson.${key}").trim()
  println("Requested Metadata $key = value")
  return value
}



void updateConfigMap(String namespace, Map<String, Object> updates) {
  try {
    // Шаг 1: Считываем текущую ConfigMap
    def jsonData = sh(
      script: "kubectl get configmap ${configMapName} -n ${namespace} -o jsonpath='{.data.metadata\\.json}'",
      returnStdout: true
    ).trim()

    println "Existing ConfigMap JSON: $jsonData"

    // Шаг 2: Парсим JSON в объект
    def jsonSlurper = new JsonSlurper()
    def configObject = jsonSlurper.parseText(jsonData)

    // Шаг 3: Обновляем данные
    updates.each { key, value ->
      println "Updating key: ${key} with value: ${value}"
      configObject[key] = value
    }

    // Шаг 4: Преобразуем обновлённый объект обратно в JSON
    def updatedJson = JsonOutput.toJson(configObject)
    println "Updated JSON: $updatedJson"

    // Шаг 5: Записываем обновлённый JSON во временный файл
    def tempFile = "updated_metadata.json"
    writeFile(file: tempFile, text: updatedJson)

    // Шаг 6: Обновляем ConfigMap через kubectl
    sh "kubectl create configmap ${configMapName} -n ${namespace} --from-file=metadata.json=${tempFile} -o yaml --dry-run=client | kubectl apply -f -"

    println "ConfigMap '${configMapName}' updated successfully in namespace '${namespace}'"

    // Удаляем временный файл
    sh "rm -f ${tempFile}"

  } catch (Exception e) {
    println "Error while updating ConfigMap: ${e.message}"
    e.printStackTrace()
  }
}


//temp method to print createNamespaceParameters
  void printParams(CreateNamespaceParameters params) {
    println "*******************METADATA******************"
    params.properties.each { key, value ->
      if (value instanceof String || value instanceof Boolean) {
        println "$key: $value"
      } else if (value instanceof List && value.every { it instanceof String }) {
        println "$key: ${value.join(', ')}"
      }
    }
    println "*****************************************"
  }



//compare with existing metadata
  void compare(CreateNamespaceParameters params) {
    def configMapName = Constants.AWS_EKS_NS_METADATA
    def namespace = params.namespaceName
    def configMapRawData = kubectl.getConfigMap(configMapName, namespace, 'configmap-data')
    def configMapData = configMapRawData.split("\n").collectEntries { line ->
      def (key, value) = line.split("=", 2)
      [(key): value]
    }

// 2: compare
    def changes = [:]
    params.properties.each { key, value ->
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
          string(defaultValue: 'Yes', description: 'Type "yes" to continue', name: 'response')
            ]
      )

      if (userInput.toLowerCase() != 'yes') {
        error "Pipeline Cancelled by User."
      }
    }
  }


//  void updateConfigMap(String configMapName, String namespace, Map changes) {
//    if (!changes) {
//      println("Нет изменений для обновления.")
//      return
//    }
//
//    def updatedData = changes.collect { key, diff -> "${key}=${diff.expected}" }.join("\n")
//
//    def tempFile = "updated-configmap-data.txt"
//    writeFile(file: tempFile, text: updatedData)
//
//    //update CM
//    try {
//
//      sh """
//        kubectl create configmap ${configMapName} --namespace=${namespace} \
//            --from-file=configmap-data=${tempFile} --dry-run=client -o yaml | \
//            kubectl apply -f -
//        """
//      println("ConfigMap '${configMapName}' успешно обновлён.")
//    } catch (Exception e) {
//      println("Ошибка при обновлении ConfigMap: ${e.message}")
//    } finally {
//      // Удаляем временный файл
//      sh "rm -f ${tempFile}"
//    }
//  }
