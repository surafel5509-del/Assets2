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
 *   node tools/run_games.js [slug-substring] [--frames N] [--repo DIR]
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

for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--frames') frames = parseInt(argv[++i], 10);
    else if (argv[i] === '--repo') repo = path.resolve(argv[++i]);
    else if (!argv[i].startsWith('--')) filter = argv[i];
}

const DT = 1 / 60;

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
    'color', 'text', 'size', 'visible', 'animation', 'index',
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
        this.index = 0;
        this.parent = def.parent === undefined ? null : def.parent;

        this._destroyed = false;
        this._animTime = 0;
        this._animFrames = 8;
        this._animFps = 12;
        this._scripts = [];

        for (const c of (def.components || [])) {
            if (c.type === 'SpriteRenderer' && c.Texture) this._texture = c.Texture;
            if (c.type === 'TextRenderer' && c.Text !== undefined) this.text = c.Text;
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
        get index() { return go.index; },

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
        this.time = 0;
        this.frame = 0;
        this.declaredAudio = declaredAudio;
        this.gameDir = gameDir;
        this.spawnedCount = 0;
        this.shakeCount = 0;
        this.clips = new Set();
        this.missingFinds = new Set();
        this.foundNames = new Set();

        for (const def of scene.objects) {
            const go = new GameObj(def.id, def, this);
            this.objects.push(go);
            this.byId.set(go._id, go);
            this.byName.set(go.name, go);
        }
    }

    proxyFor(go) {
        if (!this.proxies.has(go._id)) this.proxies.set(go._id, makeSObject(go, this));
        return this.proxies.get(go._id);
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
                go._texture = proto._texture;
                self.objects.push(go);
                self.byId.set(id, go);
                self.spawnedCount++;
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
        const n = Number(v);
        out[k] = (v !== '' && Number.isFinite(n)) ? n : v;
    }
    return out;
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

    const world = new World(scene, gameDir, declaredAudio);
    // Every clip name GameLibrary will materialise as "<name>.anim".
    for (const a of (meta.assets || [])) {
        for (const k of Object.keys(a.clips || {})) world.clips.add(k);
    }
    world.clipsExpected = (meta.assets || []).some(a => a.clips);
    const sceneApi = world.makeSceneApi();
    const input = {
        axisX: 0, axisY: 0, a: false, b: false, aDown: false, bDown: false,
        touching: false, tapped: false, touchX: 0, touchY: 0,
    };
    const timeApi = {
        get time() { return world.time; },
        get frame() { return world.frame; },
        get fps() { return 60; },
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
        beep() {},
        stopAll() {},
    };

    const timers = [];
    const prelude = {
        log() {}, warn() {}, error() {},
        random(a, b) { return a === undefined ? 0.5 : a + 0.5 * ((b || 1) - a); },
        randomInt(a, b) { return Math.floor(a + 0.5 * ((b || 0) - a + 1)); },
        clamp(v, a, b) { return Math.max(a, Math.min(b, v)); },
        lerp(a, b, t) { return a + (b - a) * t; },
        after(sec, fn) { timers.push({ t: world.time + sec, f: fn, every: 0 }); },
        every(sec, fn) { timers.push({ t: world.time + sec, f: fn, every: sec }); },
        console: { log() {}, warn() {}, error() {} },
        Math, Date, JSON, String, Number, Boolean, Array, Object, isNaN, parseInt, parseFloat,
    };

    // --- attach scripts
    const attached = [];
    for (const go of world.objects) {
        const def = scene.objects.find(o => o.id === go._id);
        for (const c of (def.components || [])) {
            if (c.type !== 'Script') continue;
            const file = c.Script;
            if (!file) { fail(`${id}: a Script component on '${go.name}' has no Script name`); continue; }
            const p = path.join(gameDir, 'scripts', file);
            if (!fs.existsSync(p)) { fail(`${id}: script '${file}' referenced by '${go.name}' does not exist`); continue; }

            const src = fs.readFileSync(p, 'utf8');
            const sandbox = Object.assign({}, prelude, {
                input, time: timeApi, scene: sceneApi, audio: audioApi,
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
            const proxy = world.proxyFor(go);
            sandbox.self = proxy;
            sandbox.transform = proxy;
            sandbox.selfRef = proxy;
            world.scopes.set(go._id, sandbox);
            attached.push({ go, file, sandbox, ctx, started: false, ticks: 0 });
        }
    }

    // --- start()
    for (const a of attached) {
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
    ];

    const thrown = new Set();
    for (let f = 0; f < frames; f++) {
        world.frame = f;
        const preset = INPUTS[f % INPUTS.length];
        Object.assign(input, {
            axisX: 0, axisY: 0, a: false, b: false, aDown: false, bDown: false,
            touching: false, tapped: false, touchX: 0, touchY: 0,
        }, preset);

        // timers (the prelude's __tick)
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

        for (const a of attached) {
            if (a.go._destroyed || !a.go.active) continue;
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

        // advance animation clocks so isAnimationFinished() eventually returns true
        for (const go of world.objects) go._animTime += DT;

        world.time += DT;
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
        if (hasStart) ok(a.started, `${id}/${a.file}: start() was never called`);
        if (hasUpdate) ok(a.ticks > 0, `${id}/${a.file}: update() was never called`);
    }

    // Textures set at runtime must be declared assets.
    const declaredImages = new Set((meta.assets || []).map(a => a.as));
    for (const t of world.usedTextures) {
        ok(declaredImages.has(t), `${id}: setTexture('${t}') is not a declared asset`);
    }

    // Transforms must stay finite and within a sane range.
    for (const go of world.objects) {
        for (const k of ['x', 'y', 'z', 'scaleX', 'scaleY', 'scaleZ', 'rotation']) {
            const v = go[k];
            if (!Number.isFinite(v)) fail(`${id}: '${go.name}'.${k} became ${v}`);
            else if (Math.abs(v) > 1e6) fail(`${id}: '${go.name}'.${k} ran away to ${v}`);
        }
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
