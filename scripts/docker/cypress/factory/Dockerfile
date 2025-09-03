ARG NODE_VERSION='20.8.1'
ARG YARN_VERSION='1.22.19'
ARG CHROME_VERSION='139.0.7258.154-1'
ARG CYPRESS_VERSION='12.0.0'

FROM cypress/factory:3.5.4

COPY . /opt/app
WORKDIR /opt/app
RUN npm install --save-dev cypress
