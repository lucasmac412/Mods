#!/usr/bin/env bash
# Tests real language/HTTP behavior without downloading Minecraft.
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
task_cache=${LUMA_TEST_CACHE:-/workspace/.cache/luma/core-tests}
mkdir -p "$task_cache"
export LUMA_TEST_CACHE="$task_cache"
python3 - <<'PY'
import hashlib, os, urllib.request
from pathlib import Path
cache = Path(os.environ['LUMA_TEST_CACHE'])
artifacts = {
    'gson.jar': 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar',
    'junit.jar': 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar'
}
for name, url in artifacts.items():
    expected = urllib.request.urlopen(url + '.sha256', timeout=30).read().decode().split()[0]
    path = cache / name
    if not path.exists(): urllib.request.urlretrieve(url, path)
    with path.open('rb') as stream: actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != expected: raise SystemExit('Checksum inválido: ' + name)
    print('SHA-256 verificado:', name)
PY
task_jdk=${LUMA_JAVA_HOME:-${JAVA_HOME:-/workspace/.tools/jdk-25.0.4.1+1}}
mkdir -p "$task_cache/classes"
mapfile -t task_sources < <(find "$repo_root/src/main/java/br/com/luma/ai" "$repo_root/src/main/java/br/com/luma/voice" \
    "$repo_root/src/test/java/br/com/luma/ai" "$repo_root/src/test/java/br/com/luma/voice" -name '*.java' -type f | sort)
"$task_jdk/bin/javac" --release 25 -encoding UTF-8 \
    -cp "$task_cache/gson.jar:$task_cache/junit.jar" -d "$task_cache/classes" "${task_sources[@]}"
"$task_jdk/bin/java" -jar "$task_cache/junit.jar" execute \
    --class-path "$task_cache/classes:$task_cache/gson.jar:$repo_root/src/main/resources" \
    --scan-class-path --fail-if-no-tests --disable-banner
