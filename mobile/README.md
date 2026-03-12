# SquadX Mobile

React Native (Expo) mobile application for SquadX.dev.

## Prerequisites

- Node.js 18+
- [Expo CLI](https://docs.expo.dev/get-started/installation/) (`npx expo`)
- iOS Simulator (macOS) or Android Emulator
- Expo Go app on a physical device (optional)

## Setup

```bash
cd mobile
npm install
```

### Environment

Create a `.env` file in this directory (or set the variable via EAS):

```
EXPO_PUBLIC_API_URL=http://localhost:8080
```

When testing on a physical device, replace `localhost` with your machine's LAN IP address.

## Running

```bash
# Start the Expo development server
npm start

# iOS
npm run ios

# Android
npm run android
```

## Project Structure

```
mobile/
  app/
    _layout.tsx          # Root layout with auth guard
    (auth)/
      login.tsx          # Login screen
    (tabs)/
      _layout.tsx        # Tab navigator
      index.tsx          # Dashboard tab
      tasks.tsx          # Tasks list
      live.tsx           # Live sessions + join
      settings.tsx       # Profile, notifications, logout
    live/
      [code].tsx         # Live session view (WebRTC + chat)
  lib/
    api.ts               # API client with AsyncStorage token management
    auth.ts              # Auth context and provider
  app.json               # Expo configuration
  package.json
  tsconfig.json
```

## Notes

- Authentication tokens are persisted via `@react-native-async-storage/async-storage`.
- The app uses `expo-router` for file-based routing (same mental model as Next.js).
- WebRTC support is provided by `react-native-webrtc` for the live session screen.
- Push notifications use `expo-notifications`.
- The UI uses plain React Native components with a dark-navy theme matching the web app.
