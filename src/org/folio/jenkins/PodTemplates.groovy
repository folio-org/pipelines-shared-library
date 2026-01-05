package org.folio.jenkins

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.OnFailure
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge
import org.folio.Constants
import org.folio.utilities.Logger

/**
 * {@code PodTemplates} provides predefined Kubernetes pod templates for Jenkins pipelines,
 * tailored for different types of builds (e.g., Java builds, Cypress tests, Rancher jobs).
 *
 * <p>It abstracts common container patterns, volume mounts, and resource configurations,
 * ensuring consistency, maintainability, and optimized agent deployments in the Jenkins cluster.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Dynamic container configurations (Java, Docker-in-Docker, Cypress, Kaniko).</li>
 *   <li>Support for workspace ephemeral storage and caching (Maven, Yarn).</li>
 *   <li>Debugging support with pod YAML dump on failure.</li>
 * </ul>
 *
 * <h2>References:</h2>
 * <ul>
 *   <li><a href="https://plugins.jenkins.io/kubernetes/">Jenkins Kubernetes Plugin</a></li>
 *   <li><a href="https://github.com/jenkinsci/kubernetes-plugin">Plugin Source Repository</a></li>
 *   <li><a href="https://www.jenkins.io/doc/pipeline/steps/kubernetes/">Pipeline Kubernetes Steps</a></li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * def templates = new PodTemplates(this)
 * templates.cypressAgent {
 *   container('cypress') {
 *     sh 'yarn run test'
 *   }
 * }
 *}</pre>
 */
class PodTemplates {

  // ===== Static Configuration =====
  private static final String CLOUD_NAME = 'folio-jenkins-agents'
  private static final String NAMESPACE = 'jenkins-agents'
  private static final String SERVICE_ACCOUNT = 'jenkins-service-account'
  public static final String WORKING_DIR = '/home/jenkins/agent'
  private static final String YARN_CACHE_PVC = 'yarn-cache-pvc'
  private static final String MAVEN_CACHE_PVC = 'maven-cache-pvc'
  private static final String ECR_REPOSITORY = '732722833398.dkr.ecr.us-west-2.amazonaws.com'

  // ===== Instance State =====
  private Object steps
  private boolean debug
  private Logger logger
  private PodRetention podRetention = new Never()

  /**
   * Creates a new instance of {@code PodTemplates}.
   *
   * @param context The Jenkins pipeline script context (typically {@code this} in a Jenkinsfile).
   * @param debug If {@code true}, enables YAML output for pod templates on agent creation failures.
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
   * Defines the base pod template configuration.
   *
   * <p>Provides workspace volume, pod security context, and a minimal JNLP container.</p>
   *
   * @param body The closure to execute inside this pod.
   */
  private void defaultTemplate(Closure body) {
    logger.debug("Debug mode: ${debug}")

    steps.podTemplate(
      cloud: CLOUD_NAME,
      label: JenkinsAgentLabel.DEFAULT_AGENT.getLabel(),
      namespace: NAMESPACE,
      serviceAccount: SERVICE_ACCOUNT,
      nodeUsageMode: 'EXCLUSIVE',
      showRawYaml: debug,
      yamlMergeStrategy: new Merge(),
      yaml: '''
spec:
  securityContext:
    fsGroup: 1000
''',
      podRetention: podRetention,
      inheritYamlMergeStrategy: true,
      slaveConnectTimeout: 900,
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
          resourceRequestMemory: '1024Mi',
          resourceLimitMemory: '12228Mi',
          envVars: [
            new KeyValueEnvVar('JENKINS_JAVA_OPTS',
              '-Dorg.jenkinsci.remoting.engine.JnlpAgentEndpointResolver.PING_INTERVAL=30' +
                ' -Dorg.jenkinsci.remoting.engine.JnlpAgentEndpointResolver.PING_TIMEOUT=600' +
                ' -Dorg.jenkinsci.remoting.websocket.WebSocketSession.pingInterval=30')
          ]
        )]) {
      body.call()
        }
  }

  /**
   * Generates a pod label based on a base agent label and an optional UUID suffix.
   *
   * @param label Base {@link JenkinsAgentLabel}.
   * @param uuid Optional unique identifier suffix.
   * @return Combined label.
   */
  private static String generatePodLabel(JenkinsAgentLabel label, String uuid = null) {
    return uuid?.trim() ? "${label.getLabel()}-${uuid}" : label.getLabel()
  }

  // ========= Container Builders =========

  /**
   * Builds a Java container definition for a specified Java version.
   *
   * @param javaVersion Java version tag (e.g., {@code 17}, {@code 21}).
   * @param extraEnvVars Optional extra environment variables.
   * @param resourceRequestMemory Minimum memory request (default {@code 2Gi}).
   * @param resourceLimitMemory Memory limit (default {@code 12Gi}).
   * @return Java container definition.
   */
  private Object buildJavaContainer(
    String javaVersion,
    List<KeyValueEnvVar> extraEnvVars = [],
    String resourceRequestMemory = '2Gi',
    String resourceLimitMemory = '12Gi'
  ) {
    return steps.containerTemplate(
      name: 'java',
      image: "${ECR_REPOSITORY}/amazoncorretto:${javaVersion}-alpine-jdk",
      alwaysPullImage: true,
      envVars: [new KeyValueEnvVar('HOME', WORKING_DIR),
                new KeyValueEnvVar('MAVEN_OPTS', '-XX:MaxRAMPercentage=75.0 ' +
                  '-javaagent:/jmx_exporter/jmx_prometheus_javaagent.jar=9991:/jmx_exporter/jmx_prometheus_config.yaml')] + extraEnvVars,
      ports: [[name: 'jmx', containerPort: '9991']],
      command: 'sleep',
      args: '99d',
      runAsGroup: '1000',
      runAsUser: '1000',
      resourceRequestMemory: resourceRequestMemory,
      resourceLimitMemory: resourceLimitMemory
    )
  }

  /**
   * Builds a Docker-in-Docker (DIND) container.
   *
   * @param extraEnvVars Optional additional environment variables.
   * @return DIND container definition.
   */
  private Object buildDindContainer(
    List<KeyValueEnvVar> extraEnvVars = [],
    String resourceRequestMemory = '128Mi',
    String resourceLimitMemory = '12228Mi') {
    return steps.containerTemplate(
      name: 'dind',
      image: 'docker:dind',
      envVars: [new KeyValueEnvVar('DOCKER_TLS_CERTDIR', '')] + extraEnvVars,
      privileged: true,
      args: '--host=tcp://0.0.0.0:2375 --host=unix:///var/run/docker.sock',
      resourceRequestMemory: resourceRequestMemory,
      resourceLimitMemory: resourceLimitMemory
    )
    }

  /**
   * Builds a Kaniko container for Docker image builds without Docker daemon.
   *
   * @param extraEnvVars Optional environment variables.
   * @param resourceRequestMemory Minimum memory request (default {@code 2Gi}).
   * @param resourceLimitMemory Memory limit (default {@code 12Gi}).
   * @return Kaniko container definition.
   */
  private Object buildKanikoContainer(
    List<KeyValueEnvVar> extraEnvVars = [],
    String resourceRequestMemory = '2Gi',
    String resourceLimitMemory = '12Gi'
  ) {
    return steps.containerTemplate(
      name: 'kaniko',
      image: 'gcr.io/kaniko-project/executor:debug',
      alwaysPullImage: true,
      envVars: [new KeyValueEnvVar('KANIKO_DIR', "${WORKING_DIR}/kaniko")] + extraEnvVars,
      command: 'sleep',
      args: '99d',
      resourceRequestMemory: resourceRequestMemory,
      resourceLimitMemory: resourceLimitMemory
    )
  }

  /**
   * Builds a Cypress test container.
   *
   * @param extraEnvVars Optional environment variables.
   * @param resourceRequestMemory Minimum memory request (default {@code 2Gi}).
   * @param resourceLimitMemory Memory limit (default {@code 3Gi}).
   * @return Cypress container definition.
   */
  private Object buildCypressContainer(
    List<KeyValueEnvVar> extraEnvVars = [],
    String resourceRequestMemory = '3072Mi',
    String resourceLimitMemory = '12228Mi'
  ) {
    return steps.containerTemplate(
      name: 'cypress',
      image: "${ECR_REPOSITORY}/cypress/browsers:latest",
      alwaysPullImage: true,
      command: 'sleep',
      args: '99d',
      envVars: [
        new KeyValueEnvVar('YARN_CACHE_FOLDER', "${WORKING_DIR}/.yarn/cache"),
        new KeyValueEnvVar('NODE_PATH', "${WORKING_DIR}/.yarn/cache/node_modules")
      ] + extraEnvVars,
      runAsGroup: '1000',
      runAsUser: '1000',
      resourceRequestMemory: resourceRequestMemory,
      resourceLimitMemory: resourceLimitMemory
    )
  }

  /**
   * Creates a customized pod template and executes a pipeline body inside.
   *
   * @param config Configuration object defining the template (volumes, containers, yaml, etc.).
   * @param body Closure to execute inside the configured pod.
   */
  private void createTemplate(PodTemplateConfig config, Closure body) {
    defaultTemplate {
      steps.podTemplate(
        label: config.label ?: JenkinsAgentLabel.DEFAULT_AGENT.getLabel(),
        idleMinutes: config.idleMinutes ?: 0,
        workspaceVolume: config.workspaceVolume ?: steps.genericEphemeralVolume(
          accessModes: 'ReadWriteOnce',
          requestsSize: '5Gi',
          storageClassName: 'gp3'
        ),
        yaml: config.yaml ?: '''
spec:
  securityContext:
    fsGroup: 1000
''',
        containers: config.containers ?: [],
        volumes: config.volumes ?: []
      ) {
        body.call()
      }
    }
  }

  // ========= Public Pod Template Methods =========

  /**
   * Defines a Java build agent template.
   *
   * <p>Installs Java, Docker daemon, and Kaniko for Maven and Docker builds.</p>
   *
   * @param javaVersion Java version to install.
   * @param body Pipeline steps to execute inside this agent.
   */
  void javaBuildAgent(String javaVersion, Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.JAVA_BUILD_AGENT.getLabel(),
      volumes: [steps.persistentVolumeClaim(claimName: MAVEN_CACHE_PVC, mountPath: "${WORKING_DIR}/.m2/repository")],
      containers: [
        buildKanikoContainer([], '512Mi', '12228Mi'),
        buildJavaContainer(javaVersion, [new KeyValueEnvVar('DOCKER_HOST', 'tcp://localhost:2375')], '768Mi', '12228Mi'),
        buildDindContainer([], '4096Mi', '12228Mi')
      ]
    )) {
      steps.node(JenkinsAgentLabel.JAVA_BUILD_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines a Java agent optimized for Karate tests.
   *
   * @param javaVersion Java version to install.
   * @param body Pipeline steps to execute inside this agent.
   */
  void javaKarateAgent(String javaVersion, Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.JAVA_KARATE_AGENT.getLabel(),
      volumes: [steps.persistentVolumeClaim(claimName: MAVEN_CACHE_PVC, mountPath: "${WORKING_DIR}/.m2/repository")],
      containers: [
        buildJavaContainer(javaVersion, [], '5120Mi', '12228Mi')
      ]
    )) {
      steps.node(JenkinsAgentLabel.JAVA_KARATE_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines a Stripes UI build agent.
   *
   * <p>Kaniko is provided for building containerized frontend applications.</p>
   *
   * @param body Pipeline steps to execute inside this agent.
   */
  void stripesAgent(Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.STRIPES_AGENT.getLabel(),
      yaml: """
spec:
  topologySpreadConstraints:
  - maxSkew: 2
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        jenkins/label: "${JenkinsAgentLabel.STRIPES_AGENT.getLabel()}"
""",
      containers: [
        buildKanikoContainer([], '9Gi', '12Gi'),
      ]
    )) {
      steps.node(JenkinsAgentLabel.STRIPES_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines an agent specialized for Cypress end-to-end testing.
   *
   * @param body Pipeline steps to execute inside this agent.
   */
  void cypressAgent(Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.CYPRESS_AGENT.getLabel(),
      idleMinutes: 5,
      workspaceVolume: steps.genericEphemeralVolume(accessModes: 'ReadWriteOnce',
        requestsSize: '20Gi',
        storageClassName: 'gp3'),
      volumes: [steps.persistentVolumeClaim(claimName: YARN_CACHE_PVC, mountPath: "${WORKING_DIR}/.yarn/cache")],
      containers: [
        buildCypressContainer([], '2048Mi', '4096Mi'),
      ]
    )) {
      steps.node(JenkinsAgentLabel.CYPRESS_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines a minimal Rancher CLI agent (without extra tools).
   *
   * @param body Pipeline steps to execute inside this agent.
   */
  void rancherAgent(Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.RANCHER_AGENT.getLabel()
    )) {
      steps.node(JenkinsAgentLabel.RANCHER_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines a Rancher CLI agent with Java pre-installed.
   *
   * @param body Pipeline steps to execute inside this agent.
   */
  void rancherJavaAgent(Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.RANCHER_JAVA_AGENT.getLabel(),
      containers: [
        buildJavaContainer(Constants.JAVA_LATEST_VERSION, [], '4096Mi', '12228Mi')
      ]
    )) {
      steps.node(JenkinsAgentLabel.RANCHER_JAVA_AGENT.getLabel()) {
        body.call()
      }
    }
  }

  /**
   * Defines a minimal Kaniko agent for container builds only.
   *
   * @param body Pipeline steps to execute inside this agent.
   */
  void kanikoAgent(Closure body) {
    createTemplate(new PodTemplateConfig(
      label: JenkinsAgentLabel.KANIKO_AGENT.getLabel(),
      containers: [
        buildKanikoContainer()
      ]
    )) {
      steps.node(JenkinsAgentLabel.KANIKO_AGENT.getLabel()) {
        body.call()
      }
    }
  }

}
