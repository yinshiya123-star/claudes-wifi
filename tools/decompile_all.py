#!/usr/bin/env python3
"""Batch-decompile the locally extracted project bytecode with pycdc."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pycdc", type=Path, required=True)
    parser.add_argument("--bytecode", type=Path, required=True)
    parser.add_argument("--main", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    inputs = [args.main, *sorted(args.bytecode.rglob("*.pyc"))]
    failures: list[str] = []

    for source in inputs:
        if source == args.main:
            destination = args.output / "claude_net.py"
        else:
            relative = source.relative_to(args.bytecode)
            destination = args.output / relative.with_suffix(".py")
        destination.parent.mkdir(parents=True, exist_ok=True)

        result = subprocess.run(
            [str(args.pycdc), str(source)],
            check=False,
            capture_output=True,
            text=True,
        )
        destination.write_text(result.stdout, encoding="utf-8")
        if result.returncode or result.stderr:
            failures.append(
                f"{source}: exit={result.returncode}; {result.stderr.strip()}"
            )
        print(f"{source} -> {destination} ({len(result.stdout)} chars)")

    report = args.output / "DECOMPILATION_WARNINGS.txt"
    report.write_text("\n".join(failures) + ("\n" if failures else ""), encoding="utf-8")
    print(f"完成：{len(inputs)} 个文件；警告：{len(failures)}；报告：{report}")


if __name__ == "__main__":
    main()
