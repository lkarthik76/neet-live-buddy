# Android resources (`androidMain/res`)

| Path | Role |
|------|------|
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon (API 26+). |
| `mipmap-*/ic_launcher.png` | Legacy launcher bitmaps (pre–adaptive). |
| `drawable-*/ic_launcher_foreground.png` | Adaptive foreground layers + splash (`Theme.SmartStudyBuddy.Splash`). |
| `values/ic_launcher_background.xml` | Adaptive icon background color (`#FFFFFF`). |
| `values/splash_theme.xml` | Splash screen theme. |
| `values/strings.xml` | `app_name` (short launcher label). |

**Regenerate** mipmaps and drawables from `kmp-app/design-assets/app_icon_source_1024.png`:

```bash
cd kmp-app && python3 scripts/regenerate_app_icons.py
```

`android:roundIcon` uses the same `@mipmap/ic_launcher` as `android:icon` (no duplicate `ic_launcher_round` assets).
