"""Build the standalone AI authoring kit from tracked sources."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
skill = ROOT / ".agents/skills/music-widget-designer"
output = ROOT / "dist/music-widget-ai-kit.zip"
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(skill.rglob("*")):
        if path.is_file() and "__pycache__" not in path.parts:
            archive.write(path, path.relative_to(skill).as_posix())
    archive.write(ROOT / "docs/ai-kit-start.md", "README.md")
    archive.write(ROOT / "requirements.txt", "requirements.txt")
    archive.writestr("AGENTS.md", "Read README.md and SKILL.md before creating a widget theme.\n")
    for path in sorted((ROOT / "themes").rglob("*")):
        if path.is_file():
            archive.write(path, "examples/" + path.relative_to(ROOT / "themes").as_posix())
print(output)
