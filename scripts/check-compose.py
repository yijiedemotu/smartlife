"""Compose 编排文件的本地静态校验（无需安装 Docker）。

校验内容：
  1. YAML 语法是否合法
  2. 三个 compose 文件的服务/卷/网络引用是否自洽
  3. 生产编排的关键约束：依赖健康检查、端口收敛、密钥必须来自环境变量
  4. .env.prod.example 是否覆盖了 prod 编排里引用的全部变量

用法（项目根目录）：python scripts/check-compose.py
"""
import os
import re
import sys

try:
    import yaml
except ImportError:
    print("需要 PyYAML：pip install pyyaml")
    sys.exit(2)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = [
    "docker-compose.yml",
    "docker-compose.full.yml",
    "docker-compose.prod.yml",
    "docker-compose.runtime.yml",
]

failures = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else " -> " + str(detail)))
    if not ok:
        failures.append(name)


def load(fname):
    """加载 compose 文件。

    compose 里 healthcheck 是 YAML 锚点（&health-default），如果 safe_load 返回的
    dict 恰好与锚点对象是同一个引用，修改一处会污染其它服务。这里用深拷贝隔离，
    保证每个服务拿到的是自己的副本。
    """
    import copy
    path = os.path.join(ROOT, fname)
    with open(path, "r", encoding="utf-8") as fh:
        return copy.deepcopy(yaml.safe_load(fh))


def volumes_referenced(service):
    out = []
    for v in service.get("volumes", []) or []:
        if isinstance(v, str):
            out.append(v.split(":")[0])
        elif isinstance(v, dict):
            out.append(v.get("source"))
    return [v for v in out if v and not v.startswith((".", "/"))]


def main():
    print("=== 1. YAML 语法与结构 ===")
    docs = {}
    for fname in FILES:
        try:
            data = load(fname)
            docs[fname] = data
            check(f"{fname} YAML 合法且含 services", bool(data.get("services")), "缺少 services")
        except Exception as exc:  # noqa: BLE001
            check(f"{fname} YAML 合法", False, exc)

    print("\n=== 2. 卷引用自洽（服务引用的命名卷必须已声明；bind mount 会挂载宿主机目录）===")
    for fname, data in docs.items():
        declared = set((data.get("volumes") or {}).keys())
        used = set()
        for svc in data["services"].values():
            used |= set(volumes_referenced(svc))
        named_used = {v for v in used if v in declared}
        missing = {v for v in used if v not in declared and not v.startswith(("./", "/"))}
        check(f"{fname} 命名卷均已声明", not missing, missing)
        print(f"         声明 {len(declared)} 个，使用 {len(named_used)} 个")

    print("\n=== 3. 生产编排关键约束 ===")
    prod = docs["docker-compose.prod.yml"]
    svcs = prod["services"]

    check("prod 含 mysql/redis/rabbitmq/backend/frontend 五个服务",
          {"mysql", "redis", "rabbitmq", "backend", "frontend"} <= set(svcs), list(svcs))

    check("backend 依赖三个中间件且等待健康检查",
          all(svcs["backend"]["depends_on"][s].get("condition") == "service_healthy"
              for s in ("mysql", "redis", "rabbitmq")),
          svcs["backend"]["depends_on"])

    # 健康检查：中间件在 compose 里声明；backend/frontend 的健康检查定义在各自 Dockerfile 的 HEALTHCHECK 指令中
    compose_health = {"mysql", "redis", "rabbitmq"}
    dockerfile_health = {"backend": "backend/Dockerfile", "frontend": "frontend/Dockerfile"}
    for svc in ("mysql", "redis", "rabbitmq"):
        check(f"{svc} 在 compose 中配置了 healthcheck", "healthcheck" in svcs[svc])
    for svc, df in dockerfile_health.items():
        with open(os.path.join(ROOT, df), "r", encoding="utf-8") as fh:
            content = fh.read()
        check(f"{svc} 在 {df} 中声明了 HEALTHCHECK", "HEALTHCHECK" in content)

    # 端口收敛：只有 frontend 绑 0.0.0.0，其余必须绑 127.0.0.1
    for svc in ("mysql", "redis", "rabbitmq", "backend"):
        ports = [str(p) for p in svcs[svc].get("ports", [])]
        check(f"{svc} 端口仅绑定 127.0.0.1（不对公网暴露）",
              ports and all(p.startswith("127.0.0.1:") for p in ports), ports)
    front_ports = [str(p) for p in svcs["frontend"].get("ports", [])]
    check("frontend 对外暴露（默认 80）",
          any(p.endswith(":80") for p in front_ports), front_ports)

    # 密钥不得硬编码：backend 环境变量里的敏感项必须用 ${VAR} 引用（值来自宿主机环境/.env）
    env = svcs["backend"].get("environment", {})
    for key in ("MYSQL_PASSWORD", "REDIS_PASSWORD", "RABBIT_PASSWORD", "SMARTLIFE_JWT_SECRET"):
        raw = str(env.get(key, ""))
        check(f"backend {key} 由环境变量注入而非硬编码",
              raw.startswith("${") and raw.endswith("}"), raw)

    # 状态化服务必须用命名卷持久化
    for svc in ("mysql", "redis", "rabbitmq"):
        check(f"{svc} 数据使用命名卷持久化",
              any(not v.startswith((".", "/")) for v in volumes_referenced(svcs[svc])),
              svcs[svc].get("volumes"))

    check("mysql 挂载 sql/init.sql 供首次初始化",
          any("sql/init.sql" in str(v) for v in svcs["mysql"].get("volumes", [])),
          svcs["mysql"].get("volumes"))

    print("\n=== 4. .env.prod.example 覆盖 prod 编排引用的变量 ===")
    with open(os.path.join(ROOT, ".env.prod.example"), "r", encoding="utf-8") as fh:
        example = fh.read()
    with open(os.path.join(ROOT, "docker-compose.prod.yml"), "r", encoding="utf-8") as fh:
        raw_prod = fh.read()

    referenced = set(re.findall(r"\$\{([A-Z_][A-Z0-9_]*)[:}]", raw_prod))
    # SM(ARTLIFE)_ 开头的由容器内 application.yml 再解析，无需出现在 .env
    need = {v for v in referenced if not v.startswith("SMARTLIFE_")}
    defined = set(re.findall(r"^([A-Z_][A-Z0-9_]*)=", example, re.MULTILINE))
    missing = sorted(need - defined)
    check(".env.prod.example 覆盖全部必需变量", not missing, missing)
    print(f"         编排引用 {sorted(need)}")
    print(f"         模板定义 {sorted(defined)}")

    print("\n=== 5. 前端 Nginx 反代目标与后端服务名一致 ===")
    with open(os.path.join(ROOT, "frontend", "nginx.conf"), "r", encoding="utf-8") as fh:
        nginx = fh.read()
    check("nginx 代理到 backend:8080/api/", "proxy_pass http://backend:8080/api/;" in nginx)
    check("nginx 配置了 WebSocket 升级", 'proxy_set_header Connection "upgrade";' in nginx)
    check("nginx 配置了 history 路由回退", "try_files $uri $uri/ /index.html;" in nginx)
    check("nginx 配置了 gzip", "gzip on;" in nginx)

    print("\n=== 6. 后端 Dockerfile 与 compose 构建上下文一致 ===")
    with open(os.path.join(ROOT, "backend", "Dockerfile"), "r", encoding="utf-8") as fh:
        be = fh.read()
    check("后端镜像多阶段构建（含 maven 构建阶段）", "AS build" in be and "maven:3.9" in be)
    check("后端运行阶段基于 JRE 17", "eclipse-temurin:17-jre" in be)
    with open(os.path.join(ROOT, "frontend", "Dockerfile"), "r", encoding="utf-8") as fh:
        fe = fh.read()
    check("前端镜像多阶段构建（node + nginx）", "node:20-alpine" in fe and "nginx:1.25-alpine" in fe)
    check("前端镜像拷贝了自己的 nginx.conf", "COPY nginx.conf" in fe)

    # 运行版 Dockerfile（不联网构建：只拷预编译产物），供拉不到 maven/node 镜像的服务器使用
    be_rt = os.path.join(ROOT, "backend", "Dockerfile.runtime")
    fe_rt = os.path.join(ROOT, "frontend", "Dockerfile.runtime")
    check("backend/Dockerfile.runtime 存在", os.path.exists(be_rt))
    check("frontend/Dockerfile.runtime 存在", os.path.exists(fe_rt))
    if os.path.exists(be_rt):
        with open(be_rt, "r", encoding="utf-8") as fh:
            t = fh.read()
        # 只看 RUN 指令里是否真的执行了构建命令（避免误匹配注释里的 "Maven" 字样）
        run_lines = [ln.strip() for ln in t.splitlines()
                     if ln.strip().upper().startswith("RUN ")]
        has_build_cmd = any(re.search(r"\bmvn\b|\bmaven\b", ln) for ln in run_lines)
        check("后端运行版只 COPY jar（不在构建阶段跑 Maven）",
              "COPY target/smartlife-backend-1.0.0.jar" in t and not has_build_cmd,
              f"RUN 行：{run_lines}")
        check("后端运行版基础镜像可被 ARG 覆盖", "ARG JRE_IMAGE" in t)
    if os.path.exists(fe_rt):
        with open(fe_rt, "r", encoding="utf-8") as fh:
            t = fh.read()
        run_lines = [ln.strip() for ln in t.splitlines()
                     if ln.strip().upper().startswith("RUN ")]
        has_build_cmd = any(re.search(r"\bnpm\b|\bnode\b", ln) for ln in run_lines)
        check("前端运行版只 COPY dist（不在构建阶段跑 npm）",
              "COPY dist" in t and not has_build_cmd,
              f"RUN 行：{run_lines}")
        check("前端运行版基础镜像可被 ARG 覆盖", "ARG NGINX_IMAGE" in t)

    print("\n=== 7. Dockerfile 的 COPY 源必须真的在构建上下文里 ===")
    # 这一类问题的典型症状：构建时报
    #   failed to calculate checksum of ref ...: "/xxx": not found
    # 根因是 .dockerignore 排除了某个被 COPY 的文件（本仓库真实踩过一次坑）。
    #
    # 判定方法：真正模拟 Docker 的行为 —— 先把上下文里所有文件列出来，减去
    # .dockerignore 命中的部分，再看 Dockerfile 的每个 COPY 源是否还能匹配到至少一个文件。
    # 这样无论是 `COPY settings.xml` 还是 `COPY settings.xm[l]` 都能正确判定。
    #
    # 预编译产物（target/*.jar、dist/**）在开发机上可能不存在，这里单独标注为"待构建产物"，
    # 不计为失败——因为 Dockerfile.runtime 的前提就是用本机构建好的产物。
    import fnmatch
    import glob

    PREBUILT_PREFIXES = ("target/", "dist/")
    for ctx, df_rel in (("backend", "Dockerfile"), ("backend", "Dockerfile.runtime"),
                        ("frontend", "Dockerfile"), ("frontend", "Dockerfile.runtime")):
        label = f"{ctx}/{df_rel}"
        ctx_root = os.path.join(ROOT, ctx)
        df_path = os.path.join(ctx_root, df_rel)
        if not os.path.exists(df_path):
            continue
        with open(df_path, "r", encoding="utf-8") as fh:
            dockerfile = fh.read()

        patterns = []
        di_path = os.path.join(ctx_root, ".dockerignore")
        if os.path.exists(di_path):
            with open(di_path, "r", encoding="utf-8") as fh:
                patterns = [ln.strip() for ln in fh
                            if ln.strip() and not ln.strip().startswith("#")]

        def excluded(rel_path):
            for pat in patterns:
                pure = pat.rstrip("/")
                if fnmatch.fnmatch(rel_path, pure) or fnmatch.fnmatch(rel_path, pure + "/*"):
                    return True
                if fnmatch.fnmatch(os.path.basename(rel_path), pure):
                    return True
            return False

        # 构建上下文的实际内容（应用 .dockerignore 之后）
        effective = []
        for dirpath, dirnames, filenames in os.walk(ctx_root):
            rel_dir = os.path.relpath(dirpath, ctx_root).replace("\\", "/")
            rel_dir = "" if rel_dir == "." else rel_dir
            # 目录整体被排除时连同子树一起剪掉
            dirnames[:] = [d for d in dirnames
                           if not excluded(f"{rel_dir}/{d}".lstrip("/"))]
            for fn in filenames:
                rel = f"{rel_dir}/{fn}".lstrip("/")
                if not excluded(rel):
                    effective.append(rel)

        check(f"{label}: 构建上下文非空（.dockerignore 没把整个目录排掉）",
              bool(effective), f"剩余 {len(effective)} 个文件")

        for line in dockerfile.splitlines():
            line = line.strip()
            if not line.upper().startswith("COPY "):
                continue
            if "--from=" in line:
                continue
            parts = line[5:].split()
            if len(parts) < 2:
                continue
            src = parts[0]

            # `COPY . .`（拷贝整个上下文）不适用单文件判定，只要上下文非空即可
            if src in (".", "./", "*"):
                check(f"{label}: COPY 源 {src} 可用（拷贝整个上下文）", bool(effective))
                continue

            exact = glob.glob(os.path.join(ctx_root, src))
            if not exact and src.startswith(PREBUILT_PREFIXES):
                # 预编译产物（jar / dist）属于"本机构建后才存在"的文件，
                # 开发机上没有是正常的，不判失败，但明确提示。
                check(f"{label}: COPY 源 {src} 为预编译产物（需先在本机构建）", True)
                print(f"         ⚠ 当前磁盘上不存在：{src}（构建镜像前必须先 mvn package / npm run build）")
                continue
            if exact:
                # 源路径在磁盘上存在，检查是否被 .dockerignore 吃掉
                alive = []
                for p in exact:
                    rel = os.path.relpath(p, ctx_root).replace("\\", "/")
                    if os.path.isdir(p):
                        alive += [f for f in effective if f.startswith(rel + "/")]
                    elif rel in effective:
                        alive.append(rel)
                check(f"{label}: COPY 源 {src} 未被 .dockerignore 排除",
                      bool(alive), f"命中 {len(exact)} 个路径但都被 .dockerignore 排除了")
                continue

            # 磁盘上没有精确命中：可能是合法的"可选文件"写法（如 settings.xm[l]）
            fallback = glob.glob(os.path.join(ctx_root, src.replace("[", "").replace("]", "")))
            if fallback:
                check(f"{ctx}: COPY 源 {src} 为可选文件写法且磁盘上确实存在", True)
            else:
                check(f"{label}: COPY 源 {src} 在构建上下文中可用", False,
                      "文件不存在（也不是通配写法）")

    print("\n=== 8. 可选文件的 COPY 必须有运行时兜底 ===")
    # `COPY xxx[l] ...` 这类写法在源缺失时不会让构建失败，
    # 但必须配合 RUN 兜底，否则后续步骤会用到不存在的文件。
    with open(os.path.join(ROOT, "backend", "Dockerfile"), "r", encoding="utf-8") as fh:
        be_text = fh.read()
    has_optional_copy = "settings.xm[l]" in be_text or "xm[l]" in be_text
    check("backend/settings.xml 采用可选 COPY 写法", has_optional_copy)
    if has_optional_copy:
        check("可选 copy 有 RUN 兜底（缺失时自动生成阿里云 Maven 配置）",
              "[ ! -s /root/.m2/settings.xml ]" in be_text and "aliyun" in be_text)

    print("\n============================ SUMMARY ============================")
    if failures:
        print(f"  通过 {checks - len(failures)} / {checks}，失败 {len(failures)}：{failures}")
        return 1
    print(f"  通过 {checks} / {checks}，全部校验通过 ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main())
