package org.folio.models.parameters

import com.cloudbees.groovy.cps.NonCPS

class CreateNamespaceParameters implements Cloneable {
  String clusterName

  String namespaceName

  String folioBranch

  String okapiVersion

  String configType

  boolean loadReference

  boolean loadSample

  boolean consortia

  boolean rwSplit

  boolean greenmail

  boolean mockServer

  boolean rtr

  String pgType

  String pgVersion

  String kafkaType

  String opensearchType

  String s3Type

  String members

  boolean namespaceOnly = false

  private CreateNamespaceParameters() {}

  @NonCPS
  @Override
  CreateNamespaceParameters clone() {
    try {
      CreateNamespaceParameters clone = (CreateNamespaceParameters) super.clone()
      return clone
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Cloning not supported", e)
    }
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static class Builder {

    private CreateNamespaceParameters parameters = new CreateNamespaceParameters()

    public Builder() {}

    public Builder(CreateNamespaceParameters existingParameters) {
      this.parameters = existingParameters.clone()
    }

    public Builder clusterName(String clusterName) {
      parameters.clusterName = clusterName
      return this
    }

    public Builder namespaceName(String namespaceName) {
      parameters.namespaceName = namespaceName
      return this
    }

    public Builder folioBranch(String folioBranch) {
      parameters.folioBranch = folioBranch
      return this
    }

    public Builder okapiVersion(String okapiVersion) {
      parameters.okapiVersion = okapiVersion
      return this
    }

    public Builder configType(String configType) {
      parameters.configType = configType
      return this
    }

    public Builder loadReference(boolean loadReference) {
      parameters.loadReference = loadReference
      return this
    }

    public Builder loadSample(boolean loadSample) {
      parameters.loadSample = loadSample
      return this
    }

    public Builder consortia(boolean consortia) {
      parameters.consortia = consortia
      return this
    }

    public Builder rwSplit(boolean rwSplit) {
      parameters.rwSplit = rwSplit
      return this
    }

    public Builder greenmail(boolean greenmail) {
      parameters.greenmail = greenmail
      return this
    }

    public Builder mockServer(boolean mockServer) {
      parameters.mockServer = mockServer
      return this
    }

    public Builder rtr(boolean rtr) {
      parameters.rtr = rtr
      return this
    }

    public Builder pgType(String pgType) {
      parameters.pgType = pgType
      return this
    }

    public Builder pgVersion(String pgVersion) {
      parameters.pgVersion = pgVersion
      return this
    }

    public Builder kafkaType(String kafkaType) {
      parameters.kafkaType = kafkaType
      return this
    }

    public Builder opensearchType(String opensearchType) {
      parameters.opensearchType = opensearchType
      return this
    }

    public Builder s3Type(String s3Type) {
      parameters.s3Type = s3Type
      return this
    }

    public Builder members(String members) {
      parameters.members = members
      return this
    }

    public Builder namespaceOnly(boolean namespaceOnly) {
      parameters.namespaceOnly = namespaceOnly
      return this
    }

    public CreateNamespaceParameters build() {
      // Optionally validate the parameter object here or in the build() method
      return parameters
    }
  }
}
