#!/usr/bin/env python3
"""Rebuild the synthetic v2 training split; never use held-out replies for training."""
from pathlib import Path
import hashlib
import json
import re
import unicodedata

BASE = Path(__file__).resolve().parent


def key(text):
    normalized = "".join(c for c in unicodedata.normalize("NFD", text)
                         if not unicodedata.category(c).startswith("M")).lower()
    letters_and_numbers = "".join(c if unicodedata.category(c)[0] in "LN" else " "
                                 for c in normalized)
    return re.sub(r"\s+", " ", letters_and_numbers).strip()


def rows(path):
    result = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or any(not part.strip() or len(part) > 1000 for part in parts):
            raise ValueError(f"Invalid TSV at {path.name}:{number}")
        result.append(tuple(part.strip() for part in parts))
    return result


def main():
    destination = BASE / "data-v2"
    # Only prompt identities are used to prevent exact collisions. Held-out answers
    # never enter the training rows, tokenizer or augmentation.
    held_out = {key(prompt) for split in ["validation.tsv", "test.tsv"]
                for prompt, _ in rows(destination / split)}
    sources = [destination / "base.tsv", destination / "conversation.tsv", BASE / "data/train.tsv"]
    source_rows = {path.relative_to(BASE).as_posix(): rows(path) for path in sources}
    originals, seen = [], set(held_out)
    excluded = 0
    for examples in source_rows.values():
        for prompt, answer in examples:
            normalized = key(prompt)
            if normalized in seen:
                excluded += 1
                continue
            originals.append((prompt, answer))
            seen.add(normalized)
    examples = list(originals)
    for prompt, answer in originals:
        for variant in ["Luma, " + prompt, "Me ajuda: " + prompt]:
            normalized = key(variant)
            if normalized not in seen:
                examples.append((variant, answer))
                seen.add(normalized)
    target = destination / "train.tsv"
    target.write_text("".join(f"{prompt}\t{answer}\n" for prompt, answer in examples), encoding="utf-8")
    metadata = {
        "authorship": "Synthetic texts written by Codex; no downloaded corpus or pretrained weights",
        "source_rows": {name: len(items) for name, items in source_rows.items()},
        "distinct_base_pairs": len(originals), "training_pairs": len(examples),
        "excluded_duplicate_or_held_out_prompts": excluded,
        "augmentation": ["original", "Luma, <original prompt>", "Me ajuda: <original prompt>"],
        "augmentation_is_new_knowledge": False,
        "held_out_replies_used_for_training": False,
        "sha256": {split: hashlib.sha256((destination / split).read_bytes()).hexdigest()
                   for split in ["train.tsv", "validation.tsv", "test.tsv"]},
    }
    (destination / "manifest.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in metadata.items() if k not in ["sha256", "augmentation"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
