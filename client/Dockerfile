FROM node:22-alpine AS build

# VITE_-prefixed build args are inlined into the bundle by Vite at build
# time. Override per environment via docker-compose `build.args` or
# `docker build --build-arg`. The default keeps host-local dev working
# out of the box.
ARG VITE_AUTH_API=http://localhost:8081
ENV VITE_AUTH_API=$VITE_AUTH_API

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
