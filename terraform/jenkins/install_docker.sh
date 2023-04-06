#!/usr/bin/env bash
#Read https://wiki.folio.org/display/FOLIJET/Launch+a+new+Jenkins+Docker+agent
sudo apt-get remove docker docker-engine docker.io containerd runc -y
sudo apt-get update -y
sudo apt-get install     ca-certificates     curl     gnupg     lsb-release -y
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install docker-ce docker-ce-cli containerd.io -y
sudo systemctl enable docker.service
sudo systemctl enable containerd.service
sudo mkdir -p /etc/systemd/system/docker.service.d/
echo "[Service]" | sudo tee /etc/systemd/system/docker.service.d/override.conf > /dev/null
echo "ExecStart=" | sudo tee -a /etc/systemd/system/docker.service.d/override.conf > /dev/null
echo "ExecStart=/usr/bin/dockerd -H unix:// -H tcp://0.0.0.0:2375" | sudo tee -a /etc/systemd/system/docker.service.d/override.conf > /dev/null
sudo systemctl daemon-reload
sudo systemctl restart docker
sudo groupmod -g 496 docker
sudo chgrp 496 /var/run/docker.sock
sudo groupadd jenkins
sudo useradd -g jenkins jenkins
sudo mkdir /home/jenkins
sudo chown jenkins /home/jenkins
sudo chgrp jenkins /home/jenkins
sudo usermod -aG docker jenkins
