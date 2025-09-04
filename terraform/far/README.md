# FOLIO Application Registry (FAR) Terraform Module

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Required Variables](#required-variables)
- [Usage](#usage)
- [Recovery Procedures](#recovery-procedures)

## Overview

This Terraform module deploys the FOLIO Application Registry (FAR) infrastructure on AWS EKS with Rancher. It provisions
a PostgreSQL database with durable EBS storage, automated backups, and the MGR Applications service in FAR mode.

## Architecture

The infrastructure consists of:

- Rancher project and Kubernetes namespace
- AWS EBS volume with daily snapshots (180-day retention)
- PostgreSQL database deployed via Helm
- MGR Applications service in FAR mode
- Secure credential management with AWS Secrets Manager

![Architecture Diagram](./images/architecture.svg)

## Prerequisites

- AWS CLI configured with appropriate permissions
- Terraform v1.6.1+
- Access to an EKS cluster
- Rancher API token
- `kubectl` configured for the target cluster

## Required Variables

| Variable                 | Description                                        | Type   | Default                         |
|--------------------------|----------------------------------------------------|--------|---------------------------------|
| `cluster_name`           | EKS cluster name                                   | string | rancher                         |
| `project_name`           | Kubernetes namespace name and Rancher project name | string | folio-applications-registry     |
| `domain_name`            | Domain name for the application                    | string | far.ci.folio.org                |
| `aws_region`             | AWS region for resources                           | string | us-west-2'                      |
| `rancher_server_url`     | Rancher API URL                                    | string | https://rancher.ci.folio.org/v3 |
| `rancher_token_key`      | Rancher API token                                  | string |                                 |
| `postgres_chart_version` | PostgreSQL Helm chart version                      | string | 16.7.21                         |
| `mgr_chart_version`      | MGR Applications Helm chart version                | string | 0.0.15                          |

For a complete list of variables, see the `variables.tf` file.

## Usage

1. **Initialize Terraform**
   ```bash
   terraform init
   ```

2. **Configure Variables**
   Create a `terraform.tfvars` file:
   ```hcl
   cluster_name      = "my-eks-cluster"
   project_name    = "far"
   domain_name       = "far.ci.folio.org"
   aws_region        = "us-west-2"
   rancher_server_url = "https://rancher.example.com"
   rancher_token_key = "token-xxxxx"
   ```

3. **Review Plan**
   ```bash
   terraform plan -input=false -out tfplan
   ```

4. **Apply Configuration**
   ```bash
   terraform apply -input=false tfplan
   ```

5. **Verify Deployment**
   ```bash
   kubectl get pods -n far
   ```

## Recovery Procedures

For disaster recovery scenarios, refer to:
- [Recovery Procedures Documentation](./RECOVERY.md)

These documents cover various failure scenarios including:

- EBS volume or Kubernetes infrastructure loss
- PostgreSQL StatefulSet deletion
- MGR Application deployment deletion
- Credential loss 
