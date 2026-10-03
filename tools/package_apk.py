#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 aapt2 链接产生的 base.apk 与 d8 产生的 classes.dex 合并成可签名 APK。

要点：resources.arsc 必须以「不压缩」方式存放，否则 Android 11+ 会安装失败；
对齐由随后的 zipalign 负责。

用法: python package_apk.py <base.apk> <dex_dir> <out.apk>
"""

import os
import sys
import zipfile


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    base, dex_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]
    if os.path.exists(out):
        os.remove(out)

    dexes = sorted(f for f in os.listdir(dex_dir) if f.endswith(".dex"))
    if not dexes:
        print("no dex found in " + dex_dir)
        return 1

    with zipfile.ZipFile(base, "r") as zin:
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename == "resources.arsc":
                    info = zipfile.ZipInfo(item.filename, date_time=item.date_time)
                    info.compress_type = zipfile.ZIP_STORED
                    info.external_attr = item.external_attr
                    zout.writestr(info, data)
                else:
                    zout.writestr(item, data)
            for name in dexes:
                with open(os.path.join(dex_dir, name), "rb") as f:
                    data = f.read()
                info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                # 不压缩，交给 zipalign 做 4 字节对齐，安装后可直接 mmap
                info.compress_type = zipfile.ZIP_STORED
                info.external_attr = 0o644 << 16
                zout.writestr(info, data)

    print("packaged %s (%d dex, %.2f MB)"
          % (out, len(dexes), os.path.getsize(out) / 1048576.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
