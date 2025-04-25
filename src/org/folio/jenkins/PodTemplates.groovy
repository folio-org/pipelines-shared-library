package org.folio.jenkins

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.OnFailure
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge
import org.folio.Constants
import org.folio.utilities.Logger

/**
 * The {@code PodTemplates} class defines Kubernetes pod templates for Jenkins pipelines.
 *
 * <p>This class provides structured pod templates for different Jenkins agents, ensuring consistent configuration.
 * Each method represents a specific Jenkins agent type and provisions the required containers dynamically.
 * It leverages the Jenkins Kubernetes Plugin to manage agent pods.</p>
 *
 * <h2>References:</h2>
 * <ul>
 *     <li><a href="https://plugins.jenkins.io/kubernetes/#plugin-content-nesting-pod-templates">Jenkins Kubernetes Plugin</a> - Official documentation</li>
 *     <li><a href="https://www.jenkins.io/doc/pipeline/steps/kubernetes/">Jenkins Pipeline Kubernetes Steps</a> - Guide for Kubernetes steps in pipelines</li>
 *     <li><a href="https://github.com/jenkinsci/kubernetes-plugin">Kubernetes Plugin Repository</a> - Source code repository</li>
 * </ul>
 *
 * <h2>Usage Example in a Jenkinsfile:</h2>
 *
 * <pre>{@code
 * import org.folio.jenkins.PodTemplates;
 *
 * podTemplates = new PodTemplates(this)
 * ansiColor('xterm') {
 *     podTemplates.cypressTemplate {
 *         podTemplates.javaTemplate("11") {
 *             node(JenkinsAgentLabel.JAVA_AGENT.getLabel()) {
 *                 container('java') {
 *                     sh 'java -version'
 *                 }
 *                 container('cypress') {
 *                     sh 'yarn -v'
 *                 }
 *             }
 *         }
 *     }
 * }
 *}</pre>*/
class PodTemplates {
  private static final String CLOUD_NAME = 'folio-jenkins-agents'
  private static final String NAMESPACE = 'jenkins-agents'
  private static final String SERVICE_ACCOUNT = 'jenkins-service-account'
  public static final String WORKING_DIR = '/home/jenkins/agent'
  private static final String YARN_CACHE_PVC = 'yarn-cache-pvc'
  private static final String MAVEN_CACHE_PVC = 'maven-cache-pvc'
  private static final String ECR_REPOSITORY = '732722833398.dkr.ecr.us-west-2.amazonaws.com'


  private Object steps
  private boolean debug
  private Logger logger
  private PodRetention podRetention = new Never()

  /**
   * Constructs a {@code PodTemplates} instance.
   *
   * @param context The pipeline script context (usually `this` in a Jenkinsfile).
   * @param debug Enables debugging mode to display raw YAML configurations (default: {@code false}).
   */
  PodTemplates(context, boolean debug = false) {
    this.steps = context
    this.debug = debug
    this.logger = new Logger(context, 'PodTemplates')
    if (debug) {
      this.podRetention = new OnFailure()
    }
  }

  /**
   * Defines a default Kubernetes pod template for Jenkins agents.
   *
   * <p>This template serves as a base for other pod templates, providing consistent configurations.</p>
   *
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void defaultTemplate(Closure body) {
    logger.debug("Debug mode: ${debug}")

    steps.podTemplate(cloud: CLOUD_NAME,
      label: JenkinsAgentLabel.DEFAULT_AGENT.getLabel(),
      namespace: NAMESPACE,
      serviceAccount: SERVICE_ACCOUNT,
      nodeUsageMode: 'EXCLUSIVE',
      showRawYaml: debug,
      yamlMergeStrategy: new Merge(),
      yaml: """
spec:
  securityContext:
    fsGroup: 1000
""",
      podRetention: podRetention,
      inheritYamlMergeStrategy: true,
      slaveConnectTimeout: 300,
      hostNetwork: false,
      workspaceVolume: steps.genericEphemeralVolume(accessModes: 'ReadWriteOnce',
        requestsSize: '5Gi',
        storageClassName: 'gp3'),
      containers: [
        steps.containerTemplate(name: 'jnlp',
          image: "${ECR_REPOSITORY}/folio-jenkins-agent:latest",
          alwaysPullImage: true,
          ttyEnabled: true,
          workingDir: WORKING_DIR,
          resourceRequestMemory: '1536Mi',
          resourceLimitMemory: '2048Mi',
        )]) {
      body.call()
    }
  }

  /**
   * Defines a pod template for Java-based builds.
   *
   * <p>Provisions a pod with a Java container for the specified Java version.</p>
   *
   * @param javaVersion The Java version (e.g., "11", "17", "21").
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void javaBaseTemplate(String javaVersion, String podLabel = JenkinsAgentLabel.JAVA_AGENT.getLabel(), Closure body) {
    logger.info("Using Java version: ${javaVersion}")
//    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.JAVA_AGENT.getLabel()
//      : "${JenkinsAgentLabel.JAVA_AGENT.getLabel()}-${podUuid}"

    defaultTemplate {
      steps.podTemplate(label: podLabel,
        volumes: [steps.persistentVolumeClaim(claimName: MAVEN_CACHE_PVC, mountPath: "${WORKING_DIR}/.m2/repository")],
        containers: [
          steps.containerTemplate(name: 'java',
            image: "${ECR_REPOSITORY}/amazoncorretto:${javaVersion}-alpine-jdk",
            alwaysPullImage: true,
            envVars: [new KeyValueEnvVar('HOME', WORKING_DIR)],
            command: 'sleep',
            args: '99d',
            runAsGroup: '1000',
            runAsUser: '1000',
            resourceRequestMemory: '2Gi',
            resourceLimitMemory: '4Gi')]) {

        body.call()
      }
    }
  }

  /**
   * Defines a pod template for Java-based builds.
   *
   * <p>Provisions a pod with a Java container for the specified Java version.</p>
   *
   * @param javaVersion The Java version (e.g., "11", "17", "21").
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void javaDindTemplate(String javaVersion, String podUuid = '', Closure body) {
    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.JAVA_AGENT.getLabel()
      : "${JenkinsAgentLabel.JAVA_AGENT.getLabel()}-${podUuid}"

    javaBaseTemplate(javaVersion, podUuid) {
      steps.podTemplate(label: podLabel,
        containers: [
          steps.containerTemplate(name: 'java',
            image: "${ECR_REPOSITORY}/amazoncorretto:${javaVersion}-alpine-jdk",
            envVars: [new KeyValueEnvVar('DOCKER_HOST', 'tcp://localhost:2375'),
                      new KeyValueEnvVar('HOME', WORKING_DIR)],
            resourceRequestMemory: '4Gi',
            resourceLimitMemory: '5Gi'),
          steps.containerTemplate(name: 'dind',
            image: 'docker:dind',
            envVars: [new KeyValueEnvVar('DOCKER_TLS_CERTDIR', '')],
            privileged: true,
            args: '--host=tcp://0.0.0.0:2375 --host=unix:///var/run/docker.sock'
          )]) {
        body.call()
      }
    }
  }

  /**
   * Defines a pod template for building Stripes UI bundle.
   *
   * <p>This template provisions a pod with a predefined Jenkins agent container.</p>
   *
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void stripesTemplate(Closure body) {
    kanikoTemplate {
      steps.podTemplate(label: JenkinsAgentLabel.STRIPES_AGENT.getLabel(),
        yaml: """
spec:
  topologySpreadConstraints:
  - maxSkew: 2
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        jenkins/label: "stripes-agent"
""",
        containers: [steps.containerTemplate(name: 'kaniko',
          image: 'gcr.io/kaniko-project/executor:debug',
          alwaysPullImage: true,
          runAsGroup: '1000',
          runAsUser: '1000',
          resourceRequestMemory: '8Gi',
          resourceLimitMemory: '10Gi')]) {
        body.call()
      }
    }
  }

  /**
   * Defines a pod template for Kaniko, used for building container images in Kubernetes.
   *
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void kanikoTemplate(String podUuid = '', Closure body) {
    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.KANIKO_AGENT.getLabel()
      : "${JenkinsAgentLabel.KANIKO_AGENT.getLabel()}-${podUuid}"

    defaultTemplate {
      steps.podTemplate(label: podLabel,
        containers: [
          steps.containerTemplate(name: 'kaniko',
            image: 'gcr.io/kaniko-project/executor:debug',
            alwaysPullImage: true,
            envVars: [new KeyValueEnvVar('KANIKO_DIR', "${WORKING_DIR}/kaniko")],
            command: 'sleep',
            args: '99d'
            // TODO: Define resource requests/limits after production load testing
          )]) {
        body.call()
      }
    }
  }

  /**
   * Defines a pod template for Cypress end-to-end testing.
   *
   * @param body A closure containing the pipeline logic to execute within this pod.
   */
  void cypressTemplate(String podUuid = '', Closure body) {
    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.CYPRESS_AGENT.getLabel()
      : "${JenkinsAgentLabel.CYPRESS_AGENT.getLabel()}-${podUuid}"

    defaultTemplate {
      steps.podTemplate(label: podLabel,
        volumes: [steps.persistentVolumeClaim(claimName: YARN_CACHE_PVC, mountPath: "${WORKING_DIR}/.yarn/cache")],
        containers: [
          steps.containerTemplate(name: 'cypress',
            image: "${ECR_REPOSITORY}/cypress/browsers:latest",
            alwaysPullImage: true,
            command: 'sleep',
            args: '99d',
            envVars: [new KeyValueEnvVar('YARN_CACHE_FOLDER', "${WORKING_DIR}/.yarn/cache"),
                      new KeyValueEnvVar('NODE_PATH', "${WORKING_DIR}/.yarn/cache/node_modules")],
            runAsGroup: '1000',
            runAsUser: '1000',
            resourceRequestMemory: '2Gi',
            resourceLimitMemory: '3Gi'
          )]) {
        body.call()
      }
    }
  }

  void cypressTestsAgent(Closure body) {
    String podUuid = UUID.randomUUID().toString().replaceAll("-", "").take(6)
    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.CYPRESS_AGENT.getLabel()
      : "${JenkinsAgentLabel.CYPRESS_AGENT.getLabel()}-${podUuid}"

    cypressTemplate(podUuid) {
      steps.node(podLabel) {
        body.call()
      }
    }
  }

  void javaPlainAgent(String javaVersion, Closure body) {
    String podUuid = UUID.randomUUID().toString().replaceAll("-", "").take(6)
    String podLabel = podUuid?.isEmpty() ? JenkinsAgentLabel.JAVA_AGENT.getLabel()
      : "${JenkinsAgentLabel.JAVA_AGENT.getLabel()}-${podUuid}"

    javaBaseTemplate(javaVersion, podLabel) {
      steps.node(podLabel) {
        body.call()
      }
    }
  }

  void javaTestsAgent() {}

  void javaBuildAgent() {}

  void stripesAgent() {}

  void rancherAgent(Closure body) {
    String podLabel = 'rancher-agent'

    javaBaseTemplate(Constants.JAVA_LATEST_VERSION, podLabel) {
      steps.node(podLabel) {
        body.call()
      }
    }
  }
}