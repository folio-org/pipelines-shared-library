import groovy.json.JsonSlurperClassic
import groovy.yaml.YamlBuilder

String folio_branch = 'snapshot'
String configType = 'performance' // Possible values development|testing|performance
String nameGroup = "moduleName"
String patternModuleVersion = /^(?<moduleName>.*)-(?<moduleVersion>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*).*)$/
String configYaml = ""
Long memorySum = 0

println("Config type: ${configType}\n")

URLConnection installJson = new URL('https://raw.githubusercontent.com/folio-org/platform-complete/' + folio_branch + '/install.json').openConnection()
if (installJson.getResponseCode().equals(200)) {
    new JsonSlurperClassic().parseText(installJson.getInputStream().getText())*.id.findAll { it ==~ /mod-.*|edge-.*/ }.sort().each { value ->
        // Using a regular expression to extract the module name and version from the module id.
        def matcherModule = value =~ patternModuleVersion
        assert matcherModule.matches()
        Map launch_descriptor = getLaunchDescriptor(matcherModule.group(nameGroup))
        YamlBuilder moduleConfig = new YamlBuilder()
        if (launch_descriptor) {
            Long bytes = launch_descriptor.dockerArgs.HostConfig.Memory.toLong()
            Map memoryResources = calculateMemory(configType, bytes)
            // Creating a yaml config for a module.
            moduleConfig.(matcherModule.group(nameGroup)) {
                javaOptions launch_descriptor.env.findAll { it.name == 'JAVA_OPTIONS' }[0].value
                postJob {
                    enabled false
                }
                replicaCount 1
                resources {
                    limits {
                        memory memoryResources.limits
                    }
                    requests {
                        memory memoryResources.requests
                    }
                }
            }
            // Adding the memory of each module to the total memory.
            memorySum += bytes
        }else{
            // Creating a yaml config for a module.
            moduleConfig.(matcherModule.group(nameGroup)) {
                postJob {
                    enabled false
                }
                replicaCount 1
            }
        }
        configYaml += moduleConfig.toString().split(System.getProperty('line.separator')).drop(1).join(System.getProperty('line.separator')) + System.getProperty('line.separator')
    }

}

println('\n' + '=' * 60 + '\n')
Map totalMemory = calculateMemory(configType, memorySum)
println("Total memory:\nRequests: ${totalMemory.requests}\nLimits: ${totalMemory.limits}")

// Writing the configYaml to a file.
File file = new File("${configType}-draft.yaml")
file.write configYaml.tr(/"'/, /'"/)

// A function that gets the launch descriptor for a module.
Map getLaunchDescriptor(String module_name) {
    Map descriptor = [:]
    URLConnection defaultModuleDescriptor = new URL('https://raw.githubusercontent.com/folio-org/' + module_name + '/master/descriptors/ModuleDescriptor-template.json').openConnection()
    // This is checking for the existence of the ModuleDescriptor-template.json file in the module's repository.
    if (defaultModuleDescriptor.getResponseCode().equals(200)) {
        descriptor = new JsonSlurperClassic().parseText(defaultModuleDescriptor.getInputStream().getText())
    } else {
        URLConnection customModuleDescriptor = new URL('https://raw.githubusercontent.com/folio-org/' + module_name + '/master/service/src/main/okapi/ModuleDescriptor-template.json').openConnection()
        if (customModuleDescriptor.getResponseCode().equals(200)) {
            descriptor = new JsonSlurperClassic().parseText(customModuleDescriptor.getInputStream().getText())
        } else {
            println("${module_name} module descriptor not found!")
            return null
        }
    }
    // This is checking for the existence of the launchDescriptor in the module's descriptor.
    if (descriptor.launchDescriptor) {
        return descriptor.launchDescriptor
    } else {
        println("${module_name} launchDescriptor not specified!")
        return null
    }
}

// Converting bytes to Ki or Mi.
static String bytesConverter(Long bytes) {
    Long absolute = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes)
    if (absolute < 1024) {
        return (bytes / 1024.0).round() + "Ki"
    } else {
        return (bytes / (1024L * 1024L)).round() + "Mi"
    }
}

// Calculating the memory requests and limits for each module.
static Map<String, String> calculateMemory(String configType, Long bytes) {
    switch (configType) {
        case 'development':
            return [requests: bytesConverter((bytes * 0.75).toLong()),
                    limits  : bytesConverter(bytes)]
            break
        case 'testing':
            return [requests: bytesConverter(bytes),
                    limits  : bytesConverter((bytes * 1.25).toLong())]
            break
        case 'performance':
            return [requests: bytesConverter(bytes),
                    limits  : bytesConverter((bytes * 1.25).toLong())]
            break
        default:
            throw new Exception("Config type ${configType} is unknown")
            break
    }
}
