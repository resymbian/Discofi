#!/usr/bin/env python3
"""Package and audit the JARs produced by build.sh."""
import hashlib
import os
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BIN = os.path.join(ROOT, "bin")

TARGETS = {
    "discofi": {"lite": False, "debug": False, "max_size": 220000},
    "discofi_samsung": {"lite": False, "debug": False, "max_size": 220000},
    "discofi_lite": {"lite": True, "debug": False, "max_size": 170000},
    "discofi_debug": {"lite": False, "debug": True, "max_size": 300000},
}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_manifest(jar):
    with zipfile.ZipFile(jar) as z:
        return z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")


def make_jad(jar):
    manifest = read_manifest(jar)
    wanted = (
        "MIDlet-Name", "MIDlet-Vendor", "MIDlet-Version", "MIDlet-1",
        "MicroEdition-Configuration", "MicroEdition-Profile",
        "MIDlet-Permissions-Opt",
    )
    values = {}
    for line in manifest.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        if key in wanted:
            values[key] = value.strip()

    for key in wanted:
        if key not in values:
            raise SystemExit(f"{jar}: manifest is missing {key}")

    name = os.path.basename(jar)
    lines = [
        f"MIDlet-Name: {values['MIDlet-Name']}",
        f"MIDlet-Vendor: {values['MIDlet-Vendor']}",
        f"MIDlet-Version: {values['MIDlet-Version']}",
        f"MIDlet-1: {values['MIDlet-1']}",
        f"MicroEdition-Configuration: {values['MicroEdition-Configuration']}",
        f"MicroEdition-Profile: {values['MicroEdition-Profile']}",
        f"MIDlet-Permissions-Opt: {values['MIDlet-Permissions-Opt']}",
        f"MIDlet-Jar-URL: {name}",
        f"MIDlet-Jar-Size: {os.path.getsize(jar)}",
    ]

    jad = os.path.splitext(jar)[0] + ".jad"
    with open(jad, "w", encoding="ascii", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return jad


def verify_jar(target, spec):
    jar = os.path.join(BIN, target + ".jar")
    if not os.path.exists(jar):
        raise SystemExit(f"missing {jar}")

    size = os.path.getsize(jar)
    if size > spec["max_size"]:
        raise SystemExit(f"{target}.jar is {size} bytes, budget is {spec['max_size']}")

    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if "META-INF/MANIFEST.MF" not in names:
            raise SystemExit(f"{target}: missing manifest")

        manifest_bytes = z.read("META-INF/MANIFEST.MF")
        bare_lf = manifest_bytes.replace(b"\r\n", b"").find(b"\n") != -1
        if b"\r\n" not in manifest_bytes or bare_lf:
            raise SystemExit(f"{target}: manifest must use CRLF line endings")

        manifest = manifest_bytes.decode("utf-8", "replace")
        midlet_line = next((line for line in manifest.splitlines()
                            if line.startswith("MIDlet-1:")), None)
        if midlet_line is None:
            raise SystemExit(f"{target}: manifest has no MIDlet-1")

        midlet_class = midlet_line.split(":", 1)[1].rsplit(",", 1)[-1].strip()
        midlet_entry = midlet_class.replace(".", "/") + ".class"
        if midlet_entry not in names:
            raise SystemExit(f"{target}: missing MIDlet class {midlet_entry}")

        class_name = midlet_entry
        if not class_name:
            raise SystemExit(f"{target}: no classes")

        header = z.read(class_name)[:8]
        major = int.from_bytes(header[6:8], "big")
        if major != 46:
            raise SystemExit(f"{target}: class version {major}, expected 46 (Java 1.2)")

        has_emoji = "emoji/emoji.dat" in names and "emoji/emoji0.png" in names
        if spec["lite"] and has_emoji:
            raise SystemExit(f"{target}: lite build must not contain emoji assets")
        if not spec["lite"] and not has_emoji:
            raise SystemExit(f"{target}: missing emoji assets")

        has_demo = "fi/gtrxac/discord/DemoScreen.class" in names
        if not spec["debug"] and has_demo:
            raise SystemExit(f"{target}: release build contains DemoScreen")
        if spec["debug"] and not has_demo:
            raise SystemExit(f"{target}: debug build is missing DemoScreen")

    return jar, size


def verify_manifest(manifest_path):
    base = os.path.dirname(os.path.dirname(manifest_path))
    with open(manifest_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            expected, path = line.split(None, 1)
            full = os.path.join(base, path)
            if not os.path.exists(full):
                raise SystemExit(f"missing audited file: {full}")
            actual = sha256(full)
            if actual != expected:
                raise SystemExit(f"hash mismatch for {path}: {actual} != {expected}")


def main():
    for manifest in ("sdk/lib.sha256", "res/emoji.sha256"):
        verify_manifest(os.path.join(ROOT, manifest))

    jars = []
    print("Artifact audit")
    normalizer = os.path.join(ROOT, "sdk", "normalize-jar.py")
    for target, spec in TARGETS.items():
        raw = os.path.join(BIN, target + ".jar")
        normalized = raw + ".normalized"
        subprocess.check_call([sys.executable, normalizer, raw, normalized])
        os.replace(normalized, raw)

        jar, size = verify_jar(target, spec)
        jad = make_jad(jar)
        jars.extend([jar, jad])
        print(f"  {os.path.basename(jar):24} {size:>8} bytes  (limit {spec['max_size']})")

    sums = os.path.join(BIN, "SHA256SUMS")
    with open(sums, "w", encoding="ascii", newline="\n") as f:
        for path in sorted(jars):
            f.write(f"{sha256(path)}  {os.path.basename(path)}\n")

    print(f"  {os.path.basename(sums):24} written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
