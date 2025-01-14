import groovy.json.JsonSlurper
import org.folio.Constants
import org.folio.models.parameters.CreateNamespaceParameters
import groovy.json.JsonOutput


void create(CreateNamespaceParameters params) {
  println "Metadata Create"
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonString = JsonOutput.toJson(params)
  def tempFile = "metadataJson"
  writeFile(file: tempFile, text: jsonString)

  try {
    if (isMetadataExist(namespace)) {
      println "ConfigMap ${configMapName} already exist in namespace ${namespace} and will be overwritten"
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
  println "Metadata isMetadataExist "
  def name = Constants.AWS_EKS_NS_METADATA
  try {
    sh "kubectl get configmap $name --namespace=${namespace}"
    return true
  } catch (Exception e) {
    println "The metadata configMap does not exits: ${e.message}"
    e.printStackTrace()
    return false
  }
}

void recreate(CreateNamespaceParameters params) {
  println("Metadata recreate")
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonString = JsonOutput.toJson(params)
  def tempFile = "metadataJson"
  writeFile(file: tempFile, text: jsonString)

  kubectl.recreateConfigMap(configMapName, namespace, tempFile)
}

def getMetadataAll(CreateNamespaceParameters params) {
  println("Metadata getMetadataAll")
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  if (isMetadataExist(namespace)) {
    def jsonData = sh(
      script: "kubectl get configmap ${configMapName} -n ${namespace} -o jsonpath='{.data.metadataJson}'",
      returnStdout: true
    ).trim()
    println "Полученные данные ConfigMap: $jsonData"
    return jsonData
  }
}

  def getMetadataKey(CreateNamespaceParameters params, String key) {
    println("Metadata getMetadataKey")
    def configMapName = Constants.AWS_EKS_NS_METADATA
    def namespace = params.namespaceName
    // Выполняем команду и проверяем результат
    if (!isMetadataExist(namespace)) {
      println("Metadata Configmap does not exist")
      return null
    }
    def jsonData = sh(
      script: "kubectl get configmap ${configMapName} -n ${namespace} -o jsonpath='{.data.metadataJson}'",
      returnStdout: true
    ).trim()

    def jsonSlurper = new JsonSlurper()
    def metadataObject = jsonSlurper.parseText(jsonData)
    def value = metadataObject[key]
    if (!value) {
      println "Ключ '${key}' отсутствует в ConfigMap '${configMapName}' в namespace '${namespace}'."
      return null
    }
    println "Запрошенный ключ $key = $value"
    return value
  }



  void updateConfigMap(CreateNamespaceParameters params, Map<String, Object> updates) {
    println("Metadata UpdateConfigMap")
    def configMapName = Constants.AWS_EKS_NS_METADATA
    def namespace = params.namespaceName
    try {
      // Шаг 1: Считываем текущую ConfigMap
      def jsonData = sh(
        script: "kubectl get configmap ${configMapName} -n ${namespace} -o jsonpath='{.data.metadataJson}'",
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
    println("Metadata Compare")
    def configMapName = Constants.AWS_EKS_NS_METADATA
    def namespace = params.namespaceName
    def configMapRawData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')
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
