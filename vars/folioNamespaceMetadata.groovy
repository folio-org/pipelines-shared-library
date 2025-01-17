import groovy.json.JsonSlurper
import org.folio.Constants
import org.folio.models.parameters.CreateNamespaceParameters
import groovy.json.JsonOutput


void create(CreateNamespaceParameters params) {
  println "************************Metadata Create*************************"
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  //def jsonString = JsonOutput.toJson(params)
  def tempFile = "metadataJson"
  writeJSON(file: tempFile, text: params)
  try {
    if (isMetadataExist(namespace)) {
      println "ConfigMap ${configMapName} already exist in namespace ${namespace} and will be overwritten"
      compare(params)

      kubectl.recreateConfigMap(configMapName, namespace, tempFile)

      println "ConfigMap '${configMapName}' recreated successfully in namespace '${namespace}'"
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
  def configMapName = Constants.AWS_EKS_NS_METADATA
  println "Checking if ${configMapName} ConfigMap exists"
  try {
    def result = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')
    println "Result from getConfigMap: '${result}'"
    return result != null && !result.isEmpty()  // Упрощённая проверка
  } catch (Exception e) {
    println "Error during ConfigMap check: ${e.message}"
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
  writeJSON(file: tempFile, text: params)

  kubectl.recreateConfigMap(configMapName, namespace, tempFile)
}

def getMetadataAll(CreateNamespaceParameters params) {
  println("Metadata getMetadataAll")
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')
  return jsonData
}

def getMetadataKey(CreateNamespaceParameters params, String key) {
  println("Metadata getMetadataKey")
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  def jsonData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')
  def metadataObject = readJSON(text: jsonData)
  def value = metadataObject[key]
  if (!value) {
    println "Key '${key}' does not exist in ConfigMap '${configMapName}' в namespace '${namespace}'."
    return null
  }
  println "Requested key $key = $value"
  return value
}


void updateConfigMap(CreateNamespaceParameters params, Map<String, Object> updates) {
  println("Metadata UpdateConfigMap")
  def configMapName = Constants.AWS_EKS_NS_METADATA
  def namespace = params.namespaceName
  try {
    // read currents configmap
    def jsonData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')
    println "Existing ConfigMap JSON:" + jsonData
        // parse json to object
    def configObject = readJSON(text: jsonData)

    //update data
    updates.each { key, value ->
      println "Updating key: ${key} with value: ${value}"
      configObject[key] = value
    }

    //convert updated object to json
    println "ConfigJSON object: " + configObject
    def updatedJson = JsonOutput.toJson(configObject)
    input message: "please proceed-a? "
    println "Updated JSON: $updatedJson"

    // write json to file and save in configmap
    input message: "please proceed-0? "
    sh "pwd && ls -la"
    input message: "please proceed-1? "
    def tempFile = "metadataJson"
    writeJSON(file: tempFile, text: configObject)
    input message: "please proceed-2? "
    sh "ls -la"

    def fileContent = readFile(file: tempFile)
    println "File content to be used for ConfigMap: $fileContent"

    kubectl.recreateConfigMap(configMapName, namespace, tempFile)

    println "ConfigMap '${configMapName}' updated successfully in namespace '${namespace}'"

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
  def jsonData = kubectl.getConfigMap(configMapName, namespace, 'metadataJson')

  def configObject = readJSON(text: jsonData)

// 2: compare
  def changes = [:]
  params.properties.each { key, value ->
    if (key in ['class', 'metaClass']) return
    def configValue = configObject[key]
    def objectValue = value

    if (configValue != objectValue) {
      changes[key] = [current: configValue, expected: objectValue]
    }
  }

  if (changes) {
    println("Found Differences in Existing Metadata and Desired Parameters:")
    changes.each { key, diff ->
      println("Key '${key}': Existing Value '${diff.current}', New Value  '${diff.expected}'")
    }
  }
}
