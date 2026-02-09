# 🎨 SquadX Live - Wireframes e Design da UI

**Mockups detalhados da interface do usuário**

---

## 1. Dashboard Principal

### 1.1 Kanban Board com Live View Indicators

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  SquadX - E-commerce Platform Project                    [João Silva ▼] [⚙️]  │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  ┌──────────────────────┐  Filters: [All] [In Progress] [Live] [My Tasks]    │
│  │  🔍 Search tasks...  │  Sort: [Priority ▼]                                 │
│  └──────────────────────┘                                                      │
│                                                                                │
│  ┌────────────┬──────────────────┬──────────────────┬────────────────────┐   │
│  │  📋 TODO   │  ⚙️ IN PROGRESS  │  👀 IN REVIEW    │  ✅ DONE           │   │
│  │  (8 tasks) │  (4 tasks)       │  (2 tasks)       │  (12 tasks)        │   │
│  ├────────────┼──────────────────┼──────────────────┼────────────────────┤   │
│  │            │                  │                  │                    │   │
│  │┌──────────┐│  ┌────────────┐  │  ┌────────────┐  │  ┌──────────────┐ │   │
│  ││Task #101 ││  │ Task #99   │  │  │ Task #87   │  │  │  Task #56    │ │   │
│  ││          ││  │            │  │  │            │  │  │              │ │   │
│  ││Login UI  ││  │ JWT Auth   │  │  │ Checkout   │  │  │ Search       │ │   │
│  ││          ││  │            │  │  │ Flow       │  │  │ Feature      │ │   │
│  ││@Maria    ││  │ @João      │  │  │ @Pedro     │  │  │              │ │   │
│  ││🔴 High   ││  │            │  │  │            │  │  │ ✅ Approved  │ │   │
│  │└──────────┘│  │            │  │  │ Maria R.   │  │  └──────────────┘ │   │
│  │            │  │ ┌────────┐ │  │  └────────────┘  │                    │   │
│  │            │  │ │●●●●●○○│ │  │                  │                    │   │
│  │            │  │ │  75%  │ │  │                  │                    │   │
│  │            │  │ └────────┘ │  │                  │                    │   │
│  │            │  │            │  │                  │                    │   │
│  │            │  │ Agent:     │  │                  │                    │   │
│  │            │  │ Backend    │  │                  │                    │   │
│  │            │  │            │  │                  │                    │   │
│  │            │  │ ┌───────────────────────────┐    │                    │   │
│  │            │  │ │ 🔴 LIVE - 3 watching      │    │                    │   │
│  │            │  │ │                           │    │                    │   │
│  │            │  │ │ [●] [●] [●] +2 more       │    │                    │   │
│  │            │  │ │ João, Maria, Pedro...     │    │                    │   │
│  │            │  │ │                           │    │                    │   │
│  │            │  │ │ Time: 12:35 elapsed       │    │                    │   │
│  │            │  │ │                           │    │                    │   │
│  │            │  │ │ ┌─────────────────────┐   │    │                    │   │
│  │            │  │ │ │  [🎥 Watch Live]    │   │    │                    │   │
│  │            │  │ │ └─────────────────────┘   │    │                    │   │
│  │            │  │ │                           │    │                    │   │
│  │            │  │ │ Files changed: 3          │    │                    │   │
│  │            │  │ │ • auth.py (+87)           │    │                    │   │
│  │            │  │ │ • routes.py (+12)         │    │                    │   │
│  │            │  │ └───────────────────────────┘    │                    │   │
│  │            │  │                                  │                    │   │
│  │            │  │ [View Details] [Stop]            │                    │   │
│  │            │  └────────────┘                     │                    │   │
│  │            │                                     │                    │   │
│  └────────────┴──────────────────┴──────────────────┴────────────────────┘   │
│                                                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │  💡 Quick Actions                                                       │  │
│  │  [+ New Task]  [📅 Calendar]  [💬 Team Chat]  [📊 Analytics]           │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────┘

Legend:
• [●] = Avatar (user watching)
• 🔴 = Live indicator (pulsing red dot)
• ●●●●●○○ = Progress bar (75%)
• @Maria = Assigned to Maria
```

---

## 2. Live View Session (Multi-Viewer)

### 2.1 Full Screen Live Session

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  🎥 Live: Task #99 - JWT Authentication Implementation    [João Silva ▼] [✕]  │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                          │                     │
│                                                          │  👥 Viewers (3/5)   │
│                                                          │  ─────────────────  │
│                                                          │                     │
│   ┌────────────────────────────────────────────┐        │  ┌────────────────┐│
│   │  🔴 LIVE                                   │        │  │ [●] João       ││
│   │  Backend Agent - Ubuntu Terminal           │        │  │     (you)      ││
│   │                                            │        │  │     Owner      ││
│   │  ┌──────────────────────────────────────┐ │        │  │                ││
│   │  │ ubuntu@agent:~/workspace$ vim auth.py│ │        │  │     🎮 Control ││
│   │  │                                      │ │        │  │     ✏️ Annotate││
│   │  │ from jwt import encode, decode       │ │        │  └────────────────┘│
│   │  │ from datetime import datetime        │ │        │                     │
│   │  │                                      │ │        │  ┌────────────────┐│
│   │  │ def create_token(user_id: int):      │ │        │  │ [●] Maria      ││
│   │  │     payload = {                      │ │        │  │     Silva      ││
│   │  │         "user_id": user_id,          │ │        │  │     Senior Dev ││
│   │  │         "exp": datetime.now() + ...  │ │        │  │                ││
│   │  │     }                                │ │        │  │     ✏️ Annotate││
│   │  │     return encode(payload, SECRET▋   │ │        │  │                ││
│   │  │                                      │ │        │  │ [Grant         ││
│   │  │                                      │ │        │  │  Control] 🎮   ││
│   │  │ # Agent is typing...                │ │        │  └────────────────┘│
│   │  │                                      │ │        │                     │
│   │  │                                      │ │        │  ┌────────────────┐│
│   │  └──────────────────────────────────────┘ │        │  │ [●] Pedro      ││
│   │                                            │        │  │     Costa      ││
│   │  Tests running...                          │        │  │     DevOps     ││
│   │  ✅ test_create_token PASSED              │        │  │                ││
│   │  ⏳ test_validate_token ...                │        │  │     👁️ View    ││
│   │                                            │        │  │                ││
│   └────────────────────────────────────────────┘        │  └────────────────┘│
│                                                          │                     │
│   Controls:                                              │  ──────────────────│
│   [◀ Slower] [⏸ Pause] [▶ Faster]                       │                     │
│                                                          │  💬 Team Chat       │
│   Quality: [HD ▼] | Latency: 120ms | FPS: 30           │  ─────────────────  │
│                                                          │                     │
│   Annotations: [✏️ Draw] [👆 Point] [💬 Comment]         │  João: Looking     │
│                [🔴 Record] [📸 Screenshot]               │  good! 👍          │
│                                                          │  10:32 AM          │
│                                                          │                     │
│   Files Changed (3):                                     │  Maria: Should we  │
│   • auth.py (+87 lines)                                  │  add rate limiting │
│   • routes.py (+12 lines)                                │  here? 🤔         │
│   • tests/test_auth.py (+45 lines)                       │  10:33 AM          │
│                                                          │                     │
│                                                          │  João: @Maria good │
│                                                          │  idea! Let's add:  │
│                                                          │                     │
│                                                          │  ```python         │
│                                                          │  @limiter.limit    │
│                                                          │  ```               │
│                                                          │  10:33 AM          │
│                                                          │                     │
│                                                          │  Pedro: ✅ Deploy  │
│                                                          │  pipeline ready    │
│                                                          │  10:34 AM          │
│                                                          │                     │
│                                                          │  ┌────────────────┐│
│                                                          │  │ Type message...││
│                                                          │  │                ││
│                                                          │  │ [@] [💻] [📎]  ││
│                                                          │  └────────────────┘│
│                                                          │                     │
│                                                          │  [📅 Schedule      │
│                                                          │   Code Review]     │
└──────────────────────────────────────────────────────────┴─────────────────────┘

Bottom Action Bar:
[◀ Exit Live View] [⏺ Start Recording] [🔊 Start Voice Call] [⚙️ Settings]
```

---

## 3. Task Card (Detailed View)

### 3.1 Task Card with Live Controls

```
┌───────────────────────────────────────────────────────────────┐
│  Task #99: Implement JWT Authentication                      │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  Status: ⚙️ IN PROGRESS                                       │
│  Priority: 🔴 HIGH                                            │
│  Assigned: Backend Agent                                      │
│  Started: 10:15 AM (22 minutes ago)                           │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Progress                                               │ │
│  │  ●●●●●●●○○○  75% Complete                              │ │
│  │                                                         │ │
│  │  Current Step:                                          │ │
│  │  Writing unit tests for token validation               │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  Description:                                                 │
│  Implement JWT-based authentication for the API including:    │
│  - Token generation and validation                            │
│  - Refresh token mechanism                                    │
│  - Middleware integration                                     │
│  - Unit and integration tests                                 │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  🎥 LIVE VIEW AVAILABLE                                 │ │
│  │                                                         │ │
│  │  3 people watching:                                     │ │
│  │  [●][●][●]                                              │ │
│  │  João Silva, Maria Silva, Pedro Costa                   │ │
│  │                                                         │ │
│  │  Session started: 10:15 AM (22 min ago)                 │ │
│  │  Latency: 120ms • Quality: HD                           │ │
│  │                                                         │ │
│  │  ┌───────────────────────────────────────────────────┐ │ │
│  │  │        [🎥 Watch Live]                            │ │ │
│  │  └───────────────────────────────────────────────────┘ │ │
│  │                                                         │ │
│  │  Alternative:                                           │ │
│  │  [📹 View Recording] (available after completion)       │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  Files Changed (3):                                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  📄 auth.py                          +87 | -0 lines     │ │
│  │  📄 routes.py                        +12 | -3 lines     │ │
│  │  📄 tests/test_auth.py               +45 | -0 lines     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  Metrics:                                                     │
│  • LLM Tokens Used: 12,450                                    │
│  • Cost: $0.18                                                │
│  • Estimated Time Remaining: 8 minutes                        │
│                                                               │
│  Actions:                                                     │
│  [📋 View Full Details] [⏸️ Pause Agent] [🛑 Stop Agent]     │
│  [💬 Add Comment] [🔔 Get Notifications]                     │
│                                                               │
│  Approval Required: ✅ Yes (when complete)                    │
│  Auto-merge: ❌ No                                            │
└───────────────────────────────────────────────────────────────┘
```

---

## 4. Calendar & Meetings Integration

### 4.1 Calendar View with Scheduled Code Reviews

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  📅 SquadX Calendar                                      [João Silva ▼] [⚙️]   │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  ┌──────────────────────┐  [◀ Previous] February 2026 [Next ▶]              │
│  │  📅 Today             │                                                    │
│  │  Feb 7, 2026         │  Mon  Tue  Wed  Thu  Fri  Sat  Sun                │
│  │                      │   3    4    5    6   [7]   8    9                 │
│  │  🎥 3 live sessions  │  10   11   12   13   14   15   16                 │
│  │  📅 2 meetings today │  17   18   19   20   21   22   23                 │
│  │                      │  24   25   26   27   28                           │
│  └──────────────────────┘                                                    │
│                                                                                │
│  Today's Schedule:                                                             │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  10:00 AM - 10:30 AM                                                     │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Daily Standup - Dev Team                                          │ │ │
│  │  │  📍 Google Meet                                                     │ │ │
│  │  │  👥 João, Maria, Pedro, Carlos                                      │ │ │
│  │  │                                                                     │ │ │
│  │  │  [Join Meeting] [View Details]                                      │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  2:00 PM - 3:00 PM                                                       │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  🎥 Live Code Review: JWT Authentication                           │ │ │
│  │  │  📋 Task #99 (Backend Agent)                                        │ │ │
│  │  │  👥 João (host), Maria, Pedro                                       │ │ │
│  │  │                                                                     │ │ │
│  │  │  Session will automatically start agent execution                   │ │ │
│  │  │  and open live view for all participants                            │ │ │
│  │  │                                                                     │ │ │
│  │  │  [Start Session Now] [Reschedule] [Cancel]                          │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Upcoming This Week:                                                           │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Tomorrow, 10:00 AM - Weekly Planning                                    │ │
│  │  Feb 10, 3:00 PM - Architecture Review                                   │ │
│  │  Feb 12, 2:00 PM - Sprint Demo                                           │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Actions:                                                                      │
│  [+ New Meeting] [🔗 Connect Google Calendar] [⚙️ Notification Settings]     │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Team Chat

### 5.1 Integrated Team Chat

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  💬 Team Chat                                            [João Silva ▼] [⚙️]   │
├────────────────────────────────────────────────────────────────────────────────┤
│                                │                                               │
│  Conversations                 │  #backend-team                                │
│                                │  👥 4 members                                 │
│  ┌──────────────────────────┐  │                                               │
│  │  🔍 Search...            │  │  ─────────────────────────────────────────   │
│  └──────────────────────────┘  │                                               │
│                                │  João Silva                         10:15 AM  │
│  Channels                      │  Starting work on JWT auth now                │
│  • #general          🔴 3      │                                               │
│  • #backend-team     🔴 12     │  Maria Silva                        10:16 AM  │
│  • #frontend-team             │  Perfect timing! I can review later today     │
│                                │                                               │
│  Direct Messages               │  SquadX Bot                         10:17 AM  │
│  • Maria Silva       🔴 2      │  🤖 Agent started execution:                 │
│  • Pedro Costa                 │  Task #99 - JWT Authentication                │
│  • Carlos Mendes              │  [Watch Live] [View Details]                  │
│                                │                                               │
│  Live Sessions                 │  João Silva                         10:20 AM  │
│  • Task #99 (3 👁️)   🎥        │  @Maria can you check if we need rate        │
│  • Task #87 (1 👁️)   🎥        │  limiting on the login endpoint?              │
│                                │                                               │
│                                │  Maria Silva                        10:21 AM  │
│                                │  @João yes, definitely! Let me share a        │
│                                │  snippet:                                     │
│                                │                                               │
│                                │  ┌───────────────────────────────────────┐   │
│                                │  │ ```python                             │   │
│                                │  │ from slowapi import Limiter           │   │
│                                │  │                                       │   │
│                                │  │ @limiter.limit("5 per minute")        │   │
│                                │  │ async def login(...):                 │   │
│                                │  │     ...                               │   │
│                                │  │ ```                                   │   │
│                                │  └───────────────────────────────────────┘   │
│                                │                                               │
│                                │  Pedro Costa                        10:25 AM  │
│                                │  I'm watching the agent now, it's adding      │
│                                │  the tests. Looking clean! ✅                 │
│                                │                                               │
│                                │  João Silva                         10:26 AM  │
│                                │  Great! Once tests pass we can merge          │
│                                │                                               │
│                                │  ┌───────────────────────────────────────┐   │
│                                │  │  Type a message...                    │   │
│                                │  │                                       │   │
│                                │  │  [@] [💻] [📎] [😊]          [Send ▶] │   │
│                                │  └───────────────────────────────────────┘   │
└────────────────────────────────┴───────────────────────────────────────────────┘
```

---

## 6. Mobile Responsive (PWA)

### 6.1 Mobile Kanban View

```
┌─────────────────────────┐
│  ☰  SquadX      João ▼ │
├─────────────────────────┤
│                         │
│  E-commerce Platform    │
│  [Search...      ]  🔍  │
│                         │
│  ┌─────────────────────┐│
│  │  Filters            ││
│  │  [All ▼] [Live 🔴] ││
│  └─────────────────────┘│
│                         │
│  ┌─────────────────────┐│
│  │ 📋 TODO (8)         ││
│  │                     ││
│  │ ┌─────────────────┐ ││
│  │ │ Task #101       │ ││
│  │ │ Login UI        │ ││
│  │ │ @Maria 🔴       │ ││
│  │ └─────────────────┘ ││
│  └─────────────────────┘│
│                         │
│  ┌─────────────────────┐│
│  │ ⚙️ IN PROGRESS (4)  ││
│  │                     ││
│  │ ┌─────────────────┐ ││
│  │ │ Task #99 🎥     │ ││
│  │ │ JWT Auth        │ ││
│  │ │ @João           │ ││
│  │ │                 │ ││
│  │ │ 🔴 LIVE         │ ││
│  │ │ 3 👁️ watching   │ ││
│  │ │                 │ ││
│  │ │ [Watch]         │ ││
│  │ └─────────────────┘ ││
│  └─────────────────────┘│
│                         │
│  [+ New Task]           │
│                         │
└─────────────────────────┘

Bottom Nav:
[🏠] [📋] [💬] [📅] [👤]
```

### 6.2 Mobile Live View

```
┌─────────────────────────┐
│  ◀ 🎥 Task #99    ⋮     │
├─────────────────────────┤
│                         │
│  ┌─────────────────────┐│
│  │ 🔴 LIVE             ││
│  │                     ││
│  │ Backend Agent       ││
│  │                     ││
│  │ [Agent Screen]      ││
│  │                     ││
│  │ $ vim auth.py       ││
│  │                     ││
│  │ def create_token(): ││
│  │   ...               ││
│  │                     ││
│  │ ▋                   ││
│  └─────────────────────┘│
│                         │
│  👥 3 watching          │
│  [●][●][●]              │
│                         │
│  75% Complete           │
│  ●●●●●●●○○○             │
│                         │
│  [⏸ Pause] [Settings]  │
│                         │
│  ┌─────────────────────┐│
│  │ 💬 Chat (12)        ││
│  │                     ││
│  │ João: Good! 👍      ││
│  │ 10:32               ││
│  │                     ││
│  │ Maria: Add rate     ││
│  │ limiting? 🤔        ││
│  │ 10:33               ││
│  │                     ││
│  │ [Type...]      [▶]  ││
│  └─────────────────────┘│
│                         │
└─────────────────────────┘
```

---

## 7. Settings & Preferences

### 7.1 Live View Settings

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  ⚙️ Live View Settings                                  [João Silva ▼] [✕]    │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  General                                                                       │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Auto-start Live View                                                    │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  ☑ Automatically start live session when task begins              │ │ │
│  │  │  ☐ Only start when I click "Watch Live"                            │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                          │ │
│  │  Default Quality                                                         │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  ○ Auto (adapt to connection)                                      │ │ │
│  │  │  ● HD (1280x720)                                                    │ │ │
│  │  │  ○ SD (854x480)                                                     │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                          │ │
│  │  Max Viewers per Session                                                 │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  [5] viewers (Professional plan limit: 3)                          │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Permissions                                                                   │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Default Viewer Permissions                                              │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  ☑ Allow annotations                                               │ │ │
│  │  │  ☐ Allow remote control (requires explicit approval)               │ │ │
│  │  │  ☑ Allow chat                                                       │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                          │ │
│  │  Approval Workflow                                                       │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Require approval for:                                             │ │ │
│  │  │  ☑ Remote control requests                                         │ │ │
│  │  │  ☐ Recording sessions                                              │ │ │
│  │  │  ☑ Granting control to new viewers                                 │ │ │
│  │  └────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Recording                                                                     │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  ☑ Auto-record all sessions (Professional+ only)                         │ │
│  │  ☑ Keep recordings for 30 days                                           │ │
│  │  ☐ Email me when recording is ready                                      │ │
│  │                                                                          │ │
│  │  Storage Used: 2.3 GB / 100 GB                                           │ │
│  │  [Manage Recordings]                                                      │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Notifications                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Notify me when:                                                         │ │
│  │  ☑ Live session starts                                                   │ │
│  │  ☑ Someone joins my session                                              │ │
│  │  ☐ Session ends                                                          │ │
│  │  ☑ Someone requests control                                              │ │
│  │  ☑ Recording is available                                                │ │
│  │                                                                          │ │
│  │  Notification channels:                                                   │ │
│  │  ☑ In-app                                                                │ │
│  │  ☑ Email                                                                 │ │
│  │  ☐ Slack                                                                 │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  [Save Changes] [Cancel]                                                      │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Analytics Dashboard

### 8.1 Live View Analytics

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  📊 SquadX Analytics - Live View Insights                [João Silva ▼] [⚙️]  │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  Period: [Last 30 Days ▼]                                                     │
│                                                                                │
│  ┌──────────────────┬──────────────────┬──────────────────┬─────────────────┐ │
│  │  Total Sessions  │  Avg Viewers     │  Total Duration  │  Total Cost     │ │
│  │                  │                  │                  │                 │ │
│  │      127         │      2.8         │    42h 15min     │    $23.45       │ │
│  │  ↑ 23% vs prev   │  ↑ 15% vs prev   │  ↑ 31% vs prev   │  ↓ 12% vs prev  │ │
│  └──────────────────┴──────────────────┴──────────────────┴─────────────────┘ │
│                                                                                │
│  Session Trends                                                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  40 ┤                                                            ╭──      │ │
│  │  35 ┤                                              ╭─────╮       │        │ │
│  │  30 ┤                                    ╭─────────╯     ╰───────╯        │ │
│  │  25 ┤                          ╭─────────╯                                │ │
│  │  20 ┤                ╭─────────╯                                          │ │
│  │  15 ┤      ╭─────────╯                                                    │ │
│  │  10 ┤──────╯                                                              │ │
│  │   5 ┤                                                                     │ │
│  │   0 └────────────────────────────────────────────────────────────────    │ │
│  │      Jan 1    Jan 8   Jan 15  Jan 22  Jan 29   Feb 5                     │ │
│  │                                                                          │ │
│  │  ─ Sessions Started  ─ Avg Viewers                                       │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Most Watched Tasks                                                            │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Task                        │ Views │ Avg Viewers │ Avg Duration        │ │
│  ├──────────────────────────────┼───────┼─────────────┼────────────────────┤ │
│  │  #99 JWT Authentication      │  45   │     3.2     │    25 min          │ │
│  │  #87 Checkout Flow           │  38   │     2.8     │    32 min          │ │
│  │  #76 Payment Integration     │  32   │     4.1     │    41 min          │ │
│  │  #65 Search Optimization     │  28   │     2.1     │    18 min          │ │
│  │  #54 User Dashboard          │  24   │     2.5     │    28 min          │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  Team Engagement                                                               │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  User             │ Sessions │ Hours │ Annotations │ Comments            │ │
│  ├───────────────────┼──────────┼───────┼─────────────┼────────────────────┤ │
│  │  João Silva       │    52    │  18.5 │     124     │    89              │ │
│  │  Maria Silva      │    48    │  16.2 │      98     │    67              │ │
│  │  Pedro Costa      │    35    │  12.1 │      45     │    34              │ │
│  │  Carlos Mendes    │    27    │   9.3 │      32     │    28              │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                │
│  [Export Report] [Schedule Email] [Share Dashboard]                           │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

**Próximo: [BUSINESS-MODEL.md](BUSINESS-MODEL.md)**
