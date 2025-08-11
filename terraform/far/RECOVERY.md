# FOLIO Application Registry (FAR) Recovery Procedures

This document outlines detailed recovery procedures for various failure scenarios in the FOLIO Application Registry (FAR) infrastructure. The procedures are designed to be followed in sequence based on the type of failure encountered.

## Table of Contents

1. [Scenario 1: EBS Volume Exists, Kubernetes Infrastructure Wiped](#scenario-1-ebs-volume-exists-kubernetes-infrastructure-wiped)
2. [Scenario 2: EBS Volume Deleted, Snapshot Exists](#scenario-2-ebs-volume-deleted-snapshot-exists)
3. [Scenario 3: PostgreSQL StatefulSet Wiped/Deleted](#scenario-3-postgresql-statefulset-wipeddeleted)
4. [Scenario 4: MGR App Deployment Wiped/Deleted](#scenario-4-mgr-app-deployment-wipeddeleted)
5. [Scenario 5: Complete Infrastructure Wiped, Only Snapshot Available](#scenario-5-complete-infrastructure-wiped-only-snapshot-available)
6. [Scenario 6: Kubernetes Secret with DB Credentials Wiped/Deleted](#scenario-6-kubernetes-secret-with-db-credentials-wipeddeleted)

## Scenario 1: EBS Volume Exists, Kubernetes Infrastructure Wiped

### Description
The AWS EBS volume containing PostgreSQL data still exists, but Kubernetes resources (PV, PVC, pods, etc.) have been deleted.

### Recovery Steps

1. **Verify EBS Volume Health**
   ```bash
   aws ec2 describe-volumes --volume-ids <volume-id> --query 'Volumes[0].State'
   ```
   Ensure the state is "available" or "in-use".

2. **Run Terraform Plan**
   ```bash
   cd /path/to/terraform/far
   terraform plan
   ```
   Review the plan to ensure it will recreate the necessary Kubernetes resources without attempting to recreate the EBS volume.

3. **Apply Terraform Configuration**
   ```bash
   terraform apply
   ```

4. **Verify PV/PVC Binding**
   ```bash
   kubectl get pv,pvc -n <namespace>
   ```
   Ensure the PV is bound to the correct PVC.

5. **Wait for PostgreSQL Pod to Start**
   ```bash
   kubectl get pods -n <namespace> -l app=postgres -w
   ```
   Wait until the pod is in "Running" state.

6. **Verify MGR App Connectivity to DB**
   ```bash
   kubectl logs -n <namespace> -l app=mgr-applications
   ```
   Check logs for successful database connection.

## Scenario 2: EBS Volume Deleted, Snapshot Exists

### Description
The EBS volume has been deleted, but snapshots of the volume are available.

### Recovery Steps

1. **Identify Latest EBS Snapshot**
   ```bash
   aws ec2 describe-snapshots --filters "Name=tag:Name,Values=*postgres*" --query 'Snapshots[*].{ID:SnapshotId,StartTime:StartTime,Description:Description}' --output table | sort -r -k 3
   ```

2. **Set Snapshot ID in Terraform Variables**
   Edit `terraform.tfvars` to include:
   ```hcl
   snapshot_id = "snap-0123456789abcdef0"
   ```

3. **Run Terraform Apply with Snapshot ID**
   ```bash
   terraform apply
   ```

4. **Wait for New EBS Volume Creation**
   Monitor the AWS console or use:
   ```bash
   aws ec2 describe-volumes --filters "Name=tag:Name,Values=*postgres*" --query 'Volumes[*].{ID:VolumeId,State:State}'
   ```

5. **Verify PV/PVC Creation**
   ```bash
   kubectl get pv,pvc -n <namespace>
   ```

6. **Wait for PostgreSQL Pod to Start**
   ```bash
   kubectl get pods -n <namespace> -l app=postgres -w
   ```

7. **Verify Database Content**
   ```bash
   kubectl exec -it -n <namespace> <postgres-pod-name> -- psql -U <username> -d <database> -c "SELECT count(*) FROM pg_catalog.pg_tables;"
   ```

8. **Verify MGR App Connectivity**
   ```bash
   kubectl logs -n <namespace> -l app=mgr-applications
   ```

## Scenario 3: PostgreSQL StatefulSet Wiped/Deleted

### Description
The PostgreSQL StatefulSet/Deployment has been deleted, but the EBS volume and other resources remain intact.

### Recovery Steps

1. **Taint PostgreSQL Resources**
   ```bash
   terraform taint module.far_postgres_db.helm_release.postgres
   ```

2. **Run Terraform Plan**
   ```bash
   terraform plan
   ```
   Review the plan to ensure it will only recreate the PostgreSQL resources.

3. **Apply Terraform Configuration**
   ```bash
   terraform apply
   ```

4. **Verify PostgreSQL Pod Recreation**
   ```bash
   kubectl get pods -n <namespace> -l app=postgres -w
   ```

5. **Verify Database Content**
   ```bash
   kubectl exec -it -n <namespace> <postgres-pod-name> -- psql -U <username> -d <database> -c "SELECT count(*) FROM pg_catalog.pg_tables;"
   ```

6. **Verify MGR App Connectivity**
   ```bash
   kubectl logs -n <namespace> -l app=mgr-applications
   ```

## Scenario 4: MGR App Deployment Wiped/Deleted

### Description
The MGR Application deployment has been deleted, but PostgreSQL and other resources remain intact.

### Recovery Steps

1. **Taint MGR App Resources**
   ```bash
   terraform taint module.far_mgr_app_helm.helm_release.far_mgr_app
   ```

2. **Run Terraform Plan**
   ```bash
   terraform plan
   ```
   Review the plan to ensure it will only recreate the MGR App resources.

3. **Apply Terraform Configuration**
   ```bash
   terraform apply
   ```

4. **Verify MGR App Pod Recreation**
   ```bash
   kubectl get pods -n <namespace> -l app=mgr-applications -w
   ```

5. **Verify DB Connectivity**
   ```bash
   kubectl logs -n <namespace> -l app=mgr-applications | grep -i "database"
   ```

6. **Verify Application Functionality**
   ```bash
   curl -k https://<ingress-hostname>/admin/health
   ```

## Scenario 5: Complete Infrastructure Wiped, Only Snapshot Available

### Description
The entire infrastructure (Kubernetes resources, EBS volumes) has been deleted, but snapshots of the EBS volume are available.

### Recovery Steps

1. **Identify Latest EBS Snapshot**
   ```bash
   aws ec2 describe-snapshots --filters "Name=tag:Name,Values=*postgres*" --query 'Snapshots[*].{ID:SnapshotId,StartTime:StartTime,Description:Description}' --output table | sort -r -k 3
   ```

2. **Create New Rancher Project**
   Use the Rancher UI or API to create a new project.

3. **Create New Kubernetes Namespace**
   ```bash
   kubectl create namespace <namespace>
   ```

4. **Set Snapshot ID in Terraform Variables**
   Edit `terraform.tfvars` to include:
   ```hcl
   snapshot_id = "snap-0123456789abcdef0"
   ```

5. **Run Terraform Apply with Snapshot ID**
   ```bash
   terraform apply
   ```

6. **Wait for Complete Infrastructure Creation**
   Monitor the Terraform output and Kubernetes resources:
   ```bash
   kubectl get all -n <namespace>
   ```

7. **Verify PostgreSQL Pod Status**
   ```bash
   kubectl get pods -n <namespace> -l app=postgres -w
   ```

8. **Verify MGR App Pod Status**
   ```bash
   kubectl get pods -n <namespace> -l app=mgr-applications -w
   ```

9. **Verify Application Functionality**
   ```bash
   curl -k https://<ingress-hostname>/admin/health
   ```

## Scenario 6: Kubernetes Secret with DB Credentials Wiped/Deleted

### Description
The Kubernetes Secret containing database credentials has been deleted, but other resources remain intact.

### Recovery Steps

1. **Check AWS Secrets Manager for Credentials**
   ```bash
   aws secretsmanager get-secret-value --secret-id "/<cluster-name>/<namespace>/postgres-credentials"
   ```

2. **Retrieve Credentials from AWS Secrets Manager**
   ```bash
   aws secretsmanager get-secret-value --secret-id "/<cluster-name>/<namespace>/postgres-credentials" --query 'SecretString' --output text
   ```

3. **Recreate Kubernetes Secret Manually**
   ```bash
   kubectl create secret generic <secret-name> -n <namespace> \
     --from-literal=DB_HOST=<host> \
     --from-literal=DB_PORT=<port> \
     --from-literal=DB_DATABASE=<database> \
     --from-literal=DB_USERNAME=<username> \
     --from-literal=DB_PASSWORD=<password> \
     --from-literal=DB_MAXPOOLSIZE=<max-pool-size> \
     --from-literal=DB_CHARSET=<charset> \
     --from-literal=DB_QUERYTIMEOUT=<timeout>
   ```

4. **Restart MGR App Pods**
   ```bash
   kubectl rollout restart deployment -n <namespace> -l app=mgr-applications
   ```

5. **Verify DB Connectivity**
   ```bash
   kubectl logs -n <namespace> -l app=mgr-applications | grep -i "database"
   ```

6. **Run Terraform Plan to Ensure Consistency**
   ```bash
   terraform plan
   ```
   Check if Terraform detects any drift in the secret.

7. **Apply Terraform if Needed**
   ```bash
   terraform apply
   ```

## Additional Resources

- [AWS EBS Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ebs-volumes.html)
- [Kubernetes PV/PVC Documentation](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
- [Terraform Taint Command](https://www.terraform.io/cli/commands/taint)
- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)

## Troubleshooting Common Issues

### PV/PVC Binding Issues
If PV and PVC are not binding correctly:
```bash
kubectl patch pv <pv-name> -p '{"spec":{"claimRef":null}}'
kubectl delete pvc <pvc-name> -n <namespace>
terraform apply
```

### PostgreSQL Pod Crash Looping
Check logs for permission issues:
```bash
kubectl logs -n <namespace> <pod-name>
```

### MGR App Database Connection Issues
Verify secret contents match what the application expects:
```bash
kubectl get secret <secret-name> -n <namespace> -o jsonpath='{.data}' | jq 'map_values(@base64d)'
``` 

## Recovery Flows
The following diagram illustrates the recovery flows for each scenario described above:
![Recovery flows](./images/recovery_plan.svg)