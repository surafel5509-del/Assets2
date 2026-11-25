# S Engine Game Agent

Autonomous backend companion for the existing S Engine Android editor/runtime. It does not replace the Kotlin/OpenGL engine.

## Provider configuration (Railway)

Set these variables on the `Game Agent` service:

- `OPENROUTER_API_KEY` (the code also accepts `OPENAI_API_KEY` as a compatibility alias): required; paste your OpenRouter key into Railway Variables, never into the Android app or source code.
- `OPENAI_BASE_URL=https://openrouter.ai/api/v1`
- `OPENAI_MODEL=z-ai/glm-5.3-flash`
- `OPENROUTER_REASONING_ENABLED=true`
- Optional: `OPENROUTER_SITE_URL`, `OPENROUTER_APP_NAME`, `CORS_ORIGIN`, `AGENT_API_TOKEN`, `PORT`.

The service uses the OpenAI-compatible OpenRouter Chat Completions endpoint. When a model returns `reasoning_details`, the agent preserves that value unchanged in the assistant message sent in the next turn. It does not expose hidden reasoning to the client.

## Run locally

- Node.js 20+
- Set the environment variables above.
- `npm start`
- `npm run check`

## API

- `GET /health` — service, provider, model and API-key readiness.
- `GET /api/agent/status` — agent configuration and QA readiness (never returns secrets).
- `POST /api/games/validate` — validate a GameSpec against S Engine's registered component types and scene/object structure.
- `POST /api/games/generate` — run a Planner → Builder → QA pipeline, validate it, run an independent model QA pass, repair up to two times, and return a normalized S Engine project payload.

Example request:

```json
{ "prompt": "Create a polished 2D endless runner with a menu, score, obstacles, restart and game-over screen." }
```

## Android integration

The S Engine Projects screen includes an **AI Game** action. It sends the prompt to this service, receives the validated project payload, imports scenes through the engine's real `SceneSerializer.fromJson`, writes generated scripts/assets through `Project.writeAsset`, creates a local project, and opens it in the existing editor.

The Android client uses `AiGameClient.API_BASE`; change that constant if the Railway domain changes. Internet permission is declared in the app manifest.

## Engine contract and limitations

- Scene JSON follows the current `SceneSerializer` structure and component names from `ComponentRegistry.types`.
- Project files are stored in the app's existing `scenes/` and `assets/` directories.
- JavaScript behaviours run in the engine's Rhino runtime.
- The model must use actual S Engine script APIs; QA checks the generated project shape but cannot execute the Android renderer or prove every script's runtime behaviour.
- This milestone imports project data and scripts. It does not yet synthesize binary art/audio, run emulator-based gameplay QA, or export APKs automatically. Those need a further asset-generation and device-test pipeline.
