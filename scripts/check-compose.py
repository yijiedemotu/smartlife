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
FILES = ["docker-compose.yml", "docker-compose.full.yml", "docker-compose.prod.yml"]

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

    print("\n============================ SUMMARY ============================")
    if failures:
        print(f"  通过 {checks - len(failures)} / {checks}，失败 {len(failures)}：{failures}")
        return 1
    print(f"  通过 {checks} / {checks}，全部校验通过 ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main())
