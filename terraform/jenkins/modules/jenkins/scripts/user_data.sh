#!/bin/bash
set -eux # Exit immediately if a command fails and print each command before execution.

# Update the system packages to the latest version
yum update -y

# Install required dependencies:
# - wget & curl: Download files from the web
# - awscli: AWS command-line tool for S3 backups
# - xfsprogs: Tools for working with XFS filesystems (for EBS volume)
# - java-17-amazon-corretto: Java runtime required for Jenkins
yum install -y wget curl awscli xfsprogs java-17-amazon-corretto fontconfig htop git --allowerasing

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

# Set up a systemd service and timer to run the backup script daily
echo "Setting up systemd-based Jenkins backup..."

BACKUP_SCRIPT_PATH="/usr/local/bin/jenkins_backup.sh"
SERVICE_PATH="/etc/systemd/system/jenkins_backup.service"
TIMER_PATH="/etc/systemd/system/jenkins_backup.timer"

# Create a script to sync Jenkins data to an S3 bucket (excluding cache, tools, and plugins)
cat <<EOF > "$BACKUP_SCRIPT_PATH"
#!/bin/bash
TIMESTAMP=\$(date +'%Y-%m-%d_%H-%M-%S')
LOGFILE="/var/log/jenkins_backup.log"

echo "[\$(date)] Starting Jenkins backup..." >> \$LOGFILE
aws s3 sync /var/lib/jenkins "s3://${backup_bucket}/\$TIMESTAMP/" \\
  --exclude "cache/*" \\
  --exclude "tools/*" \\
  --exclude "plugins/*" >> \$LOGFILE 2>&1

echo "[\$(date)] Backup completed." >> \$LOGFILE
EOF

chmod +x "$BACKUP_SCRIPT_PATH"

# Create a systemd service to run the backup script
cat <<EOF > "$SERVICE_PATH"
[Unit]
Description=Jenkins backup service
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=$BACKUP_SCRIPT_PATH
EOF

# Create a systemd timer to run the backup service daily at 2:00 AM
cat <<EOF > "$TIMER_PATH"
[Unit]
Description=Daily Jenkins backup timer

[Timer]
OnCalendar=*-*-* 02:00:00
Unit=jenkins_backup.service
Persistent=true

[Install]
WantedBy=timers.target
EOF

# Reload the systemd configuration
systemctl daemon-reload
# Enable and start the timer
systemctl enable jenkins_backup.timer
systemctl start jenkins_backup.timer

echo "Systemd-based Jenkins backup setup complete."
echo "Jenkins setup complete!"
