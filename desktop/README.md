# SquadX Desktop

Tauri v2 desktop application that wraps the SquadX Next.js frontend in a native WebView, providing a native desktop experience for AI-powered dev squad management.

## Architecture

The desktop app uses [Tauri v2](https://v2.tauri.app/) to embed the Next.js frontend into a lightweight native window. In development, it proxies to the Next.js dev server (`http://localhost:3000`). For production builds, it bundles the static export from `frontend/out/`.

Custom Rust commands (`greet`, `get_app_version`, `get_system_info`) are exposed to the frontend via the Tauri IPC bridge.

## Prerequisites

- [Rust](https://rustup.rs/) (stable toolchain)
- [Node.js](https://nodejs.org/) 20+
- [pnpm](https://pnpm.io/) 9+

### Platform-Specific Dependencies

**macOS:**
```bash
xcode-select --install
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt install libwebkit2gtk-4.1-dev build-essential curl wget file \
  libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev
```

**Windows:**
- [Microsoft Visual Studio C++ Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/)
- WebView2 (pre-installed on Windows 10/11)

## Development

```bash
# Install dependencies
cd desktop
pnpm install

# Run in development mode (starts Next.js dev server + Tauri window)
pnpm tauri:dev
```

The `beforeDevCommand` in `tauri.conf.json` automatically starts the Next.js dev server.

## Production Build

```bash
cd desktop
pnpm tauri:build
```

The `beforeBuildCommand` runs `pnpm build && pnpm export` in the frontend directory. The bundled application is output to `src-tauri/target/release/bundle/`.

## Icons

Generate application icons from a 1024x1024 PNG source:

```bash
pnpm tauri:icon path/to/icon-1024x1024.png
```

See `src-tauri/icons/README.md` for details.

## Custom Commands

The Rust backend exposes these IPC commands to the frontend:

| Command | Description |
|---------|-------------|
| `greet(name)` | Returns a greeting string (test command) |
| `get_app_version()` | Returns the app version from Cargo.toml |
| `get_system_info()` | Returns OS, architecture, and memory info |

Invoke from the frontend using `@tauri-apps/api`:

```typescript
import { invoke } from "@tauri-apps/api/core";

const version = await invoke<string>("get_app_version");
```
