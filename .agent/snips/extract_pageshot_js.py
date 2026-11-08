#!/usr/bin/env python3
"""从 PageShot.java 里抽出 PREP/RESTORE/CLEAN 三个 Java 字符串常量，落成 .js 供 node --check。

产物固定写在仓库内 `.agent/snips/`（用户硬规则：禁止写 /tmp），文件名 `pageshot-<name>.js`，
随 commit 一起保留，任务结束不删。
"""
import os
import re
import sys

SRC = "/home/SQL916/zuoyebang_xposed/src/io/github/sql0106/zybclean/PageShot.java"
OUT = "/home/SQL916/zuoyebang_xposed/.agent/snips"
NAMES = ["PREP", "RESTORE", "CLEAN"]

text = open(SRC, encoding="utf-8").read()

ESC = {"b": "\b", "t": "\t", "n": "\n", "f": "\f", "r": "\r",
       "\\": "\\", "'": "'", '"': '"'}


def decode(java_literal_body):
    out = []
    i = 0
    while i < len(java_literal_body):
        c = java_literal_body[i]
        if c != "\\":
            out.append(c)
            i += 1
            continue
        nxt = java_literal_body[i + 1]
        if nxt == "u":
            out.append(chr(int(java_literal_body[i + 2:i + 6], 16)))
            i += 6
        else:
            out.append(ESC.get(nxt, nxt))
            i += 2
    return "".join(out)


def extract(name):
    m = re.search(r"private static final String " + name + r"\s*=", text)
    if not m:
        sys.exit("constant not found: " + name)
    i = m.end()
    parts = []
    in_str = in_line_comment = in_block_comment = False
    buf = []
    while i < len(text):
        c = text[i]
        two = text[i:i + 2]
        if in_str:
            if c == "\\":
                buf.append(text[i:i + 2])
                i += 2
                continue
            if c == '"':
                in_str = False
                parts.append(decode("".join(buf)))
                buf = []
                i += 1
                continue
            buf.append(c)
            i += 1
            continue
        if in_line_comment:
            if c == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if two == "*/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if two == "//":
            in_line_comment = True
            i += 2
            continue
        if two == "/*":
            in_block_comment = True
            i += 2
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == ";":
            break
        i += 1
    return "".join(parts)


os.makedirs(OUT, exist_ok=True)
for n in NAMES:
    js = extract(n)
    p = os.path.join(OUT, "pageshot-%s.js" % n.lower())
    with open(p, "w", encoding="utf-8") as f:
        f.write(js)
    print("%-8s %5d chars  -> %s" % (n, len(js), p))
