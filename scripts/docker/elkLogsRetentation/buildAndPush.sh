#!/bin/bash

PROFILE=default

docker machine start

aws ecr get-login-password --region us-west-2 --profile $PROFILE | docker login --username AWS --password-stdin 732722833398.dkr.ecr.us-west-2.amazonaws.com

docker build --platform linux/amd64 -t logging-cleanup .

docker tag logging-cleanup:latest 732722833398.dkr.ecr.us-west-2.amazonaws.com/logging-cleanup:latest

docker push 732722833398.dkr.ecr.us-west-2.amazonaws.com/logging-cleanup:latest
