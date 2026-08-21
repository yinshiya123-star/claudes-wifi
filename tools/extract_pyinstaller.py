#!/usr/bin/env python3
"""Minimal PyInstaller CArchive extractor for the bundled executable."""

from __future__ import annotations

import argparse
import marshal
import struct
import zlib
from pathlib import Path


MAGIC = b"MEI\x0c\x0b\x0a\x0b\x0e"
COOKIE = struct.Struct("!8sIIII64s")


def safe_path(root: Path, name: str) -> Path:
    candidate = (root / name).resolve()
    if root.resolve() not in candidate.parents and candidate != root.resolve():
        raise ValueError(f"拒绝不安全的归档路径: {name!r}")
    return candidate


def extract(executable: Path, output: Path) -> None:
    data = executable.read_bytes()
    cookie_pos = data.rfind(MAGIC)
    if cookie_pos < 0:
        raise SystemExit("未找到 PyInstaller CArchive cookie")

    magic, package_len, toc_offset, toc_len, pyver, pylib = COOKIE.unpack_from(
        data, cookie_pos
    )
    package_start = cookie_pos + COOKIE.size - package_len
    toc_start = package_start + toc_offset
    toc_end = toc_start + toc_len
    output.mkdir(parents=True, exist_ok=True)

    print(
        f"Python {pyver // 100}.{pyver % 100}; "
        f"package_start={package_start}; pylib={pylib.rstrip(bytes([0])).decode()}"
    )

    cursor = toc_start
    count = 0
    while cursor < toc_end:
        entry_len = struct.unpack_from("!I", data, cursor)[0]
        pos, compressed_len, raw_len, compressed, typecode = struct.unpack_from(
            "!IIIBc", data, cursor + 4
        )
        name_start = cursor + 4 + struct.calcsize("!IIIBc")
        name_raw = data[name_start : cursor + entry_len]
        name = name_raw.split(bytes([0]), 1)[0].decode("utf-8", "replace")
        if not name:
            name = f"unnamed_{count}"

        payload = data[
            package_start + pos : package_start + pos + compressed_len
        ]
        if compressed:
            payload = zlib.decompress(payload)
        if len(payload) != raw_len:
            print(f"警告: {name} 预期 {raw_len} 字节，得到 {len(payload)} 字节")

        suffix = ".pyc" if typecode in (b"s", b"m", b"M") else ""
        if suffix:
            # Python 3.11 magic from the embedded PYZ, followed by a standard
            # zeroed header, lets bytecode tools recognize the raw code object.
            payload = bytes.fromhex("a70d0d0a") + bytes(12) + payload
        destination = safe_path(output, name + suffix)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(payload)
        print(
            f"{count:03d} {typecode.decode('ascii', 'replace')} "
            f"{raw_len:>10} {name}"
        )
        count += 1
        cursor += entry_len

    print(f"已提取 {count} 个条目到 {output}")


def extract_pyz(archive: Path, output: Path, prefix: str | None) -> None:
    data = archive.read_bytes()
    if data[:4] != b"PYZ\x00":
        raise SystemExit("不是 PyInstaller PYZ 归档")
    pyc_magic = data[4:8]
    toc_offset = struct.unpack("!I", data[8:12])[0]
    toc = marshal.loads(data[toc_offset:])
    output.mkdir(parents=True, exist_ok=True)

    count = 0
    for name, (kind, offset, length) in toc:
        if prefix and name != prefix and not name.startswith(prefix + "."):
            continue
        payload = zlib.decompress(data[offset : offset + length])
        relative = Path(*name.split("."))
        if kind == 1:
            destination = output / relative / "__init__.pyc"
        else:
            destination = output / relative.with_suffix(".pyc")
        destination.parent.mkdir(parents=True, exist_ok=True)
        # Hash/timestamp fields are irrelevant to decompilers; the marshalled code
        # object begins immediately after this conventional 16-byte pyc header.
        destination.write_bytes(pyc_magic + bytes(12) + payload)
        print(f"{kind} {len(payload):>8} {name} -> {destination}")
        count += 1
    print(f"已从 PYZ 提取 {count} 个模块到 {output}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("executable", type=Path, nargs="?")
    parser.add_argument("output", type=Path)
    parser.add_argument("--pyz", type=Path)
    parser.add_argument("--prefix")
    args = parser.parse_args()
    if args.pyz:
        extract_pyz(args.pyz, args.output, args.prefix)
    elif args.executable:
        extract(args.executable, args.output)
    else:
        parser.error("需要 executable 或 --pyz")


if __name__ == "__main__":
    main()
