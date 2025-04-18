package org.folio.jenkins

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.OnFailure
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge
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
      containers: [steps.containerTemplate(name: 'jnlp',
        image: '732722833398.dkr.ecr.us-west-2.amazonaws.com/folio-jenkins-agent:latest',
        ttyEnabled: true,
        alwaysPullImage: true,
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
  void javaTemplate(String javaVersion, Closure body) {
    defaultTemplate {
      steps.podTemplate(label: JenkinsAgentLabel.JAVA_AGENT.getLabel(),
        volumes: [steps.persistentVolumeClaim(claimName: MAVEN_CACHE_PVC, mountPath: "${WORKING_DIR}/.m2/repository")],
        containers: [steps.containerTemplate(name: 'java',
          image: "amazoncorretto:${javaVersion}-alpine-jdk",
          command: 'sleep',
          args: '99d',
          resourceRequestMemory: '4Gi',
          resourceLimitMemory: '5Gi')]) {
        logger.info("Using Java version: ${javaVersion}")
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
  void kanikoTemplate(Closure body) {
    defaultTemplate {
      steps.podTemplate(label: JenkinsAgentLabel.KANIKO_AGENT.getLabel(),
        containers: [steps.containerTemplate(name: 'kaniko',
          image: 'gcr.io/kaniko-project/executor:debug',
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
  void cypressTemplate(Closure body) {
    defaultTemplate {
      steps.podTemplate(label: JenkinsAgentLabel.CYPRESS_AGENT.getLabel(),
        volumes: [steps.persistentVolumeClaim(claimName: YARN_CACHE_PVC, mountPath: "${WORKING_DIR}/.yarn/cache")],
        containers: [steps.containerTemplate(name: 'cypress',
          image: '732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:latest',
          command: 'sleep',
          args: '99d',
          envVars: [new KeyValueEnvVar('YARN_CACHE_FOLDER', "${WORKING_DIR}/.yarn/cache"),
                    new KeyValueEnvVar('NODE_PATH', "${WORKING_DIR}/.yarn/cache/node_modules")],
          resourceRequestMemory: '2Gi',
          resourceLimitMemory: '3Gi'
        )]) {
        body.call()
      }
    }
  }
}