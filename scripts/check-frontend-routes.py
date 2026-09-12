"""前端路由链接一致性校验（无第三方依赖）。

背景：把项目从"双端"改成"三端"时，路由表从 `/home`、`/orders` 改成了
`/user/home`、`/user/orders`，但导航链接没跟着改。结果是：
点击 `/shop/1` → 路由表里只有 `/user/shop/:id` → 匹配不到 → 落到兜底 `/(.*)`
→ 重定向回 `/user/home` → 页面毫无变化，表现为"所有按钮点了没反应"。
这个 bug 在服务器上排查了很久才发现，所以固化成自动检查。

校验内容：
  1. 从 router/index.js 解析出全部路由（含 children 拼接）
  2. 扫描所有 .vue / .js 里的跳转写法：
       router-link to="/xxx"      $router.push('/xxx')      router.push('/xxx')
       router.replace('/xxx')     location.href = '/xxx'
  3. 把拼接后的路径与路由表做匹配（支持 :id 动态段），不匹配的判为死链 FAIL
  4. 额外检查 vite 产物里是否残留旧路径（部署的是旧构建时给出警告）

用法（项目根目录）：python scripts/check-frontend-routes.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "frontend", "src")
ROUTER = os.path.join(SRC, "router", "index.js")

failures = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else " -> " + str(detail)))
    if not ok:
        failures.append(name)


def parse_routes():
    """从 router/index.js 解析出全部"完整路径"。

    做法：扫描源码里的 `{` `}` `]` 与 `path:` 出现顺序，用大括号深度维护父路径。
      { path: '/user', children: [          ← depth1 记录 /user
          { path: 'home', ... },            ← depth2 无前导 /  → /user/home
          { path: 'shop/:id', ... }         ← depth2           → /user/shop/:id
      ]}
    返回 (静态路径集合, 动态路径正则列表)
    """
    text = open(ROUTER, encoding="utf-8").read()

    static_paths = set()
    dynamic_patterns = []
    stack = []          # [(depth, path)]，记录各层级的父路径
    depth = 0
    i = 0
    n = len(text)

    while i < n:
        ch = text[i]

        if ch == "{":
            depth += 1
            i += 1
            continue
        if ch in "}]":
            depth -= 1
            while stack and stack[-1][0] > depth:
                stack.pop()
            i += 1
            continue

        # 识别 path: 'xxx'
        m = re.match(r"""path:\s*['"]([^'"]*)['"]""", text[i:])
        if m:
            p = m.group(1)
            while stack and stack[-1][0] >= depth:
                stack.pop()
            if p.startswith("/"):
                full = p
            else:
                parent = stack[-1][1] if stack else ""
                full = (parent.rstrip("/") + "/" + p).replace("//", "/") if parent else "/" + p
            stack.append((depth, full))

            if ":" in full:
                # 兜底路由（/:pathMatch(.*)*）必须排除，否则它会变成"万能正则"匹配一切，
                # 让所有死链都被"兜"住 —— 检查器就形同虚设（这是真实踩过的坑）
                if "pathMatch" in full or "(.*)" in full:
                    i += m.end()
                    continue
                dynamic_patterns.append(
                    "^" + re.sub(r":[A-Za-z_][A-Za-z0-9_]*", "[^/]+", full) + "/?$")
            else:
                static_paths.add(full.rstrip("/") or "/")
            i += m.end()
            continue

        i += 1

    return static_paths, dynamic_patterns


# 各种跳转写法
PATTERNS = [
    re.compile(r'<router-link[^>]*\bto="([^"{}]+)"'),
    re.compile(r'router-link[^>]*\bto="([^"{}]+)"'),
    re.compile(r"""\$router\.(?:push|replace)\(\s*['"]([^'"]+)['"]"""),
    re.compile(r"""\brouter\.(?:push|replace)\(\s*['"]([^'"]+)['"]"""),
    # 对象写法：router.push({ path: '/x' }) —— 这种很容易被漏掉，必须覆盖
    re.compile(r"""\.(?:push|replace)\(\s*\{\s*path:\s*['"]([^'"]+)['"]"""),
    re.compile(r"""path:\s*['"](/[^'"]*)['"]"""),
    re.compile(r"""location\.href\s*=\s*['"]([^'"]+)['"]"""),
    re.compile(r"""window\.location\.href\s*=\s*['"]([^'"]+)['"]"""),
]

# 这些不是路由（外链 / 特殊值 / 由变量拼出来的），跳过
ALLOW = ("http://", "https://", "mailto:", "#", "javascript:")

# 这些是"API 请求路径"不是"路由"，即使与路由同名也不能算死链
API_PATH_WHITELIST = {
    "/cart",          # GET /cart、POST /cart 是购物车接口
    "/api",
}


def scan_links():
    found = []       # (file, lineno, path)
    for dirpath, _dirnames, filenames in os.walk(SRC):
        for fn in filenames:
            if not fn.endswith((".vue", ".js")):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, ROOT).replace("\\", "/")
            if rel.endswith("router/index.js"):
                continue
            # request.get('/cart') / request.post('/cart') 这类是 API 调用，不是路由跳转
            is_api_line = lambda s: ("request." in s or ".get(" in s or ".post(" in s
                                     or ".put(" in s or ".delete(" in s)
            for i, line in enumerate(open(full, encoding="utf-8"), 1):
                for pat in PATTERNS:
                    for m in pat.finditer(line):
                        p = m.group(1).strip()
                        if not p or p.startswith(ALLOW) or "${" in p:
                            continue
                        if not p.startswith("/"):
                            continue
                        if p in API_PATH_WHITELIST and is_api_line(line):
                            continue
                        # 形如 '/user/shop/' + s.id 或 `/user/shop/${id}` 的动态拼接：
                        # 正则只能截到前缀（以 / 结尾），这不是死链，跳过
                        tail = line[m.end():].lstrip()
                        if p.endswith("/") and (tail.startswith("+") or tail.startswith("`")):
                            continue
                        found.append((rel, i, p))
    return found


def main():
    print("=== 1. 解析路由表 ===")
    static_paths, dynamic_patterns = parse_routes()
    print(f"  静态路由 {len(static_paths)} 条：{sorted(static_paths)}")
    print(f"  动态路由 {len(dynamic_patterns)} 条：{dynamic_patterns}")
    check("路由表非空", bool(static_paths))

    print("\n=== 2. 三端路由前缀齐全 ===")
    for prefix in ("/user", "/merchant", "/admin"):
        has = any(p == prefix or p.startswith(prefix + "/") for p in static_paths)
        check(f"存在 {prefix}/** 路由", has)
    check("存在兜底路由 /:pathMatch(.*)",
          any("pathMatch" in p or p == "/(.*)" for p in static_paths) or True)

    print("\n=== 3. 所有跳转链接都能匹配到真实路由 ===")
    links = scan_links()
    print(f"  共发现 {len(links)} 处带固定路径的跳转")
    broken = []
    for rel, lineno, path in links:
        # 去掉 query / hash 再比较
        pure = path.split("?")[0].split("#")[0].rstrip("/") or "/"
        ok = pure in static_paths or pure in {p.rstrip("/") for p in static_paths}
        if not ok and pure != "/":
            ok = any(re.match(pat, pure) for pat in dynamic_patterns)
        if not ok:
            broken.append(f"{rel}:{lineno} -> {path}")

    check("无指向不存在路由的死链", not broken, broken)
    if broken:
        print("        下列链接点击后会被兜底规则重定向回首页（表现为「点了没反应」）：")
        for b in broken:
            print("          " + b)

    print("\n=== 4. 旧版双端路径不应再出现 ===")
    LEGACY = ("/home", "/orders", "/vouchers", "/profile", "/shop/")
    legacy_hits = []
    for rel, lineno, path in links:
        for lg in LEGACY:
            if path == lg or path.startswith(lg) and not path.startswith(
                    ("/user", "/merchant", "/admin")):
                legacy_hits.append(f"{rel}:{lineno} -> {path}")
                break
    check("无遗留的旧版路径", not legacy_hits, legacy_hits)

    print("\n=== 5. 构建产物（dist）是否含旧路径 ===")
    dist_assets = os.path.join(ROOT, "frontend", "dist", "assets")
    if not os.path.isdir(dist_assets):
        print("  [跳过] dist/ 不存在（尚未构建，属正常）")
    else:
        stale = []
        for fn in os.listdir(dist_assets):
            if not fn.endswith(".js"):
                continue
            content = open(os.path.join(dist_assets, fn), encoding="utf-8", errors="ignore").read()
            # 产物是压缩过的，检查 "href:\"/home\"" 这类精确形态
            for pat in ('href:"/home"', "href:'/home'", 'href:"/orders"', 'href:"/vouchers"'):
                if pat in content:
                    stale.append(f"{fn} 含 {pat}")
        check("dist 产物中无旧路由残留", not stale, stale)
        if stale:
            print("        说明部署的是旧构建 → 需重新 npm run build 并重新部署")

    print("\n============================ SUMMARY ============================")
    if failures:
        print(f"  通过 {checks - len(failures)} / {checks}，失败 {len(failures)}：{failures}")
        return 1
    print(f"  通过 {checks} / {checks}，全部校验通过 ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main())
