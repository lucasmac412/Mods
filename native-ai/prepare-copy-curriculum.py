#!/usr/bin/env python3
"""Teach the own model to use a supplied note despite changes to the question.

All recombined questions/notes come from training, never held-out references.
This trains note-based generation, not knowledge acquired solely in weights.
"""
from pathlib import Path
import hashlib
import json
import random
import re
import shutil
import unicodedata

BASE = Path(__file__).resolve().parent
SOURCE = BASE / "data-v2-grounded"
TARGET = BASE / "data-v2-copy"


def normalized(text):
    plain = "".join(c for c in unicodedata.normalize("NFD", text)
                    if not unicodedata.category(c).startswith("M")).lower()
    return re.sub(r"\s+", " ", "".join(c if unicodedata.category(c)[0] in "LN" else " " for c in plain)).strip()


def main():
    rng = random.Random(412)
    original = [tuple(line.split("\t")) for line in (BASE / "data-v2/train.tsv").read_text().splitlines()]
    prepared = [tuple(line.split("\t")) for line in (SOURCE / "train.tsv").read_text().splitlines()]
    # Use one row for each authored answer, not each prefix augmentation.
    answers = list(dict.fromkeys(answer for _, answer in original))
    questions = list(dict.fromkeys(question for question, _ in original))
    result = list(prepared)
    seen = {normalized(question) for question, _ in result}
    def add(question, answer):
        key = normalized(question)
        if key not in seen:
            result.append((question, answer)); seen.add(key)
    for answer in answers:
        for question in rng.sample(questions, 12):
            add("Pergunta: " + question + " | Informação: " + answer, answer)
    code = (BASE / "src/main/java/br/com/luma/nativeai/LocalMemory.java").read_text()
    unknown = re.search(r'public static final String UNKNOWN = "([^"\\]*)";', code).group(1)
    for question in rng.sample(questions, 128):
        add("Pergunta: " + question + " | Informação: " + unknown,
            "Não tenho informação suficiente para responder. Pode explicar melhor a pergunta?")
    TARGET.mkdir(exist_ok=True)
    (TARGET / "train.tsv").write_text("".join(q + "\t" + a + "\n" for q, a in result), encoding="utf-8")
    # The held-out columns are copied without inspecting/printing their contents.
    for name in ["validation.tsv", "test.tsv", "knowledge.tsv"]:
        shutil.copyfile(SOURCE / name, TARGET / name)
    metadata = {
        "purpose": "Neural generation from supplied training-only notes with varied question contexts",
        "training_only_recombined_contexts": True,
        "seed": 412, "base_grounded_rows": len(prepared), "unique_authored_answers": len(answers),
        "training_rows": len(result), "unknown_note_examples": 128,
        "new_factual_knowledge_from_recombination": False,
        "held_out_replies_used_in_training": False,
        "sha256": {name: hashlib.sha256((TARGET / name).read_bytes()).hexdigest()
                   for name in ["train.tsv", "validation.tsv", "test.tsv", "knowledge.tsv"]}
    }
    (TARGET / "curriculum.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in metadata.items() if k != "sha256"}))


if __name__ == "__main__":
    main()
