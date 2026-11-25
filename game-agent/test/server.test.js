import test from "node:test";
import assert from "node:assert/strict";
import { createServer, parseModelJson, VERSION } from "../src/server.js";
import { validateGameSpec, normalizeGameSpec, toSengineProject, validateBlueprintContent } from "../src/schema.js";

const SCRIPT = [
  "function start() { state.set('score', 0); }",
  "function update(dt) {",
  "  var dx = input.axisX * 4 * dt;",
  "  self.move(dx, 0);",
  "  if (input.tapped) { self.addForce(0, 3); }",
  "  if (time.time > 30) { state.update('score', 1); scene.reload(); }",
  "}",
  "function onTrigger(other) { state.update('score', 1); audio.beep(); }",
  "function onCollision(other) { game.save('score', state.get('score')); }",
].join("\n");

const camera2d = { type: "Camera", enabled: true, Size: 9, Background: "#FF101827", Priority: 10 };
const textHud = { type: "TextRenderer", enabled: true, Text: "Score 0", Size: 0.5, Color: "#FFFFFFFF", Align: "Center", "Screen Space UI": true };
const sprite = { type: "SpriteRenderer", enabled: true, Shape: "Square", Color: "#FF41D6A6", Texture: "", "Screen Space UI": false };
const rigidbody2d = { type: "Rigidbody2D", enabled: true, "Body Type": "Dynamic", Mass: 1, "Gravity Scale": 1 };
const collider2d = { type: "Collider2D", enabled: true, Shape: "Box", Width: 1, Height: 1, "Is Trigger": false };
const script = { type: "Script", enabled: true, Script: "Controller.js", Params: "" };
const camera3d = { type: "Camera3D", enabled: true, "Field of View": 60, Priority: 10, "Sky Top": "#FF3B7BD4" };
const mesh = { type: "MeshRenderer", enabled: true, Mesh: "Cube", Color: "#FF41D6A6", Texture: "" };
const light = { type: "Light", enabled: true, Type: "Directional", Intensity: 1 };
const rigidbody3d = { type: "Rigidbody3D", enabled: true, "Body Type": "Dynamic", Mass: 1 };
const collider3d = { type: "Collider3D", enabled: true, Shape: "Box", "Size X": 1, "Size Y": 1, "Size Z": 1 };

function gameSpec(title, components2d, threeDimensional = false) {
  return {
    project: { name: title, startScene: "Main", orientation: 0 },
    scenes: [{
      name: "Main",
      gravityX: 0,
      gravityY: threeDimensional ? 0 : -9.81,
      gravity3D: -9.81,
      objects: [
        { id: 1, name: "Main Camera", tag: "Untagged", x: 0, y: 0, components: [threeDimensional ? camera3d : camera2d] },
        { id: 2, name: "Player", tag: "Player", x: 0, y: 0, components: [...components2d, script] },
        { id: 3, name: "HUD", tag: "HUD", x: 0, y: 3, components: [textHud] },
      ],
    }],
    assets: [{ name: "Controller.js", content: SCRIPT }],
    notes: ["Built-in shapes only; no binary art/audio assets are generated."],
  };
}

const VALID_SPEC = gameSpec("Endless Runner", [sprite, rigidbody2d, collider2d]);

function scriptedProvider(responses) {
  const calls = [];
  let index = 0;
  const callModel = async (messages, context) => {
    calls.push({ messages, label: context.label, attempt: context.attempt, model: context.model });
    const next = responses[index++];
    if (next === undefined) throw new Error(`Unexpected provider call ${index}: ${context.label}`);
    if (typeof next === "function") return next(messages, context);
    if (next && next.throw) throw Object.assign(new Error(next.message || "provider failure"), { status: next.status || 502, retryable: next.retryable });
    if (typeof next === "string") return { role: "assistant", content: next };
    return next;
  };
  return { callModel, calls };
}

async function withServer(t, options, fn) {
  // Isolate tests from Railway build/runtime environment variables (especially AGENT_API_TOKEN).
  const server = createServer({ env: {}, retryDelayMs: 1, ...options });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  t.after(() => new Promise(resolve => server.close(resolve)));
  return fn(`http://127.0.0.1:${port}`, server);
}

async function request(base, path, { method = "GET", body, token, raw } = {}) {
  const headers = { "content-type": "application/json" };
  if (token) headers.authorization = `Bearer ${token}`;
  const response = await fetch(base + path, {
    method,
    headers,
    body: raw !== undefined ? raw : (body === undefined ? undefined : JSON.stringify(body)),
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data, text };
}

test("health, status and version never expose configured secrets", async t => {
  await withServer(t, { env: { OPENROUTER_API_KEY: "provider-secret-not-real", OPENAI_MODEL: "test/model" } }, async base => {
    const health = await request(base, "/health");
    assert.equal(health.status, 200);
    assert.equal(health.data.ok, true);
    assert.equal(health.data.version, VERSION);
    assert.equal(health.data.apiKeyConfigured, true);
    assert.ok(!health.text.includes("provider-secret-not-real"));
    const status = await request(base, "/api/agent/status");
    assert.equal(status.status, 200);
    assert.equal(status.data.ready, true);
    assert.equal(status.data.qaEnabled, true);
    assert.equal(status.data.maxRepairs, 2);
    assert.ok(!status.text.includes("provider-secret-not-real"));
    assert.equal((await request(base, "/missing")).status, 404);
  });
});

test("validate endpoint enforces auth, JSON shape, request limit and engine contract", async t => {
  const cases = [
    ["missing fields", { project: {}, scenes: [] }],
    ["unsupported component", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], objects: [VALID_SPEC.scenes[0].objects[0], { id: 2, name: "Bad", components: [{ type: "NetworkTransform" }] }] }] }],
    ["duplicate IDs", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], objects: VALID_SPEC.scenes[0].objects.map((o, i) => ({ ...o, id: i === 1 ? 1 : o.id })) }] }],
    ["invalid parent", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], objects: VALID_SPEC.scenes[0].objects.map(o => o.id === 2 ? { ...o, parent: 999 } : o) }] }],
    ["invalid script", { ...VALID_SPEC, assets: [{ name: "Controller.js", content: "function start() { self.teleport(1, 2); }" }] }],
    ["path traversal", { ...VALID_SPEC, assets: [{ name: "../Controller.js", content: SCRIPT }] }],
    ["missing script asset", { ...VALID_SPEC, assets: [] }],
    ["unknown serialized field", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], objects: VALID_SPEC.scenes[0].objects.map(o => o.id === 2 ? { ...o, components: o.components.map(c => c.type === "SpriteRenderer" ? { ...c, shape: "Circle" } : c) } : o) }] }],
    ["unknown project field", { ...VALID_SPEC, project: { ...VALID_SPEC.project, unityVersion: "2022" } }],
    ["unknown scene field", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], physicsEngine: "Box2D" }] }],
    ["unknown object field", { ...VALID_SPEC, scenes: [{ ...VALID_SPEC.scenes[0], objects: VALID_SPEC.scenes[0].objects.map(o => o.id === 2 ? { ...o, prefab: "Player" } : o) }] }],
  ];
  const token = "agent-token-not-real";
  await withServer(t, { env: { AGENT_API_TOKEN: token } }, async base => {
    assert.equal((await request(base, "/api/games/validate", { method: "POST", body: { spec: VALID_SPEC } })).status, 401);
    assert.equal((await request(base, "/api/games/validate", { method: "POST", body: { spec: VALID_SPEC }, token: "wrong" })).status, 401);
    const good = await request(base, "/api/games/validate", { method: "POST", body: { spec: VALID_SPEC }, token });
    assert.equal(good.status, 200);
    assert.equal(good.data.valid, true);
    assert.equal((await request(base, "/api/games/validate", { method: "POST", token, raw: "{not json" })).status, 400);
    assert.equal((await request(base, "/api/games/validate", { method: "POST", token, raw: "x".repeat(1_000_001) })).status, 413);
    assert.equal((await request(base, "/api/games/generate", { method: "GET", token })).status, 405);
    for (const [name, spec] of cases) {
      const result = await request(base, "/api/games/validate", { method: "POST", body: { spec }, token });
      assert.equal(result.status, 422, `${name} should be rejected`);
      assert.ok(result.data.errors.length > 0, `${name} should report errors`);
    }
  });
});

test("strict validation accepts all requested 2D and 3D game prototypes", () => {
  const prototypes = {
    "2D endless runner": gameSpec("2D Endless Runner", [sprite, rigidbody2d, collider2d]),
    "2D platformer": gameSpec("2D Platformer", [sprite, rigidbody2d, collider2d]),
    "2D top-down shooter": gameSpec("2D Top Down Shooter", [sprite, rigidbody2d, { ...collider2d, "Is Trigger": true }]),
    "3D obstacle runner": gameSpec("3D Obstacle Runner", [mesh, rigidbody3d, collider3d, light], true),
    "3D exploration": gameSpec("3D Exploration", [mesh, rigidbody3d, collider3d, light], true),
  };
  for (const [name, spec] of Object.entries(prototypes)) {
    assert.deepEqual(validateGameSpec(spec), [], `${name} has validation errors`);
    const project = toSengineProject(spec);
    assert.equal(project.format, "s-engine-project");
    assert.equal(project.scenes.length, 1);
    assert.equal(project.assets.length, 1);
  }
});

test("normalizer repairs unsafe assets, IDs, parents and unsupported fields without silent success", () => {
  const bad = JSON.parse(JSON.stringify(VALID_SPEC));
  bad.engine = "Unity";
  bad.project.unityVersion = "2022";
  bad.scenes[0].physicsEngine = "Box2D";
  bad.scenes[0].objects[1].prefab = "Player";
  bad.assets[0].name = "../Controller.js";
  bad.scenes[0].objects[2].id = 2;
  bad.scenes[0].objects[1].parent = 3;
  bad.scenes[0].objects[2].parent = 2;
  bad.scenes[0].objects[1].components.push({ type: "NetworkTransform" });
  bad.scenes[0].objects[1].components = bad.scenes[0].objects[1].components.map(c => c.type === "Script" ? { ...c, Script: "../Controller.js", script: "old-alias" } : c);
  const normalized = normalizeGameSpec(bad);
  assert.deepEqual(validateGameSpec(normalized.spec), []);
  assert.ok(normalized.warnings.length > 0);
  assert.ok(normalized.normalizations.some(n => n.includes("renamed unsafe asset")));
  assert.ok(normalized.normalizations.some(n => n.includes("removed unsupported component")));
  assert.ok(normalized.normalizations.some(n => n.includes("removed unsupported field")));
  assert.ok(!JSON.stringify(normalized.spec).includes("../"));
});

test("blueprint assets must use real graph nodes and reject arbitrary code nodes", () => {
  const valid = JSON.stringify({
    format: "sengine-blueprint-1",
    nodes: [{ id: 1, type: "OnStart", x: 0, y: 0, params: {} }, { id: 2, type: "Log", x: 1, y: 0, params: { message: "\"ready\"" } }],
    links: [{ from: 1, pin: "out", to: 2 }],
  });
  assert.deepEqual(validateBlueprintContent("Controller.bp", valid), []);
  assert.ok(validateBlueprintContent("Controller.bp", "{bad json").length > 0);
  assert.ok(validateBlueprintContent("Controller.bp", JSON.stringify({ format: "sengine-blueprint-1", nodes: [{ id: 1, type: "Code" }], links: [] })).length > 0);
  assert.ok(validateBlueprintContent("Controller.bp", JSON.stringify({ format: "sengine-blueprint-1", nodes: [], links: [] })).length > 0);
});

test("parseModelJson handles fenced and prose-wrapped JSON but rejects malformed responses", () => {
  assert.deepEqual(parseModelJson('```json\n{"ok":true}\n```', "Test"), { ok: true });
  assert.deepEqual(parseModelJson('Here is the result: {"a":1} trailing text', "Test"), { a: 1 });
  assert.throws(() => parseModelJson("not json at all", "Builder"), /not valid JSON/);
});

test("generation runs Planner, Builder and QA with a mocked provider", async t => {
  const provider = scriptedProvider([
    JSON.stringify({ title: "Runner", genre: "2D endless runner", coreLoop: "run, jump, score", controls: "tap" }),
    JSON.stringify(VALID_SPEC),
    JSON.stringify({ pass: true, issues: [], summary: "QA passed" }),
  ]);
  await withServer(t, { callModel: provider.callModel }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 2D endless runner with score and restart." } });
    assert.equal(response.status, 200);
    assert.equal(response.data.project.format, "s-engine-project");
    assert.equal(response.data.project.project.startScene, "Main");
    assert.equal(response.data.agent.repairs, 0);
    assert.equal(response.data.agent.qaSummary, "QA passed");
    assert.deepEqual(validateGameSpec(response.data.spec), []);
    assert.deepEqual(provider.calls.map(c => c.label), ["planner", "builder", "qa"]);
  });
});

test("malformed builder JSON and QA rejection enter explicit repair loops", async t => {
  const malformed = scriptedProvider([
    JSON.stringify({ title: "Runner", coreLoop: "run" }),
    "this is not JSON",
    JSON.stringify(VALID_SPEC),
    JSON.stringify({ pass: true, issues: [], summary: "Repaired QA passed" }),
  ]);
  await withServer(t, { callModel: malformed.callModel }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 2D platformer with touch controls." } });
    assert.equal(response.status, 200);
    assert.equal(response.data.agent.repairs, 1);
    assert.ok(malformed.calls.some(c => c.label === "repair"));
  });

  const qaSpec = JSON.parse(JSON.stringify(VALID_SPEC));
  qaSpec.scenes[0].objects[1].components.push({ type: "NetworkTransform" });
  const qaRepair = scriptedProvider([
    JSON.stringify({ title: "Runner", coreLoop: "run" }),
    JSON.stringify(qaSpec),
    JSON.stringify({ pass: false, issues: ["Use only supported components and improve restart flow"], summary: "Needs repair" }),
    JSON.stringify(VALID_SPEC),
    JSON.stringify({ pass: true, issues: [], summary: "QA passed after repair" }),
  ]);
  await withServer(t, { callModel: qaRepair.callModel }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a top-down shooter with enemies." } });
    assert.equal(response.status, 200);
    assert.equal(response.data.agent.repairs, 1);
    assert.equal(response.data.agent.qaSummary, "QA passed after repair");
    assert.ok(qaRepair.calls.some(c => c.label === "repair"));
  });
});

test("provider transport failures retry, and exhausted retries do not fabricate success", async t => {
  const retry = scriptedProvider([
    { throw: true, status: 502, retryable: true, message: "temporary provider failure" },
    JSON.stringify({ title: "Runner", coreLoop: "run" }),
    JSON.stringify(VALID_SPEC),
    JSON.stringify({ pass: true, issues: [], summary: "QA passed" }),
  ]);
  await withServer(t, { callModel: retry.callModel, modelAttempts: 3 }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 3D exploration prototype." } });
    assert.equal(response.status, 200);
    assert.equal(retry.calls[0].attempt, 1);
    assert.equal(retry.calls[1].attempt, 2);
    assert.equal(retry.calls[1].label, "planner");
  });

  const exhausted = scriptedProvider([
    { throw: true, status: 502, retryable: true, message: "temporary provider failure" },
    { throw: true, status: 502, retryable: true, message: "temporary provider failure" },
  ]);
  await withServer(t, { callModel: exhausted.callModel, modelAttempts: 2 }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 2D platformer with hazards." } });
    assert.equal(response.status, 502);
    assert.match(response.data.error, /AI provider request failed|temporary provider failure/);
    assert.equal(response.data.project, undefined);
  });
});

test("provider timeout is bounded and never echoes credentials", async t => {
  const fetchImpl = (url, init) => new Promise((resolve, reject) => {
    const onAbort = () => {
      const error = new Error("aborted");
      error.name = "AbortError";
      reject(error);
    };
    if (init.signal.aborted) onAbort();
    else init.signal.addEventListener("abort", onAbort, { once: true });
  });
  await withServer(t, {
    env: { OPENROUTER_API_KEY: "provider-secret-not-real" },
    fetchImpl,
    modelTimeoutMs: 10,
    modelAttempts: 1,
  }, async base => {
    const response = await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 3D obstacle runner prototype." } });
    assert.equal(response.status, 504);
    assert.ok(!response.text.includes("provider-secret-not-real"));
    assert.equal(response.data.project, undefined);
  });
});

test("generation rejects invalid prompts and models before any provider call", async t => {
  const provider = scriptedProvider([]);
  await withServer(t, { callModel: provider.callModel }, async base => {
    assert.equal((await request(base, "/api/games/generate", { method: "POST", body: { prompt: "short" } })).status, 400);
    assert.equal((await request(base, "/api/games/generate", { method: "POST", body: { prompt: "x".repeat(4001) } })).status, 400);
    assert.equal((await request(base, "/api/games/generate", { method: "POST", body: { prompt: "Create a 2D runner", model: { nested: true } } })).status, 400);
    assert.equal(provider.calls.length, 0);
  });
});


test("real OpenRouter adapter retries empty reasoning-only output without JSON mode", async t => {
  const payloads = [];
  const outputs = [
    { choices: [{ finish_reason: "length", message: { role: "assistant", content: "", reasoning: "private reasoning must not be returned" } }], usage: { completion_tokens: 24576 } },
    { choices: [{ finish_reason: "stop", message: { role: "assistant", content: JSON.stringify({ title: "Runner", coreLoop: "run, jump, score" }) } }] },
    { choices: [{ finish_reason: "stop", message: { role: "assistant", content: JSON.stringify(VALID_SPEC) } }] },
    { choices: [{ finish_reason: "stop", message: { role: "assistant", content: JSON.stringify({ pass: true, issues: [], summary: "Provider adapter QA passed" }) } }] },
  ];
  const fetchImpl = async (_url, init) => {
    payloads.push(JSON.parse(init.body));
    const body = outputs.shift();
    return new Response(JSON.stringify(body), { status:  200, headers: { "content-type": "application/json" } });
  };
  await withServer(t, {
    env: { OPENROUTER_API_KEY: "provider-secret-not-real", OPENAI_MAX_TOKENS: "24576" },
    fetchImpl,
    modelAttempts: 3,
  }, async base => {
    const response = await request(base, "/api/games/generate", {
      method: "POST",
      body: { prompt: "Create a polished 2D endless runner with score and restart." },
    });
    assert.equal(response.status, 200, JSON.stringify(response.data));
    assert.deepEqual(validateGameSpec(response.data.spec), []);
    assert.equal(payloads.length, 4);
    assert.deepEqual(payloads[0].response_format, { type: "json_object" });
    assert.equal(payloads[0].max_tokens, 24576);
    assert.deepEqual(payloads[0].reasoning, { effort: "low" });
    assert.equal(payloads[1].response_format, undefined);
    assert.equal(response.text.includes("private reasoning must not be returned"), false);
  });
});
