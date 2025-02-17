# Jenkins Pipelines Shared Library for FOLIO

## Overview

The Jenkins Pipelines Shared Library for FOLIO is designed to facilitate and streamline the management of Jenkins pipelines. It provides reusable functions and scripts, aimed at automating CI/CD workflows for the FOLIO platform. This repository includes resources for Jenkins pipeline configurations, Jenkins plugin management, and integration with other components of the FOLIO ecosystem.

## Features

- **Jenkins Core and Plugin Updates**: Automate the process of updating Jenkins core and its plugins.
- **Gradle Integration**: Uses Gradle to sync Jenkins configuration and manage dependencies.
- **Reusable Pipeline Functions**: A set of shared pipeline steps and functions that can be used across multiple Jenkins projects.
- **Custom Properties**: Custom Gradle properties for managing Jenkins URL, user credentials, and API tokens.

## Getting Started

To get started with the shared library, you need to configure your Jenkins environment and sync the configuration using Gradle.

### Prerequisites

Before using the shared library, ensure the following:

- Jenkins is set up and running.
- Java is installed on your system.
- You have access to the Jenkins API with appropriate permissions.

### Setup

1. **Configure Jenkins Properties**: Define the following Gradle properties in your `gradle.properties` file:
  - `jenkinsUrl`: The URL of your Jenkins server.
  - `jenkinsUser`: Your Jenkins username.
  - `jenkinsApiToken`: Your Jenkins API token, which can be generated at [Jenkins User Configuration](https://{jenkinsUrl}/user/{jenkinsUrl}/configure).

2. **Retrieve Jenkins Plugin Data** :
  - Open the following URL: [Jenkins Plugin Manager](https://jenkins-aws.indexdata.com/pluginManager/api/json?depth=2).
  - Save the content and place it in the `jenkinsResources/plugins.json` file.

3. **Sync Jenkins Configuration**:
   Run the following Gradle command to sync the Jenkins configuration:
    ```bash
    gradle syncJenkinsConfig
    ```

## Directory Structure

The repository contains the following key directories and files:

- `gradle/`: Contains Gradle wrapper files and configuration.
- `jenkinsResources/`: Stores Jenkins-specific resources like plugin data.
- `pipelines/`: Contains the pipeline scripts and shared steps.
- `resources/`: General resources used by the pipeline.
- `scripts/`: Custom scripts for automating various tasks.
- `src/`: Source code for the shared library functions.
- `terraform/`: Terraform scripts for infrastructure management.
- `test/`: Unit and integration tests for the pipeline functions.
- `vars/`: Variables used in the pipeline configuration.
- `.gitignore`: Git ignore file.
- `.editorconfig`: Code style configuration file.
- `README.md`: This file.

## Usage

To use the shared library in your Jenkins pipeline, include it in your `Jenkinsfile` as follows:

```groovy
@Library('pipeline-shared-library') _
```

Then, you can call the shared functions defined in the library, such as syncing Jenkins configuration or managing plugins.

## Contributing

Contributions to the repository are welcome! If you have any improvements or bug fixes, please submit a pull request. Ensure that your code follows the existing style guidelines and includes appropriate tests.
