# Behavior Design & Priority Rules

## Task Lifecycle State Machine

```text
PENDING ──► ACTIVE ──(Emergency / Melee Threat)──► SUSPENDED
              │                                        │
              ├──(All Subtasks Verified)──► COMPLETED  │
              ├──(Owner Stop / Cancel)────► CANCELLED  │
              └──(Retries > Budget)───────► FAILED     │
              ▲                                        │
              └──────────(Emergency Cleared)───────────┘
```

## Anti-Oscillation Safeguards

1. **Minimum Switch Cooldown**: Non-critical threat switches enforce a `20`-tick hysteresis window.
2. **Single Suspended Slot**: Lower-priority player assignments (e.g., `Construct Modern Stylish House`) remain intact with their saved build origin and block index while a zombie attack or food-healing action runs.
3. **Bounded Retries**: Every task defaults to `maxRetries = 4` and a bounded tick timeout so an unreachable ore vein or enclosed build site produces a clear failure message instead of an infinite loop.
