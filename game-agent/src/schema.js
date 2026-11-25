import vm from "node:vm";

export const ENGINE_COMPONENTS = {
  SpriteRenderer: {
    Shape: "choice", Color: "color", Texture: "asset", "Flip X": "bool", "Flip Y": "bool",
    Shader: "asset", "Shader Param": "number", "Screen Space UI": "bool",
  },
  TextRenderer: {
    Text: "string", "Screen Space UI": "bool", Size: "number", Color: "color",
    Align: "choice", Bold: "bool",
  },
  Camera: {
    Size: "number", Background: "color", "Follow Target": "string", "Follow Smoothing": "number",
    "Post FX": "choice", "FX Intensity": "number", "FX Shader": "asset", Priority: "int",
    "Dead Zone W": "number", "Dead Zone H": "number", "Look Ahead": "number",
    "Clamp To Bounds": "bool", "Bound Min X": "number", "Bound Max X": "number",
    "Bound Min Y": "number", "Bound Max Y": "number", "Min Zoom": "number",
    "Max Zoom": "number", "Trauma Decay": "number", "Shake Scale": "number",
  },
  Rigidbody2D: {
    "Body Type": "choice", Mass: "number", "Gravity Scale": "number", "Linear Drag": "number",
    Bounciness: "number", Friction: "number", "Velocity X": "number", "Velocity Y": "number",
    "Max Speed": "number", "Continuous Collision": "bool",
  },
  Collider2D: {
    Shape: "choice", Width: "number", Height: "number", Radius: "number",
    "Offset X": "number", "Offset Y": "number", "Is Trigger": "bool",
    Layer: "int", Mask: "int",
  },
  Script: { Script: "asset", Params: "string" },
  ParticleEmitter: {
    Emitting: "bool", Rate: "number", Lifetime: "number", Speed: "number", Direction: "number",
    Spread: "number", "Start Size": "number", "End Size": "number", "Start Color": "color",
    "End Color": "color", Gravity: "number", "Max Particles": "int",
    "Additive Blend": "bool", Texture: "asset",
  },
  AudioSource: { Clip: "asset", "Play On Start": "bool", Loop: "bool", Volume: "number" },
  Animator: { Clip: "asset", "Play On Start": "bool", Speed: "number" },
  MeshRenderer: {
    Mesh: "choice", "Model (.obj)": "asset", Color: "color", Texture: "asset", Tiling: "number",
    Specular: "number", Shininess: "number", Emission: "number", Unlit: "bool",
    Shader: "asset", "Shader Param": "number",
  },
  Camera3D: {
    "Field of View": "number", Near: "number", Far: "number", "Sky Top": "color",
    "Sky Horizon": "color", "Follow Target": "string", "Offset X": "number",
    "Offset Y": "number", "Offset Z": "number", "Look At Target": "bool",
    "Follow Smoothing": "number", "Post FX": "choice", "FX Intensity": "number",
    "FX Shader": "asset", Priority: "int", "Dead Zone": "number", "Look Ahead": "number",
    "Clamp To Bounds": "bool", "Bound Min X": "number", "Bound Max X": "number",
    "Bound Min Z": "number", "Bound Max Z": "number", "Trauma Decay": "number",
    "Shake Scale": "number",
  },
  Light: { Type: "choice", Color: "color", Intensity: "number", Range: "number" },
  Rigidbody3D: {
    "Body Type": "choice", Mass: "number", "Gravity Scale": "number", "Linear Drag": "number",
    Bounciness: "number", Friction: "number", "Velocity X": "number", "Velocity Y": "number",
    "Velocity Z": "number", "Max Speed": "number", "Continuous Collision": "bool",
  },
  Collider3D: {
    Shape: "choice", "Size X": "number", "Size Y": "number", "Size Z": "number",
    Radius: "number", "Center X": "number", "Center Y": "number", "Center Z": "number",
    "Is Trigger": "bool", Layer: "int", Mask: "int",
  },
  Terrain: {
    Size: "number", Resolution: "int", Amplitude: "number", Frequency: "number",
    Octaves: "int", Seed: "int", Collide: "bool", Color: "color", Texture: "asset",
    Tiling: "number", Specular: "number", Emission: "number",
  },
  Steering: {
    Mode: "choice", Target: "string", Speed: "number", Acceleration: "number",
    "Slowing Radius": "number", "Wander Strength": "number", "Wander Turn": "number",
    "Patrol Distance": "number", "Face Movement": "bool",
  },
  Joint2D: {
    Mode: "choice", Target: "string", "Rest Length": "number", Stiffness: "number",
    Damping: "number", "Break Force": "number",
  },
  Joint3D: {
    Mode: "choice", Target: "string", "Rest Length": "number", Stiffness: "number",
    Damping: "number", "Break Force": "number",
  },
};

const POST_FX = ["None", "Grayscale", "Sepia", "Vignette", "CRT", "Pixelate", "Bloom", "Invert", "Chromatic", "Custom Shader"];
const BODY_TYPES = ["Dynamic", "Kinematic", "Static"];
const JOINT_MODES = ["Distance", "Spring", "Rope", "Weld"];

export const CHOICES = {
  "SpriteRenderer.Shape": ["Square", "Circle", "Triangle"],
  "TextRenderer.Align": ["Left", "Center", "Right"],
  "Camera.Post FX": POST_FX,
  "Rigidbody2D.Body Type": BODY_TYPES,
  "Collider2D.Shape": ["Box", "Circle"],
  "MeshRenderer.Mesh": ["Cube", "Sphere", "Plane", "Cylinder", "Cone", "Torus", "Capsule", "Pyramid", "Custom Model"],
  "Camera3D.Post FX": POST_FX,
  "Light.Type": ["Directional", "Point"],
  "Rigidbody3D.Body Type": BODY_TYPES,
  "Collider3D.Shape": ["Box", "Sphere"],
  "Steering.Mode": ["Seek", "Flee", "Pursue", "Wander", "Patrol"],
  "Joint2D.Mode": JOINT_MODES,
  "Joint3D.Mode": JOINT_MODES,
};

const ASSET_FIELDS = new Set([
  "Script", "Texture", "Shader", "FX Shader", "Clip", "Model (.obj)",
]);

const COLOR_RE = /^#?[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/;
export const SAFE_NAME_RE = /^[A-Za-z0-9][A-Za-z0-9._ -]{0,79}$/;
const SAFE_PROJECT_RE = /^[A-Za-z0-9 _-]{1,40}$/;
const TEXT_ASSET_EXTENSIONS = new Set([".js", ".bp", ".glsl", ".anim", ".obj", ".txt", ".json"]);
const MAX_ASSET_BYTES = 1_000_000;
const SPEC_KEYS = new Set(["project", "scenes", "assets", "notes"]);
const PROJECT_KEYS = new Set(["name", "startScene", "orientation"]);
const SCENE_KEYS = new Set([
  "name", "gravityX", "gravityY", "gravityZ", "gravity3D", "ambient", "fogColor",
  "fogStart", "fogEnd", "fog", "nextId", "objects",
]);
const OBJECT_KEYS = new Set([
  "id", "name", "tag", "active", "order", "x", "y", "z", "rotation", "rotX", "rotY",
  "scaleX", "scaleY", "scaleZ", "parent", "components",
]);
const ASSET_KEYS = new Set(["name", "content"]);

function unknownKeys(value, allowed, at) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return [];
  return Object.keys(value).filter(key => !allowed.has(key)).map(key => `${at} has unsupported field '${key}'`);
}

const ALLOWED_SCRIPT_NAMESPACE_METHODS = {
  events: ["on", "once", "off", "emit", "last", "count", "clear"],
  state: ["set", "get", "is", "elapsed", "update"],
  physics: ["circleCast", "boxCast", "sphereCast", "hitDistance", "explode", "attract", "wind", "joint", "joint3", "setIterations", "getIterations", "getJointIterations", "getBroadphasePairs", "getBruteForcePairs", "getFixedStep", "getAlpha", "getSteps", "getSeed", "getJointCount"],
  soft: ["rope", "cloth", "point", "setPoint", "pin", "wind", "force", "explode", "step", "remove", "count", "names"],
  camera: ["getX", "getY", "getZ", "getZoom", "getFov", "getPriority", "isBlending", "isCinematic", "setBounds", "setDeadZone", "setLookAhead", "zoomTo", "shake", "snap", "panTo", "setPriority", "activate", "stopPath", "playPath"],
  game: ["save", "load", "has", "clear", "keys", "emit", "getVersion", "getEdition", "getSchema", "fixedStep", "instructions", "diagnostics"],
  studio: ["report", "brief", "progress", "years", "months", "next", "milestones", "complete", "reset", "diagnostics"],
  scene: ["getName", "find", "findAll", "count", "spawn", "load", "reload", "getCamera", "getGravityX", "setGravityX", "getGravityY", "setGravityY", "shake", "broadcast", "pool", "obtain", "release", "raycast", "raycast2D", "raycastAll", "raycastAll3D", "overlapCircle", "overlapSphere", "terrainHeight", "getCamera3D", "gravity3D"],
  audio: ["play", "beep", "stopAll", "play3d", "play3dLoop", "move3d", "stop3d", "setFalloff", "setBusVolume", "playMusic", "pauseMusic", "resumeMusic", "setMusicVolume"],
  self: ["setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"],
  transform: ["setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"],
  gameObject: ["setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"],
  input: ["key"],
};

const FORBIDDEN_SCRIPT_PATTERNS = [
  [/\b(document|window|navigator|location)\s*\./, "browser DOM access"],
  [/\b(Unity|Godot|Phaser|Three|Babylon)\b/, "foreign engine API"],
  [/\b(requestAnimationFrame|setTimeout|setInterval|setImmediate|fetch|XMLHttpRequest|alert|prompt|confirm)\s*\(/, "unsupported host API"],
  [/\bconsole\s*\./, "console API is not part of S Engine's Rhino prelude"],
  [/\b(import|export|class|async|await|let|const)\b/, "modern syntax not supported by the embedded Rhino subset"],
  [/=>/, "arrow functions are not supported by the embedded Rhino subset"],
  [/\?\?/, "nullish coalescing is not supported by the embedded Rhino subset"],
  [/\?\./, "optional chaining is not supported by the embedded Rhino subset"],
  [/\beval\s*\(/, "eval is not allowed"],
  [/\bnew\s+Function\s*\(/, "dynamic Function construction is not allowed"],
  [/\bprocess\s*\./, "Node.js API is not available in Rhino"],
];

const LIFECYCLE_RE = /\bfunction\s+(start|update|onFixedUpdate|onLateUpdate|onEnable|onDisable|onCollision|onTrigger|onTriggerExit|onTap|onDestroy|onStop|onHotReload)\s*\(/;
const BLUEPRINT_NODE_TYPES = new Set([
  "AddForce", "AddVar", "Beep", "Burst", "Chance", "Cooldown", "Controller3D", "DestroyOther",
  "DestroySelf", "FlipX", "If", "JoystickMove", "Jump", "LoadScene", "Log", "Move", "Move3D",
  "OnButtonA", "OnButtonB", "OnCollision", "OnMessage", "OnStart", "OnTap", "OnTimer", "OnTouch",
  "OnTrigger", "OnTriggerExit", "OnUpdate", "Once", "Platformer", "PlayAnimation", "PlaySound",
  "Reload", "Repeat", "Rotate", "Rotate3D", "SendMessage", "Sequence", "SetActive", "SetColor",
  "SetPosition", "SetText", "SetVar", "SetVelocity", "Shake", "Spawn", "Wait",
]);
const BLUEPRINT_EVENT_TYPES = new Set([
  "OnStart", "OnUpdate", "OnTap", "OnTouch", "OnButtonA", "OnButtonB", "OnCollision",
  "OnTrigger", "OnTriggerExit", "OnTimer", "OnMessage",
]);

function safeAssetName(name) {
  if (typeof name !== "string") return false;
  if (!SAFE_NAME_RE.test(name)) return false;
  if (name.includes("..") || name.includes("/") || name.includes("\\") || name.startsWith(".")) return false;
  const ext = name.slice(name.lastIndexOf(".")).toLowerCase();
  return TEXT_ASSET_EXTENSIONS.has(ext);
}

function safeSceneName(name) {
  return typeof name === "string" && SAFE_PROJECT_RE.test(name.trim()) && !name.includes("..") && !name.includes("/") && !name.includes("\\");
}

export function validateScriptContent(assetName, content) {
  const errors = [];
  if (!/\.js$/i.test(assetName)) errors.push(`${assetName}: script assets must end with .js`);
  if (typeof content !== "string" || !content.trim()) {
    errors.push(`${assetName}: script content is empty`);
    return errors;
  }
  try {
    new vm.Script(content, { filename: assetName });
  } catch (error) {
    errors.push(`${assetName}: invalid JavaScript: ${error.message}`);
  }
  if (content.includes("`")) errors.push(`${assetName}: template literals are not supported by the embedded Rhino subset`);
  for (const [pattern, reason] of FORBIDDEN_SCRIPT_PATTERNS) {
    if (pattern.test(content)) errors.push(`${assetName}: ${reason}`);
  }
  for (const [namespace, allowed] of Object.entries(ALLOWED_SCRIPT_NAMESPACE_METHODS)) {
    const re = new RegExp(`\\b${namespace}\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(`, "g");
    let match;
    while ((match = re.exec(content)) !== null) {
      if (!allowed.includes(match[1])) errors.push(`${assetName}: unsupported ${namespace}.${match[1]}() API`);
    }
  }
  if (!LIFECYCLE_RE.test(content)) errors.push(`${assetName}: no S Engine lifecycle function found`);
  return errors;
}

export function validateBlueprintContent(assetName, content) {
  const errors = [];
  let bp;
  try { bp = JSON.parse(content); }
  catch (error) { return [`${assetName}: invalid blueprint JSON: ${error.message}`]; }
  if (!bp || typeof bp !== "object" || Array.isArray(bp)) return [`${assetName}: blueprint root must be an object`];
  if (bp.format !== "sengine-blueprint-1") errors.push(`${assetName}: blueprint format must be sengine-blueprint-1`);
  if (!Array.isArray(bp.nodes)) errors.push(`${assetName}: blueprint nodes must be an array`);
  if (!Array.isArray(bp.links)) errors.push(`${assetName}: blueprint links must be an array`);
  const ids = new Set();
  let hasEvent = false;
  for (const [i, node] of (Array.isArray(bp.nodes) ? bp.nodes : []).entries()) {
    if (!node || typeof node !== "object" || !Number.isInteger(node.id) || node.id < 1 || ids.has(node.id)) {
      errors.push(`${assetName}: nodes[${i}] requires a unique positive integer id`);
      continue;
    }
    ids.add(node.id);
    if (!BLUEPRINT_NODE_TYPES.has(node.type)) errors.push(`${assetName}: unsupported blueprint node '${node.type}'`);
    if (node.type === "Code") errors.push(`${assetName}: arbitrary Code blueprint nodes are not accepted from the AI`);
    if (BLUEPRINT_EVENT_TYPES.has(node.type)) hasEvent = true;
    if (node.params !== undefined && (typeof node.params !== "object" || node.params === null || Array.isArray(node.params))) {
      errors.push(`${assetName}: nodes[${i}].params must be an object`);
    }
  }
  for (const [i, link] of (Array.isArray(bp.links) ? bp.links : []).entries()) {
    if (!link || !ids.has(link.from) || !ids.has(link.to) || typeof link.pin !== "string") {
      errors.push(`${assetName}: links[${i}] references an unknown node or has no pin`);
    }
  }
  if (!hasEvent) errors.push(`${assetName}: blueprint has no lifecycle event node`);
  return errors;
}

function validateTextAssetContent(assetName, content) {
  if (assetName.toLowerCase().endsWith(".js")) return validateScriptContent(assetName, content);
  if (assetName.toLowerCase().endsWith(".bp")) return validateBlueprintContent(assetName, content);
  if (assetName.toLowerCase().endsWith(".json")) {
    try { JSON.parse(content); } catch (error) { return [`${assetName}: invalid JSON: ${error.message}`]; }
  }
  if (!content.trim()) return [`${assetName}: text asset content is empty`];
  return [];
}

function validateFieldValue(at, kind, value, componentType, field) {
  switch (kind) {
    case "number":
      return typeof value === "number" && Number.isFinite(value) ? [] : [`${at} must be a finite number`];
    case "int":
      return Number.isInteger(value) ? [] : [`${at} must be an integer`];
    case "bool":
      return typeof value === "boolean" ? [] : [`${at} must be a boolean`];
    case "string":
      return typeof value === "string" ? [] : [`${at} must be a string`];
    case "color":
      return typeof value === "string" && COLOR_RE.test(value) ? [] : [`${at} must be #RRGGBB or #AARRGGBB`];
    case "asset": {
      if (typeof value !== "string") return [`${at} must be a string`];
      if (value === "") return [];
      if (!safeAssetName(value)) return [`${at} is an unsafe asset path`];
      return [];
    }
    case "choice": {
      const allowed = CHOICES[`${componentType}.${field}`] || [];
      return allowed.includes(value) ? [] : [`${at} must be one of: ${allowed.join(", ")}`];
    }
    default:
      return [`${at} has unknown field kind ${kind}`];
  }
}

export function validateGameSpec(spec) {
  const errors = [];
  if (!spec || typeof spec !== "object" || Array.isArray(spec)) return ["spec must be an object"];
  errors.push(...unknownKeys(spec, SPEC_KEYS, "spec"));

  const project = spec.project;
  if (!project || typeof project !== "object" || Array.isArray(project)) errors.push("project is required");
  else {
    errors.push(...unknownKeys(project, PROJECT_KEYS, "project"));
    if (typeof project.name !== "string" || !safeSceneName(project.name)) errors.push("project.name is required and must be a safe 1–40 character name");
    if (project.startScene !== undefined && (typeof project.startScene !== "string" || !safeSceneName(project.startScene))) errors.push("project.startScene must be a safe scene name");
    if (project.orientation !== undefined && ![0, 1].includes(project.orientation)) errors.push("project.orientation must be 0 or 1");
  }

  if (!Array.isArray(spec.scenes) || spec.scenes.length === 0) errors.push("at least one scene is required");
  if (Array.isArray(spec.scenes) && spec.scenes.length > 30) errors.push("maximum 30 scenes are supported");
  const assets = Array.isArray(spec.assets) ? spec.assets : [];
  if (spec.assets !== undefined && !Array.isArray(spec.assets)) errors.push("assets must be an array");
  const assetNames = new Set();
  let assetBytes = 0;
  for (const [ai, asset] of assets.entries()) {
    const at = `assets[${ai}]`;
    if (!asset || typeof asset !== "object" || Array.isArray(asset)) { errors.push(`${at} must be an object`); continue; }
    errors.push(...unknownKeys(asset, ASSET_KEYS, at));
    if (typeof asset.name !== "string" || !safeAssetName(asset.name)) errors.push(`${at}.name is unsafe or unsupported; use a plain text asset filename`);
    else if (assetNames.has(asset.name)) errors.push(`${at}.name is duplicated`);
    else assetNames.add(asset.name);
    if (typeof asset.content !== "string") errors.push(`${at}.content must be a string`);
    else {
      assetBytes += Buffer.byteLength(asset.content, "utf8");
      if (safeAssetName(asset.name)) errors.push(...validateTextAssetContent(asset.name, asset.content));
    }
  }
  if (assetBytes > MAX_ASSET_BYTES) errors.push("total asset content exceeds 1 MB");
  if (spec.notes !== undefined && (!Array.isArray(spec.notes) || spec.notes.some(n => typeof n !== "string"))) errors.push("notes must be an array of strings");

  const sceneNames = new Set();
  const scriptComponents = [];
  for (const [si, scene] of (Array.isArray(spec.scenes) ? spec.scenes : []).entries()) {
    const sat = `scenes[${si}]`;
    if (!scene || typeof scene !== "object" || Array.isArray(scene)) { errors.push(`${sat} must be an object`); continue; }
    errors.push(...unknownKeys(scene, SCENE_KEYS, sat));
    if (typeof scene.name !== "string" || !safeSceneName(scene.name)) errors.push(`${sat}.name is required and must be a safe scene name`);
    else if (sceneNames.has(scene.name.trim())) errors.push(`${sat}.name is duplicated`);
    else sceneNames.add(scene.name.trim());
    for (const key of ["gravityX", "gravityY", "gravityZ", "gravity3D", "fogStart", "fogEnd"]) {
      if (scene[key] !== undefined && (typeof scene[key] !== "number" || !Number.isFinite(scene[key]))) errors.push(`${sat}.${key} must be finite`);
    }
    for (const key of ["ambient", "fogColor"]) {
      if (scene[key] !== undefined && (typeof scene[key] !== "string" || !COLOR_RE.test(scene[key]))) errors.push(`${sat}.${key} must be #RRGGBB or #AARRGGBB`);
    }
    if (scene.nextId !== undefined && (!Number.isSafeInteger(scene.nextId) || scene.nextId < 1)) errors.push(`${sat}.nextId must be a positive integer`);
    if (scene.fog !== undefined && typeof scene.fog !== "boolean") errors.push(`${sat}.fog must be a boolean`);
    if (!Array.isArray(scene.objects)) { errors.push(`${sat}.objects must be an array`); continue; }
    if (scene.objects.length > 2000) errors.push(`${sat} exceeds 2000 objects`);
    if (scene.objects.length < 2) errors.push(`${sat} needs at least a camera and a playable object`);

    const ids = new Set();
    const parentRefs = [];
    let hasCamera = false;
    let hasScript = false;
    for (const [oi, obj] of scene.objects.entries()) {
      const at = `${sat}.objects[${oi}]`;
      if (!obj || typeof obj !== "object" || Array.isArray(obj)) { errors.push(`${at} must be an object`); continue; }
      errors.push(...unknownKeys(obj, OBJECT_KEYS, at));
      if (!Number.isSafeInteger(obj.id) || obj.id < 1) errors.push(`${at}.id must be a positive safe integer`);
      else if (ids.has(obj.id)) errors.push(`${at}.id is duplicated`);
      else ids.add(obj.id);
      if (typeof obj.name !== "string" || !obj.name.trim()) errors.push(`${at}.name is required`);
      if (obj.tag !== undefined && typeof obj.tag !== "string") errors.push(`${at}.tag must be a string`);
      if (obj.active !== undefined && typeof obj.active !== "boolean") errors.push(`${at}.active must be a boolean`);
      if (obj.order !== undefined && !Number.isInteger(obj.order)) errors.push(`${at}.order must be an integer`);
      for (const t of ["x", "y", "z", "rotation", "rotX", "rotY", "scaleX", "scaleY", "scaleZ"]) {
        if (obj[t] !== undefined && (typeof obj[t] !== "number" || !Number.isFinite(obj[t]))) errors.push(`${at}.${t} must be finite`);
      }
      if (obj.parent != null) {
        if (!Number.isSafeInteger(obj.parent) || obj.parent < 1) errors.push(`${at}.parent must be a positive integer`);
        else parentRefs.push({ at, parent: obj.parent });
      }
      if (!Array.isArray(obj.components)) { errors.push(`${at}.components must be an array`); continue; }
      if (obj.components.length === 0) errors.push(`${at}.components must not be empty`);
      for (const [ci, component] of obj.components.entries()) {
        const cat = `${at}.components[${ci}]`;
        if (!component || typeof component !== "object" || Array.isArray(component)) { errors.push(`${cat} must be an object`); continue; }
        const type = component.type;
        const fields = ENGINE_COMPONENTS[type];
        if (!fields) { errors.push(`${cat} uses unsupported component type: ${type}`); continue; }
        if (type === "Camera" || type === "Camera3D") hasCamera = true;
        if (component.enabled !== undefined && typeof component.enabled !== "boolean") errors.push(`${cat}.enabled must be a boolean`);
        for (const [field, value] of Object.entries(component)) {
          if (field === "type" || field === "enabled") continue;
          const kind = fields[field];
          if (!kind) { errors.push(`${cat} uses unsupported field ${field} for ${type}`); continue; }
          errors.push(...validateFieldValue(`${cat}.${field}`, kind, value, type, field));
          if (ASSET_FIELDS.has(field) && typeof value === "string" && value !== "" && !assetNames.has(value)) {
            errors.push(`${cat}.${field} references missing asset: ${value}`);
          }
        }
        if (type === "Script") {
          hasScript = true;
          const scriptName = component.Script;
          if (typeof scriptName !== "string" || !scriptName.trim()) errors.push(`${cat}.Script must name a .js asset`);
          else if (!assetNames.has(scriptName)) errors.push(`${cat} references missing script asset: ${scriptName}`);
          scriptComponents.push(scriptName);
        }
      }
    }
    for (const ref of parentRefs) if (!ids.has(ref.parent)) errors.push(`${ref.at} references missing parent ${ref.parent}`);
    // Parent cycle detection.
    const byId = new Map(scene.objects.filter(o => o && Number.isSafeInteger(o.id)).map(o => [o.id, o]));
    for (const obj of scene.objects) {
      if (!obj || obj.parent == null) continue;
      const seen = new Set();
      let p = byId.get(obj.parent);
      while (p && p.parent != null) {
        if (seen.has(p.id) || p.id === obj.id) { errors.push(`parent cycle involving object ${obj.name || obj.id}`); break; }
        seen.add(p.id);
        p = byId.get(p.parent);
      }
    }
    if (!hasCamera) errors.push(`${sat} has no Camera or Camera3D component`);
    if (!hasScript) errors.push(`${sat} has no Script component`);
  }

  const names = (Array.isArray(spec.scenes) ? spec.scenes : []).map(s => typeof s?.name === "string" ? s.name.trim() : "");
  const start = project?.startScene || names[0];
  if (start && !names.includes(start)) errors.push(`project.startScene '${start}' does not exist`);
  if (scriptComponents.length === 0) errors.push("at least one Script component is required");
  return [...new Set(errors)];
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function stripUnknown(target, allowed, at, normalizations) {
  if (!target || typeof target !== "object" || Array.isArray(target)) return;
  for (const key of Object.keys(target)) {
    if (!allowed.has(key)) {
      delete target[key];
      normalizations.push(`${at}: removed unsupported field ${key}`);
    }
  }
}

function sanitizeProjectName(name) {
  const clean = String(name || "AI Game").trim().replace(/[^A-Za-z0-9 _-]/g, "").slice(0, 40).trim();
  return clean || "AI Game";
}

function sanitizeSceneName(name, fallback) {
  const clean = String(name || "").trim().replace(/[^A-Za-z0-9 _-]/g, "").slice(0, 40).trim();
  return clean || fallback;
}

function normalizedAssetName(original, index) {
  const lower = String(original || "").toLowerCase();
  const ext = TEXT_ASSET_EXTENSIONS.has(lower.slice(lower.lastIndexOf("."))) ? lower.slice(lower.lastIndexOf(".")) : ".js";
  return `asset_${index}${ext}`;
}

export function normalizeGameSpec(input) {
  let spec = clone(input || {});
  if (!spec || typeof spec !== "object" || Array.isArray(spec)) spec = {};
  const normalizations = [];
  const warnings = [];
  stripUnknown(spec, SPEC_KEYS, "spec", normalizations);
  if (!spec.project || typeof spec.project !== "object" || Array.isArray(spec.project)) spec.project = {};
  else stripUnknown(spec.project, PROJECT_KEYS, "project", normalizations);
  const originalProjectName = spec.project.name;
  spec.project.name = sanitizeProjectName(originalProjectName);
  if (spec.project.name !== originalProjectName) normalizations.push("sanitized project.name");
  spec.project.orientation = spec.project.orientation === 1 ? 1 : 0;

  if (!Array.isArray(spec.scenes) || spec.scenes.length === 0) {
    spec.scenes = [{ name: "Main", objects: [] }];
    normalizations.push("created a Main scene");
  }
  if (!Array.isArray(spec.assets)) spec.assets = [];
  const assetMap = new Map();
  const assetNames = new Set();
  spec.assets = spec.assets.map((asset, index) => {
    const a = asset && typeof asset === "object" && !Array.isArray(asset) ? { ...asset } : { name: `asset_${index + 1}.js`, content: "" };
    stripUnknown(a, ASSET_KEYS, `assets[${index}]`, normalizations);
    const original = a.name;
    if (typeof original !== "string" || !safeAssetName(original) || assetNames.has(original)) {
      let suffix = 0;
      do {
        suffix++;
        a.name = suffix === 1 ? normalizedAssetName(original, index + 1) : normalizedAssetName(`asset_${index + 1}_${suffix}.js`, index + 1);
      } while (assetNames.has(a.name));
      if (typeof original === "string") assetMap.set(original, a.name);
      normalizations.push(`renamed unsafe asset '${original || "unnamed"}' to '${a.name}'`);
    } else assetMap.set(original, original);
    assetNames.add(a.name);
    if (typeof a.content !== "string") { a.content = String(a.content ?? ""); normalizations.push(`coerced ${a.name} content to string`); }
    if (!a.content.trim() && /\.(js|bp)$/i.test(a.name)) {
      a.content = /\.bp$/i.test(a.name)
        ? JSON.stringify({ format: "sengine-blueprint-1", nodes: [{ id: 1, type: "OnStart", x: 40, y: 40, params: {} }, { id: 2, type: "OnUpdate", x: 40, y: 180, params: {} }], links: [] })
        : "function start() {}\nfunction update(dt) {}\n";
      normalizations.push(`created empty lifecycle for ${a.name}`);
    }
    return a;
  });

  const sceneNames = new Set();
  spec.scenes = spec.scenes.slice(0, 30).map((scene, si) => {
    const s = scene && typeof scene === "object" && !Array.isArray(scene) ? { ...scene } : { name: `Scene ${si + 1}`, objects: [] };
    stripUnknown(s, SCENE_KEYS, `scenes[${si}]`, normalizations);
    for (const key of ["ambient", "fogColor"]) {
      if (s[key] !== undefined && (typeof s[key] !== "string" || !COLOR_RE.test(s[key]))) {
        delete s[key];
        normalizations.push(`scenes[${si}]: removed invalid ${key}`);
      }
    }
    if (s.nextId !== undefined && (!Number.isSafeInteger(s.nextId) || s.nextId < 1)) {
      delete s.nextId;
      normalizations.push(`scenes[${si}]: removed invalid nextId`);
    }
    const originalName = s.name;
    s.name = sanitizeSceneName(originalName, `Scene ${si + 1}`);
    while (sceneNames.has(s.name)) s.name = `${s.name.slice(0, 35)} ${si + 2}`;
    sceneNames.add(s.name);
    if (s.name !== originalName) normalizations.push(`sanitized scene name '${originalName || "unnamed"}' to '${s.name}'`);
    if (!Array.isArray(s.objects)) { s.objects = []; normalizations.push(`${s.name}: created objects array`); }
    s.objects = s.objects.slice(0, 2000).map((obj, oi) => {
      const o = obj && typeof obj === "object" && !Array.isArray(obj) ? { ...obj } : { id: oi + 1, name: `Object ${oi + 1}`, components: [] };
      stripUnknown(o, OBJECT_KEYS, `${s.name}.objects[${oi}]`, normalizations);
      if (!Array.isArray(o.components)) { o.components = []; normalizations.push(`${s.name}.${o.name || oi}: created components array`); }
      o.components = o.components.map((component, ci) => {
        const c = component && typeof component === "object" && !Array.isArray(component) ? { ...component } : null;
        if (!c || !ENGINE_COMPONENTS[c.type]) {
          normalizations.push(`${s.name}.${o.name || oi}: removed unsupported component ${c?.type || "invalid"}`);
          warnings.push(`Removed unsupported component ${c?.type || "invalid"}`);
          return null;
        }
        for (const field of Object.keys(c)) {
          if (field === "type" || field === "enabled") continue;
          const kind = ENGINE_COMPONENTS[c.type][field];
          if (!kind) { delete c[field]; normalizations.push(`${s.name}.${o.name || oi}: removed unsupported field ${field}`); continue; }
          if (kind === "choice") {
            const options = CHOICES[`${c.type}.${field}`] || [];
            if (!options.includes(c[field])) { c[field] = options[0]; normalizations.push(`${s.name}.${o.name || oi}: corrected ${c.type}.${field}`); }
          } else if (validateFieldValue("value", kind, c[field], c.type, field).length) {
            delete c[field];
            normalizations.push(`${s.name}.${o.name || oi}: removed invalid ${c.type}.${field}`);
          }
          if (ASSET_FIELDS.has(field) && typeof c[field] === "string" && c[field] !== "") {
            const originalRef = c[field];
            if (assetMap.has(originalRef)) c[field] = assetMap.get(originalRef);
            if (!assetNames.has(c[field])) {
              warnings.push(`Removed missing asset reference '${originalRef}' from ${c.type}.${field}`);
              c[field] = "";
            }
          }
        }
        if (c.type === "Script") {
          const originalRef = c.Script;
          if (typeof originalRef === "string" && assetMap.has(originalRef)) c.Script = assetMap.get(originalRef);
          if (typeof c.Script !== "string" || !c.Script.trim() || !assetNames.has(c.Script)) {
            c.Script = "GeneratedController.js";
            if (!assetNames.has(c.Script)) {
              spec.assets.push({ name: c.Script, content: "function start() {}\nfunction update(dt) {}\n" });
              assetNames.add(c.Script);
            }
            normalizations.push(`${s.name}.${o.name || oi}: attached GeneratedController.js`);
          }
        }
        return c;
      }).filter(Boolean);
      return o;
    });

    // Reassign unique IDs and remap parents to the first object that used each old ID.
    const used = new Set();
    const firstByOldId = new Map();
    let nextId = 1;
    for (const o of s.objects) {
      const old = o.id;
      let id = Number.isSafeInteger(old) && old > 0 && !used.has(old) ? old : nextId++;
      while (used.has(id)) id = nextId++;
      if (id !== old) normalizations.push(`${s.name}: repaired object id ${old} -> ${id}`);
      if (!firstByOldId.has(old)) firstByOldId.set(old, id);
      o.id = id;
      used.add(id);
    }
    for (const o of s.objects) {
      if (o.parent == null) continue;
      const mapped = firstByOldId.get(o.parent);
      if (mapped == null || !used.has(mapped) || mapped === o.id) {
        delete o.parent;
        normalizations.push(`${s.name}: removed invalid parent ${o.parent}`);
      } else o.parent = mapped;
    }
    const byId = new Map(s.objects.map(o => [o.id, o]));
    for (const o of s.objects) {
      if (o.parent == null) continue;
      const seen = new Set();
      let p = byId.get(o.parent);
      while (p && p.parent != null) {
        if (seen.has(p.id) || p.id === o.id) {
          delete o.parent;
          normalizations.push(`${s.name}: removed parent cycle involving object ${o.id}`);
          break;
        }
        seen.add(p.id);
        p = byId.get(p.parent);
      }
    }

    // Guarantee a camera and at least one behaviour in every scene.
    const addObject = (name, components) => {
      const id = Math.max(0, ...used) + 1;
      used.add(id);
      s.objects.push({ id, name, tag: "Untagged", active: true, order: 0, x: 0, y: 0, components });
      return id;
    };
    if (!s.objects.some(o => Array.isArray(o.components) && o.components.some(c => c.type === "Camera" || c.type === "Camera3D"))) {
      addObject("Main Camera", [{ type: "Camera", enabled: true, Size: 9, Background: "#FF101827", "Follow Target": "", "Follow Smoothing": 6, Priority: 10 }]);
      normalizations.push(`${s.name}: added Main Camera`);
    }
    if (!s.objects.some(o => Array.isArray(o.components) && o.components.some(c => c.type === "Script"))) {
      if (!assetNames.has("GeneratedController.js")) {
        spec.assets.push({ name: "GeneratedController.js", content: "function start() {}\nfunction update(dt) {}\n" });
        assetNames.add("GeneratedController.js");
      }
      addObject("GameController", [{ type: "Collider2D", enabled: true, Shape: "Box", Width: 20, Height: 12, "Is Trigger": true }, { type: "Script", enabled: true, Script: "GeneratedController.js", Params: "" }]);
      normalizations.push(`${s.name}: added GeneratedController.js`);
    }
    return s;
  });

  const start = sanitizeSceneName(spec.project.startScene, spec.scenes[0]?.name || "Main");
  spec.project.startScene = sceneNames.has(start) ? start : spec.scenes[0].name;
  if (!Array.isArray(spec.notes)) spec.notes = [];
  spec.notes = spec.notes.filter(n => typeof n === "string").slice(0, 30);
  return { spec, normalizations, warnings };
}

export function toSengineProject(spec) {
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
      components: o.components,
    })),
  }));
  return {
    format: "s-engine-project",
    formatVersion: 1,
    project: {
      name: spec.project.name,
      startScene: spec.project.startScene || scenes[0].name,
      orientation: spec.project.orientation ?? 0,
    },
    scenes,
    assets: spec.assets || [],
    notes: spec.notes || [],
  };
}
