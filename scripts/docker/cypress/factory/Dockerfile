ARG NODE_VERSION='25.6.1'
ARG YARN_VERSION='1.22.19'
ARG CHROME_VERSION='145.0.7632.75-1'
ARG CYPRESS_VERSION='12.0.0'

FROM cypress/factory:7.2.3

COPY . /opt/app
WORKDIR /opt/app
RUN npm install --save-dev cypress
RUN npx cypress install
