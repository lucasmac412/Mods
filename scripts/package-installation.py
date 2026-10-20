#!/usr/bin/env python3
"""Package the verified experimental mod and existing official Fabric API, offline."""
from pathlib import Path
import hashlib
import io
import json
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = "0.2.0-experimental"
PACKAGE = "Luma-Fabric-26.2-IA-Propria-Experimental.zip"
OLD_ZIP_SHA = "00106b9f9a194dec6c73bf5aba125d318e2660c08d509d0e3b06c6140db79067"
# Checked against maven.fabricmc.net's official .jar.sha256 on 2026-10-20.
FABRIC_SHA = "e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a"
MODEL_RESOURCE = "assets/luma/models/luma-native.luma"


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
    selection = json.loads((ROOT / "native-ai/experiments/selection.json").read_text())
    with zipfile.ZipFile(io.BytesIO(mod)) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "luma" or metadata["version"] != VERSION:
            raise SystemExit("JAR da Luma tem identificação/versão incorreta.")
        if sha(archive.read(MODEL_RESOURCE)) != selection["model_sha256"]:
            raise SystemExit("O modelo dentro do mod difere do checkpoint selecionado.")
        if any("gametest" in name.lower() for name in archive.namelist()):
            raise SystemExit("O mod instalável contém arquivos de GameTest.")

    entries = {
        f"mods/luma-companion-{VERSION}.jar": mod,
        "mods/fabric-api-0.161.0+26.2.jar": fabric,
        "COMECE-AQUI.txt": (ROOT / "docs/COMECE-AQUI.txt").read_bytes(),
        "LICENSE": (ROOT / "LICENSE").read_bytes(),
    }
    for name in ["INSTALAR.md", "TESTES.md"]:
        entries[f"docs/{name}"] = (ROOT / "docs" / name).read_bytes()
    for directory in ["data", "docs", "experiments"]:
        for path in sorted((ROOT / "native-ai" / directory).rglob("*")):
            if path.is_file():
                entries[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    entries["native-ai/README.md"] = (ROOT / "native-ai/README.md").read_bytes()
    entries["README.txt"] = (
        f"Luma {VERSION} para Minecraft Java 26.2 / Fabric.\n"
        "Abra COMECE-AQUI.txt. Copie apenas os dois JARs de mods para a pasta mods do jogo.\n"
        "IA de texto original incluída, experimental e desligada por padrão.\n"
        "Os testes ainda produziram texto sem sentido; não equivale a um modelo grande.\n"
        "Nenhum Ollama, modelo pronto, chave de API ou serviço pago é necessário.\n"
        "Voz e microfone continuam usando recursos do navegador/sistema.\n"
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
    packages = [old_zip, target]
    (ROOT / "downloads/SHA256SUMS.txt").write_text(
        "".join(f"{sha(path.read_bytes())}  {path.name}\n" for path in packages), encoding="utf-8")
    print(f"Pacote criado e verificado: {target.name} ({target.stat().st_size} bytes)")
    print(f"SHA-256: {sha(target.read_bytes())}")


if __name__ == "__main__":
    main()
