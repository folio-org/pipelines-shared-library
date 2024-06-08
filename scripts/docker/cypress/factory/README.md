### How to build cypress custom image with [cypress/factory](https://hub.docker.com/r/cypress/factory)

- Edit Dockerfile with versions you need to build
- Build image
- Login to aws cli
- Retrive ECR token
- Push docker image

```bash
docker build . -t 732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:node-18.16.0-cypress-12.0.0-chrome-124.0.6367.60-1

aws configure

aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 732722833398.dkr.ecr.us-west-2.amazonaws.com

docker push 732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:node-18.16.0-cypress-12.0.0-chrome-124.0.6367.60-1
```
