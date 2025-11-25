#!/bin/bash

PROFILE="default" # AWS CLI profile name, please change if needed

docker machine start # Podman is used here, if, you use Docker, remove this line, Podman aliases docker

aws ecr get-login-password --region us-west-2 --profile $PROFILE | docker login --username AWS --password-stdin 732722833398.dkr.ecr.us-west-2.amazonaws.com
docker build --platform linux/amd64 -t adjust-rds-db .

docker tag adjust-rds-db:latest 732722833398.dkr.ecr.us-west-2.amazonaws.com/adjust-rds-db:latest

docker push 732722833398.dkr.ecr.us-west-2.amazonaws.com/adjust-rds-db:latest
