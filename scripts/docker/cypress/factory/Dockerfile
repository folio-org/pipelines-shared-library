ARG NODE_VERSION='18.20.3'
ARG YARN_VERSION='1.22.19'
ARG CHROME_VERSION='124.0.6367.60-1'
ARG CYPRESS_VERSION='12.0.0'

FROM cypress/factory:3.5.4

COPY . /opt/app
WORKDIR /opt/app
RUN npm install --save-dev cypress
