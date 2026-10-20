#!/usr/bin/env python3
"""Generate the fixed first ten validation prompts for candidate snapshots (never test)."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

BASE = Path(__file__).resolve().parent
ROOT = BASE.parent
OUTPUT = BASE / "experiments-v2/triage"


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python3 native-ai/triage-v2.py <checkpoint> [...]")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    cache = BASE / "runs/v2-triage"
    cache.mkdir(parents=True, exist_ok=True)
    prompts = cache / "validation-first10.tsv"
    rows = (BASE / "data-v2/validation.tsv").read_text(encoding="utf-8").splitlines()[:10]
    prompts.write_text("".join(f"{i+1}\t{line.split(chr(9))[0]}\n" for i, line in enumerate(rows)), encoding="utf-8")
    java = Path(os.environ.get("JAVA_HOME", "/workspace/.tools/jdk-25.0.4.1+1")) / "bin/java"
    jar = BASE / "build/libs/luma-native-ai-0.1.0-experimental.jar"
    for argument in sys.argv[1:]:
        model = Path(argument).resolve()
        identity = model.parent.name + "-" + model.stem
        for temperature, label in [(0, "t0"), (0.2, "t02"), (0.7, "t07")]:
            report = OUTPUT / f"{identity}-{label}.jsonl"
            if report.exists():
                header = json.loads(report.read_text(encoding="utf-8").splitlines()[0])
                if header["model_sha256"] != hashlib.sha256(model.read_bytes()).hexdigest():
                    raise SystemExit("Existing triage report belongs to different weights: " + str(report))
                continue
            subprocess.run([str(java), "-Xmx512M", "-jar", str(jar), "samples", str(model), str(prompts),
                            str(report), "--tokens", "64", "--temperature", str(temperature),
                            "--top-k", "20", "--seed", "2026"], check=True, stdout=subprocess.DEVNULL)
            print(report.relative_to(ROOT), flush=True)


if __name__ == "__main__":
    main()
