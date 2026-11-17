# Super3 fork – UI Spec (Racing overlay + Quick options)

Applies to: Sega Rally 2, Daytona USA 2, Scud Race Plus. Repo: `treegecko/Super3`. (HOTD2 is a different fork, see UI_SPEC_Flycast_HOTD2.md.)
Source of truth: `RallyOverlay.kt`, `Super3Activity.kt`, `res/layout/overlay_controls.xml`, `res/drawable/`.
Written from the repo zip (Super3-main) on 2026-11-17. Reference look: the Daytona USA 2 screenshot (stick bottom-left, brake/gas bottom-right, COIN/START bottom-centre, pause + menu top-right, view button right-middle).

Rule for future work: keep this look and flow. Do not change positions, sizes, IDs or defaults without saying so.

## 0. Design principles (the owner's standard)
- Minimalist. No flashy buttons, no decoration. Plain, semi-transparent controls.
- Menus are straight to the point: everything reachable in one tap from the menu icon (or Back).
- Standard top-right cluster on every build: **play/pause, screen switcher, menu icon** (menu opens Quick options). The screen switcher is the fullscreen-style icon between play/pause and menu.
- Gameplay controls stay out of the way; COIN / START and SERVICE / TEST can be hidden.
- New games should copy the closest existing layout instead of inventing a new one.

---

## 1. Which overlay a game gets

- **Racing games** (Games.xml input type `vehicle` or `harley`): `RallyOverlay` replaces the stock overlay. All stock widgets are hidden and RallyOverlay adds its own. This is the Daytona / Rally / Scud look.
- **Non-racing games** (guns, fighting, Star Wars, etc.): stock `overlay_controls.xml` is used. Look: SERVICE top-left, TEST top-right, save icon under SERVICE, pause under TEST, coin bottom-left, START bottom-centre. Fight buttons are a 2x2 grid bottom-right (E, G / K, P).
- Quick options menu is the compact 2-column version for all builds.

---

## 2. Racing overlay layout (RallyOverlay)

All sizes in dp. Anchors are screen edges.

| Element | Size | Anchor and margin | Notes |
|---|---|---|---|
| Analog stick (base `joystick_base`, knob `joystick_knob`) | base 160, knob 80 | bottom-left, 20 from left, 12 from bottom | Left/right = proportional steering. Flick up/down = shift up/down (also up/down in menus). |
| Gas (`btn_gas`) | 96 x 96 | bottom-right, 16 from right, 16 from bottom | Right-most pedal. |
| Brake (`btn_brake`) | 96 x 96 | bottom-right, 12 gap left of gas | |
| View (`btn_view`) | 96 x 54 | right edge, vertically centred, 16 from right | Camera/view button. On VR4 games (Daytona 2, Scud Race) each tap cycles view 1 to 4. |
| COIN (`btn_coin`) | 96 x 48 | bottom-centre, 14 from bottom, shifted 52 left | Hidden by default. |
| START (`btn_start`) | 96 x 48 | bottom-centre, 14 from bottom, shifted 52 right | Hidden by default. |
| Pause (`play_pause_24px`) | 44 round | top-right, 12 from top and right | Toggles pause. |
| Menu (`ic_menu_24`, hamburger) | 44 round | same top-right row as pause, 10 gap, menu is right-most | Opens Quick options. |
| SERVICE / TEST pills | wrap content | top-left row, 12 from top and left | Hidden by default. Text pills, rounded 18, white 2 stroke. |

Round buttons (pause/menu): black 35% fill, white icon at about 90% alpha.

### Behaviour constants
- Steering deadzone: 0.06.
- Shift trigger at 0.65 stick travel, re-arms below 0.30. Shift pulse lasts 80 ms.
- Default control opacity 0.85 (range 0.2 to 1.0). Pressed state is 70% of idle opacity. Stick base and knob go to full opacity while held.
- Images are optional: if a PNG is missing, a grey rounded rectangle is used.

### Touch finger IDs (sent to native)
| ID | Function |
|---|---|
| 1101 | Coin |
| 1102 | Start |
| 1103 | Gas |
| 1104 | Brake |
| 1105 | Service |
| 1106 | Test |
| 1107 | Steering (X mapped 0..1 across the stick) |
| 1108 | Shift (y = 0 up, y = 1 down) |
| 1152 | View (single) |
| 1153 to 1156 | View 1 to 4 (VR4 cycle) |

---

## 3. Touch-controls mode and USB controller
Saved as `rally_controls_mode` (main prefs).
- **0 Auto** (default): gameplay touch controls hide while a gamepad or joystick is connected.
- **1 Always show**.
- **2 Always hide**.
Only stick, gas, brake and view hide. Pause and menu always stay.
Listens for controller plug/unplug and re-applies the mode.

---

## 4. Quick options menu
Opened by the menu button or the Back button. Title: **Quick options**. Game pauses while open.

Layout (top to bottom):
1. **Exit game** (full width, top).
2. Row 1: **Video settings** | **Controls opacity**
3. Row 2: **Coin / Start: Shown/Hidden** | **Test / Service: Shown/Hidden**
4. Row 3: **Controller mapping** | **Touch: Auto / Always show / Always hide**
5. Row 4: **Save states** | **Restart game**
6. **Cancel** (bottom-right).

Cell style: outlined button, height 40, text 13 sp, two equal columns, 8 gap, 16 side margin.
Controls opacity opens a slider (20% to 100%), saved immediately.
Old stock rows (touch controls, gyro, Daytona timing) are hidden in this build.

---

## 5. Saved settings and defaults
| Setting | Where | Default |
|---|---|---|
| Control opacity | prefs `rally_overlay` / `opacity` | 0.85 |
| Coin / Start shown | prefs `rally_overlay` / `coin_start_visible` | hidden |
| Test / Service shown | not saved, session only | hidden |
| Touch mode | main prefs `rally_controls_mode` | 0 (Auto) |

---

## 6. Per-game notes
- **Sega Rally 2**: `srally2`, no VR4 (single view button).
- **Daytona USA 2**: `daytona2` (`daytona2.zip`), VR4 view cycle, Mr. Dawe's steering config.
- **Scud Race Plus**: `scudplusa` (needs `scud.zip` as parent ROM), VR4 view cycle.
- Each game is its own APK built from the same repo via GitHub Actions (manual run only). Each has its own launcher icon.

---

## 7. Known gaps
- Controller mapping screen title is hard-coded to "Sega Rally 2" for every build.
- Test / Service visibility is not remembered between launches (Coin / Start is).
- Test / Service buttons use the text-pill style, unlike Coin / Start which use PNGs.
- Star Wars Trilogy was dropped (feedback lever init problems); no overlay work was kept for it.
