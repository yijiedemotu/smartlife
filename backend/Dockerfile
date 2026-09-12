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
# 这里刻意把它放在 /root/.m2/settings.xml 的位置：万一下面可选的 settings.xml
# 不存在，它还能充当"占位文件"让兜底逻辑只需判断文件是否为空。
COPY pom.xml /root/.m2/settings.xml

# Maven 镜像配置（阿里云）：优先使用构建上下文里的 settings.xml（存在即覆盖占位文件）；
# 若该文件缺失（例如 clone 到的是较早的提交），则自动生成一份兜底配置。
# 注意：COPY 的源用通配符 settings.xm[l]，这样"源文件不存在"时不会让构建失败。
COPY settings.xm[l] /root/.m2/settings.xml
RUN if [ ! -s /root/.m2/settings.xml ]; then \
        printf '%s\n' \
          '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
          '  <mirrors>' \
          '    <mirror>' \
          '      <id>aliyun-central</id>' \
          '      <url>https://maven.aliyun.com/repository/public</url>' \
          '      <mirrorOf>central</mirrorOf>' \
          '    </mirror>' \
          '  </mirrors>' \
          '</settings>' > /root/.m2/settings.xml; \
        echo '[smartlife] settings.xml 缺失，已生成阿里云 Maven 兜底配置'; \
    else \
        echo '[smartlife] 使用仓库自带的 settings.xml'; \
    fi

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
