S ENGINE — JAVASCRIPT API, runtime v3.2.0, Phase 3

LIFECYCLE — define any of these in a Script component
  start()              once, when play begins
  update(dt)           every frame, dt in seconds
  onFixedUpdate(dt)    fixed 1/60 s step [max 3 per frame]
  onLateUpdate(dt)     after all update() calls
  onEnable() / onDisable()
  onCollision(other)   solid collision began
  onTrigger(other)     entered a trigger
  onTriggerExit(other) left a trigger
  onTap()              this object's collider was tapped
  onUIButtonClick(label)  UIButton tap, receives the button label
  onClick(label)       shorthand UIButton tap
  onDestroy()          object was destroyed
  onStop()             play mode / scene ended

GLOBALS
  self  transform  gameObject   [the same object wrapper]
  scene  input  time  audio  console  assets
  log()  warn()  error()

SELF / TRANSFORM / GAMEOBJECT
  id name tag active order              [read/write except id]
  x y rotation scaleX scaleY           [local, read/write]
  worldX worldY                        [read-only]
  setPosition(x,y)  setPosition(x,y,z)
  setWorldPosition(x,y)
  move(dx,dy)  move(dx,dy,dz)
  rotate(deg)  rotate(rx,ry,rz)
  distanceTo(o)  distanceTo3(o)
  is(o)  child(name)  parent           [read-only]
  destroy()  unwrap()                 [unwrap: raw GameObject, advanced]
  hasComponent(type)  setComponentEnabled(type, enabled)

PHYSICS / SELF
  vx vy vz grounded                   [read/write velocity; grounded read-only]
  setVelocity(vx,vy)  setVelocity(x,y,z)
  addForce(fx,fy)  addForce(x,y,z)   [instant impulse / mass]
  addImpulse(fx,fy)  addImpulse(x,y,z)
  groundNormal -> [x,y,z] array, 3D
  setLayer(layer)  setMask(mask)      [0..15 / 16-bit mask]
  overlaps(o)                         [real collider and tilemap test]
  setColliderVertices(vertices)       [convex polygon "x,y; x,y; ..."]
  setJointLength(v)  setJointTarget(name)  isJointStressed()  isJointBroken()
  setJointEnabled(enabled)

3D / SELF
  z rotX rotY rotZ scaleZ worldZ vz
  forward() -> [x,y,z] world direction
  setMeshColor(hex)  setIntensity(v)  setSky(top, horizon)
  setSpotCone(deg)  setOrthoSize(v)

ANIMATION & RENDERING / SELF
  play(clip)  stopAnimation()  animation  animationFinished
  setAnimSpeed(s)  setShaderParam(v)
  setMaterial(name)  setSpriteFrame(name)  setFont(name)
  color  visible  flipX  text  setTexture(name)
  burst(n)  setEmitting(enabled)      [particles]
  size                                [Camera2D size]
  send(fn, arg)  send(fn)             [call fn on another object's scripts]

PHASE 3 MODEL ANIMATION / SELF
  playModelAnimation(clip)  stopModelAnimation()
  setModelAnimationSpeed(s)  setModelAnimationTime(t)
  ModelAnimator samples 3D tracks and drives named joint objects.

CANVAS UI / SELF
  uiText  setUIText(value)  uiProgress  setUIProgress(v)
  isUIClicked()  setUIImage(name)  setUIVisible(enabled)
  setUIRect(x,y,w,h)
  UIButton taps also call onUIButtonClick(label) and onClick(label)

TILEMAP / SELF
  setTile(col,row,id)  getTile(col,row)  tileAtWorld(x,y)

INPUT
  axisX axisY [-1..1]  a b aDown bDown
  up down left right
  touching tapped touchX touchY        [world-space touch]
  key(code)                            [Android KeyEvent code held]

SCENE
  name  camera  camera3D  gravityX gravityY gravity3D
  find(name)  findAll(tag)  count(tag)
  spawn(name)  spawn(name,x,y)  spawn(name,x,y,z)
  spawnPrefab(name,x,y)  spawnPrefab(name,x,y,z)
  saveSnapshot(name)
  load(sceneName)  reload()  shake(amount)
  broadcast(fn)  broadcast(fn, arg)
  pool(name,size)  obtain(name,x,y)  obtain(name,x,y,z)  release(o)
  raycast2D(ox,oy,dx,dy,max)          -> object or null
  raycast(ox,oy,oz,dx,dy,dz,max)      -> object or null
  raycastAll2D(ox,oy,dx,dy,max)       -> array of objects, nearest first
  raycastAll(ox,oy,oz,dx,dy,dz,max)   -> array of objects, nearest first
  overlapCircle(x,y,r)  overlapCircle(x,y,r,tag)
  overlapSphere(x,y,z,r)  overlapSphere(x,y,z,r,tag)
  terrainHeight(x,z)                  -> world height [0 if no terrain]
  setFog(start,end)  setFogColor(hex)

ASSETS — project-local text data only
  assets.readText(name)  assets.writeText(name,text)
  assets.exists(name)  assets.validate(name)  assets.list(kind)
  kinds: PREFAB MATERIAL ATLAS TILEMAP SCENE TEXT ANIMATION3D SPRITE TEXTURE_META MODELPACK SHEET or all

TIME
  time.time  time.frame  time.fps  time.delta  time.scale [0..4]

AUDIO
  audio.play(file)  audio.play(file, volume)  audio.beep()
  audio.stopAll()
  audio.play3d(file,x,y,z)  audio.play3d(file,x,y,z,volume)
  audio.play3dLoop(file,x,y,z)  audio.stop3d(handle)
  audio.move3d(handle,x,y,z)  audio.setListener(x,y,z)
  audio.setFalloff(minDist,maxDist)
  audio.playMusic(file)  audio.playMusic(file,loop)
  audio.pauseMusic()  audio.resumeMusic()  audio.stopMusic()
  audio.setMusicVolume(v)  audio.setSfxVolume(v)

HELPERS
  random()  random(a,b)  random(a)  randomInt()  randomInt(a,b)  randomInt(a)
  clamp(v,a,b)  lerp(a,b,t)  moveToward(cur,target,maxDelta)  sign(x)
  choose(a,b,...)  angleDeg(dx,dy)
  dist(x1,y1,x2,y2)  dist3(x1,y1,z1,x2,y2,z2)
  smoothstep(a,b,x)  mapRange(v,inLo,inHi,outLo,outHi)  wrap(v,lo,hi)
  damp(a,b,lambda,dt)  lerpAngle(a,b,t)  rotateToward(cur,target,maxDelta)
  randomSign()  randomPick(arr)  assert(cond,msg)
  after(sec,fn)  every(sec,fn)  wait(sec,fn)  cancelTimers()
  tween(obj,prop,to,dur,ease,onDone)  cancelTweens(obj)
  require("Utils.js")                 [module.exports cached]

PARAMS
  "speed=5, jump=10" in a Script component become top-level variables.

BLUEPRINTS
  .bp files compile to this same API.

BIG EDITION RUNTIME
  Prefabs keep hierarchy, materials stay asset-backed, tilemaps collide,
  UI buttons dispatch script events, spot lights and orthographic cameras
  render in 3D, polygon hulls, materials and joints extend 2D physics.
  Invalid data assets are rejected by the editor and by assets.writeText.

PHASE 3 ASSET RUNTIME
  ZIP, store-mode RAR and ESR bundles install model, texture and animation
  assets together. Model packs link DCC source files to an open render mesh,
  texture profiles control wrap, filter and mipmaps, sprite documents export
  PNG sheets with atlas and clip assets, and 3D clips drive rigged tracks.

SPRITE SHEET RUNTIME — v3.2.0
  Sprite sheet documents slice a texture into named pixel rectangles with a
  grid, fixed cells or automatic alpha detection. Export packs the frames into
  a fresh PNG, writes an atlas whose frames carry both UVs and pixel
  rectangles, and emits a named-frame animation clip: the Animator plays
  frameNames through the atlas so packed, irregularly sized sprites animate
  without a uniform grid. Re-exporting the same sheet keeps its asset names.

SAFETY
  Scripts cannot access host Java classes, the file system or the network.
  A runaway script is aborted after its per-frame instruction/time budget.
