#!/usr/bin/env python3
"""Add classes.dex and optional lib/<abi>/*.so (stored) to the unsigned APK."""
import os, sys, zipfile

apk, dex, jni = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(apk, "a") as z:
    z.write(dex, "classes.dex")
    if os.path.isdir(jni):
        for root, dirs, files in os.walk(jni):
            for name in files:
                if not name.endswith(".so"):
                    continue
                path = os.path.join(root, name)
                rel = os.path.relpath(path, jni).replace("\\", "/")
                z.write(path, "lib/" + rel, compress_type=zipfile.ZIP_STORED)
