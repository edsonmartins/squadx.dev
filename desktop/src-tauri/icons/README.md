# SquadX Desktop Icons

This directory should contain the application icons in multiple sizes.

## Generating Icons

Place a source icon as a 1024x1024 PNG file (e.g., `app-icon.png`) in this directory, then run:

```bash
cd desktop
pnpm tauri icon src-tauri/icons/app-icon.png
```

This will automatically generate all required icon sizes:
- `32x32.png`
- `128x128.png`
- `128x128@2x.png`
- `icon.icns` (macOS)
- `icon.ico` (Windows)

## Notes

- The source image should be square and at least 1024x1024 pixels.
- Use a transparent background for best results across platforms.
- The `tauri icon` command requires the `@tauri-apps/cli` package to be installed.
