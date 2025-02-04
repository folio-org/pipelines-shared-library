#!/bin/bash
set -eux

# Update packages
yum update -y
yum install -y wget curl awscli xfsprogs java-17-amazon-corretto

# Jenkins repo setup
wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io.key

# Install Jenkins (pin version if available; otherwise default to latest)
yum install -y jenkins-${jenkins_version} || yum install -y jenkins

# Format and mount EBS volume for Jenkins data
DEVICE="/dev/sdf"
MOUNT_POINT="/var/lib/jenkins"
if [ "$(file -s $DEVICE | grep data || true)" != "" ]; then
  mkfs.xfs $DEVICE
fi
mkdir -p $MOUNT_POINT
mount $DEVICE $MOUNT_POINT
echo "$DEVICE $MOUNT_POINT xfs defaults 0 0" >> /etc/fstab
chown -R jenkins:jenkins $MOUNT_POINT

# Enable and start Jenkins on boot
systemctl enable jenkins
systemctl start jenkins

# Wait until Jenkins is up before installing plugins
sleep 30

# Install Jenkins plugins if provided
if [ "${jenkins_plugins}" != "[]" ]; then
  # Convert Terraform list of plugins into a space-separated string
  # Example: jenkins_plugins = ["git", "workflow-aggregator"] => "git workflow-aggregator"
  PLUGINS="$(echo "${jenkins_plugins}" | sed 's/\[//;s/\]//;s/,/ /g;s/\"//g')"
  # Use jenkins-plugin-cli which is available as of Jenkins 2.277+
  jenkins-plugin-cli --plugins ${PLUGINS}
  systemctl restart jenkins
fi

# Set up nightly cron job to back up Jenkins data to S3
cat <<EOF >/usr/local/bin/jenkins_backup.sh
#!/bin/bash
TIMESTAMP=\$(date +%Y%m%d%H%M)
aws s3 sync /var/lib/jenkins "s3://${backup_bucket}/jenkins-backups/\$TIMESTAMP/"
EOF

chmod +x /usr/local/bin/jenkins_backup.sh

# Cron daily at 2am
cat <<EOF >>/etc/crontab
0 2 * * * root /usr/local/bin/jenkins_backup.sh
EOF