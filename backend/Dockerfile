# =============================================================================
# 智联生活后端镜像（多阶段构建）
#   阶段 1：maven 容器内编译打包，服务器上无需安装 JDK/Maven
#   阶段 2：仅拷贝 fat jar 运行，镜像更小
#
# 构建（在仓库根目录执行）：
#   docker build -f backend/Dockerfile -t smartlife-backend:2.0.0 ./backend
# =============================================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 依赖与源码分离拷贝：pom 未变时可复用依赖层缓存，重复构建更快
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -DskipTests clean package \
    && cp target/smartlife-backend-1.0.0.jar /build/app.jar

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre
LABEL maintainer="smartlife" \
      description="智联生活三端平台后端（用户端/商家端/管理端）"

# 时区与字符集：签到/秒杀按东八区计算
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar

# 以非 root 用户运行，降低容器逃逸风险
RUN groupadd -r smartlife && useradd -r -g smartlife smartlife \
    && mkdir -p /app/logs && chown -R smartlife:smartlife /app
USER smartlife

EXPOSE 8080

# 存活探针：探测 TCP 端口是否可连接（镜像内无 curl，避免额外安装）
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
