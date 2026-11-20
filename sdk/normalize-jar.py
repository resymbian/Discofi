#!/usr/bin/env python3
"""Rewrite a JAR with fixed timestamps and stable ordering.

ProGuard/JAR timestamps make otherwise identical builds hash differently.
Normalizing entry metadata makes CI able to prove that two clean builds are
bit-for-bit identical.
"""
import os
import sys
import tempfile
import zipfile

FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def normalize(src, dst):
    with zipfile.ZipFile(src, "r") as zin:
        names = [info.filename for info in zin.infolist()]
        data = {name: zin.read(name) for name in names}

    manifest = "META-INF/MANIFEST.MF"
    if manifest not in data:
        raise SystemExit(f"{src}: missing {manifest}")

    rest = sorted(name for name in names if name != manifest)

    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for name in [manifest] + rest:
            info = zipfile.ZipInfo(name, date_time=FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            zout.writestr(info, data[name])


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: normalize-jar.py input.jar output.jar")

    src, dst = sys.argv[1], sys.argv[2]
    target_dir = os.path.dirname(os.path.abspath(dst)) or "."
    fd, tmp = tempfile.mkstemp(suffix=".jar", dir=target_dir)
    os.close(fd)

    try:
        normalize(src, tmp)
        os.replace(tmp, dst)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)
