import http from "node:http";
import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { validateGameSpec, normalizeGameSpec, toSengineProject, ENGINE_COMPONENTS, CHOICES } from "./schema.js";

const VERSION = "0.3.0";
const MAX_BODY = 1_000_000;
const MAX_PROMPT = 4_000;
const MAX_REPAIRS = 2;
const MODEL_ATTEMPTS = 3;
const DEFAULT_MODEL_TIMEOUT_MS = 120_000;
const DEFAULT_GENERATION_TIMEOUT_MS = 540_000;

const COMPONENT_FIELD_HELP = Object.entries(ENGINE_COMPONENTS)
  .map(([type, fields]) => `${type}: ${Object.keys(fields).map(k => `${k}(${fields[k]})`).join(", ")}`)
  .join("\n");
const CHOICE_HELP = Object.entries(CHOICES).map(([key, values]) => `${key}=${values.join("|")}`).join("; ");

const PLANNER_PROMPT = "You are the Game Director and Planner for S Engine. Turn the user request into an implementable game design. Return JSON with keys title, genre, coreLoop, controls, objective, winCondition, loseCondition, scenes (array of name and purpose), mechanics, requiredObjects (name, role and components), hud, progression, visualStyle, audioNotes, constraints and implementationNotes. Prefer a complete playable 2D game unless 3D is requested. Specify mobile touch controls and screen layout. Plan only supported S Engine components and script APIs. Do not invent engine capabilities or reveal chain-of-thought.";

const SYSTEM_PROMPT = `You are the autonomous game-generation engineer for S Engine 3.0, an existing Android Kotlin/OpenGL ES game engine.
Return ONLY one JSON object matching this GameSpec:
{
 "project":{"name":"string","startScene":"string","orientation":0},
 "scenes":[{"name":"Main","gravityX":0,"gravityY":-9.81,"objects":[
  {"id":1,"name":"Player","tag":"Player","active":true,"x":0,"y":0,"rotation":0,"scaleX":1,"scaleY":1,
   "components":[
    {"type":"SpriteRenderer","Shape":"Square","Color":"#FFFFFFFF","Texture":"","Screen Space UI":false},
    {"type":"Rigidbody2D","Body Type":"Dynamic","Mass":1,"Gravity Scale":1},
    {"type":"Collider2D","Shape":"Box","Width":1,"Height":1,"Is Trigger":false},
    {"type":"Script","Script":"Player.js","Params":""}
   ]}
 ]}],
 "assets":[{"name":"Player.js","content":"function start() {}\\nfunction update(dt) {}"}],
 "notes":[]
}
ENGINE CONTRACT (exact, case-sensitive serialized property names; never use lowercase aliases):
${COMPONENT_FIELD_HELP}
Choice values: ${CHOICE_HELP}. Color values are #AARRGGBB or #RRGGBB.
Component.fromJson ignores unknown fields and silently skips unsupported component types; generation must not rely on that behavior.
SCRIPT API CONTRACT (only use these actual globals and methods):
- Lifecycle functions: start(), update(dt), onCollision(other), onTrigger(other), onTriggerExit(other), onTap(), onDestroy(), onStop(), onFixedUpdate(dt), onLateUpdate(dt), onEnable(), onDisable(), onHotReload().
- self/transform/gameObject refer to the attached object. Properties: name, tag, active, x, y, rotation, scaleX, scaleY, worldX, worldY, vx, vy, grounded, visible, color, text. Methods: setPosition(x,y), move(dx,dy), rotate(deg), setVelocity(vx,vy), addForce(fx,fy), destroy(), child(name), distanceTo(other), distanceTo3(other), overlaps(other), is(other), send("fn",arg), hasComponent(type), setComponentEnabled(type,bool), setTexture(name), burst(n), setEmitting(bool), play("Run.anim"), stopAnimation(), isAnimationFinished(), setAnimSpeed(s), setShaderParam(v), setMeshColor(color), forward(), setSky(color), getParent(), setColor(color), setIntensity(v), setSize(v).
- input: axisX, axisY, a, b, aDown, bDown, touching, tapped, touchX, touchY, key(name).
- scene: find(name), findAll(tag), count(tag), spawn(name,x,y), load(sceneName), reload(), camera, gravityX, gravityY, broadcast(name,arg), obtain(pool), release(pool,object), raycast(...), raycastAll(...), terrainHeight(x,z), getCamera3D(), gravity3D(v).
- time: time.time, time.frame, time.fps. audio.play("file.wav"), audio.beep(freq,duration), audio.stopAll(), audio.play3d(...), audio.play3dLoop(...), audio.move3d(...), audio.stop3d(...), audio.setFalloff(...), audio.setBusVolume(...), audio.playMusic(...), audio.pauseMusic(), audio.resumeMusic(), audio.setMusicVolume(...).
- helpers: log(), warn(), error(), after(seconds,fn), every(seconds,fn), random(a,b), randomInt(a,b), clamp(v,a,b), lerp(a,b,t), waitSec(seconds), waitFrames(n).
- events.on/once/off/emit/last/count/clear; state.set/get/is/elapsed/update; persist.key; game.save/load/has/clear/keys/emit/getVersion/getEdition/getSchema/fixedStep/instructions/diagnostics; camera.setBounds/shake/zoomTo/snap/panTo/setPriority/activate/stopPath/playPath; physics.circleCast/boxCast/sphereCast/hitDistance/explode/attract/wind/joint/joint3; coroutine(function*(){ yield waitSec(seconds); yield waitFrames(n); }).
Use ES5-style compatible Rhino JavaScript (var, function, function* generators). Do not use let, const, class, async/await, arrow functions, template literals, optional chaining, nullish coalescing, eval, new Function, DOM, browser, Node.js, Unity, Godot, Phaser, Three.js, Babylon, fetch, XMLHttpRequest, setTimeout, setInterval, console, or hardcoded keys.
Scripts are text assets stored under assets/. A Script component MUST use the exact property "Script" (capital S) whose value equals an asset name ending in .js. Every scene needs a Camera or Camera3D and at least one Script. IDs must be unique positive integers within each scene; parent IDs must exist and must not form cycles. Include mobile touch controls and a useful playable loop with score/HUD, obstacles/enemies, win/loss and restart as appropriate. Use built-in shapes/meshes; do not claim binary art or audio generation. Add concise notes for any requested feature the engine cannot represent. Return valid JSON only.`;

const QA_PROMPT = `You are the independent QA reviewer for an S Engine GameSpec. Review the previous assistant JSON carefully.
Check JSON/game structure, exact component/property names, unique IDs, valid parents, allowed component types, Script asset references, syntactically valid Rhino scripts using only documented APIs, whether the requested game has a mobile playable loop, controls, collision/score/end/restart flow, and whether any unsupported feature was falsely claimed.
Return ONLY JSON: {"pass":true|false,"issues":["specific actionable issue"],"summary":"short assessment"}.
Do not reveal hidden chain-of-thought. Report only concise findings.`;

function configFromEnv(env) {
  return {
    port: Number(env.PORT || 3000),
    model: env.OPENAI_MODEL || "z-ai/glm-5.3-flash",
    apiBase: (env.OPENAI_BASE_URL || "https://openrouter.ai/api/v1").trim().replace(/\/+$/, ""),
    reasoningEnabled: env.OPENROUTER_REASONING_ENABLED !== "false",
    maxTokens: Math.min(65536, Math.max(4096, Number(env.OPENAI_MAX_TOKENS || 24576))),
    // Accept either a raw OpenRouter key or a pasted "Bearer <key>" value; never send an empty credential.\n    apiKey: String(env.OPENROUTER_API_KEY || env.OPENAI_API_KEY || "").trim().replace(/^Bearer\\s+/i, "").trim(),
    siteUrl: env.OPENROUTER_SITE_URL || "https://game-agent-production.up.railway.app",
    appName: env.OPENROUTER_APP_NAME || "S Engine Game Agent",
    apiToken: env.AGENT_API_TOKEN,
    corsOrigin: env.CORS_ORIGIN || "*",
    nodeEnv: env.NODE_ENV || "development",
  };
}

const json = (res, status, value, requestId, corsOrigin) => {
  if (res.headersSent || res.writableEnded) return;
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": corsOrigin || "*",
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type,authorization",
    "cache-control": "no-store",
    "x-request-id": requestId || "unknown",
  });
  res.end(JSON.stringify(value));
};

function errorStatus(error) {
  return Number.isInteger(error?.status) && error.status >= 400 && error.status < 600 ? error.status : 500;
}

function safeError(error, requestId) {
  const status = errorStatus(error);
  const redact = text => text
    .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
    .replace(/sk-[A-Za-z0-9_-]{12,}/g, "[redacted]")
    .slice(0, 500);
  const message = typeof error?.message === "string"
    ? redact(error.message)
    : "Game Agent request failed";
  return {
    error: message,
    details: Array.isArray(error?.details) ? error.details.map(x => redact(String(x)).slice(0, 300)).slice(0, 30) : [],
    qaSummary: typeof error?.qaSummary === "string" ? redact(error.qaSummary).slice(0, 1000) : null,
    requestId,
  };
}

async function readBody(req) {
  const declared = Number(req.headers["content-length"] || 0);
  if (Number.isFinite(declared) && declared > MAX_BODY) {
    req.resume();
    throw Object.assign(new Error("Request body too large"), { status: 413 });
  }
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) {
      req.resume();
      throw Object.assign(new Error("Request body too large"), { status: 413 });
    }
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString("utf8");
  try { return raw ? JSON.parse(raw) : {}; }
  catch { throw Object.assign(new Error("Body must be valid JSON"), { status: 400 }); }
}

function sameToken(expected, supplied) {
  const a = createHash("sha256").update(String(expected)).digest();
  const b = createHash("sha256").update(String(supplied)).digest();
  return timingSafeEqual(a, b);
}

function isAuthorized(req, apiToken) {
  if (!apiToken) return true;
  const header = String(req.headers.authorization || "");
  if (!header.startsWith("Bearer ")) return false;
  return sameToken(apiToken, header.slice(7).trim());
}

function messageText(message) {
  if (!message) return "";
  if (typeof message.content === "string") return message.content;
  if (Array.isArray(message.content)) {
    return message.content.map(part => typeof part === "string" ? part : (typeof part?.text === "string" ? part.text : "")).join("\n");
  }
  return "";
}

function assistantHistoryMessage(message) {
  return {
    role: "assistant",
    content: messageText(message),
    ...(message?.reasoning_details === undefined ? {} : { reasoning_details: message.reasoning_details }),
  };
}

function balancedJsonObjects(text) {
  const candidates = [];
  let start = -1;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (start < 0) {
      if (ch === "{") { start = i; depth = 1; inString = false; escaped = false; }
      continue;
    }
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === "\"") inString = false;
      continue;
    }
    if (ch === "\"") inString = true;
    else if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) { candidates.push(text.slice(start, i + 1)); start = -1; }
    }
  }
  return candidates;
}

export function parseModelJson(content, label) {
  const raw = typeof content === "string" ? content.trim() : messageText({ content });
  const cleaned = raw
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "")
    .trim();
  const attempts = [cleaned, ...balancedJsonObjects(cleaned)];
  for (const candidate of attempts) {
    if (!candidate) continue;
    try { return JSON.parse(candidate); }
    catch { /* try the next balanced JSON object */ }
  }
  throw Object.assign(new Error(`${label} response was not valid JSON`), { status: 502, retryable: false });
}

function combinedSignal(signal, timeoutMs) {
  const signals = [AbortSignal.timeout(timeoutMs)];
  if (signal) signals.unshift(signal);
  return AbortSignal.any(signals);
}

async function callOpenAiCompatible(messages, context) {
  const { config, model, signal, timeoutMs, fetchImpl } = context;
  if (!config.apiKey) {
    throw Object.assign(new Error("AI provider key is not configured on the Game Agent service"), { status: 503, retryable: false });
  }
  const attempt = Number(context.attempt || 1);
  const payload = {
    model: model || config.model,
    temperature: 0.25,
    max_tokens: config.maxTokens || 24576,
    messages,
    // OpenRouter's GLM reasoning models can spend the entire output budget thinking.
    // Keep the reasoning budget conservative and reserve room for the actual JSON.
    ...(config.reasoningEnabled ? { reasoning: { effort: "low" } } : {}),
    // Some reasoning/provider combinations return empty message.content with JSON mode.
    // Retry without constrained decoding; parseModelJson still validates the result.
    ...(attempt === 1 ? { response_format: { type: "json_object" } } : {}),
  };
  let response;
  try {
    response = await fetchImpl(`${config.apiBase}/chat/completions`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${config.apiKey}`,
        "content-type": "application/json",
        "HTTP-Referer": config.siteUrl,
        "X-Title": config.appName,
      },
      body: JSON.stringify(payload),
      signal: combinedSignal(signal, timeoutMs),
    });
  } catch (error) {
    if (signal?.aborted) throw Object.assign(new Error("Generation cancelled"), { status: 504, retryable: false });
    const aborted = error?.name === "AbortError" || error?.name === "TimeoutError";
    throw Object.assign(new Error(aborted ? "AI provider request timed out" : "AI provider transport failed"), { status: 504, retryable: aborted });
  }
  if (!response.ok) {
    const providerStatus = Number(response.status);
    // Billing/credit and authentication failures are not transient: retrying only burns time.
    if (providerStatus === 402) {
      throw Object.assign(new Error("OpenRouter returned 402 Payment Required: account credits or spending limit are insufficient for this model. Add credits or increase the limit in OpenRouter, then try again."), {
        status: 402,
        retryable: false,
        providerStatus,
      });
    }
    if (providerStatus === 401 || providerStatus === 403) {
      // Read a bounded provider diagnostic so missing Bearer auth is distinguishable
      // from an invalid key. Never return the raw provider body to the Android client.
      let providerMessage = "";
      try {
        const body = await response.clone().json();
        providerMessage = String(body?.error?.message || body?.message || "").slice(0, 300);
      } catch { /* ignore non-JSON provider errors */ }
      const cookieAuth = /no user or org id found in auth cookie|no cookie auth credentials found/i.test(providerMessage);
      const message = cookieAuth
        ? "OpenRouter received this request without valid API-key authentication. Check that Railway has a non-empty OPENROUTER_API_KEY (raw sk-or-v1-... key, without quotes), and that OPENAI_BASE_URL is https://openrouter.ai/api/v1. The Game Agent sends Authorization: Bearer automatically."
        : `OpenRouter rejected the API key or model access (${providerStatus}). Check the API key is active and has access to the selected model.`;
      throw Object.assign(new Error(message), {
        status: providerStatus,
        retryable: false,
        providerStatus,
      });
    }
    const retryable = [408, 409, 429, 500, 502, 503, 504].includes(providerStatus);
    // Never echo provider response bodies: they may contain sensitive metadata.
    throw Object.assign(new Error(`AI provider request failed (${providerStatus})`), { status: 502, retryable });
  }
  let data;
  try { data = await response.json(); }
  catch {
    throw Object.assign(new Error("AI provider returned a non-JSON response"), { status: 502, retryable: true });
  }
  const message = data?.choices?.[0]?.message;
  const content = messageText(message);
  if (!content.trim()) {
    const finishReason = String(data?.choices?.[0]?.finish_reason || "unknown").slice(0, 40);
    const completionTokens = Number.isFinite(data?.usage?.completion_tokens) ? data.usage.completion_tokens : "unknown";
    const diagnostic = `finish_reason=${finishReason}, completion_tokens=${completionTokens}`;
    throw Object.assign(new Error(`AI provider returned empty content (${diagnostic})`), {
      status: 502,
      retryable: true,
      providerDiagnostic: { finishReason, completionTokens, hasReasoning: Boolean(message?.reasoning || message?.reasoning_content) },
    });
  }
  return { ...message, content };
}

function delay(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(Object.assign(new Error("Generation cancelled"), { status: 504, retryable: false }));
    const timer = setTimeout(() => { signal?.removeEventListener("abort", onAbort); resolve(); }, ms);
    function onAbort() { clearTimeout(timer); reject(Object.assign(new Error("Generation cancelled"), { status: 504, retryable: false })); }
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

async function withRetry(fn, { signal, attempts, retryDelayMs }) {
  let last;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    if (signal?.aborted) throw Object.assign(new Error("Generation cancelled"), { status: 504, retryable: false });
    try { return await fn(attempt); }
    catch (error) {
      last = error;
      if (error?.retryable === false || attempt === attempts || signal?.aborted) break;
      await delay(retryDelayMs * (2 ** (attempt - 1)), signal);
    }
  }
  throw last;
}

function modelJson(label, message) {
  return parseModelJson(messageText(message), label);
}

async function generateGame(prompt, options) {
  const { callModel, model, signal } = options;
  const call = (messages, label) => withRetry(
    attempt => callModel(messages, { model, signal, label, attempt }),
    { signal, attempts: options.modelAttempts, retryDelayMs: options.retryDelayMs },
  );

  const plannerHistory = [
    { role: "system", content: PLANNER_PROMPT },
    { role: "user", content: prompt },
  ];
  const planMessage = await call(plannerHistory, "planner");
  const plan = modelJson("Planner", planMessage);
  if (!plan || typeof plan !== "object" || Array.isArray(plan)) throw Object.assign(new Error("Planner response was not an object"), { status: 502, retryable: false });

  const history = [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: "Build a complete playable S Engine GameSpec for this request: " + prompt },
    assistantHistoryMessage(planMessage),
    { role: "user", content: "Use the preceding design plan as the source of truth. Generate the complete GameSpec JSON now. DESIGN PLAN: " + JSON.stringify(plan) },
  ];

  let gameMessage;
  let spec;
  let normalizations = [];
  let warnings = [];
  let errors;
  let repairs = 0;
  let qaSummary = "Not yet reviewed";

  const acceptGameMessage = label => {
    const normalized = normalizeGameSpec(modelJson(label, gameMessage));
    spec = normalized.spec;
    normalizations = [...normalizations, ...normalized.normalizations];
    warnings = [...warnings, ...normalized.warnings];
    errors = validateGameSpec(spec);
  };

  gameMessage = await call(history, "builder");
  try {
    acceptGameMessage("Game generation");
  } catch (error) {
    if (errorStatus(error) !== 502 || !/not valid JSON/i.test(error.message)) throw error;
    errors = [`Builder returned malformed JSON: ${error.message}`];
    gameMessage = { role: "assistant", content: "{}" };
  }

  for (let attempt = 0; attempt <= MAX_REPAIRS; attempt++) {
    if (errors.length) {
      qaSummary = errors.join("; ");
      if (attempt === MAX_REPAIRS) break;
      const repairHistory = [
        ...history,
        assistantHistoryMessage(gameMessage),
        { role: "user", content: "Repair the GameSpec using these deterministic validation errors. Keep valid work and return the complete corrected GameSpec JSON only. ERRORS: " + errors.join("; ") },
      ];
      gameMessage = await call(repairHistory, "repair");
      repairs++;
      acceptGameMessage("Repair");
      continue;
    }

    const qaHistory = [
      ...history,
      assistantHistoryMessage(gameMessage),
      { role: "user", content: `${QA_PROMPT}\nORIGINAL REQUEST: ${prompt}\nFINAL GAMESPEC: ${JSON.stringify(spec)}` },
    ];
    let qaMessage;
    let qa;
    try {
      qaMessage = await call(qaHistory, "qa");
      qa = modelJson("QA", qaMessage);
    } catch (error) {
      if (errorStatus(error) !== 502) throw error;
      qa = { pass: false, issues: [`QA response was malformed: ${error.message}`], summary: "QA could not parse its response" };
      qaMessage = { role: "assistant", content: JSON.stringify(qa) };
    }
    const qaIssues = Array.isArray(qa.issues) ? qa.issues.filter(x => typeof x === "string" && x.trim()).map(x => x.slice(0, 300)) : [];
    errors = qa.pass === true && qaIssues.length === 0 ? [] : (qaIssues.length ? qaIssues : ["QA did not confirm the game is playable"]);
    qaSummary = typeof qa.summary === "string" ? qa.summary.slice(0, 1000) : errors.join("; ");
    if (!errors.length) break;
    if (attempt === MAX_REPAIRS) break;

    const repairHistory = [
      ...qaHistory,
      assistantHistoryMessage(qaMessage),
      { role: "user", content: "Repair the GameSpec using this QA feedback. Keep valid work and return the complete corrected GameSpec JSON only. QA issues: " + errors.join("; ") },
    ];
    gameMessage = await call(repairHistory, "repair");
    repairs++;
    acceptGameMessage("Repair");
  }

  if (errors.length) {
    throw Object.assign(new Error("Generated game did not pass QA"), { status: 422, details: errors, qaSummary, retryable: false });
  }
  return {
    id: randomUUID(),
    plan,
    spec,
    project: toSengineProject(spec),
    agent: {
      plannerModel: model,
      builderModel: model,
      qaModel: model,
      repairs,
      maxRepairs: MAX_REPAIRS,
      qaSummary,
      normalizations,
      warnings,
    },
    model,
  };
}

async function handle(req, res, runtime) {
  const requestId = randomUUID();
  const controller = new AbortController();
  let aborted = false;
  const abort = () => { if (!aborted) { aborted = true; controller.abort(); } };
  req.on("aborted", abort);
  res.on("close", () => { if (!res.writableEnded) abort(); });

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": runtime.config.corsOrigin,
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "content-type,authorization",
      "x-request-id": requestId,
    });
    return res.end();
  }
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  if (req.method === "GET" && url.pathname === "/health") {
    return json(res, 200, {
      ok: true,
      service: "S Engine Game Agent",
      version: VERSION,
      provider: runtime.config.apiBase.includes("openrouter.ai") ? "openrouter" : "openai",
      model: runtime.config.model,
      apiKeyConfigured: Boolean(runtime.config.apiKey),
    }, requestId, runtime.config.corsOrigin);
  }
  if (req.method === "GET" && url.pathname === "/api/agent/status") {
    return json(res, 200, {
      ready: Boolean(runtime.config.apiKey),
      provider: runtime.config.apiBase,
      model: runtime.config.model,
      reasoningEnabled: runtime.config.reasoningEnabled,
      qaEnabled: true,
      maxRepairs: MAX_REPAIRS,
      modelAttempts: runtime.modelAttempts,
      modelTimeoutMs: runtime.modelTimeoutMs,
      authRequired: Boolean(runtime.config.apiToken),
    }, requestId, runtime.config.corsOrigin);
  }

  if (!isAuthorized(req, runtime.config.apiToken) && req.method === "POST" && url.pathname.startsWith("/api/")) {
    console.warn(JSON.stringify({ requestId, event: "auth_failed", path: url.pathname }));
    return json(res, 401, { error: "Unauthorized: configure the Game Agent token in S Engine settings", requestId }, requestId, runtime.config.corsOrigin);
  }

  if (url.pathname === "/api/games/validate" || url.pathname === "/api/games/generate") {
    if (req.method !== "POST") return json(res, 405, { error: "Method not allowed", requestId }, requestId, runtime.config.corsOrigin);
    try {
      const body = await readBody(req);
      if (url.pathname === "/api/games/validate") {
        const errors = validateGameSpec(body?.spec);
        return json(res, errors.length ? 422 : 200, {
          valid: errors.length === 0,
          errors,
          supportedComponents: Object.keys(ENGINE_COMPONENTS),
          requestId,
        }, requestId, runtime.config.corsOrigin);
      }
      const prompt = body?.prompt;
      if (typeof prompt !== "string" || prompt.trim().length < 8 || prompt.length > MAX_PROMPT) {
        return json(res, 400, { error: `prompt must contain between 8 and ${MAX_PROMPT} characters`, requestId }, requestId, runtime.config.corsOrigin);
      }
      const requestedModel = body?.model;
      if (requestedModel !== undefined && (typeof requestedModel !== "string" || !/^[A-Za-z0-9._:/-]{1,120}$/.test(requestedModel))) {
        return json(res, 400, { error: "model must be a safe provider model identifier", requestId }, requestId, runtime.config.corsOrigin);
      }
      const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(runtime.generationTimeoutMs)]);
      const result = await generateGame(prompt.trim(), {
        ...runtime,
        model: requestedModel || runtime.config.model,
        signal,
      });
      console.log(JSON.stringify({ requestId, event: "generation_complete", repairs: result.agent.repairs, warnings: result.agent.warnings.length }));
      return json(res, 200, { ...result, requestId }, requestId, runtime.config.corsOrigin);
    } catch (error) {
      const status = errorStatus(error);
      console.warn(JSON.stringify({ requestId, event: "request_failed", status, message: status >= 500 ? "internal" : "validation" }));
      return json(res, status, safeError(error, requestId), requestId, runtime.config.corsOrigin);
    }
  }
  return json(res, 404, { error: "Not found", requestId }, requestId, runtime.config.corsOrigin);
}

export function createServer(options = {}) {
  const env = options.env || process.env;
  const config = configFromEnv(env);
  const runtime = {
    config,
    modelAttempts: options.modelAttempts || MODEL_ATTEMPTS,
    modelTimeoutMs: options.modelTimeoutMs || DEFAULT_MODEL_TIMEOUT_MS,
    generationTimeoutMs: options.generationTimeoutMs || DEFAULT_GENERATION_TIMEOUT_MS,
    retryDelayMs: options.retryDelayMs ?? 200,
    callModel: options.callModel || ((messages, context) => callOpenAiCompatible(messages, {
      config,
      model: context.model,
      signal: context.signal,
      timeoutMs: options.modelTimeoutMs || DEFAULT_MODEL_TIMEOUT_MS,
      attempt: context.attempt,
      fetchImpl: options.fetchImpl || globalThis.fetch,
    })),
  };
  const server = http.createServer((req, res) => {
    handle(req, res, runtime).catch(error => {
      console.error(JSON.stringify({ event: "unhandled_request_error", message: "internal" }));
      json(res, 500, { error: "Internal Game Agent error" }, "unknown", config.corsOrigin);
    });
  });
  server.requestTimeout = runtime.generationTimeoutMs + 30_000;
  server.headersTimeout = 65_000;
  return server;
}

export { configFromEnv, validateGameSpec, normalizeGameSpec, toSengineProject, VERSION };

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const server = createServer();
  const config = configFromEnv(process.env);
  server.listen(config.port, "0.0.0.0", () => {
    console.log(JSON.stringify({ event: "listening", version: VERSION, port: config.port, provider: config.apiBase, model: config.model, nodeEnv: config.nodeEnv }));
  });
}
