# 02 — Graphics

Two render paths exist: the **Kotlin runtime** that ships today (OpenGL ES 2.0),
and the **native RHI** designed for GLES 3.x / Vulkan. They are not the same code,
and the gap between them is the largest in the project. It is documented precisely
in §7 rather than glossed over.

**Kotlin runtime:** 1 815 lines across 11 files in `engine/render/`.

```
SceneRenderer.kt   497   orchestrates a frame: cull, sort, dispatch 2D/3D, post FX
Shaders.kt         303   GLSL sources + uniform binding
Meshes.kt          246   procedural primitives (cube, sphere, capsule, torus, …)
Renderer2D.kt      194   sprites, shapes, text, particles
Renderer3D.kt      191   Blinn-Phong meshes, sky, fog, lights
View3D.kt           96   3D camera state and projection
PostProcessor.kt    86   framebuffer post effects
Textures.kt         84   bitmap upload, caching, mipmaps
GL.kt               59   thin GLES20 wrapper
EditorState.kt      32   selection overlays, gizmo state
View2D.kt           27   2D camera state
```

## 1. What renders today

`Renderer3D` is, in its own header comment, a *"Forward Blinn-Phong mesh renderer
with gradient sky, fog, 1 directional + 4 point lights."* That is accurate:

- **Shading model:** Blinn-Phong, not PBR. `MeshRenderer` exposes `specular`
  (0–2) and `shininess` (1–256) rather than metallic/roughness.
- **Lights:** one directional plus the four point lights nearest the camera. The
  selection is per-frame and distance-sorted, so a scene with many point lights
  degrades gracefully instead of exceeding a uniform limit. With no lights present
  a default sun is substituted, so an unlit scene is still visible.
- **Atmosphere:** gradient sky (`skyTop` → `skyHorizon`) and distance fog
  (`fogStart`/`fogEnd`).
- **Shadows:** none in the runtime. The 3D games use projected blob shadows — a
  dark sprite parented under the character — which is the correct choice for this
  renderer and cheap enough to leave on always.

2D rendering handles sprites, primitive shapes (square, circle, triangle), text,
and particles, sorted by an explicit `order` field so UI can be layered over world
content without a separate canvas.

## 2. Post-processing

`PostProcessor` runs the frame through a framebuffer and applies one of ten
effects, selectable per camera on both `Camera` and `Camera3D`:

`None · Grayscale · Sepia · Vignette · CRT · Pixelate · Bloom · Invert ·
Chromatic · Custom Shader`

`Custom Shader` loads a user `.glsl` asset, which is also how the terrain and
mesh shaders work. The effect shader contract is:

```glsl
vec4 effect(vec4 color, vec2 uv) {
    // uTime, uParam, uTex, uUseTex, uColor, uResolution
}
```

## 3. Particles

`ParticleEmitter` is a component, not a separate system, so particles live in the
scene graph and inherit its transform. Properties: `rate`, `lifetime`, `speed`,
`direction`, `spread`, `startSize`/`endSize`, `startColor`/`endColor`, `gravity`,
`maxParticles`, `additiveBlend`, `texture`.

Two emission modes matter for the shipped games:

- **Continuous** (`emitting = true`, `rate > 0`) — campfire flames, engine exhaust.
- **Burst** (`emitting = false`, `rate = 0`, driven by `burst(n)`) — impact
  sparks, coin collection. The games call `burst()` from script rather than
  toggling `emitting`, because a burst that is never switched off leaks particles.

`maxParticles` is a hard cap. Particles are pooled; hitting the cap drops new
emissions rather than growing the buffer, so a mis-set emitter cannot exhaust
memory.

## 4. Textures

`Textures.kt` uploads bitmaps with an LRU cache. Nearest-neighbour filtering is
used for the pixel-art packs — the store's sprite sheets are 32×32 and 96×96 cell
grids, and linear filtering blurs them into mud at the scales the games use.

## 5. The native RHI

`native/engine/render/RHI.h` (260 lines) defines the device/command/resource
interface for a modern backend, and `NullRHI` (109 + 187 lines) implements it as a
**validating** backend that records draws and flags any issued outside a render
pass. See [01-NATIVE-CORE.md](01-NATIVE-CORE.md#5-render-hardware-interface).

The design targets, in the order they should be built:

1. **GLES 3.x backend** — instancing and UBOs are the two wins that matter most for
   the tilemap and track-segment rendering the shipped games do.
2. **PBR** — replace Blinn-Phong with metallic/roughness. Requires an IBL
   prefilter, which is a build-time step in the asset store, not a runtime cost.
3. **Cascaded shadow maps** — the directional light is the only caster that needs
   cascades; point lights can stay shadowless. Three cascades is the usual mobile
   compromise.
4. **Vulkan backend** — last, and only once the GLES 3 backend is stable, because
   the value is in explicit synchronisation and lower driver overhead, not in
   features GLES 3 lacks.

## 6. Reference shading maths (native, tested)

`native/engine/render/Pbr.h` and `native/engine/render/ShadowCascades.h` implement
the PBR BRDF and cascaded shadow maps as **CPU reference code**, covered by the host
test suite.

They exist for three reasons:

1. **They are testable.** Energy conservation, BRDF reciprocity, cascade monotonicity
   and shadow-map stability are properties you can assert on the host. A shader you
   cannot test is a shader you debug by squinting at a phone screen.
2. **They are the specification.** When the GLES and Vulkan backends disagree about
   how a surface looks, these files are the arbiter.
3. **Some of it is genuinely CPU work.** The split-sum environment BRDF and the
   cascade matrices are computed per frame or at build time, not per pixel.

What is in them:

- **`Pbr.h`** — Cook-Torrance with GGX normal distribution, Smith correlated
  geometry and Schlick fresnel; metallic/roughness workflow with the `f0` split
  between dielectric (0.04) and metal (albedo-tinted); spot cone falloff;
  windowed distance attenuation that reaches **exactly zero** at `range`, which is
  what makes the "nearest N lights" cull correct rather than a visible pop;
  split-sum IBL; Reinhard tone mapping plus the sRGB transfer.
- **`ShadowCascades.h`** — practical split scheme (log/uniform blend by `lambda`),
  frustum-slice unprojection through the inverse view-projection, light matrices
  with **texel snapping**, and cascade selection.

The stabilisation test is the one worth calling out. A shadow map that shifts by a
fraction of a texel between frames makes every shadow edge crawl, and it is the
property most shadow implementations get wrong; the suite asserts that a sub-texel
camera move leaves the cascade projection bit-identical.

Supporting this required adding a **general 4×4 inverse** to `Math.h`. Only
`invertRigid` existed, and it cannot serve here: a view-projection matrix carries
the perspective divide and is not a rigid transform. It returns `false` on singular
input rather than silently producing garbage.

**What this is not:** none of it is wired into the Kotlin runtime. The runtime still
draws Blinn-Phong with blob shadows. These headers are the tested specification the
GPU backends get written against, not a working renderer.

## 7. Gap between target and current

| Capability | Target | Runtime today | Native maths |
|---|---|---|---|
| API | Vulkan / GLES 3.x | **GLES 2.0** | interface only |
| Shading | PBR metallic/roughness | **Blinn-Phong** | **`Pbr.h`, tested** |
| Directional light | yes | **yes** | **`Pbr.h`, tested** |
| Point lights | yes | **yes (nearest 4)** | **`Pbr.h`, tested** |
| Spot lights | yes | **no** | **`Pbr.h`, tested** |
| Cascaded shadow maps | yes | **no** (blob shadows) | **`ShadowCascades.h`, tested** |
| Post FX | yes | **yes (10 effects)** | not implemented |
| Particles | yes | **yes** | not implemented |
| Instancing | yes | **no** | not implemented |

So the shading maths is ahead of the renderer that would use it. That is a
deliberate order — the maths is the part that is expensive to get right and cheap to
verify, while a GL backend cannot be exercised at all without a device.

`Light.kind` in the Kotlin runtime still distinguishes only `Directional` and
`Point`. The `LightKind::Spot` enum value and its cone falloff exist and are tested
in `Pbr.h`; exposing it means a third choice in the Inspector and a cone term in the
fragment shader. Small and well-contained, but not done.
