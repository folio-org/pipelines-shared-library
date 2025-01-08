import groovy.json.JsonSlurper
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
boolean isExist(String namespace) {
  def name = Constants.AWS_EKS_NS_METADATA
  try {
    sh "kubectl get configmap $name --namespace=${namespace}"
    return true
  } catch (Exception e) {
    return false
  }
}
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
  println"*****************************************"
}

//compare with existing
void compare(CreateNamespaceParameters param) {
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = param.namespaceName
  def configMapJson = sh(
    script: "kubectl get configmap ${configMapName} -n ${namespace} -o json",
    returnStdout: true
  ).trim()
  def configMapData = readJSON text: configMapJson
  def existingData = configMapData?.data ?: [:]


// Шаг 2: Сравниваем свойства объекта с ключами ConfigMap
  def changes = [:] // Храним изменения
  param.properties.each { key, value ->
    if (key in ['class', 'metaClass']) return // Пропускаем технические свойства

    def configValue = configMapData[key]
    def objectValue = value instanceof List ? value.join("\n") : value.toString()

    if (configValue != objectValue) {
      changes[key] = [current: configValue, expected: objectValue]
    }
  }

// Шаг 3: Выводим сообщение об изменениях
  if (changes) {
    println("Найдены отличия между объектом и ConfigMap:")
    changes.each { key, diff ->
      println("Ключ '${key}': текущее значение '${diff.current}', ожидаемое значение '${diff.expected}'")
    }
    def userInput = input(
      message: 'Обнаружены изменения в ConfigMap. Продолжить обновление?',
      ok: 'Да',
      parameters: [
        string(defaultValue: 'Да', description: 'Введите "Да" для продолжения или "Нет" для прерывания', name: 'response')
      ]
    )

    if (userInput.toLowerCase() != 'да') {
      error "Пайплайн прерван пользователем."
    }

  }
}



//void printMetadata() {
//  def configMapName = Constants.AWS_EKS_NS_METADATA
//  def namespace = param.namespaceName
//  getConfigMap(String name, String namespace, String data)
//
//}
