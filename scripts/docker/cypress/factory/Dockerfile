FROM node:20.10.0

RUN apt-get update && \
    apt-get install -y \
    chromium \
    xvfb \
    x11-utils \
    xdg-utils \
    fonts-liberation && \
    rm -rf /var/lib/apt/lists/* && \

    npm install -g cypress@12.0.0

WORKDIR /opt/app
COPY . /opt/app

RUN yarn install
WORKDIR /opt/app
RUN npm install --save-dev cypress
