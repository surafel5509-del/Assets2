# S Engine Game Agent

Autonomous backend companion for the existing S Engine Android editor/runtime. It does not replace the Kotlin/OpenGL ES/Rhino engine and does not invent component types or script APIs.

## Railway deployment configuration

The intended service settings are checked into `game-agent/railway.toml`:

- Service root: `/game-agent` (set in Railway; this file lives in that root).
- Builder: Nixpacks.
- Start command: `node src/server.js`.
- Healthcheck: `/health`, 30-second timeout.
- Branch: `arena/94ba1396-assets2` for this workspace.
- Node.js: 20 or newer (`package.json` `engines`).

Set these variable **names** in Railway Variables. No values belong in Git, chat, logs, or the Android APK:

- `OPENROUTER_API_KEY` (canonical) or `OPENAI_API_KEY` (compatibility alias): provider key.
- `OPENAI_BASE_URL`: OpenAI-compatible Chat Completions base URL, default `https://openrouter.ai/api/v1`.
- `OPENAI_MODEL`: provider model identifier, default `z-ai/glm-5.3-flash`.
- `OPENROUTER_REASONING_ENABLED`: set to `false` to disable OpenRouter reasoning.
- `OPENROUTER_APP_NAME`: optional X-Title application name.
- `OPENROUTER_SITE_URL`: optional HTTP-Referer value.
- `AGENT_API_TOKEN`: optional bearer token required for `POST /api/*`; use a strong random value and configure the same token in the Android AI Game dialog.
- `NODE_ENV`, `PORT`, `CORS_ORIGIN`: optional runtime settings.

`/.env.example` lists names only. Real `.env` files are ignored. `/health` and `/api/agent/status` reveal configuration state, never credential values. `/api/agent/status` reports `authRequired` so clients can explain a 401 without exposing the token.

## Run and test locally

- Node.js 20+
- `npm run check` — syntax check both modules.
- `npm test` — offline suite with mocked provider calls; no real API key is needed.
- `npm start` — start the service (requires a provider key for generation).

## API

- `GET /health` — service, version, provider family, model, and whether a provider key is configured.
- `GET /api/agent/status` — provider base, model, reasoning/QA/retry/timeout state, and auth requirement (never secrets).
- `POST /api/games/validate` — strict validation of `{ "spec": GameSpec }`. Returns 200 with `valid: true`, or 422 with actionable `errors`.
- `POST /api/games/generate` — Planner → Builder → independent QA → Repair. Returns 200 with `plan`, strict-validated `spec`, normalized `project`, and an `agent` report including repairs, QA summary, normalizations, and warnings. Provider failures, malformed JSON, exhausted retries, QA rejection, and timeouts return explicit error statuses rather than a fabricated project.

Example request:

```json
{ "prompt": "Create a polished 2D endless runner with a menu, score, obstacles, restart and game-over screen." }
```

The HTTP layer enforces a 1 MB body limit, 4,000-character prompt limit, safe model identifiers, constant-time bearer-token comparison when `AGENT_API_TOKEN` is configured, per-call and overall generation timeouts, request-cancellation propagation, retryable provider transport failures, secret-free logs, and structured error responses.

## GameSpec and engine contract

`src/schema.js` derives its contract from the engine's actual `ComponentRegistry.types` and case-sensitive inspector property labels. It rejects:

- unknown component types and unknown component fields;
- invalid numeric/boolean/string/color/choice/asset values;
- duplicate or non-positive object IDs and invalid/cyclic parents;
- scene/project names that could create unsafe file paths;
- path traversal, absolute paths, duplicate names, unsupported binary asset names, and oversized assets;
- missing script assets, invalid JavaScript syntax, unsupported modern Rhino syntax, foreign engine/DOM/Node.js APIs, and undocumented engine namespace methods;
- scenes with no camera, no script, or insufficient playable objects.

Generation normalizes recoverable model mistakes (for example a lowercase `script` alias or a missing camera), but every removal/rename is reported in `agent.normalizations` and `agent.warnings`. Strict `/validate` never applies those repairs. `toSengineProject` emits the exact `SceneSerializer` scene/project shape and preserves `Script` component property casing.

Scripts are Rhino JavaScript text assets attached through the engine's `Script` component. The supported API list is constrained to the documented lifecycle, `self`, `input`, `scene`, `time`, `audio`, helpers, `events`, `state`, `physics`, `soft`, `camera`, `game`, `studio`, and coroutine surfaces exposed by `ScriptEditorActivity`/`ScriptSystem`.

## Android integration

The S Engine Projects screen includes an **AI Game** action. It sends the prompt and optional token only to the HTTPS Railway endpoint, parses the response strictly against actual engine component properties, writes scripts/assets with `Project.writeAsset`, saves scenes with `Project.saveScene`, verifies the saved project, and opens it in the existing editor. Failed imports are cleaned up; the app disallows cleartext traffic and does not log the token. The progress dialog is cancellable and the network call runs off the UI thread.

## Verified limitations

- QA performs structural, static script/API, and model review; it does not execute the Android renderer or prove every script's runtime behavior.
- Binary art/audio generation is **not implemented**. Generated games use built-in shapes/meshes, text, and documented procedural APIs; missing binary asset references are rejected or reported.
- Real-provider generation can only be tested when a provider key and optional access token are securely available. Ordinary tests mock the provider and never require production credentials.
- Emulator/device gameplay testing and automatic APK export from the backend are outside this service.
