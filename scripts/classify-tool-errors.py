#!/usr/bin/env python3
"""Classify tool AssertionErrors in a sqlancer log as permission vs non-permission.

Under a non-English (zh_CN) PostgreSQL locale, the perm-substring scan misses
localized permission messages. This decoder walks each AssertionError's Caused-by
chain, decodes GBK, and flags permission/superuser-class errors so we can reliably
confirm "no permission-inaccessible SQL" even without restarting the server.
"""
import re, sys, glob, os

# Permission-class patterns (English + Chinese zh_CN PG messages)
PERM_PAT = re.compile(
    r"permission denied|must be (owner|superuser|member)|insufficient privilege|"
    r"权限不足|权限不允许|必须是|没有权限|必须是超级用户|必须是所有者|"
    r"superuser-only|requires superuser|pg_read_all_settings|pg_execute_server_program|"
    r"permission denied for function|permission denied for table|permission denied for schema",
    re.IGNORECASE,
)

def decode(b):
    for enc in ("utf-8", "gbk", "cp936"):
        try:
            return b.decode(enc)
        except Exception:
            continue
    return b.decode("utf-8", "replace")

def classify(logpath):
    raw = open(logpath, "rb").read().decode("utf-8", "replace")
    # try gbk if it looks mojibake
    txt = decode(open(logpath, "rb").read())
    perm_hits = []
    other_count = 0
    # iterate AssertionError blocks
    for m in re.finditer(r"java\.lang\.AssertionError:\s*([^\n]{4,140})", txt):
        sql = m.group(1).strip()[:120]
        seg = txt[m.end():m.end()+600]
        cb = re.search(r"Caused by:[^\n]*", seg)
        err = cb.group(0)[:140] if cb else "(no caused-by)"
        if PERM_PAT.search(err):
            perm_hits.append((sql, err))
        else:
            other_count += 1
    return perm_hits, other_count

def main():
    logs = sys.argv[1:] or sorted(glob.glob("logs/perm-confirm/*.log"))
    total_perm = 0
    total_other = 0
    for lp in logs:
        if not os.path.isfile(lp):
            continue
        ph, oc = classify(lp)
        name = os.path.basename(lp).replace(".log", "")
        total_perm += len(ph)
        total_other += oc
        print(f"{name:24s} perm={len(ph):3d} other_tool={oc:3d}")
        for sql, err in ph[:3]:
            print(f"    PERM: {sql[:70]}")
            print(f"          {err[:90]}")
    print(f"\nTOTAL: perm={total_perm}  other_tool={total_other}")
    print("PRIMARY_CRITERION (no permission SQL): " + ("PASS" if total_perm == 0 else "FAIL"))

if __name__ == "__main__":
    main()
