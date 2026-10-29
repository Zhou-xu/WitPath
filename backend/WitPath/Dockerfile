# syntax=docker/dockerfile:1
# =============================================================================
# WitPath 后端镜像（多阶段：Maven 构建 → JRE 运行）
# 构建上下文 = backend/WitPath（多模块根）
# 注意：src/main/resources/application-secret.yaml 会随 jar 打进镜像
#      （含 OSS 凭证），镜像只能推私有仓库，禁止公开分发。
# =============================================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 再下载依赖：源码改动时可复用该缓存层，避免每次全量下载
COPY pom.xml ./
COPY common/pom.xml common/
COPY pojo/pom.xml pojo/
COPY server/pom.xml server/
RUN mvn -B -q dependency:go-offline -pl server -am || true

COPY common/src common/src
COPY pojo/src pojo/src
COPY server/src server/src
RUN mvn -B -q -pl server -am package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

# spring-boot repackage 后的可执行 jar（*.jar.original 不会被通配匹配）
COPY --from=build /build/server/target/*.jar app.jar

# /app/storage：本地资料存储；/app/log：业务日志（含 userlog），运行时挂宿主机目录持久化
VOLUME ["/app/storage", "/app/log"]

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
