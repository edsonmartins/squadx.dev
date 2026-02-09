# 🔧 SquadX Live - Integration Guide

## Quick Start (15 minutes)

### 1. Fork PairUX
```bash
git clone https://github.com/profullstack/pairux.com.git
cd pairux.com
git remote add upstream https://github.com/profullstack/pairux.com.git
git checkout -b squadx-integration
```

### 2. Rename to SquadX Live
```bash
# Update package.json
sed -i 's/pairux/squadx-live/g' package.json
sed -i 's/PairUX/SquadX Live/g' apps/*/package.json

# Update branding
# - Logo: public/logo.png
# - Colors: tailwind.config.js (brand colors)
# - Name: All UI strings
```

### 3. Setup Development
```bash
pnpm install
pnpm dev
# Opens http://localhost:3000
```

### 4. Test Multi-Viewer
```bash
# Terminal 1: Start host
pnpm --filter @squadx-live/desktop dev

# Browser 1: Create session (host)
# Browser 2: Join session (viewer 1)
# Browser 3: Join session (viewer 2)
```

## Backend Integration

### 1. Add Live View Endpoints
```python
# backend/app/api/v1/live_view.py
# (See ARCHITECTURE.md for full code)

@router.post("/sessions")
async def create_live_session(task_id: int):
    # Create session
    # Send WebSocket to client
    pass
```

### 2. WebSocket Events
```python
# backend/app/websocket.py

@sio.on("start_live_view")
async def handle_start(sid, data):
    # Client starts live session
    pass

@sio.on("live_view_ready")
async def handle_ready(sid, data):
    # Session URL ready
    # Notify frontend
    pass
```

## Client Integration

### 1. Add LiveViewManager
```python
# squadx-client/src/live_view/manager.py

class LiveViewManager:
    async def start_session(self, config):
        # Start SquadX Live Host
        # Connect to VNC
        # Create WebRTC session
        pass
```

### 2. Docker Agent Setup
```dockerfile
# Add Xvfb + x11vnc to agent image
RUN apt-get install -y xvfb x11vnc fluxbox
ENV DISPLAY=:99
CMD ["/start-agent.sh"]
```

## Testing Checklist

- [ ] Single viewer works
- [ ] Multi-viewer (3+) works
- [ ] Chat sync works
- [ ] Annotations visible to all
- [ ] Latency <300ms
- [ ] No crashes after 1h session
- [ ] Recording saves properly
- [ ] Mobile PWA works

## Deployment

### Development
```bash
docker-compose up -d
```

### Production
```bash
# Deploy to Vercel/AWS
# See docs/DEPLOYMENT.md
```
