ARG NODE_VERSION='25.9.0'
ARG YARN_VERSION='1.22.22'
ARG CHROME_VERSION='150.0.7871.186-1'
ARG CYPRESS_VERSION='15.19.0'

FROM cypress/factory:9.0.0

COPY . /opt/app
WORKDIR /opt/app
RUN npm install --save-dev cypress
RUN npx cypress install