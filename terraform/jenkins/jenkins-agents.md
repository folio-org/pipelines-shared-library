# Jenkins Kubernetes Cloud Setup for Jenkins Agents (AWS EKS & Rancher)

## Overview

This guide provides step-by-step instructions to set up Jenkins to use Kubernetes for dynamic build agents. We will create an AWS EKS cluster, configure it via Rancher, and set up Jenkins with the Kubernetes plugin to launch agents on the cluster.

## Requirements

- **Jenkins**: Version 2.492 or higher (with the Kubernetes plugin installed)
- **Rancher**: Version 2.8 or higher (to manage the Kubernetes cluster)
- **Kubernetes**: Cluster running version 1.32 or higher (Amazon EKS in this guide)
- **AWS CLI / AWS Access**: Permissions to create and manage EKS clusters on AWS

## 1. Create an AWS EKS Cluster

First, provision a new **Amazon EKS** Kubernetes cluster for Jenkins agents.

1. **Run the Jenkins job**: Trigger the Jenkins job `createAwsEksCluster`. This job is pre-configured to create an EKS cluster.
2. **Wait for completion**: The job will call Terraform scripts to create the cluster.
3. **Verify the cluster**: After the job completes, ensure the EKS cluster is up and running.
   ```bash
   aws eks describe-cluster --name <YOUR_CLUSTER_NAME> --query 'cluster.status'
   ```

## 2. Create a Rancher Project for Jenkins Agents

1. **Open Rancher**: Log in to Rancher UI and navigate to the Cluster that was just created.
2. **Create Project**: Go to **☰ > Cluster Management > Projects/Namespaces**. Click **Create Project** and name it `jenkins-agents`.
3. **Confirm Namespace**: The new namespace should appear under the `jenkins-agents` project.

## 3. Create a Kubernetes Namespace for Jenkins Agents

1. **Create Namespace**: In Rancher UI, inside the `jenkins-agents` project, click **Create Namespace**. Name the namespace `jenkins-agents`.

   Alternatively, using `kubectl`:
   ```bash
   kubectl create namespace jenkins-agents
   ```

## 4. Set Up a Jenkins Service Account and RBAC

**a. Create the ServiceAccount**:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jenkins-service-account
  namespace: jenkins-agents
```

```bash
kubectl apply -f service-account.yml
```

**b. Create the Role**:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: jenkins-agent-role
  namespace: jenkins-agents
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["create","delete","get","list","patch","update","watch"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["create","delete","get","list","patch","update","watch"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get","list","watch"]
- apiGroups: [""]
  resources: ["events"]
  verbs: ["watch"]
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get"]
```

```bash
kubectl apply -f role.yml
```

**c. Create the RoleBinding**:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-agent-role-binding
  namespace: jenkins-agents
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: jenkins-agent-role
subjects:
- kind: ServiceAccount
  name: jenkins-service-account
  namespace: jenkins-agents
```

```bash
kubectl apply -f role-binding.yml
```

## 5. Generate a Long-Lived Kubernetes API Token for Jenkins

1. **Create a secret for the token**:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jenkins-agent-sa-token
  namespace: jenkins-agents
  annotations:
    kubernetes.io/service-account.name: jenkins-service-account
type: kubernetes.io/service-account-token
```

```bash
kubectl apply -f secret.yml
```

2. **Extract the token**:

```bash
TOKEN=$(kubectl -n jenkins-agents get secret jenkins-agent-sa-token -o jsonpath='{.data.token}' | base64 -d)
echo $TOKEN
```

## 6. Configure Jenkins Kubernetes Cloud

1. **Gather cluster connection info**:

  - API Server URL:
    ```bash
    aws eks describe-cluster --name <YOUR_CLUSTER_NAME> --query cluster.endpoint --output text
    ```
  - CA Certificate:
    ```bash
    kubectl -n jenkins-agents get secret jenkins-agent-sa-token -o jsonpath='{.data.ca\.crt}' | base64 -d
    ```

2. **Add Kubernetes cloud in Jenkins**:

  - Open **Manage Jenkins > Manage Nodes and Clouds > Configure Clouds**.
  - Click **Add a new cloud** → **Kubernetes**.
  - Enter API Server URL and CA certificate.
  - Add a **Secret Text** credential in Jenkins with the extracted token.
  - Save and test connection.

## 7. Troubleshooting Tips

### Jenkins cannot connect to Kubernetes

- Verify API server URL, token, and CA cert.
- Ensure Jenkins has network access to the Kubernetes cluster.

### Authentication failures

- Check if the token is valid using:
  ```bash
  kubectl --token=$TOKEN get pods -n jenkins-agents
  ```
- Ensure RBAC RoleBinding is applied correctly.

### Agents not starting or stuck in Pending

- Check if pods are being scheduled:
  ```bash
  kubectl -n jenkins-agents get pods
  ```
- Ensure enough node resources are available.

### Cleaning Up

- Delete agent pods manually if needed:
  ```bash
  kubectl delete pod -n jenkins-agents <agent-pod-name>
  ```

By following these steps, Jenkins will dynamically provision agents on Kubernetes, leveraging AWS EKS and Rancher for scalable, containerized builds.

