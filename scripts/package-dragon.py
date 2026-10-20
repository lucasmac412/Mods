#!/usr/bin/env python3
"""Build the installation ZIP from the tested dragon JAR and verified official Fabric API."""
from pathlib import Path
import hashlib
import io
import json
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = "0.1.0-experimental"
NAME = "Dragao-Celestial-Fabric-26.2-v0.1.0-Experimental.zip"
FABRIC_SHA = "e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a"
OLD_PACKAGE_SHA = "00106b9f9a194dec6c73bf5aba125d318e2660c08d509d0e3b06c6140db79067"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    baseline = (ROOT / "downloads/Luma-Fabric-26.2.zip").read_bytes()
    if sha(baseline) != OLD_PACKAGE_SHA:
        raise SystemExit("O pacote de origem do Fabric API não corresponde ao checksum registrado.")
    with zipfile.ZipFile(io.BytesIO(baseline)) as archive:
        entry = next(n for n in archive.namelist() if n.endswith("/fabric-api-0.161.0+26.2.jar"))
        fabric = archive.read(entry)
    if sha(fabric) != FABRIC_SHA:
        raise SystemExit("Fabric API com checksum incorreto.")
    with zipfile.ZipFile(io.BytesIO(fabric)) as archive:
        fabric_license = archive.read("LICENSE-fabric-api")
    jar_name = f"celestial-dragon-{VERSION}.jar"
    mod = (ROOT / "dragon-mod/build/libs" / jar_name).read_bytes()
    with zipfile.ZipFile(io.BytesIO(mod)) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "celestial_dragon" or metadata["version"] != VERSION:
            raise SystemExit("O JAR não é a versão correta do dragão.")
        required = ["br/com/celestialdragon/entity/CelestialDragonEntity.class",
                    "br/com/celestialdragon/client/DragonModel.class",
                    "br/com/celestialdragon/client/DragonSettingsScreen.class",
                    "assets/celestial_dragon/textures/entity/celestial_dragon.png",
                    "data/celestial_dragon/worldgen/biome/celestial_grove.json",
                    "data/celestial_dragon/worldgen/structure/dragon_nest.json",
                    "data/celestial_dragon/recipe/celestial_incubator.json"]
        for path in required:
            if path not in archive.namelist():
                raise SystemExit("Recurso obrigatório ausente: " + path)
        sounds = json.loads(archive.read("assets/celestial_dragon/sounds.json"))
        for key in ["dragon.roar", "dragon.bite", "dragon.beam", "dragon.hurt", "dragon.death", "biome.ambience"]:
            if key not in sounds:
                raise SystemExit("Som obrigatório ausente: " + key)
            for sound in sounds[key]["sounds"]:
                sound = sound if isinstance(sound, str) else sound["name"]
                namespace, path = sound.split(":", 1) if ":" in sound else ("minecraft", sound)
                if f"assets/{namespace}/sounds/{path}.ogg" not in archive.namelist():
                    raise SystemExit("Arquivo de som ausente: " + sound)
        if any("gametest" in n.lower() for n in archive.namelist()):
            raise SystemExit("O mod instalável contém testes de desenvolvimento.")
        if archive.testzip() is not None:
            raise SystemExit("Falha de CRC no JAR.")

    entries = {
        "mods/" + jar_name: mod,
        "mods/fabric-api-0.161.0+26.2.jar": fabric,
        "COMECE-AQUI.txt": (ROOT / "dragon-mod/COMECE-AQUI.txt").read_bytes(),
        "docs/INSTALAR-DRAGAO.md": (ROOT / "docs/INSTALAR-DRAGAO.md").read_bytes(),
        "docs/TESTES-DRAGAO.md": (ROOT / "docs/TESTES-DRAGAO.md").read_bytes(),
        "README.md": (ROOT / "dragon-mod/README.md").read_bytes(),
        "LICENSE": (ROOT / "LICENSE").read_bytes(),
        "licenses/LICENSE-fabric-api.txt": fabric_license,
        "THIRD-PARTY-NOTICES.txt": (
            "Dragão Celestial: código, modelo, texturas e sons originais deste projeto, licença MIT (LICENSE).\n\n"
            "Fabric API 0.161.0+26.2: FabricMC contributors, licença Apache-2.0.\n"
            "Licença preservada em licenses/LICENSE-fabric-api.txt e dentro do JAR oficial.\n"
            "Fonte: https://github.com/FabricMC/fabric\n"
            "Distribuição: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.2/\n"
            "O Fabric Loader e o Minecraft são instalados separadamente. Minecraft não está incluído.\n"
        ).encode("utf-8"),
    }
    for path in sorted((ROOT / "docs/dragao").glob("*")):
        if path.is_file():
            entries["docs/dragao/" + path.name] = path.read_bytes()
    entries["SHA256SUMS.txt"] = "".join(f"{sha(value)}  {key}\n" for key, value in sorted(entries.items())).encode()
    output = ROOT / "downloads" / NAME
    temporary = output.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for key, value in sorted(entries.items()):
            info = zipfile.ZipInfo(key, date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, value)
    with zipfile.ZipFile(temporary) as archive:
        if archive.testzip() is not None:
            raise SystemExit("Falha de CRC no pacote final.")
    temporary.replace(output)
    (ROOT / "downloads/SHA256SUMS.txt").write_text("".join(
        f"{sha(p.read_bytes())}  {p.name}\n" for p in sorted((ROOT / "downloads").glob("*.zip"))), encoding="utf-8")
    print(f"Pacote verificado: {NAME}; {output.stat().st_size} bytes; SHA-256 {sha(output.read_bytes())}")


if __name__ == "__main__":
    main()
