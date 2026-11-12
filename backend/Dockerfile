# Mall-X 后端通用镜像：--build-arg MODULE=mall-mgr|mall-gateway|mall-alipay
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml ./
# 父 pom 聚合了三个子模块，Maven 解析聚合需要全部子模块的 pom 存在（源码只复制目标模块）
COPY mall-gateway/pom.xml mall-gateway/
COPY mall-mgr/pom.xml mall-mgr/
COPY mall-alipay/pom.xml mall-alipay/
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/mall-*-1.0.0.jar app.jar
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
