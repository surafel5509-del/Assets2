# S Engine Game Agent

A Railway-ready backend companion for the existing S Engine Android editor/runtime. It does not replace or reimplement the Kotlin/OpenGL engine.

## Run

- Node.js 20+
- Set `OPENAI_API_KEY` in the deployment environment.
- Optional: `OPENAI_MODEL` (default `gpt-4.1-mini`), `OPENAI_BASE_URL` (default OpenAI API base), `CORS_ORIGIN`, `PORT`.

```sh
npm start
```

## API

- `GET /health` — health check.
- `POST /api/games/validate` — validate a GameSpec against S Engine's registered component types and scene/object structure.
- `POST /api/games/generate` — generate a GameSpec from a natural-language prompt and return both the validated spec and a normalized S Engine project payload.

Example:

```json
{ "prompt": "Create a polished 2D endless runner with a menu, score, obstacles, restart and game-over screen." }
```

## Engine integration contract

The adapter is grounded in the current S Engine source:
- `SceneSerializer.toJson/fromJson` stores scene-level gravity, ambient/fog settings, nextId and objects.
- Each object stores id/name/tag/active/order, transforms, optional parent id, and components.
- Component names are restricted to the current `ComponentRegistry.types`.
- `Project.saveScene` writes `scenes/<name>.scene.json`; assets are stored in the project's `assets/` directory.
- JavaScript behaviours are Rhino assets attached with the `Script` component.

The response's `project` field is an interchange payload for a future S Engine import endpoint. The Android app does not yet call this backend automatically; a small authenticated HTTP client/import flow in S Engine is the next integration milestone. Do not assume a remote project-import API exists until that Android-side bridge is implemented.

## Current scope

This first milestone includes the HTTP service, prompt contract, schema normalization, component allowlist, asset reference checks, and validation. It does not yet generate binary art/audio assets, run Android rendering/physics tests, or export APKs. Those require an engine-side import/QA bridge and asset pipeline.
