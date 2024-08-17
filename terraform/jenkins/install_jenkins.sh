#!/usr/bin/env bash
#Wait for the volume to be attached
sleep 300
#EBS volume mounting
DEVICE='/dev/xvdb'
MOUNT_POINT='/home/jenkins'
DEVICE_FS_TYPE=`sudo file -sL $DEVICE`
if [[ $DEVICE_FS_TYPE == *"ext4"* ]]; then
  echo "Device formatted"
else
  echo "Formatting $DEVICE with an Ext4 fs"
  sudo mkfs.ext4 -q -F $DEVICE
  echo
fi
sudo e2label  $DEVICE $MOUNT_POINT
sudo cp /etc/fstab /etc/fstab.orig
if [ ! -d "$MOUNT_POINT" ]; then sudo mkdir -p $MOUNT_POINT; fi
sudo echo "LABEL=$MOUNT_POINT     $MOUNT_POINT           ext4    defaults,nofail  2   2" | sudo tee -a /etc/fstab
sudo mount -a
#Jenkins Installation
if [ ! -d "/var/lib/jenkins" ]; then sudo ln -s /home/jenkins /var/lib/jenkins; fi
sudo yum update -y
sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
sudo yum upgrade -y
sudo sudo yum install java-17-amazon-corretto -y
sudo yum install jenkins-${jenkins_version} -y
sudo systemctl enable jenkins
sudo systemctl start jenkins
