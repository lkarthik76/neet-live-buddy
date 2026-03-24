# App icon source

- **`app_icon_source_1024.png`** — master 1024×1024 RGBA (transparent outside the icon where the source had a flat canvas).

## Update from `docs/images/` (Copilot export)

Canonical app-icon source: **`docs/images/Copilot_20260323_103308.png`** (1024² square; dark canvas removed on import).

```bash
cd kmp-app
python3 scripts/import_icon_from_docs.py
python3 scripts/regenerate_app_icons.py
```

To use a different export, pass its path as the first argument. Wide logos are letterboxed; very wide+tall PNGs are treated as two-up mockups.

## Regenerate only (after hand-editing the master)

```bash
cd kmp-app
python3 scripts/regenerate_app_icons.py
```

**Icon scale:** Foreground art uses **72%** of the adaptive layer; legacy mipmaps **80%**, on launcher background **`#FFFFFF`**. Edit `ADAPTIVE_ARTWORK_FRACTION` / `LEGACY_MIPMAP_ARTWORK_FRACTION` in `scripts/regenerate_app_icons.py` if you need smaller (safer masks) vs larger.

**Launcher label length:** Use short `app_name` in `res/values/strings.xml`; full name stays in the in-app header.

Outputs: Android `mipmap-*`, `drawable-*/ic_launcher_foreground.png`, `../ios-app-icon/` (9 iPhone + App Store PNGs; see `../ios-app-icon/README.md`), `../playstore_icon_512.png`.
