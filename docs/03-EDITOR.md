# 03 — Editor

The editor is 4 523 lines of Kotlin across 18 files in `ui/`, built with plain
Android Views rather than a game-engine UI toolkit — it is a tool, so it uses the
platform's own widget set, accessibility, and text editing.

```
EditorActivity.kt              872   the editor shell: panels, tabs, menus, play mode
InspectorPanel.kt              407   property editing, driven by Prop metadata
AnimationEditorActivity.kt     389   sprite-sheet slicing and .anim authoring
ScriptEditorActivity.kt        328   JS editing with a syntax cheat sheet
Viewport3DController.kt        304   3D orbit/pan/zoom and picking
ViewportController.kt          299   2D pan/zoom and picking
AssetPacksActivity.kt          284   asset-store pack browser
BlueprintView.kt               280   node-graph canvas
ProjectsActivity.kt            265   project list, first-run seeding
BuildActivity.kt               231   APK export
BlueprintEditorActivity.kt     177   node-graph editor host
AssetStoreActivity.kt          155   asset browser and import
PlayerActivity.kt              112   in-app play mode
GameControlsView.kt            106   virtual joystick + A/B buttons
Ui.kt                          101   palette and widget helpers
HierarchyAdapter.kt             90   scene tree list
ColorPickerDialog.kt            79   colour property editor
History.kt                      44   undo/redo stack
```

## 1. Dark-mode palette

Defined once in `Ui.kt` as `C`, so every panel agrees:

```
BG      #1E1F22    PANEL   #2B2D31    PANEL2  #383A40
HEADER  #232428    FIELD   #1A1B1E    ACCENT  #4C8DFF
TEXT    #E6E6E6    DIM     #9AA0A6
```

## 2. The four panels

**Hierarchy** — the scene tree with parent/child nesting, selection, and
create/duplicate/delete. Objects can be created as children, which is what makes
composite props (a character with a shadow sprite, a torch with a point light)
editable as a unit.

**Inspector** — driven entirely by `Prop` metadata rather than hand-written forms.
Each `Component` declares `props()` returning a list of typed properties, and the
Inspector renders the right widget per type:

| `Prop` | Widget |
|---|---|
| `Prop.F` | slider + numeric field, with min/max/step |
| `Prop.B` | switch |
| `Prop.S` | text field |
| `Prop.Color` | swatch → `ColorPickerDialog` |
| `Prop.Choice` | dropdown over a fixed option list |
| `Prop.Asset` | asset picker filtered by `AssetKind` |

The consequence is that adding a component property requires no UI code, and —
importantly for the shipped games — **the Inspector's display names are the
serialisation keys**. A scene file stores `"Texture"`, not `"texture"`. This is why
`tools/validate_games.py` validates scene JSON against the `ComponentRegistry`
rather than against the Kotlin class fields.

**Viewport** — 2D (`ViewportController`) and 3D (`Viewport3DController`) with
pan/zoom/orbit, tap-to-pick, and a "Frame in View" command. Gizmo and selection
overlay state lives in `EditorState` so it is drawn by the same renderer as the
scene, which means what you select is exactly what renders.

**Console** — a log panel receiving `log`/`warn`/`error` from scripts and engine
subsystems, posted to the UI thread via a handler so a script cannot deadlock the
editor by logging from a worker.

## 3. Undo/redo

`History.kt` snapshots editor state; `↶`/`↷` toolbar buttons drive it. Selection
is restored with the snapshot (`history.undo(state.selectedId)`), so undoing a
delete reselects the object that came back — the small detail that makes undo feel
correct rather than merely functional.

## 4. Sprite-sheet animation editor

`AnimationEditorActivity` slices a sheet into a grid, lets you pick frames
individually or by row, reverse or ping-pong a selection, preview the loop, and
save a `.anim` clip:

```json
{ "texture": "hero_walk.png", "columns": 8, "rows": 1,
  "frames": [0,1,2,3,4,5,6,7], "fps": 12, "loop": true }
```

`frames` accepts range syntax (`"0-3, 5, 7-6"`), and `duration` is derived as
`frames.size / fps`.

This editor is what consumes the store's sheet detection: when an image with a
detected grid is imported, a `.anim` is generated alongside it, so a sheet imported
from the store is animatable without manual slicing.

## 5. Visual scripting

`Blueprint.kt` defines **49 node types**. `BlueprintView` renders the graph: drag
from an output pin to an input to connect execution flow, pinch to zoom, drag the
background to pan. Graphs serialise to `.bp` JSON and are attached to an object
through the same `Script` component a `.js` file uses, so a Blueprint and a script
are interchangeable from the runtime's point of view.

## 6. Touch controls

`GameControlsView` provides a virtual joystick on the left and A/B buttons on the
right; touches elsewhere pass through to the game. This is what `input.axisX`,
`input.axisY`, `input.a` and `input.b` read in scripts, and it is why the shipped
games can use the same input API on a phone and in the editor's play mode.

## 7. Features that do not exist

The brief called for several editors that are **not implemented**. They are listed
here rather than described as if present:

| Requested | Status |
|---|---|
| **Terrain editor** (sculpt, texture paint, foliage) | **Not implemented.** No terrain subsystem exists anywhere in the codebase. `game 3` fakes terrain with a scaled `Plane` mesh plus scattered Nature Kit props. A real one needs a heightmap texture, a sculpt brush writing to it, splat-map texture painting, and an instanced foliage renderer — none of which are present. |
| **Timeline animation editor** | **Not implemented.** `AnimationEditorActivity` authors frame-based sprite clips only. There is no keyframe track, no curve editing, no multi-track sequencing. |
| **3D skeletal animation** | **Not implemented.** No skeleton, bone, or skinning support. The 3D games billboard 2D sprite characters over 3D geometry, which is documented in their `engineNotes`. |
| **Node-based particle editor** | **Not implemented.** Particles are edited through Inspector properties, not a node graph. The Blueprint graph could host particle nodes — 49 node types exist — but no particle nodes are among them. |
| **Drag-and-drop UI builder** | **Partially.** UI is built by placing objects with `Screen Space UI` enabled and parenting them to the camera, and the Blueprint canvas does support drag-to-connect. There is no dedicated drag-and-drop canvas for laying out menus and HUDs. |

Each of these is a substantial subsystem, not a missing button. They are the main
outstanding work on the editor side, and they are ordered roughly by how much the
shipped games would benefit.
