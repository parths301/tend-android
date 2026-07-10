# App design request

_Started 2026-07-09 21:18 UTC_

---

## User

Design this app.

## Assistant

_[tool: questions_v2]_

## User

Questions answered:
- relationship: No — I want a similar app of my own (original design)
- goal: New original habit tracker redesigned completely. Need streaks widget as well and some other widgets apart form the tracker itself. Make it a personal companion app where I can plan my day as well as do habit tracking and time tracking and notes and to do and so on. All things for personal assistant including AI option to add all these things using an AI chat right in the home screen. Swipe gestures. 
- screens: Home (habit grids), Weekly view, Daily planner timeline, Time-based habits, Habit detail (calendar + notes), Todo / task list, Analytics, Home-screen widgets, All of them
- fidelity: Interactive prototype (tappable, working state)
- variations: One polished version
- aesthetic: Fresh look — surprise me
- device: Android device frame

## Assistant

_[tool: copy_starter_component]_

_[tool: update_todos]_

_[tool: grep]_

_[tool: set_project_title]_

_[tool: dc_write]_

## User

continue

## Assistant

_[tool: ready_for_verification]_

Built **Tend** — an original personal-companion prototype in an Android frame, warm cream aesthetic with terracotta/violet/teal accents (nothing borrowed from the reference app):

- **Today** — week-ring strip, category filters, habit cards with pixel heatmaps + Grid/Week toggle, tap-to-check with live streak updates
- **Plan** — day timeline with focus blocks, gaps, and check-offs
- **Tasks** — grouped list with add-via-AI
- **Stats** — insights, focus-hours trend, per-habit completion
- **Widgets** — home-screen mock: streak, quick-check, heatmap, and Deep Work bar widgets
- **Habit detail** — month calendar, streak stats, notes you can add
- **AI sheet** — "Ask Tend" bar on every tab; type "add buy groceries at 5pm" or use the chips and it actually adds tasks/plan blocks
- **Swipe** left/right on a touch device switches tabs

Tweaks: heatmap width + AI-bar toggle. Try tapping a habit's icon to open its detail page.

