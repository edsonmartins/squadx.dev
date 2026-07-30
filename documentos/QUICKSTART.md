# 🚀 SquadX Live - Quickstart

## For Product Managers

### Create Your First Live Session

1. **Login to SquadX Dashboard**
   - Go to https://squadx.dev
   - Login with your account

2. **Create a Task**
   - Click "+ New Task"
   - Fill: "Implement user login"
   - Assign to "Backend Agent"
   - Click "Create"

3. **Watch Live**
   - Wait ~30s for agent to start
   - Click "🎥 Watch Live" when available
   - See agent coding in real-time!

4. **Invite Team**
   - Click "Invite" in live session
   - Share link with team
   - Everyone sees same screen

## For Developers

### Install SquadX Client (Dev LIGHT — Mac)

Supported path today (monorepo checkout, ADR-0009):

```bash
./scripts/install-mac-client.sh
source ~/.squadx/env.sh
squadx-client doctor
squadx-client start -f
```

Details: [DEV-LIGHT-MAC.md](./DEV-LIGHT-MAC.md). Homebrew formula is placeholder until a release tag ships.

### Team DOCKER (Linux VPS)

```bash
./scripts/install-vps.sh --pull-images
# client/deploy/README.md
```

### Start Client
```bash
source ~/.squadx/env.sh   # Mac Dev LIGHT
squadx-client start -f
```

### Your First Live Session
```bash
# Client will auto-start live view
# when PM requests it
# Check system tray for notifications
```

## For Viewers

### Join a Live Session

1. **Click the Link**
   - PM sends: https://live.squadx.dev/ABC123
   - Click to join

2. **Watch Agent Work**
   - See screen in real-time
   - Use chat to discuss
   - Add annotations

3. **Request Control** (if enabled)
   - Click "Request Control"
   - Wait for PM approval
   - You can now drive!

## Troubleshooting

**No live view available?**
- Check client is running
- Task must be "In Progress"
- Wait 30s for container to start

**Latency too high?**
- Check network connection
- Lower quality to SD
- Close other video apps

**Can't join session?**
- Session might be full
- Check permissions
- Try refreshing page

## Next Steps

- Read [ARCHITECTURE.md](ARCHITECTURE.md)
- See [UI-WIREFRAMES.md](UI-WIREFRAMES.md)
- Review [ROADMAP.md](ROADMAP.md)
