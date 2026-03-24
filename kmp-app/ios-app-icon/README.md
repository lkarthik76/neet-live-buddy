# iOS app icons (iPhone + App Store)

These PNGs map to the usual **Xcode → AppIcon** slots for **iPhone** (and Apple Watch notification/settings where sizes overlap). **iPad-only** sizes were dropped to reduce clutter; add them in Xcode if you target iPad.

| File | Points | Pixels | Use |
|------|--------|--------|-----|
| `icon-20@2x.png` | 20 @2x | 40 | Notifications |
| `icon-20@3x.png` | 20 @3x | 60 | Notifications |
| `icon-29@2x.png` | 29 @2x | 58 | Settings |
| `icon-29@3x.png` | 29 @3x | 87 | Settings |
| `icon-40@2x.png` | 40 @2x | 80 | Spotlight |
| `icon-40@3x.png` | 40 @3x | 120 | Spotlight |
| `icon-60@2x.png` | 60 @2x | 120 | Home screen |
| `icon-60@3x.png` | 60 @3x | 180 | Home screen |
| `icon-1024.png` | — | 1024 | App Store |

**Regenerate** from `design-assets/app_icon_source_1024.png`:

```bash
cd kmp-app && python3 scripts/regenerate_app_icons.py
```

You cannot safely remove more without Xcode warnings or missing slots; Apple expects these roles for a standard iPhone app icon set.
