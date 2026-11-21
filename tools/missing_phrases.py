"""Lists the interface's phrases (T("...") in the app's screens, and the desktop's lists they use)
that neither the desktop's dictionary nor the phone's own has, for a language (default: ar).

  python tools/missing_phrases.py [code]
"""
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"


def main() -> None:
    code = sys.argv[1] if len(sys.argv) > 1 else "ar"
    have = json.loads((ASSETS / "i18n" / f"{code}.json").read_text(encoding="utf-8"))
    phone = ASSETS / "i18n-phone" / f"{code}.json"
    if phone.exists():
        have.update(json.loads(phone.read_text(encoding="utf-8")))
    src = "".join(Path(f).read_text(encoding="utf-8") for f in glob.glob(str(ROOT / "app/src/main/kotlin/app/vitranslator/phone/ui/*.kt")))
    phrases = set()
    for m in re.finditer(r'T\(\s*"((?:[^"\\]|\\.)*)"', src):
        phrases.add(m.group(1).encode().decode("unicode_escape").encode("latin-1").decode("utf-8"))
    missing = sorted(p for p in phrases if p not in have)
    print(json.dumps(missing, ensure_ascii=False, indent=1))
    print(f"{len(phrases)} phrases, {len(missing)} missing for {code}", file=sys.stderr)


if __name__ == "__main__":
    main()
