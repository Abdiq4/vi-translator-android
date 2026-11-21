"""Takes what the phone app shares with the desktop app's interface (vi-translator-desktop's ui/):

  ui/i18n/<code>.js  ->  app/src/main/assets/i18n/<code>.json   the interface's phrases, by their English
  ui/i18n.js         ->  app/src/main/assets/i18n/languages.json the interface languages (code, name, rtl)
  ui/fonts/*.ttf     ->  app/src/main/assets/fonts/              the fonts bundled with the app (OFL)

so the phone's screens say exactly what the desktop's say, in all its languages, with its fonts.
Run it from the repository's root after moving native/core.ref to a newer desktop commit:

  python tools/sync_desktop_ui.py <path to vi-translator-desktop>
"""
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"


def js_object(text: str) -> dict:
    """The object literal of `I18N.add("xx", {...});`: JSON once trailing commas are gone."""
    start = text.index("{")
    end = text.rindex("}")
    # (whole-line // comments: no phrase starts a line with them)
    body = "\n".join(l for l in text[start : end + 1].splitlines() if not l.lstrip().startswith("//"))
    body = re.sub(r",(\s*[}\]])", r"\1", body)
    return json.loads(body)


def main() -> None:
    desktop = Path(sys.argv[1]).resolve()
    ui = desktop / "ui"
    out = ASSETS / "i18n"
    out.mkdir(parents=True, exist_ok=True)
    for js in sorted((ui / "i18n").glob("*.js")):
        code = js.stem
        d = js_object(js.read_text(encoding="utf-8"))
        (out / f"{code}.json").write_text(json.dumps(d, ensure_ascii=False, indent=0, sort_keys=True), encoding="utf-8")
    # the language list in i18n.js: { code: "ar", name: "العربية", rtl: true },
    langs = [
        {"code": m.group(1), "name": m.group(2), "rtl": m.group(3) == "true"}
        for m in re.finditer(r'\{\s*code:\s*"([^"]+)",\s*name:\s*"([^"]+)",\s*rtl:\s*(true|false)\s*\}', (ui / "i18n.js").read_text(encoding="utf-8"))
    ]
    (out / "languages.json").write_text(json.dumps(langs, ensure_ascii=False, indent=1), encoding="utf-8")
    fonts = ASSETS / "fonts"
    fonts.mkdir(parents=True, exist_ok=True)
    for f in sorted((ui / "fonts").iterdir()):
        if f.suffix.lower() in (".ttf", ".txt"):
            shutil.copy2(f, fonts / f.name)
    print(f"{len(langs)} languages, {len(list(out.glob('*.json'))) - 1} dictionaries, fonts in {fonts}")


if __name__ == "__main__":
    main()
