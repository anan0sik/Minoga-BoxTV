# Minoga TV Box — Design System (Stitch Specification)

> **Status**: Proposed / Awaiting Acceptance  
> **Target**: Android TV / Google TV (10-foot UI, D-pad navigation, 16:9 1080p/4K)  
> **Aesthetic Philosophy**: Obsidian Cinematic Glassmorphism with Electric Cyan Focus Telemetry

---

## 1. Visual Atmosphere & Philosophy

- **Density**: 6 / 10 ("Daily TV App Balanced") — generous focus padding, high legibility from 3 meters (10-foot experience).
- **Tone**: Cinematic OLED Dark — absolute contrast without eye strain, deep charcoal obsidian backdrops, frosted glass cards.
- **Focus Discipline**: Single primary accent (Electric Cyan `#00E5FF` / `#4F8EF7`) with tactile scale (`1.02x–1.04x`), illuminated border strokes, and ambient glow.
- **Anti-Patterns**:
  - NO blinding neon rainbow gradients.
  - NO pure black `#000000` — use Deep Obsidian `#07090E` and Charcoal `#0D111A`.
  - NO tiny unreadable text (minimum `13.sp` for metadata, `16.sp` for channel names, `20.sp+` for titles).
  - NO cluttered rows without clear D-pad focus indicators.

---

## 2. Color Palette & Semantic Tokens

| Token Name | Hex Code | Purpose / Semantic Role |
|:---|:---|:---|
| `BgObsidian` | `#07090E` | Main full-screen background (deep OLED dark) |
| `SurfaceBase` | `#0E121B` | Neutral container surface (unfocused cards) |
| `SurfaceElevated` | `#161B26` | Elevated cards, side panels, dialog surfaces |
| `SurfaceGlass` | `rgba(22, 27, 38, 0.72)` | Frosted glass with `16.dp` backdrop blur |
| `BorderSubtle` | `#1F2737` | Unfocused borders and subtle dividers |
| `BorderFocused` | `#00E5FF` | Active D-pad focus stroke (2.dp glowing cyan) |
| `TextPrimary` | `#F1F4F9` | High-contrast readable text (headers, focused item) |
| `TextSecondary` | `#94A3B8` | Subtitles, progress times, metadata |
| `AccentCyan` | `#00E5FF` | Primary action accent, focus glow, active toggles |
| `ArchiveGreen` | `#10B981` | Dedicated catch-up / archive indicator badge |
| `LiveRed` | `#EF4444` | Live on-air broadcast indicator / favorite red tag |
| `GoldLock` | `#F59E0B` | Parental control PIN lock indicator (18+) |

---

## 3. Typography Architecture

- **Primary Font**: Clean Geometric Sans (`Outfit` / `Inter` / `Roboto`)
- **Monospace Font**: `JetBrains Mono` / `Roboto Mono` for timestamps, countdowns, and bitrates
- **Scale Hierarchy**:
  - `Display Large`: `32.sp`, Bold (Player OSD channel title, Section titles)
  - `Title Medium`: `20.sp`, SemiBold (Category cards, modal headers)
  - `Body Large`: `16.sp`, Medium (Channel names, active program titles)
  - `Body Medium`: `14.sp`, Regular (Upcoming programs, settings descriptions)
  - `Caption / Badge`: `11.sp–12.sp`, Bold / All-Caps (`● АРХИВ`, `PREVIEW`, `4K UHD`, `+10 сек`)

---

## 4. Window & Screen Architecture

### Screen 1: Папки и Категории (Folders Hub)
- **Grid Layout**: 3–4 columns of tactile folder cards.
- **Card Content**: Category icon, title, channel count pill badge (`540 каналов`), lock icon for 18+.
- **Top Bar**: Profile avatar chip, Search action button, Settings cog, live system clock.
- **Focus State**: Card expands by `1.03x` scale, displays a 2dp `#00E5FF` border, and soft cyan ambient drop shadow.

### Screen 2: Список каналов + Live Preview + EPG Архива (Channels & Archive Side Panel)
- **Left Column (Channels)**:
  - Channel number in monospace.
  - Crisp high-res logo with rounded fallback container.
  - Two-line title layout: Channel Name (Bold) + Current Show (with real-time progress bar).
  - Glowing Green Archive Badge (`ArchiveGreen` calendar/rewind icon) for all channels with catchup.
- **Top Right (Live Preview Window)**:
  - Dedicated 16:9 mini-player with a subtle glowing border and `● LIVE PREVIEW` badge.
  - Automatically loads preview stream after 1.5s delay if enabled in Settings.
- **Bottom Right (EPG Side Panel)**:
  - Slide-in frosted glass panel (`SurfaceGlass`).
  - Categorized timeline: **Прошедшие** (Past - with clickable `В архиве / ⏯` slots), **Сейчас** (Live), **Далее** (Future).
- **Bottom Remote Guide**:
  - Color-coded action hints: `[● Красная] Избранное`, `[OK] Просмотр`, `[►] Архив передачи`.

### Screen 3: Видеоплеер и Шкала Архива (Full Player with Catch-up Timeline)
- **Full-Screen Video**: Hardware-accelerated ExoPlayer.
- **Floating OSD Bar**:
  - Disappears after 5 seconds of inactivity.
  - Shows channel logo, name, stream quality badges (`4K UHD`, `50fps`).
  - **Archive Mode Indicator**: Glowing badge `● АРХИВ / TIMESHIFT`.
  - **Continuous Timeline**: Interactive scrub bar with program division notches, seek head, and delta timestamp (`-01:24:15 / 14:35:00`).
  - **Rewind Step Pill**: Displays active step `[+10 сек]` / `[-10 сек]` as configured in settings.
  - Audio tracks and subtitle pills (`Stereo / 5.1`, `RU`).

### Screen 4: Центр настроек (Settings Hub & Dialogs)
- **Split-Screen Master-Detail / Card Grid**:
  - **Плейлисты**: Active playlist card with `[Обновить]`, `[Редактировать]`, `[Удалить]` buttons.
  - **Воспроизведение**:
    - Превью канала в списке: Toggle switch with smooth slide animation.
    - Картинка в картинке (PiP): Toggle switch.
    - Тип архива: Horizontal segmented buttons `[Авто] [Shift] [Flussonic] [Xtream] [Append] [Отключен]`.
    - Шаг перемотки: Horizontal pills `[5 сек] [10 сек] [30 сек] [1 мин]`.
    - Декодер: Segmented pills `[Авто] [Аппаратный HW] [Программный SW]`.

---

## 5. Motion & TV Remote Physics

- **D-Pad Transition**: `tween(durationMillis = 180, easing = FastOutSlowInEasing)`
- **Focus Scaling**: `animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f)`
- **Side Panel Reveal**: `slideInHorizontally(initialOffsetX = { it }) + fadeIn()`
- **Hardware Acceleration**: All animations operate strictly on `scale`, `alpha`, and `translation` to preserve 60 FPS on low-power TV boxes.
