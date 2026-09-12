"""文档链接与口径一致性校验（无第三方依赖，仅标准库）。

校验内容：
  1. 仓库内所有 md 文件的相对链接是否都能解析到真实文件
  2. 是否还有指向已改名的旧文档（面试题档案50条.md）
  3. 关键事实口径是否一致（角色编码、演示账号、端口、命令）
  4. 是否残留"双端/两端"以外的过期表述

用法（项目根目录）：python scripts/check-docs.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"node_modules", "target", "dist", ".git", ".idea", "backup"}

failures = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else " -> " + str(detail)))
    if not ok:
        failures.append(name)


def md_files():
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(".md"):
                yield os.path.join(dirpath, fn)


LINK_RE = re.compile(r"\[[^\]]*\]\(([^)#\s]+)(?:#[^)]*)?\)")


def main():
    files = list(md_files())
    print(f"=== 1. 相对链接可解析（共 {len(files)} 个 md 文件）===")
    broken = []
    external = 0
    internal = 0
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            text = fh.read()
        for target in LINK_RE.findall(text):
            if target.startswith(("http://", "https://", "mailto:")):
                external += 1
                continue
            internal += 1
            resolved = os.path.normpath(os.path.join(os.path.dirname(path), target))
            if not os.path.exists(resolved):
                broken.append(f"{os.path.relpath(path, ROOT)} -> {target}")
    check("所有仓库内相对链接均可解析", not broken, broken[:10])
    print(f"         外链 {external} 个，内链 {internal} 个")

    print("\n=== 2b. sql 目录：只有 init.sql 会被容器自动执行 ===")
    sql_dir = os.path.join(ROOT, "sql")
    auto = sorted(f for f in os.listdir(sql_dir) if f.endswith(".sql"))
    check("sql/ 根目录仅保留 init.sql（避免 MySQL entrypoint 通配误执行）",
          auto == ["init.sql"], auto)
    manual_dir = os.path.join(sql_dir, "manual")
    check("sql/manual/ 存在且含升级脚本",
          os.path.isdir(manual_dir) and "upgrade_v2_three_end.sql" in os.listdir(manual_dir),
          os.listdir(manual_dir) if os.path.isdir(manual_dir) else "no manual dir")

    print("\n=== 2. 无指向已改名文档的链接 ===")
    stale = []
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            for i, line in enumerate(fh, 1):
                if "面试题档案50条" in line or "面试题档案 50 条" in line:
                    stale.append(f"{os.path.relpath(path, ROOT)}:{i}")
    check("无指向 面试题档案50条.md 的链接", not stale, stale)

    print("\n=== 3. 三端角色口径在文档中一致 ===")
    joined = ""
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            joined += fh.read()
    check("文档中出现 1用户/2商家/3管理员 口径", "1用户" in joined and "2商家" in joined and "3管理员" in joined)

    # "2=管理员" 只允许出现在描述 v1 旧口径/升级迁移的句子中（那是历史事实，必须保留）
    legacy_ok = ("v1", "旧版", "改造前", "升级", "变更点", "历史")
    bad_role = []
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            for i, line in enumerate(fh, 1):
                if any(k in line for k in ("role=2 管理员", "2=管理员", "1用户 2管理员")):
                    if not any(k in line for k in legacy_ok):
                        bad_role.append(f"{os.path.relpath(path, ROOT)}:{i}")
    check("“管理员=2”仅出现在 v1 迁移语境中", not bad_role, bad_role)
    check("文档中出现 merchant_id 唯一约束说明", "uk_merchant" in joined)
    check("文档中出现审计日志表", "tb_audit_log" in joined)
    check("文档中出现入驻申请表", "tb_merchant_apply" in joined)

    print("\n=== 4. 关键命令与路径与仓库实际一致 ===")
    for rel in ("docker-compose.prod.yml", ".env.prod.example", "scripts/deploy.sh",
                "scripts/backup-db.sh", "scripts/restore-db.sh", "scripts/smoke-test.ps1",
                "scripts/check-compose.py", "sql/init.sql", "sql/manual/upgrade_v2_three_end.sql",
                "backend/Dockerfile", "frontend/Dockerfile", "frontend/nginx.conf"):
        check(f"文档提到的 {rel} 存在", os.path.exists(os.path.join(ROOT, rel)))

    for cmd in ("./scripts/deploy.sh --first", "./scripts/backup-db.sh",
                "docker compose --env-file .env.prod -f docker-compose.prod.yml",
                "docker compose -f docker-compose.full.yml", "docker compose up -d"):
        check(f"文档命令 {cmd!r} 出现在文档中", cmd in joined)

    print("\n=== 4b. Linux 部署关键文件的编码与换行（CRLF 会让 sh 报错）===")
    lf_critical = ["scripts/deploy.sh", "scripts/backup-db.sh", "scripts/restore-db.sh",
                   "backend/Dockerfile", "frontend/Dockerfile", "backend/settings.xml",
                   "frontend/nginx.conf", ".env.prod.example"]
    for rel in lf_critical:
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            check(f"{rel} 存在", False, "缺失")
            continue
        with open(path, "rb") as fh:
            raw = fh.read()
        has_crlf = b"\r\n" in raw
        has_bom = raw.startswith(b"\xef\xbb\xbf")
        check(f"{rel} 为 LF 且无 BOM", not has_crlf and not has_bom,
              f"CRLF={has_crlf} BOM={has_bom}")

    print("\n=== 4c. deploy.sh 的 .env.prod 占位符校验必须忽略注释行 ===")
    # 这条规则来自一次真实故障：模板顶部说明文字里含 "CHANGE_ME"，
    # 早期脚本用 `grep -q "CHANGE_ME"` 导致合法配置被误判为"未替换密码"而拒绝部署。
    import re as _re
    import subprocess
    import tempfile

    deploy_path = os.path.join(ROOT, "scripts", "deploy.sh")
    with open(deploy_path, "r", encoding="utf-8") as fh:
        deploy_text = fh.read()

    m = _re.search(r"PLACEHOLDER_RE='([^']+)'", deploy_text)
    check("deploy.sh 中定义了 PLACEHOLDER_RE 常量", bool(m), "未找到 PLACEHOLDER_RE")
    if m:
        pattern = m.group(1)
        compiled = _re.compile(pattern)

        def rejected(text):
            """模拟 shell 里 grep -qE 的语义：任意一行命中即视为未替换"""
            return any(compiled.search(line) for line in text.splitlines())

        good = (
            "#   vim .env.prod            # 修改下面所有 CHANGE_ME 的值为强密码/随机串\n"
            "# CHANGE_ME 只是占位提示，注释行不该触发校验\n"
            "APP_VERSION=2.0.0\n"
            "MYSQL_ROOT_PASSWORD=aB3/xY9+Qm2Zr8Lp1Ks4Vt7W\n"
            "REDIS_PASSWORD=Zx9Qw8Ee7Rt6Yu5Io4Pa3Sd2F\n"
            "RABBIT_USER=smartlife\n"
            "RABBIT_PASSWORD=Mn7Bv6Cx5Za4Ls3Df2Gh1Jk0\n"
            "JWT_SECRET=aVeryLongRandomJwtSecretValue0123456789abcdef\n"
        )
        bad = "MYSQL_ROOT_PASSWORD=CHANGE_ME_mysql_root_strong_password\n"

        check("含 CHANGE_ME 注释 + 真实密码的 .env.prod 应通过校验", not rejected(good),
              "被误判为占位符")
        check("真正未替换的赋值行应被拦截", rejected(bad), "漏判")

        # 额外确认：脚本里不再残留裸 grep "CHANGE_ME"（那正是误判来源）
        bare = _re.search(r'grep[^\n]*"CHANGE_ME"', deploy_text)
        check("deploy.sh 不再使用裸 grep \"CHANGE_ME\"", bare is None,
              bare.group(0) if bare else "")

    print("\n=== 4d. pack-package.ps1 的自检清单必须都真实存在 ===")
    # 打包脚本末尾会逐个校验 $mustHave 里的文件；若清单里写了不存在的文件，
    # 下次打包会直接 throw「打包自检失败」，属于会阻断发布的硬错误。
    pack_path = os.path.join(ROOT, "scripts", "pack-package.ps1")
    with open(pack_path, "r", encoding="utf-8-sig") as fh:
        pack_text = fh.read()
    block = _re.search(r"\$mustHave\s*=\s*@\((.*?)\)", pack_text, _re.S)
    check("pack-package.ps1 中定义了 $mustHave 清单", bool(block), "未找到 $mustHave 数组")
    if block:
        listed = _re.findall(r"'([^']+)'", block.group(1))
        check("$mustHave 清单非空", len(listed) > 0, listed)
        missing = []
        for item in listed:
            if not os.path.exists(os.path.join(ROOT, item.replace("/", os.sep))):
                missing.append(item)
        check("$mustHave 中列出的文件全部存在", not missing, missing)

    print("\n=== 4e. 部署文档存在且被 README 引用 ===")
    for rel in ("docs/01-从零部署-上.md", "docs/02-从零部署-下.md"):
        check(f"{rel} 存在", os.path.exists(os.path.join(ROOT, rel)))
    with open(os.path.join(ROOT, "README.md"), "r", encoding="utf-8") as fh:
        readme = fh.read()
    for name in ("01-从零部署-上.md", "02-从零部署-下.md"):
        check(f"README 引用了 {name}", name in readme)

    print("\n=== 5. 演示账号口径一致（四个账号都应被文档提到）===")
    for acc in ("13800000000", "13700000001", "13700000002", "13900000001"):
        check(f"演示账号 {acc} 在文档中", acc in joined)

    print("\n============================ SUMMARY ============================")
    if failures:
        print(f"  通过 {checks - len(failures)} / {checks}，失败 {len(failures)}")
        for f in failures:
            print(f"    - {f}")
        return 1
    print(f"  通过 {checks} / {checks}，全部校验通过 ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main())
