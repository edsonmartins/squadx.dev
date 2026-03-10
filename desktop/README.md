# SquadX Desktop

Tauri v2 desktop wrapper for the SquadX Next.js frontend.

## Prerequisites

- [Rust](https://rustup.rs/) (stable toolchain)
- [Node.js](https://nodejs.org/) (v18+)
- [pnpm](https://pnpm.io/)
- Platform-specific dependencies:
  - **macOS**: Xcode Command Line Tools (`xcode-select --install`)
  - **Linux**: `sudo apt install libwebkit2gtk-4.1-dev build-essential curl wget file libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev`
  - **Windows**: [Microsoft Visual Studio C++ Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/), WebView2 (pre-installed on Windows 11)

## Setup

```bash
# Install JS dependencies for the desktop wrapper
cd desktop
pnpm install

# Start the frontend dev server (in a separate terminal)
cd ../frontend
pnpm dev

# Run the desktop app in development mode
cd ../desktop
pnpm tauri:dev
```

## Build for Production

```bash
# First, build the Next.js static export
cd frontend
pnpm build

# Then build the desktop app
cd ../desktop
pnpm tauri:build
```

The built application will be in `desktop/src-tauri/target/release/bundle/`.
