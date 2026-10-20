#!/usr/bin/env bash
# Reusable Linux x86_64 cloud setup; no credentials or unchecked artifacts.
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
mkdir -p /workspace/.tools /workspace/.cache/gradle
python3 - <<'PY'
from pathlib import Path
import hashlib, os, platform, tarfile, urllib.parse, urllib.request
if platform.machine() != 'x86_64': raise SystemExit('Este setup é para Linux x86_64.')
base = Path('/workspace/.tools')
archive = base / 'OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz'
url = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/' + archive.name
expected = 'dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e'
if not archive.exists(): urllib.request.urlretrieve(url, archive)
with archive.open('rb') as stream: actual = hashlib.file_digest(stream, 'sha256').hexdigest()
if actual != expected: raise SystemExit('Checksum inválido do Java; download não será utilizado.')
jdk = base / 'jdk-25.0.4.1+1'
if not (jdk / 'bin/javac').is_file():
    with tarfile.open(archive) as stream: stream.extractall(base, filter='data')
wrapper = Path('gradle/wrapper/gradle-wrapper.jar')
with wrapper.open('rb') as stream: actual = hashlib.file_digest(stream, 'sha256').hexdigest()
if actual != '7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d':
    raise SystemExit('Checksum inválido do Gradle Wrapper.')
# Java does not read HTTP(S)_PROXY automatically. Use existing routing and trust;
# do not copy proxy credentials or disable certificate checks.
proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
settings = []
if proxy:
    parsed = urllib.parse.urlsplit(proxy)
    for scheme in ('http', 'https'):
        settings += [f'systemProp.{scheme}.proxyHost={parsed.hostname}',
                     f'systemProp.{scheme}.proxyPort={parsed.port or 80}']
    settings.append('systemProp.http.nonProxyHosts=localhost|127.*|[::1]')
    trust = Path('/etc/ssl/certs/java/cacerts')
    if not trust.is_file(): raise SystemExit('Trust store Java do sistema não encontrado.')
    settings += [f'systemProp.javax.net.ssl.trustStore={trust}',
                 'systemProp.javax.net.ssl.trustStorePassword=changeit']
target = Path('/workspace/.cache/gradle/gradle.properties')
# This cache is dedicated to this cloud environment. Preserve unrelated settings.
existing = target.read_text().splitlines() if target.exists() else []
keys = {line.split('=', 1)[0] for line in settings}
lines = [line for line in existing if line.split('=', 1)[0] not in keys]
target.write_text('\n'.join(lines + settings) + '\n')
print('Java e Gradle Wrapper verificados. Proxy e TLS configurados para o ambiente.')
PY
export JAVA_HOME=/workspace/.tools/jdk-25.0.4.1+1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.cache/gradle
./gradlew --no-daemon build
./gradlew --no-daemon -p native-ai build
