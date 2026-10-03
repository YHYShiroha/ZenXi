#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
APK 静态自检：结构、压缩方式、对齐、关键资源是否齐全。

用法: python verify_apk.py dist/ZenXi-v1.0.0.apk
"""

import struct
import sys
import zipfile

problems = []
notes = []


def check(cond, ok, bad):
    if cond:
        notes.append("  OK   " + ok)
    else:
        problems.append(bad)


def main():
    path = sys.argv[1]
    z = zipfile.ZipFile(path)
    infos = {i.filename: i for i in z.infolist()}

    print("APK: %s  (%.2f MB, %d entries)"
          % (path, len(open(path, "rb").read()) / 1048576.0, len(infos)))

    check("AndroidManifest.xml" in infos, "AndroidManifest.xml 存在", "缺少 AndroidManifest.xml")
    check("resources.arsc" in infos, "resources.arsc 存在", "缺少 resources.arsc")
    check("classes.dex" in infos, "classes.dex 存在", "缺少 classes.dex")

    arsc = infos.get("resources.arsc")
    if arsc:
        check(arsc.compress_type == zipfile.ZIP_STORED,
              "resources.arsc 未压缩存放 (STORED)", "resources.arsc 被压缩，Android 11+ 可能安装失败")
        check(arsc.header_offset % 4 == 0,
              "resources.arsc 4 字节对齐 (offset=%d)" % arsc.header_offset,
              "resources.arsc 未 4 字节对齐 (offset=%d)" % arsc.header_offset)

    dex = infos.get("classes.dex")
    if dex:
        magic = z.open("classes.dex").read(8)
        check(magic.startswith(b"dex\n"), "classes.dex 魔数正确 (%s)" % magic[:4],
              "classes.dex 魔数异常: %r" % magic)
        check(dex.header_offset % 4 == 0,
              "classes.dex 4 字节对齐 (offset=%d)" % dex.header_offset,
              "classes.dex 未 4 字节对齐 (offset=%d)" % dex.header_offset)

    # 资源清单
    res = [n for n in infos if n.startswith("res/")]
    raw = [n for n in res if n.startswith("res/raw/")]
    check(len(raw) == 20, "铃音资源 20 个", "铃音资源数量异常: %d" % len(raw))
    wav_ok = all(infos[n].compress_type == zipfile.ZIP_STORED for n in raw)
    check(wav_ok, "wav 未压缩存放（SoundPool 要求）", "存在被压缩的 wav，SoundPool 可能加载失败")

    icons = sorted(n for n in infos if "ic_launcher" in n)
    check(len(icons) >= 16, "启动图标各密度齐全 (%d 个)" % len(icons),
          "启动图标缺失，只有 %d 个" % len(icons))
    check(any(n.endswith("ic_stat_bell.png") for n in infos),
          "通知栏图标存在", "缺少通知栏图标 ic_stat_bell.png")

    # 二进制 XML 校验（AndroidManifest 必须是编译后的资源 XML）
    if "AndroidManifest.xml" in infos:
        data = z.read("AndroidManifest.xml")
        check(data[:2] == b"\x03\x00", "AndroidManifest.xml 已编译为二进制 XML",
              "AndroidManifest.xml 不是二进制 XML")

    # 签名块（v2/v3 scheme 会写入 APK Signing Block，位于中央目录之前）
    data = open(path, "rb").read()
    check(b"APK Sig Block 42" in data, "存在 APK Signing Block (v2/v3 签名)",
          "未找到 APK Signing Block，签名可能只有 v1")

    print("\n".join(notes))
    if problems:
        print("\n存在问题:")
        for p in problems:
            print("  FAIL " + p)
        return 1
    print("\n全部检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
