#!/usr/bin/env python3
"""从 WebKiller.java 的 buildJs() 抽出字符串字面量，拼成 .js 供 node --check 校验。"""
import re, sys

SRC = "/home/SQL916/zuoyebang_xposed/src/io/github/sql0106/zybclean/WebKiller.java"
OUT = "/home/SQL916/zuoyebang_xposed/.agent/snips/webkiller.js"

text = open(SRC, encoding="utf-8").read()
i = text.index("private static String buildJs()")
j = text.index('+ "})()";', i)
region = text[i:j + len('+ "})()";')]

# Java 词法扫描：跳过注释，收集字符串字面量
lits, buf = [], []
k, n = 0, len(region)
state = "code"
while k < n:
    c = region[k]
    if state == "code":
        if c == '"':
            state = "str"
            buf = []
            k += 1
            continue
        if c == "/" and k + 1 < n and region[k + 1] == "/":
            state = "line"
            k += 2
            continue
        if c == "/" and k + 1 < n and region[k + 1] == "*":
            state = "block"
            k += 2
            continue
        k += 1
        continue
    if state == "line":
        if c == "\n":
            state = "code"
        k += 1
        continue
    if state == "block":
        if c == "*" and k + 1 < n and region[k + 1] == "/":
            state = "code"
            k += 2
            continue
        k += 1
        continue
    # state == "str"
    if c == "\\":
        buf.append(region[k:k + 2])
        k += 2
        continue
    if c == '"':
        lits.append("".join(buf))
        state = "code"
        k += 1
        continue
    buf.append(c)
    k += 1

if state != "code":
    sys.exit("lexer ended in state %s" % state)


def unescape(s):
    out, p = [], 0
    while p < len(s):
        ch = s[p]
        if ch != "\\":
            out.append(ch)
            p += 1
            continue
        e = s[p + 1]
        if e == "n":
            out.append("\n")
        elif e == "t":
            out.append("\t")
        elif e == "r":
            out.append("\r")
        elif e == "u":
            out.append(chr(int(s[p + 2:p + 6], 16)))
            p += 6
            continue
        else:            # \\  \"  \'  等
            out.append(e)
        p += 2
    return "".join(out)


js = "".join(unescape(x) for x in lits)
open(OUT, "w", encoding="utf-8").write(js + "\n")
print("literals=%d chars=%d -> %s" % (len(lits), len(js), OUT))
print("head:", js[:60])
print("tail:", js[-60:])
