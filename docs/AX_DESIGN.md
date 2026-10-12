# AX Design

The design language of MaxxTermux, named after its author, Anshuman X (AX).
It is not Material You. Everything lives in `app/.../design/` and is prefixed `Ax`.

## Principles
- Dark first, tonal teal surfaces, one soft accent (`AxColors.Accent`).
- Big radii (24 to 36 dp) and mixed corners on icon tiles.
- Content scrolls under a floating glass dock.
- Progress is a first-class element: straight, wavy bar, circle or wavy circle, chosen by the user.

## Pieces
| File | What it holds |
| --- | --- |
| `AxTokens.kt` | `AxColors`, `AxShapes` |
| `AxComponents.kt` | `AxText`, `AxIcon`, `AxGlassDock`, `AxTab` |
| `AxProgress.kt` | `AxProgress`, `AxProgressStyle` |
