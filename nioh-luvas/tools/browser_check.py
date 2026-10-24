#!/usr/bin/env python3
"""Run the standalone preview in an installed headless Chrome or Edge."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(output):
    candidates = [shutil.which("chromium"), shutil.which("google-chrome")]
    for base in (os.environ.get("ProgramFiles", ""), os.environ.get("ProgramFiles(x86)", "")):
        candidates += [str(Path(base) / "Google/Chrome/Application/chrome.exe"),
                       str(Path(base) / "Microsoft/Edge/Application/msedge.exe")]
    browser = next((c for c in candidates if c and Path(c).is_file()), None)
    if not browser:
        raise RuntimeError("No installed Chrome/Edge found; browser validation did not run")
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="nioh-preview-") as profile:
        common = [browser, "--headless=new", "--disable-gpu", "--no-first-run",
                  "--disable-background-networking", "--disable-component-update",
                  "--no-default-browser-check", "--no-sandbox", "--disable-dev-shm-usage",
                  "--window-size=1280,900", "--hide-scrollbars", "--user-data-dir=" + profile]
        url = (ROOT / "preview/Previa.html").as_uri()
        result = subprocess.run(common + ["--dump-dom", "--virtual-time-budget=1800", url + "?selftest=1"],
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=55)
        (output / "browser-result.html").write_text(result.stdout, encoding="utf-8")
        if 'data-test="passed"' not in result.stdout or "PASS: 8 cuts" not in result.stdout:
            raise RuntimeError("Preview browser self-test failed: " + result.stderr[-1200:])
        shot = (output / "caps-preview.png").resolve()
        subprocess.run(common + ["--screenshot=" + str(shot), "--virtual-time-budget=1200", url + "?demo=1"],
                       capture_output=True, timeout=55, check=True)
        if not shot.is_file() or shot.stat().st_size < 10000:
            raise RuntimeError("Preview screenshot was not produced")
    print("PASS: real headless browser executed 8-cut/Ki/cooldown preview and rendered screenshot. Not a Nioh gameplay test.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    run(parser.parse_args().output)
