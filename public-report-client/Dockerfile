FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
# Vite builds with base '/report/', so assets reference /report/... — serve the
# bundle from a matching subdirectory so those URLs resolve.
COPY --from=build /app/dist /usr/share/nginx/html/report

EXPOSE 80
