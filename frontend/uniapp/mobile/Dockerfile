# Mall-X 移动端 H5：uni-app 构建 + nginx 托管
# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build:h5

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/build/h5 /usr/share/nginx/html
