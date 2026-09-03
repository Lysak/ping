# Play Store listing assets

Generated from the app's own launcher icon and theme colour `#DBF1B3` on
`#111010`. SVG sources are kept next to each PNG/JPG so they can be re-rendered
(`sips -s format png foo.svg --out foo.png`).

| File | Play Console slot | Spec |
|------|-------------------|------|
| `icon-512.png` | App icon | 512×512, 32-bit PNG |
| `feature-graphic-1024x500.jpg` | Feature graphic | 1024×500, no alpha |
| `screenshots/01-idle-overlay.png` | Phone screenshot 1 | 1080×2160 (2:1) |
| `screenshots/02-active.png` | Phone screenshot 2 | 1080×2160 |
| `screenshots/03-summary.png` | Phone screenshot 3 | 1080×2160 |

Raw uncropped captures: `screenshots/01-idle.png` (pre-overlay). Device:
motorola razr plus 2024, debug build.

The razr's native capture is 1080×2640 (2.44:1) which Play rejects (max 2:1);
all three are cropped to 1080×2160 from the top (drops only the status bar).

Suggested screenshot order in Play: put `02-active` first as the hero, then
`03-summary`, then `01-idle-overlay`.
