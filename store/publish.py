#!/usr/bin/env python3
"""Loja de widgets HTML do Launcher.

Publica um widget enviado por issue (rodado pelo GitHub Action quando a issue recebe a etiqueta
"aprovado") e remonta o catálogo `store/index.json`, que o app baixa.

Uso:
  ISSUE_BODY=... ISSUE_NUMBER=12 ISSUE_AUTHOR=fulano python3 store/publish.py
  python3 store/publish.py --rebuild     # só remonta o index.json a partir de store/widgets/
"""
import datetime
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.abspath(__file__))
WIDGETS = os.path.join(ROOT, "widgets")
INDEX = os.path.join(ROOT, "index.json")
ROLES = {"DESKTOP", "LOCK", "APPS", "DOCK"}
MAX_HTML = 200_000


def slug(text):
    text = unicodedata.normalize("NFD", text).encode("ascii", "ignore").decode()
    text = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return text[:40] or "widget"


def clean(text, limit):
    return re.sub(r"\s+", " ", str(text or "")).strip()[:limit]


def parse_issue(body):
    """O app manda o widget num bloco ```json ... ``` dentro da issue."""
    match = re.search(r"```(?:json)?\s*(\{.*\})\s*```", body or "", re.S)
    if not match:
        raise ValueError("Não achei o bloco ```json``` com o widget na issue.")
    data = json.loads(match.group(1))
    html = data.get("html")
    if not isinstance(html, str) or not html.strip():
        raise ValueError("O widget não tem código (campo html).")
    if len(html) > MAX_HTML:
        raise ValueError(f"O código passa de {MAX_HTML // 1000} KB.")
    role = str(data.get("role", "DESKTOP")).upper()
    if role not in ROLES:
        role = "DESKTOP"
    name = clean(data.get("name"), 60) or "Widget"
    return {
        "name": name,
        "author": clean(data.get("author"), 40),
        "description": clean(data.get("description"), 280),
        "role": role,
        "interactive": bool(data.get("interactive", "<script" in html)),
        "network": bool(data.get("network", False)),
        "html": html,
    }


def publish(body, number, issue_author):
    widget = parse_issue(body)
    if not widget["author"]:
        widget["author"] = issue_author or "anônimo"
    widget["id"] = f"{slug(widget['name'])}-{number}"
    widget["issue"] = int(number)
    widget["updated"] = datetime.date.today().isoformat()
    os.makedirs(WIDGETS, exist_ok=True)
    with open(os.path.join(WIDGETS, widget["id"] + ".json"), "w", encoding="utf-8") as f:
        json.dump(widget, f, ensure_ascii=False, indent=2)
        f.write("\n")
    rebuild()
    return widget


def rebuild():
    """O índice traz tudo (inclusive o código), para o app mostrar as prévias de uma vez."""
    items = []
    for name in sorted(os.listdir(WIDGETS)):
        if name.endswith(".json"):
            with open(os.path.join(WIDGETS, name), encoding="utf-8") as f:
                items.append(json.load(f))
    items.sort(key=lambda w: (w.get("updated", ""), w.get("name", "")), reverse=True)
    with open(INDEX, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "widgets": items}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    return items


if __name__ == "__main__":
    if "--rebuild" in sys.argv:
        print(f"{len(rebuild())} widgets no índice")
        sys.exit(0)
    try:
        w = publish(os.environ.get("ISSUE_BODY", ""), os.environ.get("ISSUE_NUMBER", "0"), os.environ.get("ISSUE_AUTHOR", ""))
    except (ValueError, json.JSONDecodeError) as e:
        print(f"ERRO: {e}")
        with open(os.environ.get("GITHUB_OUTPUT", os.devnull), "a") as out:
            out.write(f"error={e}\n")
        sys.exit(1)
    print(f"Publicado: {w['id']}")
    with open(os.environ.get("GITHUB_OUTPUT", os.devnull), "a") as out:
        out.write(f"id={w['id']}\nname={w['name']}\n")
