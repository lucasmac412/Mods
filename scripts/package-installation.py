#!/usr/bin/env python3
"""Package Luma, the client voice add-on and the verified Portuguese speech model."""
from pathlib import Path
import hashlib
import io
import json
from pathlib import PurePosixPath
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = "0.4.0-experimental"
PACKAGE = "Luma-Fabric-26.2-Ollama-e-Voz-v0.4.0-Experimental.zip"
OLD_ZIP_SHA = "00106b9f9a194dec6c73bf5aba125d318e2660c08d509d0e3b06c6140db79067"
# Checked against maven.fabricmc.net's official .jar.sha256 on 2026-10-20.
FABRIC_SHA = "e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a"
MODEL_RESOURCE = "assets/luma/models/luma-native.luma"
VOICE_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
VOICE_MODEL_SHA = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"


def speech_model():
    """Use a cached pinned archive, or download from the same documented official source."""
    path = ROOT.parent / ".cache/luma/v04/vosk-model-small-pt-0.3.zip"
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(VOICE_MODEL_URL, timeout=60) as response:
            data = response.read(100 * 1024 * 1024 + 1)
        if len(data) > 100 * 1024 * 1024 or sha(data) != VOICE_MODEL_SHA:
            raise SystemExit("Download do modelo de voz não corresponde ao arquivo documentado.")
        path.write_bytes(data)
    data = path.read_bytes()
    if sha(data) != VOICE_MODEL_SHA:
        raise SystemExit("Checksum inválido do modelo de voz em cache.")
    provenance = json.loads((ROOT / "third-party/voice/vosk-model-provenance.json").read_text())
    entries = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if archive.testzip() is not None:
            raise SystemExit("CRC inválido do modelo de voz.")
        for info in archive.infolist():
            parts = PurePosixPath(info.filename).parts
            if (not parts or parts[0] != "vosk-model-small-pt-0.3" or ".." in parts
                    or "\\" in info.filename or (info.external_attr >> 16) & 0o170000 == 0o120000):
                raise SystemExit("Caminho inesperado no modelo de voz.")
            if info.is_dir():
                continue
            relative = PurePosixPath(*parts[1:]).as_posix()
            value = archive.read(info)
            if sha(value) != provenance["files"].get(relative):
                raise SystemExit("Conteúdo do modelo de voz diverge do manifesto: " + relative)
            entries["config/luma-voice/model/" + relative] = value
    if len(entries) != len(provenance["files"]):
        raise SystemExit("O modelo de voz está incompleto.")
    return entries


def sha(payload):
    return hashlib.sha256(payload).hexdigest()


def main():
    old_zip = ROOT / "downloads/Luma-Fabric-26.2.zip"
    payload = old_zip.read_bytes()
    if sha(payload) != OLD_ZIP_SHA:
        raise SystemExit("O pacote anterior não corresponde ao checksum verificado.")
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        fabric_entry = next(name for name in archive.namelist() if name.endswith("/fabric-api-0.161.0+26.2.jar"))
        fabric = archive.read(fabric_entry)
    if sha(fabric) != FABRIC_SHA:
        raise SystemExit("Checksum inválido do Fabric API.")
    mod = (ROOT / f"build/libs/luma-companion-{VERSION}.jar").read_bytes()
    selection = json.loads((ROOT / "native-ai/experiments-v2/selection.json").read_text())
    with zipfile.ZipFile(io.BytesIO(mod)) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "luma" or metadata["version"] != VERSION:
            raise SystemExit("JAR da Luma tem identificação/versão incorreta.")
        if sha(archive.read(MODEL_RESOURCE)) != selection["model_sha256"]:
            raise SystemExit("O modelo dentro do mod difere do checkpoint selecionado.")
        if sha(archive.read("assets/luma/models/luma-knowledge.tsv")) != selection["knowledge_sha256"]:
            raise SystemExit("A memória incluída difere do índice selecionado.")
        if any("gametest" in name.lower() for name in archive.namelist()):
            raise SystemExit("O mod instalável contém arquivos de GameTest.")

    voice = (ROOT / f"voice-input/build/libs/luma-voice-{VERSION}.jar").read_bytes()
    with zipfile.ZipFile(io.BytesIO(voice)) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "luma_voice" or metadata["version"] != VERSION or metadata["environment"] != "client":
            raise SystemExit("JAR de voz tem identificação/versão/ambiente incorreto.")
        dependencies = metadata.get("jars", [])
        if not any("vosk" in entry["file"] for entry in dependencies) or not any("jna" in entry["file"] for entry in dependencies):
            raise SystemExit("O complemento de voz precisa incluir Vosk e JNA.")
        for entry in dependencies:
            if entry["file"] not in archive.namelist():
                raise SystemExit("Dependência de voz ausente: " + entry["file"])

    entries = {
        f"mods/luma-companion-{VERSION}.jar": mod,
        f"mods/luma-voice-{VERSION}.jar": voice,
        "mods/fabric-api-0.161.0+26.2.jar": fabric,
        "COMECE-AQUI.txt": (ROOT / "docs/COMECE-AQUI.txt").read_bytes(),
        "LICENSE": (ROOT / "LICENSE").read_bytes(),
    }
    entries.update(speech_model())
    for path in sorted((ROOT / "third-party/voice").rglob("*")):
        if path.is_file():
            entries[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    entries["voice-input/README.md"] = (ROOT / "voice-input/README.md").read_bytes()
    entries["voice-input/THIRD-PARTY-NOTICES.md"] = (ROOT / "voice-input/THIRD-PARTY-NOTICES.md").read_bytes()
    for name in ["INSTALAR.md", "TESTES.md", "OLLAMA-E-VOZ.md"]:
        entries[f"docs/{name}"] = (ROOT / "docs" / name).read_bytes()
    for directory in ["data", "data-v2", "data-v2-grounded", "data-v2-copy", "docs", "experiments", "experiments-v2"]:
        for path in sorted((ROOT / "native-ai" / directory).rglob("*")):
            if path.is_file():
                entries[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    entries["native-ai/README.md"] = (ROOT / "native-ai/README.md").read_bytes()
    entries["README.txt"] = (
        f"Luma {VERSION} para Minecraft Java 26.2 / Fabric.\n"
        "Abra COMECE-AQUI.txt. Copie os três JARs de mods para a pasta mods do jogo\n"
        "e mescle config/luma-voice na pasta config do mesmo perfil do Minecraft.\n"
        "Instale o Ollama e baixe qwen2.5:3b para a conversa: instruções no guia.\n"
        "O modelo de conversa do Ollama não está neste ZIP. /luma ia ollama ativa a integração.\n"
        "O modelo original pequeno ainda acompanha a Luma: /luma ia propria.\n"
        "O complemento Luma Voz usa Vosk com modelo de terceiros para reconhecer português\n"
        "localmente. Segure V para gravar e solte para enviar. A voz de saída depende\n"
        "do sintetizador do sistema e de uma voz adequada instalada. Veja os limites no guia.\n"
        "Ollama e os modelos de terceiros têm suas próprias licenças. Consulte third-party.\n"
        "Os relatórios e dados acompanham o pacote para consulta. Comandos de desenvolvimento\n"
        "do laboratório exigem o repositório completo: https://github.com/lucasmac412/Mods\n"
    ).encode("utf-8")
    entries["SHA256SUMS.txt"] = "".join(f"{sha(value)}  {name}\n" for name, value in sorted(entries.items())).encode("utf-8")

    target = ROOT / "downloads" / PACKAGE
    temporary = target.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, value in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.external_attr = 0o100644 << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, value)
    with zipfile.ZipFile(temporary) as archive:
        if archive.testzip() is not None:
            raise SystemExit("Falha de CRC no pacote criado.")
    temporary.replace(target)
    packages = sorted((ROOT / "downloads").glob("*.zip"))
    (ROOT / "downloads/SHA256SUMS.txt").write_text(
        "".join(f"{sha(path.read_bytes())}  {path.name}\n" for path in packages), encoding="utf-8")
    print(f"Pacote criado e verificado: {target.name} ({target.stat().st_size} bytes)")
    print(f"SHA-256: {sha(target.read_bytes())}")


if __name__ == "__main__":
    main()
