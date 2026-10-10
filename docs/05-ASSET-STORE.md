# 05 — Asset Store

A completely offline asset store: the repository's `Assets/` folder of real asset
packs is indexed at build time, shipped inside the APK as ZIPs, extracted on
demand, and imported into projects.

**Generator:** `tools/build_asset_store.py` · **Runtime:** `project/AssetStore.kt`
· **UI:** `ui/AssetPacksActivity.kt`, `ui/AssetStoreActivity.kt`

## 1. Numbers

From `app/src/main/assets/store/store_manifest.json` (schema v2, 752 KB):

```
packs            25        (24 bundled, 1 indexed-only)
files         5 008
images        3 982
models          658
audio             6
sheets          154        sprite sheets with a detected grid
bytes    51 566 992        (~49 MB)
```

The manifest is **committed**. It is the catalogue the UI renders and the contract
the games are validated against, so it must be reproducible and reviewable rather
than generated at install time.

## 2. Pipeline

```
Assets/*.zip ──► tools/build_asset_store.py
                    │  unzip → normalise names → probe images (Pillow)
                    │  two-pass sprite-grid detection → family reconciliation
                    ▼
      store_manifest.json   (catalogue, committed)
      store/pack_map.txt    (original name → packaged name)
      docs/asset-store/CATALOG.md
                    │
                    ▼  Gradle: stageStoreAssets (preBuild)
      build/generated/storeAssets/<packId>.zip   (not committed)
                    │
                    ▼  APK assets/
      AssetStore.kt: extract on first use → filesDir/store/<packId>/
                    │
                    ▼  importInto()
      project/assets/<name>  (+ sibling .anim for any detected sheet)
```

### Why the ZIPs are renamed

The real filenames contain spaces, commas and exclamation marks
(`Free Characters Animations Asset Pack!.zip`), which are awkward as Android asset
names. They are staged under `<packId>.zip`, and `pack_map.txt` is the single
source of truth for the mapping — the same file the manifest is generated from, so
the two cannot disagree.

The ZIPs are copied into `build/`, never committed: they already live in `Assets/`,
and duplicating ~49 MB into git would be pure waste. `assets.srcDir` points at the
build directory, **not** at `Assets/`.

## 3. Sprite-sheet detection

The hardest part of the pipeline, and the reason the store is useful rather than
just a file browser. 154 sheets have a recovered grid, so importing one produces a
ready-to-play `.anim` clip.

**Pass 1 — frame height.** A candidate `fh` is admissible only when every interior
grid line is a fully transparent row. Any integer multiple of the true height is
also admissible, so the tie is broken by taking the **smallest admissible `fh`
whose row bands all contain ink**.

Height cannot be disambiguated by a padding test: a sprite legitimately stands on
the floor of its frame, so "ink touches the bottom edge" carries no signal. That
was tried and abandoned.

**Pass 2 — frame width.** Same transparency test on columns, then scored on
horizontal padding: in the correct grid each frame's ink is inset from the frame's
left and right edges, while an over-large frame makes ink touch both edges and an
under-large frame leaves whole frames blank.

```
score = padded / inked - 2.0 * blank
```

**Family reconciliation.** A character exported as `Hero_Idle-Sheet.png` /
`Hero_Attack-Sheet.png` / `Hero-Sheet.png` shares one frame size, and the master
sheet often defeats the padding test (when the sprite fills its frame there is no
padding left to measure). The majority vote of its siblings wins — but only within
one directory, and only when that frame size divides the master's dimensions
exactly.

Directory scoping is essential: Legacy Fantasy mixes 48×32 tiles with 80×80
characters in one pack, so a pack-wide vote corrupts correct data.

### What detection refuses to do

`MemaoCharacterFantasySpritePack-v3/001.png` is 384×1968, which suggests 8×41 cells
of 48×48. It is **not** a valid grid: two of its columns have sprites bleeding
across row boundaries, so no interior row line is fully transparent. The store
declines to guess and ships the file with no `sheet` key.

That is correct behaviour, not a detection failure. Game 3 originally pointed at
this sheet; `tools/validate_games.py` rejected it, and the hero now uses the
grid-verified 96×96 sheets instead.

## 4. Runtime behaviour

`AssetStore.kt`:

- `load(ctx)` parses the manifest once and caches it.
- `extract(ctx, pack, onProgress)` unzips into `filesDir/store/<packId>/`, guarded
  by a `.extracted` stamp so a pack is unpacked at most once.
- `file(ctx, pack, entry)` returns an extracted file; `thumbnail(ctx, pack, entry,
  maxPx)` decodes a downsampled preview with a 96-entry LRU.
- `importInto(ctx, project, pack, entries, onProgress)` copies selected files into
  the project's flat `assets/` directory and emits a sibling `.anim` for any entry
  with a non-guessed sheet.
- `search(m, query, category, packId)` filters the catalogue.

`normalizeEntryName` / `stripRoot` in Kotlin must stay in lock-step with
`safe_member_name` / `strip_root` in the Python generator. They are the same
algorithm in two languages, and if they drift, imports silently land at the wrong
path.

## 5. Licensing

Every pack carries a `license` and a `distributable` flag in the manifest.

| Pack | Licence | Bundled |
|---|---|---|
| All Kenney packs | CC0 1.0 | yes |
| `Free_Medieval_Fantasy_UI_Pack` | free, personal + commercial | yes |
| Anokolisa `Legacy Fantasy - Debug Map` | no restrictions; cannot resell as a final product | yes |
| `FreeCharactersAnimationsAssetPack` | personal + commercial, may modify, **must not redistribute or resell** | yes |
| `RPG Top Down Character` | `LICENSE.txt.txt` is empty — no terms stated | yes |
| `Warped Vehicles` | terms only in an unparsed `public-license.pdf` | yes |
| **`Super Pixel Objects Sample`** | **clause 4: "Game making tools … are not granted a license"** | **no** |

The last row is the important one. That pack explicitly excludes game-making tools
from its licence, and this **is** a game-making tool. It is indexed with
`distributable: false` so it is searchable but never bundled — 24 of 25 packs ship.
`GameLibrary` refuses to import from a non-distributable pack and reports it rather
than silently failing.

`FreeCharactersAnimationsAssetPack` forbids redistribution, yet is bundled. That is
a tension worth flagging: it is used by three of the four games and is bundled for
this build, but a publicly distributed engine should either replace those sprites or
obtain permission. It is called out here rather than buried.

## 6. Regenerating

```bash
python3 tools/build_asset_store.py            # Pillow required; see below
python3 tools/validate_games.py               # games vs. manifest vs. engine
```

**Pillow is required, not optional.** Without it the grid probe falls back to a
heuristic and marks every sheet `guess: true`. A guessed grid is worse than no
grid: the games place their tiles and animation frames from these numbers, and the
validator cross-checks against them. Running the generator without Pillow over the
committed catalogue would replace 154 pixel-verified grids with 2 901 guesses, so
the generator now refuses and exits non-zero unless `--force` is passed.

Without Pillow the tool still runs but every sheet becomes a `guess`. The
sandbox has Pillow in `/home/user/.venv` because system-wide `pip install` is
blocked by PEP 668.
