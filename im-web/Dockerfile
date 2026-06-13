FROM node:24-alpine AS build

WORKDIR /app
COPY im-web/package*.json ./
RUN npm ci

COPY im-proto /im-proto
COPY im-web ./
RUN npm run build

FROM nginx:1.27-alpine

COPY im-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
