FROM node:24-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build


FROM node:24-alpine

RUN npm install -g serve

COPY --from=builder /app/dist ./dist

EXPOSE 4300

CMD ["serve", "-s", "dist", "-l", "4300"]
