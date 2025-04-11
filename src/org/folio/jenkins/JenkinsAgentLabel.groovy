package org.folio.jenkins

/**
 * Enum representing different Jenkins agent labels used in the pipeline.
 * <p>
 * This enum provides a centralized and type-safe way to manage Jenkins agent labels,
 * replacing the hardcoded list previously stored in {@code Constants.JENKINS_AGENTS}.
 * It can be used to retrieve agent labels dynamically, ensuring consistency and reducing errors.
 * </p>
 *
 * <h2>Usage Example in Jenkins Shared Library:</h2>
 *
 * <pre>{@code
 * import org.folio.jenkins.JenkinsAgentLabel
 *
 * // Get a single agent label
 * String agentLabel = JenkinsAgentLabel.JAVA_AGENT.getLabel()
 * println(agentLabel) // Output: "java-agent"
 *
 * // Get a list of all agent labels
 * List<String> allLabels = JenkinsAgentLabel.getAllLabels()
 * println(allLabels) // Output: ["default-agent", "java-agent", "stripes-agent", ...]
 * }</pre>
 */
enum JenkinsAgentLabel {
  /**
   * Default Jenkins agent.
   */
  DEFAULT_AGENT("default-agent"),

  /**
   * Java-specific agent for java builds.
   */
  JAVA_AGENT("java-agent"),

  /**
   * Stripes-specific agent for stripes build.
   */
  STRIPES_AGENT("stripes-agent"),

  /**
   * Jenkins agent for kaniko(docker) builds.
   */
  KANIKO_AGENT("kaniko-agent"),

  /**
   * Cypress-specific agent for stripes testing.
   */
  CYPRESS_AGENT("cypress-agent"),

  private final String label

  /**
   * Constructor for the JenkinsAgentLabel enum.
   *
   * @param label The string representation of the agent label.
   */
  JenkinsAgentLabel(String label) {
    this.label = label
  }

  /**
   * Retrieves the string label associated with the enum constant.
   *
   * @return The Jenkins agent label as a string.
   */
  String getLabel() {
    return label
  }

  /**
   * Retrieves a list of all available agent labels.
   * <p>
   * This method allows for dynamic retrieval of all agent labels, making it easy
   * to populate dropdowns in Jenkins UI or validate against a predefined list.
   * </p>
   *
   * @return A list of all agent label values.
   *
   * <h2>Example Usage:</h2>
   * <pre>{@code
   * List<String> agentLabels = JenkinsAgentLabel.getAllLabels()
   * println(agentLabels) // Output: ["default-agent", "java-agent", "stripes-agent", ...]
   * }</pre>
   */
  static List<String> getAllLabels() {
    return values().collect { it.label }
  }
}