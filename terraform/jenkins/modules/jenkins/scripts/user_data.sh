#!/bin/bash
set -eux # Exit immediately if a command fails and print each command before execution.

# Update the system packages to the latest version
yum update -y

# Install required dependencies:
# - wget & curl: Download files from the web
# - awscli: AWS command-line tool for S3 backups
# - xfsprogs: Tools for working with XFS filesystems (for EBS volume)
# - java-17-amazon-corretto: Java runtime required for Jenkins
yum install -y wget curl awscli xfsprogs java-17-amazon-corretto fontconfig --allowerasing

# Add the Jenkins repository to the system
wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key

yum upgrade -y # Upgrade all packages to the latest version

# Install Jenkins (use the specified version if available, otherwise install the latest stable version)
yum install -y jenkins-${jenkins_version} || yum install -y jenkins

DEVICE="/dev/sdf"                  # The device name where the EBS volume is attached
REAL_DEVICE=$(readlink -f $DEVICE) # Get the real device name (e.g., /dev/xvdf)
MOUNT_POINT="/var/lib/jenkins"     # Directory where Jenkins data will be stored

# Check if the volume is empty (unformatted), then format it with XFS
if [ "$(file -s $REAL_DEVICE | grep -E 'data$' || true)" != "" ]; then
  mkfs.xfs $DEVICE
fi

# Create the mount directory if it doesn't exist
mkdir -p $MOUNT_POINT
mkdir -p $MOUNT_POINT/heapdump

# Mount the volume to the Jenkins data directory
mount $DEVICE $MOUNT_POINT

# Persist the mount configuration in /etc/fstab to ensure it mounts automatically on reboot
echo "$DEVICE $MOUNT_POINT xfs defaults 0 0" >>/etc/fstab

# Set the correct ownership for the Jenkins service
chown -R jenkins:jenkins $MOUNT_POINT

# Customize the Jenkins service configuration
JENKINS_SERVICE_OVERRIDE_FOLDER="/usr/lib/systemd/system/jenkins.service.d"
mkdir -p $JENKINS_SERVICE_OVERRIDE_FOLDER
touch $JENKINS_SERVICE_OVERRIDE_FOLDER/override.conf

# Add JVM configuration options to the Jenkins service
cat <<EOF >"$JENKINS_SERVICE_OVERRIDE_FOLDER/override.conf"
[Unit]
Description=FOLIO CI Jenkins Controller

[Service]
# Add JVM configuration options
Environment="JAVA_OPTS=\
-Djava.awt.headless=true \
-Xms4096m \
-Xmx8192m \
-XX:+UseG1GC \
-XX:+ParallelRefProcEnabled \
-XX:+UseStringDeduplication \
-XX:+ExplicitGCInvokesConcurrent \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=$MOUNT_POINT/heapdump/ \
-XX:+AlwaysPreTouch"
EOF

# Reload the systemd configuration
systemctl daemon-reload

# Enable Jenkins service to start on boot
systemctl enable jenkins

# Start the Jenkins service
systemctl start jenkins

# Create a script to sync Jenkins data to an S3 bucket (excluding cache, tools, and plugins)
BACKUP_SCRIPT="/usr/local/bin/jenkins_backup.sh"
echo "Creating S3 backup script..."

cat <<EOF >"$BACKUP_SCRIPT"
#!/bin/bash
TIMESTAMP=\$(date +%Y%m%d%H%M)  # Generate timestamp for backup folder
aws s3 sync /var/lib/jenkins "s3://${backup_bucket}/\$TIMESTAMP/" \\
  --exclude "cache/*" \\
  --exclude "tools/*" \\
  --exclude "plugins/*"
EOF

# Make the backup script executable
chmod +x /usr/local/bin/jenkins_backup.sh

# Schedule the backup script to run every night at 2 AM via cron job
cat <<EOF >>/etc/crontab
0 2 * * * root /usr/local/bin/jenkins_backup.sh
EOF
