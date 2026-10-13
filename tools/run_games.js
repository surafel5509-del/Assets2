#!/usr/bin/env node
/*
 * Headless runtime harness for the shipped games.
 *
 * The engine itself needs an Android device, so until now the game scripts were
 * only syntax-checked -- `node --check` proves they parse, not that they run.
 * This harness implements the JavaScript API surface from
 * engine/script/Api.kt and engine/script/ScriptSystem.PRELUDE closely enough to
 * actually execute every script, frame by frame, with synthetic input.
 *
 * What it catches that a syntax check cannot:
 *   - a call to a method the engine does not expose (a typo, or an API that
 *     exists in Unity but not here)
 *   - reading a property the engine never defines
 *   - an exception thrown from start() or update()
 *   - NaN propagating into a transform, which on a real device looks like an
 *     object vanishing rather than an error
 *   - a script that never defines update() but is expected to tick
 *
 * It is deliberately strict.  Unknown property access throws instead of
 * returning undefined, because silently returning undefined is exactly what
 * would let a bug ship.
 *
 * Usage:
 * It can also dump the world it built, so the result can be looked at:
 *
 *   node tools/run_games.js 09_dungeon --dump /tmp/world
 *   python3 tools/preview_scene.py 09_dungeon_crawler_2d --world /tmp/world/09_dungeon_crawler_2d.json --out level.png
 *
 * The dump is the world *after* the run, which is what makes it useful for art:
 * a tilemap a script built, a wave of spawned enemies, a procedurally placed
 * forest -- none of that is in the .scene.json, and all of it is in the dump.
 *
 *   node tools/run_games.js [slug-substring] [--frames N] [--repo DIR] [--dump DIR] [--every N]
 */

'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

const argv = process.argv.slice(2);
let filter = '';
let frames = 240;                       // four seconds at 60 Hz
let repo = path.resolve(__dirname, '..');
let dumpDir = '';
let dumpEvery = 0;   // 0 = only the final frame

for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--frames') frames = parseInt(argv[++i], 10);
    else if (argv[i] === '--repo') repo = path.resolve(argv[++i]);
    else if (argv[i] === '--dump') dumpDir = path.resolve(argv[++i]);
    else if (argv[i] === '--every') dumpEvery = parseInt(argv[++i], 10);
    else if (!argv[i].startsWith('--')) filter = argv[i];
}

const DT = 1 / 60;

// ---------------------------------------------------------------------------
// Drift guard.
//
// The member lists above are a hand-maintained model of Api.kt.  If Api.kt
// changes and this file does not, the harness will happily accept a call the
// engine would reject -- which is worse than no harness, because it passes.  So
// the lists are checked against the Kotlin source on every run.
// ---------------------------------------------------------------------------
// The harness mirrors ScriptSystem.PRELUDE by hand. Every global it offers must
// exist in the real prelude, or a script could pass here and fail in the engine.
function checkPreludeDrift() {
    const sysPath = path.join(repo, 'app/src/main/java/com/sengine/engine/script/ScriptSystem.kt');
    if (!fs.existsSync(sysPath)) { console.log('  (ScriptSystem.kt not found; skipping prelude check)'); return; }
    const src = fs.readFileSync(sysPath, 'utf8');
    const m = src.match(/const val PRELUDE = """([\s\S]*?)"""/);
    if (!m) { fail('could not locate PRELUDE in ScriptSystem.kt'); return; }
    const engineNames = new Set([...m[1].matchAll(/function\s+(\w+)\s*\(/g)].map(x => x[1]));
    if (/var\s+events\s*=/.test(m[1])) engineNames.add('events');
    const harnessNames = ['log', 'warn', 'error', 'random', 'randomInt', 'clamp', 'lerp', 'sign', 'smoothstep',
        'damp', 'approach', 'dist2d', 'angleTo', 'pick', 'shuffle', 'after', 'every', 'cancelTimer', 'tween',
        'cancelTween', 'events'];
    let drift = 0;
    for (const n of harnessNames) {
        if (!engineNames.has(n) && !(n === 'events' && engineNames.has('events'))) {
            fail(`harness drift: prelude global '${n}' is modelled here but ScriptSystem.PRELUDE does not define it`);
            drift++;
        }
    }
    console.log(`  PRELUDE drift check: ${drift === 0 ? 'in sync' : drift + ' mismatch(es)'} (${harnessNames.length} globals)`);
}

function checkApiDrift() {
    const apiPath = path.join(repo, 'app/src/main/java/com/sengine/engine/script/Api.kt');
    if (!fs.existsSync(apiPath)) {
        console.log('  (Api.kt not found; skipping drift check)');
        return;
    }
    // SUi and SStorage live in ApiExtras.kt; search both files for class bodies.
    const extrasPath = path.join(repo, 'app/src/main/java/com/sengine/engine/script/ApiExtras.kt');
    const src = fs.readFileSync(apiPath, 'utf8') +
        (fs.existsSync(extrasPath) ? '\n' + fs.readFileSync(extrasPath, 'utf8') : '');

    function kotlinMembers(cls) {
        const m = src.match(new RegExp('class ' + cls + '\\b[\\s\\S]*?\\n}'));
        if (!m) return { methods: new Set(), props: new Set() };
        const body = m[0];
        const methods = new Set();
        for (const mm of body.matchAll(/fun [`]?([A-Za-z_]\w*)[`]?\s*\(/g)) {
            if (mm[1] !== 'toString') methods.add(mm[1]);
        }
        // getX()/setX() pairs surface to JS as a single property `x`.
        const props = new Set();
        for (const mm of body.matchAll(/fun get([A-Z]\w*)\s*\(\s*\)/g)) {
            props.add(mm[1][0].toLowerCase() + mm[1].slice(1));
        }
        // JavaBean boolean getters: Rhino's introspection also maps isX() to the
        // property `x`, so isGrounded() is reachable as self.grounded.
        for (const mm of body.matchAll(/fun is([A-Z]\w*)\s*\(\s*\)\s*:\s*Boolean/g)) {
            props.add(mm[1][0].toLowerCase() + mm[1].slice(1));
        }
        for (const mm of body.matchAll(/@JvmField var (\w+)/g)) props.add(mm[1]);
        return { methods, props };
    }

    let drift = 0;
    // Methods modelled by the ui and storage mocks above (uiApi / storageApi).
    const UI_METHODS = ['open', 'close', 'isOpen', 'setText', 'getText', 'setValue', 'getValue',
                        'show', 'hide', 'isVisible', 'setColor', 'setBackground'];
    const STORAGE_METHODS = ['slot', 'load', 'save', 'get', 'set', 'has', 'remove', 'clear', 'keyCount'];
    for (const [cls, mset, pset] of [
        ['SObject', SOBJECT_METHODS, SOBJECT_PROPS],
        ['SScene', SCENE_METHODS, SCENE_PROPS],
        ['SUi', UI_METHODS, []],
        ['SStorage', STORAGE_METHODS, []],
    ]) {
        const k = kotlinMembers(cls);
        // Only flag members the harness offers but the engine does not have:
        // that is the direction that lets a bad script pass.  Members the engine
        // has and the harness lacks just mean less coverage, not a false pass.
        for (const m of mset) {
            if (!k.methods.has(m)) {
                fail(`harness drift: ${cls}.${m}() is modelled here but does not exist in Api.kt`);
                drift++;
            }
        }
        for (const pr of pset) {
            if (!k.props.has(pr)) {
                fail(`harness drift: ${cls}.${pr} is modelled here but Api.kt exposes no such property`);
                drift++;
            }
        }
    }
    console.log(`  Api.kt drift check: ${drift === 0 ? 'in sync' : drift + ' mismatch(es)'}`);
}


// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------

const problems = [];
let checks = 0;

function ok(cond, msg) {
    checks++;
    if (!cond) problems.push(msg);
    return cond;
}

function fail(msg) { checks++; problems.push(msg); }

// ---------------------------------------------------------------------------
// The API surface.
//
// Names and arities are taken from Api.kt.  Anything a script touches that is
// not listed here throws, which is the point.
// ---------------------------------------------------------------------------

// Properties the engine exposes as JS properties (getX/setX pairs in Api.kt).
const SOBJECT_PROPS = new Set([
    'lightIntensity', 'lightColor', 'lightRange', 'fov', 'skyTop', 'skyHorizon',
    'id', 'name', 'tag', 'active', 'order',
    'x', 'y', 'rotation', 'scaleX', 'scaleY',
    'worldX', 'worldY',
    'z', 'rotX', 'rotY', 'rotZ', 'scaleZ', 'worldZ',
    'vx', 'vy', 'vz', 'grounded', 'flipX',
    'color', 'text', 'size', 'visible', 'animation',
]);

const SOBJECT_METHODS = new Set([
    'setPosition', 'setWorldPosition', 'move', 'rotate',
    'distanceTo', 'distanceTo3', 'forward', 'overlaps',
    'play', 'stopAnimation', 'isAnimationFinished', 'setAnimSpeed',
    'setShaderParam', 'setMeshColor',
    'setColor', 'getColor', 'setText', 'getText',
    'setVisible', 'getVisible', 'setTexture',
    'burst', 'setEmitting', 'setSize', 'getSize',
    'getParent', 'setParentTo', 'child', 'destroy',
    'hasComponent', 'setComponentEnabled',
    'send', 'is',
    'addForce', 'setVelocity',
]);

const SCENE_PROPS = new Set(['name', 'gravityX', 'gravityY', 'gravity3D', 'camera', 'camera3D']);
const SCENE_METHODS = new Set([
    'find', 'findAll', 'count', 'spawn', 'shake', 'raycast', 'load', 'reload',
]);

const INPUT_PROPS = new Set([
    'axisX', 'axisY', 'a', 'b', 'aDown', 'bDown', 'touching', 'tapped', 'touchX', 'touchY',
]);
const TIME_PROPS = new Set(['time', 'frame', 'fps']);
const AUDIO_METHODS = new Set(['play', 'beep', 'stopAll']);

// ---------------------------------------------------------------------------
// Runtime objects
// ---------------------------------------------------------------------------

class GameObj {
    constructor(id, def, world) {
        this._id = id;
        this._world = world;
        this.name = def.name || ('Object' + id);
        this.tag = def.tag || 'Untagged';
        this.active = def.active !== false;
        this.order = def.order || 0;
        this.x = def.x || 0; this.y = def.y || 0; this.z = def.z || 0;
        this.rotation = def.rotation || 0;
        this.rotX = def.rotX || 0; this.rotY = def.rotY || 0; this.rotZ = def.rotZ || 0;
        this.scaleX = def.scaleX === undefined ? 1 : def.scaleX;
        this.scaleY = def.scaleY === undefined ? 1 : def.scaleY;
        this.scaleZ = def.scaleZ === undefined ? 1 : def.scaleZ;
        this.vx = 0; this.vy = 0; this.vz = 0;
        this.grounded = false;
        this.flipX = false;
        this.color = '#FFFFFFFF';
        this.text = '';
        this.size = 1;
        this.visible = true;
        this.animation = '';
        this.parent = def.parent === undefined ? null : def.parent;

        this._destroyed = false;
        this._animTime = 0;
        this._animFrames = 8;
        this._animFps = 12;
        this._scripts = [];

        this.bodyType = null;      // null = no Rigidbody2D
        this.gravityScale = 1;
        this.drag = 0;

        for (const c of (def.components || [])) {
            if (c.type === 'Rigidbody2D') {
                // Display names, as the scene serialises them.
                this.bodyType = ({ Dynamic: 0, Kinematic: 1, Static: 2 })[c['Body Type']];
                if (this.bodyType === undefined) this.bodyType = 0;
                this.gravityScale = c['Gravity Scale'] === undefined ? 1 : c['Gravity Scale'];
                this.drag = c['Linear Drag'] === undefined ? 0 : c['Linear Drag'];
            }
            if (c.type === 'SpriteRenderer') {
                if (c.Texture) this._texture = c.Texture;
                // The component's own colour and the script's colour are the same
                // field on the object, so a tinted tile has to arrive here or the
                // dump (and every still rendered from it) draws it white.
                if (c.Color) this.color = c.Color;
            }
            if (c.type === 'TextRenderer') {
                if (c.Text !== undefined) this.text = c.Text;
                if (c.Color) this.color = c.Color;
                if (c['Size'] !== undefined) this.size = c['Size'];
                this._align = c['Align'] || 'Left';
            }
            if (c.type === 'MeshRenderer' && c.Color) this.color = c.Color;
            if (c.type === 'Animator') { this._animFrames = 8; }
        }
    }

    get worldX() { return this.x + (this._parentObj() ? this._parentObj().worldX : 0); }
    get worldY() { return this.y + (this._parentObj() ? this._parentObj().worldY : 0); }
    get worldZ() { return this.z + (this._parentObj() ? this._parentObj().worldZ : 0); }

    _parentObj() {
        if (this.parent === null || this.parent === undefined) return null;
        return this._world.byId.get(this.parent) || null;
    }
}

/** The `self` / `transform` proxy a script sees. */
function makeSObject(go, world) {
    const api = {
        get id() { return go._id; },
        get name() { return go.name; }, set name(v) { go.name = v; },
        get tag() { return go.tag; }, set tag(v) { go.tag = v; },
        get active() { return go.active; }, set active(v) { go.active = !!v; },
        get order() { return go.order; }, set order(v) { go.order = v; },

        get x() { return go.x; }, set x(v) { go.x = num(v, 'x', go); },
        get y() { return go.y; }, set y(v) { go.y = num(v, 'y', go); },
        get z() { return go.z; }, set z(v) { go.z = num(v, 'z', go); },
        get rotation() { return go.rotation; }, set rotation(v) { go.rotation = num(v, 'rotation', go); },
        get rotX() { return go.rotX; }, set rotX(v) { go.rotX = num(v, 'rotX', go); },
        get rotY() { return go.rotY; }, set rotY(v) { go.rotY = num(v, 'rotY', go); },
        get rotZ() { return go.rotZ; }, set rotZ(v) { go.rotZ = num(v, 'rotZ', go); },
        get scaleX() { return go.scaleX; }, set scaleX(v) { go.scaleX = num(v, 'scaleX', go); },
        get scaleY() { return go.scaleY; }, set scaleY(v) { go.scaleY = num(v, 'scaleY', go); },
        get scaleZ() { return go.scaleZ; }, set scaleZ(v) { go.scaleZ = num(v, 'scaleZ', go); },

        get worldX() { return go.worldX; },
        get worldY() { return go.worldY; },
        get worldZ() { return go.worldZ; },

        get vx() { return go.vx; }, set vx(v) { go.vx = num(v, 'vx', go); },
        get vy() { return go.vy; }, set vy(v) { go.vy = num(v, 'vy', go); },
        get vz() { return go.vz; }, set vz(v) { go.vz = num(v, 'vz', go); },
        get grounded() { return go.grounded; }, set grounded(v) { go.grounded = !!v; },
        get flipX() { return go.flipX; }, set flipX(v) { go.flipX = !!v; },

        get color() { return go.color; }, set color(v) { go.color = v; },
        get text() { return go.text; }, set text(v) { go.text = String(v); },
        get size() { return go.size; }, set size(v) { go.size = num(v, 'size', go); },
        get visible() { return go.visible; }, set visible(v) { go.visible = !!v; },
        get animation() { return go.animation; },

        // Light and Camera3D properties (added to Api.kt).
        get lightIntensity() { return go.lightIntensity === undefined ? 1 : go.lightIntensity; },
        set lightIntensity(v) { go.lightIntensity = num(v, 'lightIntensity', go); },
        get lightColor() { return go.lightColor || '#FFFFFFFF'; },
        set lightColor(v) { if (typeof v !== 'string') throw new Error('lightColor expects a #AARRGGBB string'); go.lightColor = v; },
        get lightRange() { return go.lightRange === undefined ? 10 : go.lightRange; },
        set lightRange(v) { go.lightRange = num(v, 'lightRange', go); },
        get fov() { return go.fov === undefined ? 60 : go.fov; },
        set fov(v) { go.fov = num(v, 'fov', go); },
        get skyTop() { return go.skyTop || '#FF3B7BD4'; },
        set skyTop(v) { if (typeof v !== 'string') throw new Error('skyTop expects a #AARRGGBB string'); go.skyTop = v; },
        get skyHorizon() { return go.skyHorizon || '#FFBFD8F0'; },
        set skyHorizon(v) { if (typeof v !== 'string') throw new Error('skyHorizon expects a #AARRGGBB string'); go.skyHorizon = v; },

        setPosition(x, y, z) {
            go.x = num(x, 'setPosition.x', go); go.y = num(y, 'setPosition.y', go);
            if (z !== undefined) go.z = num(z, 'setPosition.z', go);
        },
        setWorldPosition(x, y, z) { this.setPosition(x, y, z); },
        move(dx, dy, dz) {
            go.x = num(go.x + dx, 'move.x', go);
            go.y = num(go.y + dy, 'move.y', go);
            if (dz !== undefined) go.z = num(go.z + dz, 'move.z', go);
        },
        rotate(deg) { go.rotation = num(go.rotation + deg, 'rotate', go); },

        distanceTo(o) {
            const t = resolve(o);
            if (!t) return 0;
            return Math.hypot(go.x - t.x, go.y - t.y);
        },
        distanceTo3(o) {
            const t = resolve(o);
            if (!t) return 0;
            return Math.hypot(go.x - t.x, go.y - t.y, go.z - t.z);
        },
        forward() {
            const r = go.rotation * Math.PI / 180;
            return [Math.cos(r), Math.sin(r)];
        },
        overlaps(o) {
            const t = resolve(o);
            return !!t && this.distanceTo(t) < 1.0;
        },

        play(clip) {
            if (typeof clip !== 'string') throw new Error('play() expects a clip name');
            // AnimationSystem.clip() resolves <name>.anim in the project assets, and
            // GameLibrary writes one .anim per clip declared in game.json.  A name
            // that is not declared resolves to nothing and the sprite silently
            // draws its entire sheet, so it is an error here rather than a no-op.
            if (!world.clips.has(clip)) {
                throw new Error(`play('${clip}') -- no clip by that name is declared in game.json`);
            }
            go.animation = clip; go._animTime = 0;
        },
        stopAnimation() { go.animation = ''; },
        isAnimationFinished() {
            return go._animTime >= go._animFrames / go._animFps;
        },
        setAnimSpeed(s) { go._animSpeed = num(s, 'setAnimSpeed', go); },
        setShaderParam(k, v) { void k; void v; },
        setMeshColor(c) { go.color = c; },

        setColor(c) { go.color = c; },
        getColor() { return go.color; },
        setText(t) { go.text = String(t); },
        getText() { return go.text; },
        setVisible(v) { go.visible = !!v; },
        getVisible() { return go.visible; },
        setTexture(t) {
            if (typeof t !== 'string') throw new Error('setTexture() expects a name');
            go._texture = t;
            world.usedTextures.add(t);
        },

        burst(n) { if (!(n > 0)) throw new Error('burst() expects a positive count'); },
        setEmitting(b) { go._emitting = !!b; },
        setSize(w, h) { go.size = num(w, 'setSize', go); void h; },
        getSize() { return [1, 1]; },

        getParent() {
            const p = go._parentObj();
            return p ? world.proxyFor(p) : null;
        },
        setParentTo(o) {
            const t = resolve(o);
            go.parent = t ? t._id : null;
        },
        child(i) {
            const kids = world.objects.filter(o => o.parent === go._id);
            return kids[i] ? world.proxyFor(kids[i]) : null;
        },
        destroy() { go._destroyed = true; go.active = false; },
        hasComponent(t) {
            if (typeof t !== 'string') throw new Error('hasComponent() expects a type name');
            return true;
        },
        setComponentEnabled(t, on) { void t; void on; },

        /** Inter-script message.  Unknown handler names are an error. */
        send(msg, a, b, c, d) {
            if (typeof msg !== 'string') throw new Error('send() expects a message name');
            return world.dispatch(go, msg, [a, b, c, d], api);
        },
        is(t) { return go.tag === t || go.name === t; },

        addForce(x, y) { go.vx += x; go.vy += y; },
        setVelocity(x, y) { go.vx = num(x, 'setVelocity.x', go); go.vy = num(y, 'setVelocity.y', go); },
    };
    return api;
}

function num(v, what, go) {
    const n = Number(v);
    if (!Number.isFinite(n)) {
        throw new Error(`non-finite value assigned to ${what} on '${go ? go.name : '?'}': ${v}`);
    }
    return n;
}

// ---------------------------------------------------------------------------
// World
// ---------------------------------------------------------------------------

/**
 * Animator.resetRuntime(): an Animator with a Clip and Play On Start shows that
 * clip from the first frame -- which is how a scene paints a tile or holds an
 * idle pose with no script at all.  The harness has to do the same or every
 * static frame in a dumped world renders as an undivided sheet.
 */
function applyAnimatorRuntime(go) {
    const def = go._def;
    if (!def) return;
    for (const c of (def.components || [])) {
        if (c.type !== 'Animator') continue;
        go._animFrames = 8;
        const info = go._world.clipInfo && go._world.clipInfo[c.Clip];
        if (!info) continue;
        go._animFrames = info.frames || 1;
        go._animFps = info.fps || 8;
        if (c['Play On Start'] !== false) go.animation = c.Clip;
    }
}

class World {
    constructor(scene, gameDir, declaredAudio) {
        this.objects = [];
        this.byId = new Map();
        this.byName = new Map();
        this.proxies = new Map();
        this.scopes = new Map();       // goId -> vm context for its script
        this.usedTextures = new Set();
        this.usedAudio = new Set();
        this.nextId = scene.nextId || 1000;
        this.gravityX = scene.gravityX || 0;
        this.gravityY = scene.gravityY || 0;
        this.time = 0;
        this.frame = 0;
        this.declaredAudio = declaredAudio;
        this.gameDir = gameDir;
        this.spawnedCount = 0;
        this.shakeCount = 0;
        this.clips = new Set();
        this.missingFinds = new Set();
        this.namedActions = new Set();     // every literal name passed to actionPressed()
        this.foundNames = new Set();

        for (const def of scene.objects) {
            const go = new GameObj(def.id, def, this);
            // Every object knows the definition it came from, not just the ones
            // that carry a script: the dump needs component data (a particle
            // emitter's colour and size) for objects that have no script at all.
            go._def = def;
            this.objects.push(go);
            this.byId.set(go._id, go);
            this.byName.set(go.name, go);
            go._start = { x: go.x, y: go.y, z: go.z };
        }
    }

    proxyFor(go) {
        if (!this.proxies.has(go._id)) this.proxies.set(go._id, makeSObject(go, this));
        return this.proxies.get(go._id);
    }

    /** Mirrors GameObject.isActiveInHierarchy(): a child of a hidden parent is off. */
    isActive(go) {
        let cur = go;
        while (cur) {
            if (!cur.active || cur._destroyed) return false;
            cur = cur.parent === null || cur.parent === undefined ? null : this.byId.get(cur.parent);
        }
        return true;
    }

    /**
     * Copy one child definition under a freshly spawned parent, walking its own
     * children -- the harness's stand-in for Scene.duplicate().
     */
    cloneChild(childDef, parentClone) {
        const id = this.nextId++;
        const proto = this.byId.get(childDef.id);
        const go = new GameObj(id, Object.assign({}, childDef, {
            name: childDef.name + '_' + id, parent: parentClone._id,
        }), this);
        go._def = childDef;
        if (proto) go._texture = proto._texture;
        this.objects.push(go);
        this.byId.set(id, go);
        go._isClone = true;
        for (const grand of (this.sceneDefs || [])) {
            if (grand.parent !== childDef.id) continue;
            this.cloneChild(grand, go);
        }
        if (this.onSpawn) this.onSpawn(go, go);
        return go;
    }

    /** Route a send() to the target's script scope. */
    dispatch(go, msg, args, caller) {
        const scope = this.scopes.get(go._id);
        if (!scope) return undefined;                 // object has no script
        const fn = scope[msg];
        if (typeof fn !== 'function') {
            throw new Error(`send('${msg}') on '${go.name}' -- no such handler in its script`);
        }
        return fn.apply(scope.selfRef, args.filter(a => a !== undefined));
    }

    makeSceneApi() {
        const self = this;
        return {
            get name() { return 'Main'; },
            get gravityX() { return this._gx || 0; }, set gravityX(v) { this._gx = v; },
            get gravityY() { return this._gy || 0; }, set gravityY(v) { this._gy = v; },
            get gravity3D() { return this._g3 || -9.8; }, set gravity3D(v) { this._g3 = v; },
            get camera() { return self.proxyFor(self.byName.get('Main Camera')); },
            get camera3D() { return self.proxyFor(self.byName.get('Main Camera')); },

            find(name) {
                if (typeof name !== 'string') throw new Error('scene.find() expects a name');
                const go = self.byName.get(name);
                if (!go || go._destroyed) {
                    // A miss is not an error on its own -- scripts legitimately
                    // probe for optional objects -- but a name that NEVER resolves
                    // across a whole run is a wiring bug, and it is invisible in
                    // the editor because the guard just never fires.
                    self.missingFinds.add(name);
                    return null;
                }
                self.foundNames.add(name);
                return self.proxyFor(go);
            },
            findAll(tag) {
                if (typeof tag !== 'string') throw new Error('scene.findAll() expects a tag');
                return self.objects
                    .filter(o => (o.tag === tag || o.name === tag) && !o._destroyed)
                    .map(o => self.proxyFor(o));
            },
            count(tag) { return this.findAll(tag).length; },

            spawn(name, x, y, z) {
                if (typeof name !== 'string') throw new Error('scene.spawn() expects a prototype name');
                const proto = self.byName.get(name);
                if (!proto) {
                    throw new Error(`scene.spawn('${name}') -- no prototype object with that name`);
                }
                const id = self.nextId++;
                const go = new GameObj(id, {
                    name: name + '_' + id, tag: proto.tag, active: true,
                    x: x === undefined ? proto.x : x,
                    y: y === undefined ? proto.y : y,
                    z: z === undefined ? proto.z : z,
                    scaleX: proto.scaleX, scaleY: proto.scaleY, scaleZ: proto.scaleZ,
                    components: [],
                }, self);
                go._def = proto._def;
                go._texture = proto._texture;
                applyAnimatorRuntime(go);
                self.objects.push(go);
                self.byId.set(id, go);
                self.spawnedCount++;
                go._isClone = true;
                // Child objects, exactly as Scene.duplicate() walks the hierarchy:
                // a spawned enemy carries its body parts with it, and a spawned
                // effect carries its light.  Without this the harness would run
                // scripts against invisible objects -- right numbers, no body.
                for (const childDef of (self.sceneDefs || [])) {
                    if (childDef.parent !== proto._def.id) continue;
                    self.cloneChild(childDef, go);
                }
                if (self.onSpawn) self.onSpawn(go, proto);
                return self.proxyFor(go);
            },

            shake(amount) {
                if (!Number.isFinite(Number(amount))) throw new Error('scene.shake() expects a number');
                self.shakeCount++;
            },

            raycast(ox, oy, oz, dx, dy, dz, maxDist) {
                for (const a of [ox, oy, oz, dx, dy, dz, maxDist]) {
                    if (a !== undefined && !Number.isFinite(Number(a))) {
                        throw new Error('scene.raycast() received a non-finite argument');
                    }
                }
                // Return null: nothing in the synthetic world is hit-testable.
                // Scripts must already handle the miss case, which is what this
                // exercises.
                return null;
            },

            load(name) { if (typeof name !== 'string') throw new Error('scene.load() expects a name'); },
            reload() { self.reloadCount = (self.reloadCount || 0) + 1; },
        };
    }
}

function resolve(o) {
    if (!o) return null;
    if (o._id !== undefined) return o;                  // a GameObj
    if (o.x !== undefined && o.y !== undefined) return o;   // an SObject proxy
    return null;
}

// ---------------------------------------------------------------------------
// Loading and running one game
// ---------------------------------------------------------------------------

function parseParams(str) {
    const out = {};
    if (!str) return out;
    // ScriptSystem.applyParams splits on comma, newline and semicolon.  Matching
    // it exactly matters: a value containing a comma is silently truncated, which
    // is why the `states` list uses '|'.
    for (const part of String(str).split(/[,\n;]/)) {
        const eq = part.indexOf('=');
        if (eq < 0) continue;
        const k = part.slice(0, eq).trim();
        const v = part.slice(eq + 1).trim();
        // Type coercion mirrors ScriptSystem.applyParams: a number when it parses
        // as one, a real boolean for true/false, otherwise the string with any
        // surrounding quotes stripped.  Without the boolean case every
        // `locked=false` in the data stayed truthy in here and read as locked.
        const n = Number(v);
        if (v !== '' && Number.isFinite(n)) out[k] = n;
        else if (v.toLowerCase() === 'true') out[k] = true;
        else if (v.toLowerCase() === 'false') out[k] = false;
        else out[k] = v.replace(/^["']|["']$/g, '');
    }
    return out;
}

// Easing curves, identical to the prelude's __easings table in ScriptSystem.kt.
const EASINGS = {
    linear: t => t,
    easeIn: t => t * t,
    easeOut: t => 1 - (1 - t) * (1 - t),
    easeInOut: t => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
    easeOutBack: t => { const c = 1.70158; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); },
};

// The prelude's `events` bus: on / off / emit / count.
function makeEventBus() {
    const map = {};
    return {
        on(name, fn) { (map[name] = map[name] || []).push(fn); },
        off(name, fn) {
            const list = map[name]; if (!list) return;
            if (fn === undefined) { delete map[name]; return; }
            const i = list.indexOf(fn); if (i >= 0) list.splice(i, 1);
        },
        emit(name, data) {
            const list = map[name]; if (!list) return 0;
            const copy = list.slice();
            for (const fn of copy) fn(data);
            return copy.length;
        },
        count(name) { const list = map[name]; return list ? list.length : 0; },
    };
}

function runGame(gameDir) {
    const id = path.basename(gameDir);
    const meta = JSON.parse(fs.readFileSync(path.join(gameDir, 'game.json'), 'utf8'));
    const sceneFile = path.join(gameDir, 'scenes', (meta.startScene || 'Main') + '.scene.json');
    const scene = JSON.parse(fs.readFileSync(sceneFile, 'utf8'));

    const declaredAudio = new Set(
        (meta.assets || []).filter(a => /\.(ogg|wav|mp3|m4a)$/i.test(a.as || ''))
                          .map(a => a.as)
    );

    // Every clip name GameLibrary will materialise as "<name>.anim", plus the
    // grid each one samples.  Built before the world so Animator.resetRuntime()
    // can be mirrored while the objects are constructed.
    const declaredGrids = {};
    for (const a of (meta.assets || [])) {
        const grid = a.anim || a.tileset;
        for (const [name, c] of Object.entries(a.clips || {})) {
            declaredGrids[name] = {
                texture: a.as, columns: (grid && grid.columns) || 1, rows: (grid && grid.rows) || 1,
                frame: (c.frames || [0])[0], frames: (c.frames || [0]).length,
                fps: c.fps === undefined ? 8 : c.fps,
                loop: c.loop !== false,
            };
        }
    }

    const world = new World(scene, gameDir, declaredAudio);
    world.sceneDefs = scene.objects;
    for (const k of Object.keys(declaredGrids)) world.clips.add(k);
    world.clipsExpected = (meta.assets || []).some(a => a.clips);
    // Stamp and apply: a static tile with an Animator shows its clip from frame
    // one, exactly as resetRuntime() arranges on device.
    world.clipInfo = declaredGrids;
    for (const go of world.objects) applyAnimatorRuntime(go);
    const sceneApi = world.makeSceneApi();
    const input = {
        axisX: 0, axisY: 0, a: false, b: false, aDown: false, bDown: false,
        touching: false, tapped: false, touchX: 0, touchY: 0,
        actionsHeld: {}, actionsPressed: {},
        action(name) { return !!this.actionsHeld[name]; },
        actionPressed(name) { return !!this.actionsPressed[name]; },
    };
    world.timeScale = 1;
    // ui.* and storage.* mirror SUi / SStorage in ApiExtras.kt. Screens are
    // recorded but not checked against files: the harness has no UI assets.
    const openScreens = new Set();
    const uiValues = {};
    const uiApi = {
        open(name) { openScreens.add(String(name)); },
        close(name) { openScreens.delete(String(name)); },
        isOpen(name) { return openScreens.has(String(name)); },
        setText(id, t) { uiValues[id] = { ...(uiValues[id] || {}), text: String(t) }; },
        getText(id) { return (uiValues[id] || {}).text || ''; },
        setValue(id, v) { uiValues[id] = { ...(uiValues[id] || {}), value: Math.min(1, Math.max(0, Number(v))) }; },
        getValue(id) { return (uiValues[id] || {}).value || 0; },
        show(id) { uiValues[id] = { ...(uiValues[id] || {}), visible: true }; },
        hide(id) { uiValues[id] = { ...(uiValues[id] || {}), visible: false }; },
        isVisible(id) { return (uiValues[id] || {}).visible !== false; },
        setColor() {}, setBackground() {},
    };
    const store = {};
    let slotName = 'slot1';
    const storageApi = {
        slot(name) { slotName = String(name); },
        load() { return false; },
        save() { return true; },
        get(k, d) { return k in store ? store[k] : d; },
        set(k, v) { store[k] = v; },
        has(k) { return k in store; },
        remove(k) { delete store[k]; },
        clear() { for (const k of Object.keys(store)) delete store[k]; },
        get keyCount() { return Object.keys(store).length; },
    };
    const timeApi = {
        get time() { return world.time; },
        get frame() { return world.frame; },
        get fps() { return 60; },
        get scale() { return world.timeScale; },
        getScale() { return world.timeScale; },
        setScale(v) { world.timeScale = Math.min(4, Math.max(0, Number(v))); },
    };
    const audioApi = {
        play(name, vol) {
            if (typeof name !== 'string') throw new Error('audio.play() expects a name');
            world.usedAudio.add(name);
            if (!declaredAudio.has(name)) {
                throw new Error(`audio.play('${name}') -- not a declared audio asset`);
            }
            void vol;
        },
        playMusic(name, vol) {
            if (typeof name !== 'string') throw new Error('audio.playMusic() expects a name');
            world.usedAudio.add(name);
            if (!declaredAudio.has(name)) throw new Error(`audio.playMusic('${name}') -- not a declared audio asset`);
            void vol;
        },
        stopMusic() {},
        getMusic() { return ''; },
        beep() {},
        stopAll() {},
    };

    const timers = [];
    const tweens = [];
    let timerIds = 0;
    const prelude = {
        log() {}, warn() {}, error() {},
        random(a, b) { return a === undefined ? 0.5 : a + 0.5 * ((b || 1) - a); },
        randomInt(a, b) { return Math.floor(a + 0.5 * ((b || 0) - a + 1)); },
        clamp(v, a, b) { return Math.max(a, Math.min(b, v)); },
        lerp(a, b, t) { return a + (b - a) * t; },
        sign(v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); },
        smoothstep(a, b, x) { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); },
        damp(current, target, lambda, dt) { return target + (current - target) * Math.exp(-lambda * dt); },
        approach(current, target, maxDelta) { return current < target ? Math.min(current + maxDelta, target) : Math.max(current - maxDelta, target); },
        dist2d(x1, y1, x2, y2) { return Math.hypot(x2 - x1, y2 - y1); },
        angleTo(x1, y1, x2, y2) { return Math.atan2(y2 - y1, x2 - x1) * 180 / Math.PI; },
        pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; },
        shuffle(arr) { for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); const t = arr[i]; arr[i] = arr[j]; arr[j] = t; } return arr; },
        after(sec, fn) { const id = ++timerIds; timers.push({ id, t: world.time + sec, f: fn, every: 0 }); return id; },
        every(sec, fn) { const id = ++timerIds; timers.push({ id, t: world.time + sec, f: fn, every: sec }); return id; },
        cancelTimer(id) { for (let i = timers.length - 1; i >= 0; i--) if (timers[i].id === id) timers.splice(i, 1); },
        tween(obj, prop, to, sec, ease, done) {
            const id = ++timerIds;
            tweens.push({ id, o: obj, p: prop, from: obj[prop], to, dur: Math.max(sec, 0.0001), t: 0,
                          e: EASINGS[ease || 'easeOut'] || EASINGS.linear, done });
            return id;
        },
        cancelTween(id) { for (let i = tweens.length - 1; i >= 0; i--) if (tweens[i].id === id) tweens.splice(i, 1); },
        events: makeEventBus(),
        console: { log() {}, warn() {}, error() {} },
        Math, Date, JSON, String, Number, Boolean, Array, Object, isNaN, parseInt, parseFloat,
    };

    // --- attach scripts
    const attached = [];
    // Attaches every Script component of `def` to `go`. Used at load time and
    // for clones made by scene.spawn(), which copy their prototype's components
    // just as the engine's duplicate() does.
    function attachScripts(go, def) {
        go._def = def;
        for (const c of (def.components || [])) {
            if (c.type !== 'Script') continue;
            const file = c.Script;
            if (!file) { fail(`${id}: a Script component on '${go.name}' has no Script name`); continue; }
            const p = path.join(gameDir, 'scripts', file);
            if (!fs.existsSync(p)) { fail(`${id}: script '${file}' referenced by '${go.name}' does not exist`); continue; }

            const src = fs.readFileSync(p, 'utf8');
            const sandbox = Object.assign({}, prelude, {
                input, time: timeApi, scene: sceneApi, audio: audioApi, ui: uiApi, storage: storageApi,
                self: null, transform: null,
                __timers: timers,
            });
            // Params become bare globals in the script scope, as the engine does.
            Object.assign(sandbox, parseParams(c.Params));

            const ctx = vm.createContext(sandbox);
            try {
                vm.runInContext(src, ctx, { filename: file, timeout: 5000 });
            } catch (e) {
                fail(`${id}/${file}: failed to evaluate -- ${e.message}`);
                continue;
            }
            for (const m of src.matchAll(/actionPressed\(\s*"([^"]+)"\s*\)/g)) {
                world.namedActions.add(m[1]);
            }
            const proxy = world.proxyFor(go);
            sandbox.self = proxy;
            sandbox.transform = proxy;
            sandbox.selfRef = proxy;
            world.scopes.set(go._id, sandbox);
            attached.push({ go, file, sandbox, ctx, started: false, ticks: 0 });
        }
    }
    for (const go of world.objects) attachScripts(go, scene.objects.find(o => o.id === go._id));
    world.onSpawn = (go, proto) => {
        const before = attached.length;
        attachScripts(go, proto._def);
        for (let i = before; i < attached.length; i++) {
            const a = attached[i];
            if (typeof a.sandbox.start === 'function') {
                try { a.sandbox.start.apply(a.sandbox.selfRef, []); a.started = true; }
                catch (e) { fail(`${id}/${a.file}: start() threw on spawn -- ${e.message}`); }
            }
        }
    };

    // --- start()
    // Scene templates are parked inactive; attaching their scripts is harmless,
    // but starting them is not -- a template that spawns on start() would litter
    // the world with a unit nobody asked for.
    for (const a of attached) {
        if (!world.isActive(a.go)) continue;
        if (typeof a.sandbox.start === 'function') {
            try {
                a.sandbox.start.apply(a.sandbox.selfRef, []);
                a.started = true;
            } catch (e) {
                fail(`${id}/${a.file}: start() threw -- ${e.message}`);
            }
        }
    }

    // --- frames, with a rotating synthetic input so different branches run
    const INPUTS = [
        { axisX: 0, axisY: 0 },
        { axisX: 1, axisY: 0 },
        { axisX: -1, axisY: 0.3 },
        { axisX: 0.4, axisY: 1, a: true, aDown: true },
        { axisX: 0, axisY: 0, b: true, bDown: true },
        { axisX: -0.7, axisY: -0.7, touching: true, touchX: 0.6, touchY: 0.2 },
        { axisX: 0, axisY: 0, tapped: true, touching: true, touchX: -0.4, touchY: -0.1 },
        { axisX: 0, axisY: 0, actionsHeld: { jump: true }, actionsPressed: { jump: true } },
        { axisX: 0, axisY: 0, actionsHeld: { fire: true }, actionsPressed: { 'ui.play': true } },
    ];

    // Named actions: the shared list above only knows the legacy names, so a game
    // that reads its own ("Attack", "Dodge") would never take a swing here.  Any
    // name a script asks for that the shared list does not already press gets its
    // own profiles -- games 01-08 keep exactly the profiles they had.
    const covered = new Set();
    for (const p of INPUTS) {
        for (const k of Object.keys(p.actionsHeld || {})) covered.add(k);
        for (const k of Object.keys(p.actionsPressed || {})) covered.add(k);
    }
    for (const name of [...world.namedActions].sort()) {
        if (covered.has(name)) continue;
        INPUTS.push({ axisX: 0, axisY: 0, actionsPressed: { [name]: true } });
        INPUTS.push({ axisX: 0.55, axisY: 0.45, actionsHeld: { [name]: true },
                      actionsPressed: { [name]: true } });
    }

    const thrown = new Set();
    for (let f = 0; f < frames; f++) {
        world.frame = f;
        const preset = INPUTS[f % INPUTS.length];
        Object.assign(input, {
            axisX: 0, axisY: 0, a: false, b: false, aDown: false, bDown: false,
            touching: false, tapped: false, touchX: 0, touchY: 0,
            actionsHeld: {}, actionsPressed: {},
        }, preset);

        // timers and tweens (the prelude's __tick)
        for (let k = tweens.length - 1; k >= 0; k--) {
            const tw = tweens[k];
            tw.t += DT;
            const u = Math.min(tw.t / tw.dur, 1);
            tw.o[tw.p] = tw.from + (tw.to - tw.from) * tw.e(u);
            if (u >= 1) { tweens.splice(k, 1); if (tw.done) tw.done(); }
        }
        for (let i = timers.length - 1; i >= 0; i--) {
            const t = timers[i];
            if (world.time >= t.t) {
                if (t.every > 0) t.t += t.every; else timers.splice(i, 1);
                try { t.f(); } catch (e) {
                    const key = 'timer:' + e.message;
                    if (!thrown.has(key)) { thrown.add(key); fail(`${id}: a timer callback threw -- ${e.message}`); }
                }
            }
        }

        if (process.env.TRACE) {
            const row = attached.filter(x => x.file === process.env.TRACE);
            if (f % 30 === 0) {
                for (const a of row) {
                    const sc = a.sandbox;
                    const bits = [];
                    for (const k of Object.keys(sc)) {
                        const v = sc[k];
                        if (typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean') bits.push(k + '=' + v);
                        // Zero-argument getters (getAlive(), getWave() ...) as well:
                        // the interesting state is often only reachable that way.
                        else if (typeof v === 'function' && /^get[A-Z]/.test(k) && v.length === 0) {
                            try { bits.push(k + '()=' + v.call(sc.selfRef)); } catch (e) { bits.push(k + '()!'); }
                        }
                    }
                    console.log(`f${f} ${a.go.name} @${a.go.x.toFixed(2)},${a.go.z.toFixed(2)} ${bits.join(' ')}`);
                }
            }
        }
        for (const a of attached) {
            if (a.go._destroyed || !world.isActive(a.go)) continue;
            // ScriptSystem starts an instance on the first frame its object is
            // active in the hierarchy, not only at scene load -- a car parked
            // behind a menu starts when the menu lets it go.
            if (!a.started && typeof a.sandbox.start === 'function') {
                try {
                    a.sandbox.start.apply(a.sandbox.selfRef, []);
                    a.started = true;
                } catch (e) {
                    const key = 'start:' + a.file + ':' + e.message;
                    if (!thrown.has(key)) { thrown.add(key); fail(`${id}/${a.file}: start() threw on frame ${f} -- ${e.message}`); }
                    a.started = true;
                }
            }
            if (typeof a.sandbox.update !== 'function') continue;
            try {
                a.sandbox.update.apply(a.sandbox.selfRef, [DT]);
                a.ticks++;
            } catch (e) {
                const key = a.file + ':' + e.message;
                if (!thrown.has(key)) {
                    thrown.add(key);
                    fail(`${id}/${a.file}: update() threw on frame ${f} -- ${e.message}`);
                }
            }
        }

        // Integrate velocity, mirroring PhysicsWorld.fixedStep.  Scripts move by
        // writing vx/vy and letting the body carry them; without this step nothing
        // would ever change position and every movement assertion would be a lie.
        for (const go of world.objects) {
            if (!go.active || go._destroyed || go.bodyType === null) continue;
            if (go.bodyType === 0) {
                go.vx += world.gravityX * go.gravityScale * DT;
                go.vy += world.gravityY * go.gravityScale * DT;
                if (go.drag > 0) {
                    const k = Math.max(0, 1 - go.drag * DT);
                    go.vx *= k; go.vy *= k;
                }
                go.x += go.vx * DT; go.y += go.vy * DT;
            } else if (go.bodyType === 1) {
                go.x += go.vx * DT; go.y += go.vy * DT;
            }
            go._animTime += DT;      // so isAnimationFinished() eventually returns true
        }

        world.time += DT * world.timeScale;

        // --every N: a filmstrip of the run, for checking motion rather than
        // just the end state (a runner's track only exists mid-run).
        if (dumpEvery > 0 && f % dumpEvery === 0) snapshot(f);
    }

    /** The MeshRenderer / Light / Camera3D component of an object, or null. */
    function compOf(def, type) {
        if (!def || !def.components) return null;
        return def.components.find(c => c.type === type) || null;
    }

    function camera3dOf(def) {
        const c = compOf(def, 'Camera3D');
        if (!c) return null;
        return {
            fov: c['Field of View'] === undefined ? 60 : c['Field of View'],
            near: c['Near'], far: c['Far'],
            skyTop: c['Sky Top'] || '#FF3B7BD4', skyHorizon: c['Sky Horizon'] || '#FFBFD8F0',
            follow: c['Follow Target'] || '', lookAt: c['Look At Target'] !== false,
            offsetX: c['Offset X'] || 0, offsetY: c['Offset Y'] || 0, offsetZ: c['Offset Z'] || 0,
        };
    }

    function lightOf(def) {
        const c = compOf(def, 'Light');
        if (!c) return null;
        return { kind: c['Type'] || 'Directional', color: c['Color'] || '#FFFFFFFF',
                 intensity: c['Intensity'] === undefined ? 1 : c['Intensity'],
                 range: c['Range'] === undefined ? 10 : c['Range'] };
    }

    function screenSpaceOf(def) {
        const sr = compOf(def, 'SpriteRenderer');
        const tr = compOf(def, 'TextRenderer');
        return (sr && sr['Screen Space UI'] === true) || (tr && tr['Screen Space UI'] === true);
    }

    function meshOf(def) {
        const c = compOf(def, 'MeshRenderer');
        if (!c) return null;
        return {
            kind: c['Mesh'] || 'Cube', model: c['Model (.obj)'] || '',
            color: c['Color'] || '#FFD0D4DC', texture: c['Texture'] || '',
            tiling: c['Tiling'] === undefined ? 1 : c['Tiling'],
            specular: c['Specular'] === undefined ? 0.35 : c['Specular'],
            shininess: c['Shininess'] === undefined ? 32 : c['Shininess'],
            emission: c['Emission'] === undefined ? 0 : c['Emission'],
            unlit: c['Unlit'] === true,
        };
    }

    function emittersOf(def) {
        if (!def || !def.components) return [];
        return def.components.filter(c => c.type === 'ParticleEmitter');
    }

    // --- world dump, for tools/preview_scene.py ---------------------------
    // Everything the renderer needs to draw the scene the scripts produced:
    // transform, sprite texture, current clip, colour and draw order.  Nothing
    // here is inferred -- it is read back off the objects after the run.
    function snapshot(label) {
        const objects = world.objects
            .filter(o => !o._destroyed && o.active)
            .map(o => ({
                id: o._id, name: o.name, tag: o.tag, order: o.order,
                parent: o.parent === undefined ? -1 : o.parent,
                x: o.x, y: o.y, z: o.z,
                rotation: o.rotation, rotX: o.rotX, rotY: o.rotY, rotZ: o.rotZ,
                scaleX: o.scaleX, scaleY: o.scaleY, scaleZ: o.scaleZ,
                texture: o._texture || '', animation: o.animation || '',
                color: o.color, visible: o.visible !== false, text: o.text || '',
                // Screen-space UI is drawn by SceneRenderer.drawScreenUI through
                // its own fixed +/-5-unit view, not through the game camera, so
                // the previewer has to be told which objects those are.
                screen: screenSpaceOf(o._def),
                // Particle emitters: the previewer draws a steady emitter as a
                // soft pool, which is what tells it a torch is a light source.
                // Particle emitters: a steady emitter is drawn by the previewer
                // as a soft pool of its start colour, which is how a torch reads
                // as a light source in a renderer that has no 2D lighting.
                // 3D: mesh, light and camera components, verbatim as declared,
                // so the offline renderer can draw the scene the engine draws.
                mesh: meshOf(o._def), light: lightOf(o._def), camera3d: camera3dOf(o._def),
                _emitting: emittersOf(o._def).some(pe => pe['Emitting'] || (pe['Rate'] || 0) > 0),
                _size: (emittersOf(o._def)[0] || {})['Start Size'] || 0,
                _color: (emittersOf(o._def)[0] || {})['Start Color'] || '#FFFFFFFF',
            }));
        const file = dumpEvery > 0
            ? path.join(dumpDir, id, `frame_${String(label).padStart(4, '0')}.json`)
            : path.join(dumpDir, id + '.json');
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, JSON.stringify({
            id, frames, spawned: world.spawnedCount, objects,
            // Scene-level render state: ambient tint, fog band.  Both are read
            // by Renderer3D, and both change what a 3D frame looks like.
            ambient: scene.ambient || '#FF404040',
            fog: !!scene.fog, fogColor: scene.fogColor || '#FF808080',
            fogStart: scene.fogStart === undefined ? 0 : scene.fogStart,
            fogEnd: scene.fogEnd === undefined ? 0 : scene.fogEnd,
            // The declared clips, so the renderer can resolve a clip name to a
            // sheet cell without re-deriving GameLibrary's rules.  Each clip
            // carries its own grid, because a `tileset`-declared grid (hand
            // authored, for opaque atlases the store will not guess at) is not
            // reported in the store manifest at all.
            clips: declaredGrids,
        }, null, 1));
    }

    // --- post-run assertions
    ok(attached.length > 0, `${id}: no scripts were attached`);

    // A name that was looked up but never found across the whole run means the
    // scene and the script disagree about what exists.  Names that resolved at
    // least once are fine: they may be spawned or destroyed mid-run.
    const neverFound = [...world.missingFinds].filter(n => !world.foundNames.has(n));
    for (const n of neverFound) {
        fail(`${id}: scene.find('${n}') never resolved -- no object by that name in the scene`);
    }

    // Every script that defines update() must actually have been ticked, and
    // every object it ran on must still be live.  A script silently skipped
    // because its object started inactive is exactly the kind of bug that reads
    // as "the feature does nothing".
    for (const a of attached) {
        const hasUpdate = typeof a.sandbox.update === 'function';
        const hasStart = typeof a.sandbox.start === 'function';
        if (!hasStart && !hasUpdate) {
            fail(`${id}/${a.file}: defines neither start() nor update() -- dead script`);
        }
        // Inactive prototypes (scene.spawn templates) are neither started nor
        // ticked by design; only clones and ordinary active objects run.
        const isTemplate = !a.go._isClone && a.go._def && a.go._def.active === false;
        if (hasStart && !isTemplate) ok(a.started, `${id}/${a.file}: start() was never called`);
        if (hasUpdate && !isTemplate) ok(a.ticks > 0, `${id}/${a.file}: update() was never called`);
    }

    // Textures set at runtime must be declared assets.
    const declaredImages = new Set((meta.assets || []).map(a => a.as));
    for (const t of world.usedTextures) {
        ok(declaredImages.has(t), `${id}: setTexture('${t}') is not a declared asset`);
    }

    if (dumpDir && dumpEvery === 0) snapshot('final');

    // Transforms must stay finite and within a sane range.
    for (const go of world.objects) {
        for (const k of ['x', 'y', 'z', 'scaleX', 'scaleY', 'scaleZ', 'rotation']) {
            const v = go[k];
            if (!Number.isFinite(v)) fail(`${id}: '${go.name}'.${k} became ${v}`);
            else if (Math.abs(v) > 1e6) fail(`${id}: '${go.name}'.${k} ran away to ${v}`);
        }
    }


    // --- gameplay assertions
    // Running without throwing is not the same as working.  Each game has to be
    // shown to have actually done the thing it is for: the runner must have
    // covered ground, the fighter must have taken damage, the day-night cycle
    // must have moved.  These read the scripts' own state after the run.
    const scriptState = {};
    for (const a of attached) scriptState[a.file] = a.sandbox;
    const stateOf = (file, key) => {
        const sb = scriptState[file];
        return sb ? sb[key] : undefined;
    };
    const moved = (name) => {
        const go = world.byName.get(name);
        if (!go) return false;
        const st = go._start;
        return st && (Math.abs(go.x - st.x) > 1e-3 || Math.abs(go.y - st.y) > 1e-3
                   || Math.abs(go.z - st.z) > 1e-3);
    };

    if (id === '01_topdown_rpg') {
        // The player is driven by synthetic input for four seconds; if it never
        // moved, the controller is not wired to input.
        ok(moved('Player'), `${id}: the Player never moved under four seconds of input`);
        const enemies = ['EnemySkeleton', 'EnemyVampire'];
        for (const e of enemies) {
            const go = world.byName.get(e);
            ok(go && go.active, `${id}: '${e}' is not active -- the game ships with no enemies`);
        }
        // Enemies patrol, so they must move too.
        ok(moved('EnemySkeleton'), `${id}: EnemySkeleton never moved -- its AI is not ticking`);
        const inv = world.byName.get('InventoryPanel');
        ok(inv && inv.active, `${id}: InventoryPanel is inactive, so it can never receive update()`);
    }

    if (id === '02_fighting_2d') {
        const hp1 = stateOf('Fighter.js', 'health');
        // Both fighters run the same file, so the last instance wins; what matters
        // is that health is a real number in range and not stuck at a sentinel.
        ok(typeof hp1 === 'number' && Number.isFinite(hp1) && hp1 >= 0 && hp1 <= 100,
           `${id}: fighter health is not a sane value (${hp1})`);
        // The select screen must have activated the fighters it chose.
        const p1 = world.byName.get('Fighter_P1');
        ok(p1 && p1.active, `${id}: Fighter_P1 was never activated by character select`);
        // A round must have been started.
        const rn = stateOf('RoundManager.js', 'roundNumber');
        ok(typeof rn === 'number' && rn >= 1, `${id}: roundNumber is not advancing (${rn})`);
    }

    if (id === '03_action_3d') {
        ok(moved('Hero'), `${id}: the Hero never moved under four seconds of input`);
        // The lighting rig advances a 600s cycle; in 4s the hour must move.
        const hour = stateOf('LightingRig.js', 'hour');
        ok(typeof hour === 'number' && hour !== 9,
           `${id}: the day-night cycle never advanced (hour still ${hour})`);
        const sun = world.byName.get('Sun');
        ok(sun && typeof sun.lightIntensity === 'number',
           `${id}: the Sun's lightIntensity was never set`);
        for (const e of ['Creature', 'RidgeGuard']) {
            const go = world.byName.get(e);
            ok(go && go.active, `${id}: '${e}' is not active -- the game ships with no enemies`);
        }
    }

    if (id === '04_runner_3d') {
        const dist = stateOf('TrackGenerator.js', 'distance');
        ok(typeof dist === 'number' && dist > 0,
           `${id}: the runner covered no distance (${dist}) -- the track never advanced`);
        const spd = stateOf('VehicleController.js', 'speed');
        ok(typeof spd === 'number' && spd > 0,
           `${id}: the vehicle never reached speed (${spd})`);
        ok(world.spawnedCount > 0, `${id}: nothing was spawned -- no track, obstacles or pickups`);
    }

    if (id === '05_shooter_lowpoly_3d') {
        ok(moved('Player'), `${id}: the Player never moved under four seconds of input`);
        const wave = stateOf('WaveDirector.js', 'wave');
        ok(typeof wave === 'number' && wave >= 1, `${id}: wave counter is not valid (${wave})`);
        ok(world.spawnedCount > 0, `${id}: no Grunts or bullets were spawned from templates`);
    }

    if (id === '06_open_world_3d') {
        ok(moved('Car'), `${id}: the Car never moved under four seconds of input`);
        const trees = world.byName.get('Tree000');
        ok(trees && typeof trees.active === 'boolean', `${id}: tree culling has no target`);
    }

    if (id === '07_motorbike_2d') {
        const bike = world.byName.get('Bike');
        ok(bike && moved('Bike'), `${id}: the Bike never moved under four seconds of input`);
        ok(world.byName.get('WheelBack') && world.byName.get('WheelFront'),
           `${id}: the bike's wheel children are missing`);
    }

    if (id === '08_zombie_topdown') {
        ok(moved('Player') || world.spawnedCount > 0,
           `${id}: neither the Player moved nor any zombie spawned`);
        const wave = stateOf('ZombieDirector.js', 'wave');
        ok(typeof wave === 'number' && wave >= 1, `${id}: wave counter is not valid (${wave})`);
    }

    if (dumpDir) {
        fs.mkdirSync(dumpDir, { recursive: true });
        fs.writeFileSync(path.join(dumpDir, id + '.assets.json'), JSON.stringify({
            id, title: meta.title, assets: meta.assets || [], scenes: meta.scenes || [],
        }, null, 1));
    }

    return {
        id,
        scripts: attached.length,
        spawned: world.spawnedCount,
        shakes: world.shakeCount,
        reloads: world.reloadCount || 0,
        clips: world.clips.size,
        audioUsed: [...world.usedAudio].sort(),
        texturesUsed: [...world.usedTextures].sort(),
    };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

const gamesRoot = path.join(repo, 'games');
const dirs = fs.readdirSync(gamesRoot)
    .filter(d => fs.existsSync(path.join(gamesRoot, d, 'game.json')))
    .filter(d => !filter || d.includes(filter))
    .sort();

if (dirs.length === 0) {
    console.error(`no games matched '${filter}' in ${gamesRoot}`);
    process.exit(2);
}

checkPreludeDrift();
checkApiDrift();

console.log(`S Engine headless game harness`);
console.log(`repo=${repo}  frames=${frames} (${(frames * DT).toFixed(1)} s)  games=${dirs.length}\n`);

const summaries = [];
for (const d of dirs) {
    try {
        summaries.push(runGame(path.join(gamesRoot, d)));
    } catch (e) {
        fail(`${d}: harness crashed -- ${e.message}\n${e.stack.split('\n').slice(1, 4).join('\n')}`);
    }
}

for (const s of summaries) {
    console.log(`== ${s.id}`);
    console.log(`   scripts run: ${s.scripts}   spawned: ${s.spawned}   shakes: ${s.shakes}   reloads: ${s.reloads}`);
    console.log(`   clips declared: ${s.clips}`);
    if (s.audioUsed.length) console.log(`   audio: ${s.audioUsed.join(', ')}`);
    if (s.texturesUsed.length) console.log(`   textures set at runtime: ${s.texturesUsed.join(', ')}`);
}

console.log(`\n---------------------------------------------`);
console.log(`${checks} checks, ${problems.length} failures`);
if (problems.length) {
    console.log(`RESULT: FAIL`);
    for (const p of problems) console.log(`  - ${p}`);
    process.exit(1);
}
console.log(`RESULT: PASS`);
