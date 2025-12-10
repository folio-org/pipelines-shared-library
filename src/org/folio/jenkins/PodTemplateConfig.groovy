package org.folio.jenkins

/**
 * PodTemplateConfig is a class that represents the configuration for a Jenkins pod template.
 * It contains properties such as label, yaml, workspaceVolume, containers, and volumes.
 * This class is used to define the configuration for a Jenkins pipeline job that runs in a Kubernetes pod.
 */
class PodTemplateConfig {
  String label
  String yaml
  Object workspaceVolume
  List containers
  List volumes
  Integer idleMinutes

  /**
   * Constructor for PodTemplateConfig.
   * Initializes the properties to default values.
   */
  PodTemplateConfig() {}
}
