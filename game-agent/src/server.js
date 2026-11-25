import http from "node:http";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT || 3000);
const MODEL = process.env.OPENAI_MODEL || "gpt-4.1-mini";
const API_BASE = (process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, "");
const MAX_BODY = 1_000_000;

const COMPONENTS = new Set([
  "SpriteRenderer", "TextRenderer", "Camera", "Rigidbody2D", "Collider2D",
  "Script", "ParticleEmitter", "AudioSource", "Animator", "MeshRenderer",
  "Camera3D", "Light", "Rigidbody3D", "Collider3D", "Terrain", "Steering",
  "Joint2D", "Joint3D"
]);

const json = (res, status, value) => {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": process.env.CORS_ORIGIN || "*" });
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
  if (!spec || typeof spec !== "object") return ["spec must be an object"];
  if (!spec.project || typeof spec.project.name !== "string") errors.push("project.name is required");
  if (!Array.isArray(spec.scenes) || spec.scenes.length === 0) errors.push("at least one scene is required");
  const assetNames = new Set((spec.assets || []).map(a => a?.name).filter(Boolean));
  for (const [si, scene] of (spec.scenes || []).entries()) {
    if (!scene || typeof scene.name !== "string") errors.push(`scenes[${si}].name is required`);
    if (!Array.isArray(scene.objects)) { errors.push(`scenes[${si}].objects must be an array`); continue; }
    const ids = new Set();
    for (const [oi, obj] of scene.objects.entries()) {
      const at = `scenes[${si}].objects[${oi}]`;
      if (!Number.isInteger(obj.id) || obj.id < 1) errors.push(`${at}.id must be a positive integer`);
      else if (ids.has(obj.id)) errors.push(`${at}.id is duplicated`);
      else ids.add(obj.id);
      if (typeof obj.name !== "string" || !obj.name) errors.push(`${at}.name is required`);
      if (!Array.isArray(obj.components)) { errors.push(`${at}.components must be an array`); continue; }
      for (const [ci, component] of obj.components.entries()) {
        if (!COMPONENTS.has(component?.type)) errors.push(`${at}.components[${ci}] uses unsupported component type: ${component?.type}`);
        if (component?.type === "Script" && component.script && !assetNames.has(component.script)) {
          errors.push(`${at} references missing script asset: ${component.script}`);
        }
      }
    }
    for (const obj of scene.objects || []) {
      if (obj.parent != null && !ids.has(Number(obj.parent))) errors.push(`object ${obj.name} references missing parent ${obj.parent}`);
    }
  }
  for (const asset of spec.assets || []) {
    if (!asset || typeof asset.name !== "string" || typeof asset.content !== "string") errors.push("each asset requires name and string content");
    if (asset?.name && (asset.name.includes("..") || asset.name.startsWith("/") || asset.name.includes("\\"))) errors.push(`unsafe asset path: ${asset.name}`);
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

const SYSTEM_PROMPT = `You are the game-generation planner for S Engine 3.0, an Android Kotlin game engine.
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
The only allowed component types are: SpriteRenderer, TextRenderer, Camera, Rigidbody2D, Collider2D, Script, ParticleEmitter, AudioSource, Animator, MeshRenderer, Camera3D, Light, Rigidbody3D, Collider3D, Terrain, Steering, Joint2D, Joint3D.
Scene JSON follows S Engine SceneSerializer: scene keys name, gravityX/Y/Z, gravity3D, ambient, fogColor, fogStart, fogEnd, fog, nextId, objects. Object keys id,name,tag,active,order,x,y,rotation,scaleX,scaleY,z,rotX,rotY,scaleZ,parent,components. Component entries must use the exact registered type and their engine-native properties. Scripts are Rhino JavaScript assets under assets/; Script component.script must exactly match an asset name. Prefer proven 2D components unless user explicitly asks for 3D. Use simple geometry and built-in shapes when external art is unavailable. Build a complete playable loop: title/menu, gameplay, win/loss or results, controls, restart, HUD, and progression as appropriate. Never invent unsupported engine APIs. Include clear implementation notes for any requested feature the engine cannot represent. Keep object IDs unique per scene and parent IDs valid. Return valid JSON, no markdown fences.`;

async function generate(prompt, options = {}) {
  if (!process.env.OPENAI_API_KEY) throw Object.assign(new Error("OPENAI_API_KEY is not configured"), { status: 503 });
  const response = await fetch(`${API_BASE}/chat/completions`, {
    method: "POST",
    headers: { authorization: `Bearer ${process.env.OPENAI_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({
      model: options.model || MODEL,
      temperature: 0.35,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        { role: "user", content: `Create a complete S Engine game from this request. Return GameSpec JSON only.\n\nGAME REQUEST:\n${prompt}` }
      ]
    }),
    signal: AbortSignal.timeout(120000)
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(data.error?.message || `Model request failed (${response.status})`), { status: 502 });
  const content = data.choices?.[0]?.message?.content;
  if (!content) throw Object.assign(new Error("Model returned an empty response"), { status: 502 });
  let spec;
  try { spec = JSON.parse(content); }
  catch { throw Object.assign(new Error("Model response was not valid JSON"), { status: 502 }); }
  const errors = validateGameSpec(spec);
  if (errors.length) throw Object.assign(new Error("Generated game failed S Engine validation"), { status: 422, details: errors });
  return { id: randomUUID(), spec, project: toSengineProject(spec), model: options.model || MODEL };
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, { "access-control-allow-origin": process.env.CORS_ORIGIN || "*", "access-control-allow-methods": "GET,POST,OPTIONS", "access-control-allow-headers": "content-type,authorization" });
    return res.end();
  }
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (req.method === "GET" && url.pathname === "/health") return json(res, 200, { ok: true, service: "S Engine Game Agent", version: "0.1.0" });
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
    } catch (error) { return json(res, error.status || 500, { error: error.message, details: error.details || [] }); }
  }
  return json(res, 404, { error: "Not found" });
});

server.listen(PORT, "0.0.0.0", () => console.log(`S Engine Game Agent listening on ${PORT}`));
