// SPDX-License-Identifier: MIT
// S Engine 2.0 -- cascaded shadow maps.
//
// A single shadow map for a whole view has to cover everything from the pixel in
// front of the camera to the far plane, so its texels end up metres wide near the
// horizon and shadows look blocky.  Cascading splits the view frustum into a few
// depth slices and gives each one its own orthographic shadow map, so near slices
// get high resolution where the player is actually looking.
//
// This file is the CPU half: it computes the splits, builds the light-space
// matrices, and stabilises them.  The GPU half samples the resulting atlas.
//
// Three properties matter and are all tested:
//
//   1. Splits must be monotonic and span [near, far] exactly.
//   2. Every corner of a cascade's frustum slice must land inside its shadow map,
//      or geometry silently loses its shadow.
//   3. The matrices must be STABLE.  A shadow map that shifts by a fraction of a
//      texel between frames makes every shadow edge crawl, which reads as
//      shimmering even when nothing is moving.  This is the property most shadow
//      implementations get wrong.
#pragma once

#include "../core/Types.h"
#include "../math/Math.h"

#include <cmath>

namespace se {

/// Number of cascades.  Three is the usual mobile compromise: enough splits to
/// keep near-field shadows sharp, few enough that the extra draw passes fit in a
/// phone's frame budget.
inline constexpr int kShadowCascades = 3;

/// Resolution of one cascade in the atlas.  The atlas is
/// (kShadowCascadeResolution * kShadowCascades) wide by kShadowCascadeResolution
/// tall, so three 1024 cascades cost one 3072x1024 target.
inline constexpr int kShadowCascadeResolution = 1024;

struct ShadowSplitPlan {
    /// View-space depth of each split boundary, ascending.  Index 0 is the near
    /// plane and index `cascades` is the far plane, so there are `cascades + 1`
    /// boundaries for `cascades` slices.
    f32 boundaries[kShadowCascades + 1] = {};
    int cascades = 0;
};

struct CascadeMatrices {
    Mat4 lightView;
    Mat4 lightProj;
    Mat4 lightViewProj;      ///< world -> light clip
    /// World-space sphere enclosing this cascade's frustum slice.  Used both to
    /// size the orthographic projection and to cull shadow casters.
    Vec3 sphereCenter{ 0, 0, 0 };
    f32  sphereRadius = 0.0f;
    f32  texelSize = 0.0f;   ///< world units per shadow texel
};

// ---------------------------------------------------------------------------
// Split scheme.
// ---------------------------------------------------------------------------

/// Practical split scheme: a blend of logarithmic and uniform splits.
///
/// Uniform splits give every slice equal depth, which wastes resolution up close.
/// Logarithmic splits give every slice equal *ratio*, which is optimal for
/// perspective but leaves the far slices absurdly deep.  Blending them is what
/// production engines do, and `lambda` controls the balance:
///
///   lambda = 1.0  pure logarithmic
///   lambda = 0.0  pure uniform
///   lambda ~ 0.5  the usual compromise; higher values favour the near field.
inline ShadowSplitPlan computeSplits(f32 zNear, f32 zFar, f32 lambda = 0.5f,
                                     int cascades = kShadowCascades) {
    ShadowSplitPlan plan;
    if (cascades < 1) cascades = 1;
    if (cascades > kShadowCascades) cascades = kShadowCascades;
    plan.cascades = cascades;

    if (zFar <= zNear) {           // degenerate: keep the plan valid anyway
        for (int i = 0; i <= cascades; ++i) plan.boundaries[i] = zNear;
        return plan;
    }

    const f32 ratio = zFar / zNear;
    for (int i = 0; i <= cascades; ++i) {
        const f32 p = static_cast<f32>(i) / static_cast<f32>(cascades);
        const f32 log = zNear * std::pow(ratio, p);        // geometric
        const f32 uni = zNear + (zFar - zNear) * p;        // arithmetic
        plan.boundaries[i] = lambda * log + (1.0f - lambda) * uni;
    }
    // Pin the endpoints exactly.  Floating point in the geometric term can leave
    // boundary 0 a hair off the near plane, which then shows as a seam.
    plan.boundaries[0] = zNear;
    plan.boundaries[cascades] = zFar;
    return plan;
}

// ---------------------------------------------------------------------------
// Frustum geometry.
// ---------------------------------------------------------------------------

/// The eight corners of a perspective frustum slice in world space.
///
/// Built by unprojecting the NDC cube through the inverse view-projection rather
/// than by reconstructing from angles, because the camera matrix already encodes
/// the exact projection the renderer uses -- including any off-centre or
/// oblique terms.  Deriving it from fov/aspect separately is how a shadow map
/// ends up slightly the wrong size.
struct FrustumCorners { Vec3 p[8]; };

inline FrustumCorners frustumSliceCorners(const Mat4& viewProjInv, f32 zNdcNear,
                                          f32 zNdcFar) {
    FrustumCorners c;
    int i = 0;
    for (int z = 0; z < 2; ++z) {
        const f32 ndcZ = (z == 0) ? zNdcNear : zNdcFar;
        for (int y = 0; y < 2; ++y) {
            for (int x = 0; x < 2; ++x) {
                const Vec4 clip{ x == 0 ? -1.0f : 1.0f,
                                 y == 0 ? -1.0f : 1.0f,
                                 ndcZ, 1.0f };
                const Vec4 w = viewProjInv * clip;
                const f32 invW = (std::fabs(w.w) > 1e-6f) ? 1.0f / w.w : 0.0f;
                c.p[i++] = Vec3{ w.x * invW, w.y * invW, w.z * invW };
            }
        }
    }
    return c;
}

/// View-space depth of a world point, for converting a split boundary into NDC.
///
/// The projection maps view-space depth non-linearly into [0,1] (this engine maps
/// to [0,1], matching Vulkan), so a split boundary chosen in linear view depth has
/// to be pushed through the same curve to find its NDC value.  Getting this
/// backwards is the classic "cascades are all bunched at the far plane" bug.
inline f32 viewDepthToNdc(f32 viewDepth, f32 zNear, f32 zFar) {
    if (zFar <= zNear) return 0.0f;
    const f32 d = viewDepth < 0.0f ? -viewDepth : viewDepth;
    // perspective(): z_ndc = (zFar*(d - zNear)) / (d*(zFar - zNear))
    const f32 denom = d * (zFar - zNear);
    if (std::fabs(denom) < 1e-6f) return 0.0f;
    return (zFar * (d - zNear)) / denom;
}

// ---------------------------------------------------------------------------
// Cascade matrix construction.
// ---------------------------------------------------------------------------

/// Builds one cascade's light matrices for a frustum slice.
///
/// `stabilize` snaps the projection to whole texels and removes the rotational
/// component of the camera's roll, so the shadow map does not crawl as the camera
/// moves.  Pass false only when debugging, because the shimmer it reintroduces is
/// immediately visible.
inline CascadeMatrices buildCascade(const FrustumCorners& corners, Vec3 lightDir,
                                    bool stabilize = true,
                                    int resolution = kShadowCascadeResolution) {
    CascadeMatrices out;

    // --- bounding sphere of the slice.
    // A sphere rather than a tight AABB because the slice rotates with the
    // camera, and a sphere's projection is invariant under that rotation.  That
    // invariance is most of what makes stabilisation possible: the orthographic
    // box stays the same size as the camera turns, so only its centre moves.
    Vec3 center{ 0, 0, 0 };
    for (int i = 0; i < 8; ++i) center += corners.p[i];
    center = center * (1.0f / 8.0f);

    f32 radius = 0.0f;
    for (int i = 0; i < 8; ++i) {
        const f32 d = (corners.p[i] - center).length();
        if (d > radius) radius = d;
    }
    out.sphereCenter = center;
    out.sphereRadius = radius;

    // --- light view matrix, looking from behind the sphere along the light.
    const Vec3 L = lightDir.lengthSq() > 1e-8f ? lightDir.normalized()
                                               : Vec3{ 0.0f, -1.0f, 0.0f };
    const Vec3 eye = center - L * (radius + 1.0f);
    out.lightView = Mat4::lookAt(eye, center, Vec3{ 0.0f, 1.0f, 0.0f });

    // --- orthographic projection sized to the sphere.
    const f32 zNearL = 0.0f;
    const f32 zFarL  = radius * 2.0f + 2.0f;
    out.lightProj = Mat4::ortho(-radius, radius, -radius, radius, zNearL, zFarL);
    out.lightViewProj = out.lightProj * out.lightView;

    out.texelSize = (radius * 2.0f) / static_cast<f32>(resolution);

    if (stabilize) {
        // Snap the centre to a whole number of texels in light space.  Without
        // this, sub-texel camera movement shifts every shadow edge by a fraction
        // of a pixel each frame and the edges crawl.
        const Vec3 snapped = out.lightView.transformPoint(center);
        const f32 ts = out.texelSize > 1e-8f ? out.texelSize : 1.0f;
        const Vec3 q{ std::round(snapped.x / ts) * ts,
                      std::round(snapped.y / ts) * ts,
                      snapped.z };
        const Vec3 delta = snapped - q;
        out.lightProj = Mat4::ortho(-radius + delta.x, radius + delta.x,
                                    -radius + delta.y, radius + delta.y,
                                    zNearL, zFarL);
        out.lightViewProj = out.lightProj * out.lightView;
    }
    return out;
}

/// Convenience: full cascade set for a camera.
///
/// `viewProj` is the camera's world->clip matrix; `viewProjInv` its inverse.  The
/// camera's near/far must match the ones used to render, or the splits will not
/// line up with what is on screen.
inline void buildAllCascades(const Mat4& viewProj, const Mat4& viewProjInv,
                             f32 zNear, f32 zFar, Vec3 lightDir,
                             const ShadowSplitPlan& plan, CascadeMatrices* out) {
    for (int i = 0; i < plan.cascades; ++i) {
        const f32 n0 = viewDepthToNdc(plan.boundaries[i],     zNear, zFar);
        const f32 n1 = viewDepthToNdc(plan.boundaries[i + 1], zNear, zFar);
        const FrustumCorners c = frustumSliceCorners(viewProjInv, n0, n1);
        out[i] = buildCascade(c, lightDir);
    }
}

/// Which cascade should shade a given view-space depth.
///
/// Used by the shader to pick a slice, and by tests to assert that the selection
/// is monotonic.  Returns the last cascade for depths beyond the far plane so a
/// fragment outside the plan still gets *a* shadow rather than none.
inline int selectCascade(f32 viewDepth, const ShadowSplitPlan& plan) {
    const f32 d = viewDepth < 0.0f ? -viewDepth : viewDepth;
    for (int i = 0; i < plan.cascades; ++i) {
        if (d <= plan.boundaries[i + 1]) return i;
    }
    return plan.cascades - 1;
}

}  // namespace se
