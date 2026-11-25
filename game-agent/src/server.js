import http from "node:http";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT || 3000);
const MODEL = process.env.OPENAI_MODEL || "z-ai/glm-5.3-flash";
const API_BASE = (process.env.OPENAI_BASE_URL || "https://openrouter.ai/api/v1").replace(/\\/$/, "");
const REASONING_ENABLED = process.env.OPENROUTER_REASONING_ENABLED !== "false";
const MAX_BODY = 1_000_000;
const MAX_REPAIRS = 2;
const COMPONENTS = new Set([
  "SpriteRenderer", "TextRenderer", "Camera", "Rigidbody2D", "Collider2D",
  "Script", "ParticleEmitter", "AudioSource", "Animator", "MeshRenderer",
  "Camera3D", "Light", "Rigidbody3D", "Collider3D", "Terrain", "Steering",
  "Joint2D", "Joint3D"
]);

const json = (res, status, value) => {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": process.env.CORS_ORIGIN || "*",
    "cache-control": "no-store"
  });
  res.end(JSON.stringify(value));
};

async function readBody(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > MAX_BODY) throw Object.assign(new Error("Request body too large"), { status: 413 });
  }
  try { return raw ? JSON.parse(raw) : {}; }
  catch { throw Object.assign(new Error("Body must be valid JSON"), { status: 400 }); }
}

function validateGameSpec(spec) {
  const errors = [];
  if (!spec || typeof spec !== "object" || Array.isArray(spec)) return ["spec must be an object"];
  if (!spec.project || typeof spec.project.name !== "string" || !spec.project.name.trim()) errors.push("project.name is required");
  if (!Array.isArray(spec.scenes) || spec.scenes.length === 0) errors.push("at least one scene is required");
  if (spec.scenes?.length > 30) errors.push("maximum 30 scenes are supported");
  const assets = Array.isArray(spec.assets) ? spec.assets : [];
  const assetNames = new Set(assets.map(a => a?.name).filter(Boolean));
  for (const [si, scene] of (spec.scenes || []).entries()) {
    if (!scene || typeof scene.name !== "string" || !scene.name.trim()) errors.push(`scenes[${si}].name is required`);
    if (!Array.isArray(scene.objects)) { errors.push(`scenes[${si}].objects must be an array`); continue; }
    if (scene.objects.length > 2000) errors.push(`scenes[${si}] exceeds 2000 objects`);
    const ids = new Set();
    for (const [oi, obj] of scene.objects.entries()) {
      const at = `scenes[${si}].objects[${oi}]`;
      if (!Number.isInteger(obj.id) || obj.id < 1) errors.push(`${at}.id must be a positive integer`);
      else if (ids.has(obj.id)) errors.push(`${at}.id is duplicated`);
      else ids.add(obj.id);
      if (typeof obj.name !== "string" || !obj.name.trim()) errors.push(`${at}.name is required`);
      if (!Array.isArray(obj.components)) { errors.push(`${at}.components must be an array`); continue; }
      for (const [ci, component] of obj.components.entries()) {
        if (!COMPONENTS.has(component?.type)) errors.push(`${at}.components[${ci}] uses unsupported component type: ${component?.type}`);
        if (component?.type === "Script" && component.script && !assetNames.has(component.script)) {
          errors.push(`${at} references missing script asset: ${component.script}`);
        }
      }
    }
    for (const obj of scene.objects) {
      if (obj.parent != null && !ids.has(Number(obj.parent))) errors.push(`object ${obj.name} references missing parent ${obj.parent}`);
    }
  }
  for (const asset of assets) {
    if (!asset || typeof asset.name !== "string" || typeof asset.content !== "string") {
      errors.push("each asset requires name and string content"); continue;
    }
    if (asset.name.includes("..") || asset.name.startsWith("/") || asset.name.includes("\\\\") || asset.name.includes("/")) {
      errors.push(`unsafe asset path: ${asset.name}`);
    }
  }
  return errors;
}

function toSengineProject(spec) {
  const scenes = spec.scenes.map(scene => ({
    name: scene.name,
    gravityX: scene.gravityX ?? 0,
    gravityY: scene.gravityY ?? -9.81,
    gravityZ: scene.gravityZ ?? 0,
    gravity3D: scene.gravity3D ?? -9.81,
    ambient: scene.ambient ?? "#FF4A505A",
    fogColor: scene.fogColor ?? "#FF9DB4CF",
    fogStart: scene.fogStart ?? 20,
    fogEnd: scene.fogEnd ?? 80,
    fog: scene.fog ?? false,
    nextId: Math.max(1, ...(scene.objects || []).map(o => Number(o.id) + 1)),
    objects: scene.objects.map(o => ({
      id: o.id, name: o.name, tag: o.tag ?? "Untagged", active: o.active ?? true,
      order: o.order ?? 0, x: o.x ?? 0, y: o.y ?? 0, rotation: o.rotation ?? 0,
      scaleX: o.scaleX ?? 1, scaleY: o.scaleY ?? 1, z: o.z ?? 0,
      rotX: o.rotX ?? 0, rotY: o.rotY ?? 0, scaleZ: o.scaleZ ?? 1,
      ...(o.parent == null ? {} : { parent: o.parent }),
      components: o.components
    }))
  }));
  return {
    format: "s-engine-project",
    formatVersion: 1,
    project: { name: spec.project.name, startScene: spec.project.startScene || scenes[0].name, orientation: spec.project.orientation ?? 0 },
    scenes,
    assets: spec.assets || [],
    notes: spec.notes || []
  };
}

const SYSTEM_PROMPT = `You are the autonomous game-generation engineer for S Engine 3.0, an existing Android Kotlin/OpenGL ES game engine.
Return ONLY one JSON object matching this GameSpec:
{
 "project":{"name":"string","startScene":"string","orientation":0},
 "scenes":[{"name":"Main","gravityX":0,"gravityY":-9.81,"objects":[
  {"id":1,"name":"Player","tag":"Player","active":true,"x":0,"y":0,"rotation":0,"scaleX":1,"scaleY":1,
   "components":[{"type":"SpriteRenderer","shape":0,"color":"#FFFFFFFF"},{"type":"Rigidbody2D","bodyType":0},{"type":"Collider2D","shape":0},{"type":"Script","script":"Player.js","params":""}]}
 ]}],
 "assets":[{"name":"Player.js","content":"function start() {}\\nfunction update(dt) {}"}],
 "notes":[]
}
Allowed component types only: SpriteRenderer, TextRenderer, Camera, Rigidbody2D, Collider2D, Script, ParticleEmitter, AudioSource, Animator, MeshRenderer, Camera3D, Light, Rigidbody3D, Collider3D, Terrain, Steering, Joint2D, Joint3D.
Use exact component names and engine-native serialized fields. Scene JSON uses name, gravityX/Y/Z, gravity3D, ambient, fogColor, fogStart, fogEnd, fog, nextId, objects. Object JSON uses id,name,tag,active,order,x,y,rotation,scaleX,scaleY,z,rotX,rotY,scaleZ,parent,components.
Scripts are Rhino JavaScript assets stored under assets/. Script component.script must exactly equal an asset name. Only use the engine's documented S Engine scripting API; never invent APIs. Prefer a complete playable 2D game unless 3D is requested. Include menu/title, game loop, player controls, obstacles/enemies, score/HUD, win/loss, restart and progression as appropriate. Use deterministic simple mechanics and built-in shapes when art is unavailable. IDs must be unique within each scene; parent IDs must exist. Every script must be syntactically valid, defensive, and use engine-supported APIs. Add concise notes for any requested feature the engine cannot represent. Return valid JSON only.`;

const QA_PROMPT = `You are the independent QA reviewer for an S Engine GameSpec. Review the previous assistant JSON carefully.
Check JSON/game structure, unique IDs, parent references, allowed component types, Script asset references, whether the requested game has a playable loop, controls, collision/score/end/restart flow, and whether scripts avoid invented APIs.
Return ONLY JSON: {"pass":true|false,"issues":["specific actionable issue"],"summary":"short assessment"}.
Do not reveal hidden chain-of-thought. Report only concise findings.`;

async function modelCall(messages, model) {
  if (!process.env.OPENAI_API_KEY) throw Object.assign(new Error("OPENAI_API_KEY is not configured on the Game Agent service"), { status: 503 });
  const body = {
    model: model || MODEL,
    temperature: 0.25,
    response_format: { type: "json_object" },
    messages,
    ...(REASONING_ENABLED ? { reasoning: { enabled: true } } : {})
  };
  const response = await fetch(`${API_BASE}/chat/completions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
      "content-type": "application/json",
      "HTTP-Referer": process.env.OPENROUTER_SITE_URL || "https://game-agent-production.up.railway.app",
      "X-Title": process.env.OPENROUTER_APP_NAME || "S Engine Game Agent"
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(120000)
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(data.error?.message || `Model request failed (${response.status})`), { status: 502 });
  const message = data.choices?.[0]?.message;
  if (!message?.content) throw Object.assign(new Error("Model returned an empty response"), { status: 502 });
  return message;
}

function parseModelJson(content, label) {
  try { return JSON.parse(content); }
  catch {
    const cleaned = content.replace(/^\`\`\`(?:json)?\\s*/i, "").replace(/\\s*\`\`\`$/, "");
    try { return JSON.parse(cleaned); }
    catch { throw Object.assign(new Error(`${label} response was not valid JSON`), { status: 502 }); }
  }
}

// Preserve OpenRouter's reasoning_details exactly as returned when the assistant turn is replayed.
function assistantHistoryMessage(message) {
  return {
    role: "assistant",
    content: message.content,
    ...(message.reasoning_details === undefined ? {} : { reasoning_details: message.reasoning_details })
  };
}

async function generate(prompt, options = {}) {
  const model = options.model || MODEL;
  const history = [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: `Create a complete playable S Engine game from this request. Return GameSpec JSON only.\\n\\nGAME REQUEST:\\n${prompt}` }
  ];
  let gameMessage = await modelCall(history, model);
  let spec = parseModelJson(gameMessage.content, "Game generation");
  let errors = validateGameSpec(spec);
  let qaSummary = "Not yet reviewed";
  let repairs = 0;

  for (let attempt = 0; attempt <= MAX_REPAIRS; attempt++) {
    if (errors.length) {
      qaSummary = errors.join("; ");
    } else {
      const qaHistory = [
        ...history,
        assistantHistoryMessage(gameMessage),
        { role: "user", content: QA_PROMPT }
      ];
      const qaMessage = await modelCall(qaHistory, model);
      const qa = parseModelJson(qaMessage.content, "QA");
      const qaIssues = Array.isArray(qa.issues) ? qa.issues.filter(x => typeof x === "string") : [];
      errors = qa.pass === true ? [] : (qaIssues.length ? qaIssues : ["QA did not confirm the game is playable"]);
      qaSummary = typeof qa.summary === "string" ? qa.summary : (errors.length ? errors.join("; ") : "QA passed");
      if (!errors.length) break;
      if (attempt === MAX_REPAIRS) break;
      const repairHistory = [
        ...qaHistory,
        assistantHistoryMessage(qaMessage),
        { role: "user", content: `Repair the GameSpec using this QA feedback. Keep all valid existing work, return the complete corrected GameSpec JSON only.\\nQA issues:\\n${errors.map(x => "- " + x).join("\\n")}` }
      ];
      gameMessage = await modelCall(repairHistory, model);
      spec = parseModelJson(gameMessage.content, "Repair");
      errors = validateGameSpec(spec);
      repairs++;
      continue;
    }
    if (attempt === MAX_REPAIRS) break;
    const repairHistory = [
      ...history,
      assistantHistoryMessage(gameMessage),
      { role: "user", content: `Fix these deterministic S Engine validation errors. Return the complete corrected GameSpec JSON only:\\n${errors.map(x => "- " + x).join("\\n")}` }
    ];
    gameMessage = await modelCall(repairHistory, model);
    spec = parseModelJson(gameMessage.content, "Repair");
    errors = validateGameSpec(spec);
    repairs++;
  }

  if (errors.length) throw Object.assign(new Error("Generated game did not pass QA"), { status: 422, details: errors, qaSummary });
  return {
    id: randomUUID(),
    spec,
    project: toSengineProject(spec),
    agent: { plannerModel: model, qaModel: model, repairs, qaSummary },
    model
  };
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": process.env.CORS_ORIGIN || "*",
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "content-type,authorization"
    });
    return res.end();
  }
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (req.method === "GET" && url.pathname === "/health") {
    return json(res, 200, { ok: true, service: "S Engine Game Agent", version: "0.2.0", provider: API_BASE.includes("openrouter.ai") ? "openrouter" : "openai", model: MODEL, apiKeyConfigured: Boolean(process.env.OPENAI_API_KEY) });
  }
  if (req.method === "GET" && url.pathname === "/api/agent/status") {
    return json(res, 200, { ready: Boolean(process.env.OPENAI_API_KEY), provider: API_BASE, model: MODEL, reasoningEnabled: REASONING_ENABLED, qaEnabled: true, maxRepairs: MAX_REPAIRS });
  }
  if (req.method === "POST" && url.pathname === "/api/games/validate") {
    try {
      const body = await readBody(req);
      const errors = validateGameSpec(body.spec);
      return json(res, errors.length ? 422 : 200, { valid: errors.length === 0, errors });
    } catch (error) { return json(res, error.status || 400, { error: error.message }); }
  }
  if (req.method === "POST" && url.pathname === "/api/games/generate") {
    try {
      const body = await readBody(req);
      if (typeof body.prompt !== "string" || body.prompt.trim().length < 8) return json(res, 400, { error: "prompt must contain at least 8 characters" });
      const result = await generate(body.prompt.trim(), { model: body.model });
      return json(res, 200, result);
    } catch (error) {
      return json(res, error.status || 500, { error: error.message, details: error.details || [], qaSummary: error.qaSummary || null });
    }
  }
  return json(res, 404, { error: "Not found" });
});

server.listen(PORT, "0.0.0.0", () => console.log(`S Engine Game Agent listening on ${PORT}; provider=${API_BASE}; model=${MODEL}`));
