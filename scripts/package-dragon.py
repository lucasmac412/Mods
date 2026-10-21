#!/usr/bin/env python3
"""Build the installation ZIP from the tested dragon JAR and verified official Fabric API."""
from pathlib import Path
import hashlib
import io
import json
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = "0.3.4-experimental"
NAME = "Dragao-Celestial-Fabric-26.2-v0.3.4-Experimental.zip"
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
                    "br/com/celestialdragon/entity/CelestialOrbEntity.class",
                    "br/com/celestialdragon/client/SpiritOrbRenderer.class",
                    "br/com/celestialdragon/client/DragonEnergyGeometry.class",
                    "br/com/celestialdragon/mixin/client/DragonEnergyFogMixin.class",
                    "br/com/celestialdragon/mixin/client/DragonCameraMixin.class",
                    "br/com/celestialdragon/mixin/world/DragonRidingMixin.class",
                    "br/com/celestialdragon/entity/DragonAttackAnimation.class",
                    "br/com/celestialdragon/client/DragonModel.class",
                    "br/com/celestialdragon/client/DragonGeometry.class",
                    "br/com/celestialdragon/client/DragonAnimation.class",
                    "br/com/celestialdragon/client/DragonSettingsScreen.class",
                    "br/com/celestialdragon/content/DragonCreativeTab.class",
                    "br/com/celestialdragon/content/DragonSpawnEggItem.class",
                    "META-INF/licenses/dragon-audio/CREDITS.txt",
                    "META-INF/licenses/dragon-audio/CC-BY-SA-3.0.txt",
                    "assets/celestial_dragon/items/wild_dragon_spawn_egg.json",
                    "assets/celestial_dragon/items/tamed_dragon_spawn_egg.json",
                    "assets/celestial_dragon/models/item/wild_dragon_spawn_egg.json",
                    "assets/celestial_dragon/models/item/tamed_dragon_spawn_egg.json",
                    "assets/celestial_dragon/textures/entity/celestial_dragon.png",
                    "assets/celestial_dragon/textures/block/celestial_lantern_side.png",
                    "assets/celestial_dragon/textures/block/celestial_lantern_top.png",
                    "assets/celestial_dragon/textures/block/celestial_lantern_bottom.png",
                    "data/celestial_dragon/worldgen/biome/celestial_grove.json",
                    "data/celestial_dragon/worldgen/structure/dragon_nest.json",
                    "data/celestial_dragon/recipe/celestial_incubator.json"]
        for material in ["celestial_bricks", "celestial_runestone", "celestial_obsidian", "celestial_pillar", "celestial_lantern"]:
            required.extend([f"assets/celestial_dragon/models/block/{material}.json",
                             f"assets/celestial_dragon/blockstates/{material}.json",
                             f"assets/celestial_dragon/items/{material}.json",
                             f"data/celestial_dragon/loot_table/blocks/{material}.json"])
        for path in required:
            if path not in archive.namelist():
                raise SystemExit("Recurso obrigatório ausente: " + path)
        lantern = json.loads(archive.read("assets/celestial_dragon/models/block/celestial_lantern.json"))
        expected_lantern = {face: f"celestial_dragon:block/celestial_lantern_{face}"
                            for face in ("side", "top", "bottom")}
        if lantern.get("textures") != expected_lantern:
            raise SystemExit("A lanterna rúnica precisa usar suas três texturas próprias.")
        sounds = json.loads(archive.read("assets/celestial_dragon/sounds.json"))
        for key in ["dragon.roar", "dragon.bite", "dragon.beam", "dragon.hurt", "dragon.death", "biome.ambience", "dragon.breath", "dragon.flight", "dragon.beam_charge", "dragon.spirit_charge", "dragon.spirit_launch", "dragon.spirit_impact"]:
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
            "Dragão Celestial: código, modelo e texturas do projeto, licença MIT (LICENSE).\n"
            "Áudio 0.3.4: CC BY-SA 3.0, adaptado de gravações de Michael Klier e de rugido de leão em domínio público.\n"
            "Créditos, mudanças, fontes e licença integral: licenses/dragon-audio/.\n\n"
            "Os modelos de blocos também referenciam texturas fornecidas pelo próprio Minecraft; esses arquivos do jogo não são redistribuídos neste pacote.\n\n"
            "Fabric API 0.161.0+26.2: FabricMC contributors, licença Apache-2.0.\n"
            "Licença preservada em licenses/LICENSE-fabric-api.txt e dentro do JAR oficial.\n"
            "Fonte: https://github.com/FabricMC/fabric\n"
            "Distribuição: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.2/\n"
            "O Fabric Loader e o Minecraft são instalados separadamente. Minecraft não está incluído.\n"
        ).encode("utf-8"),
    }
    for name in ["CREDITS.txt", "CC-BY-SA-3.0.txt", "sources.json"]:
        entries["licenses/dragon-audio/" + name] = (ROOT / "third-party/dragon-audio" / name).read_bytes()
    entries["OUVIR-SONS.html"] = (ROOT / "dragon-mod/preview-sounds.html").read_bytes()
    for sound in sorted((ROOT / "dragon-mod/src/main/resources/assets/celestial_dragon/sounds").glob("*.ogg")):
        entries["sons/" + sound.name] = sound.read_bytes()
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
