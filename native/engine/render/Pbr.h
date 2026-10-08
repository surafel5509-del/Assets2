// SPDX-License-Identifier: MIT
// S Engine 2.0 -- physically based shading math.
//
// This is the CPU reference implementation of the BRDF the GPU shaders run.  It
// exists for three reasons:
//
//   1. It is testable.  Energy conservation, reciprocity and the limiting cases
//      of a BRDF are properties you can assert on the host, and a shader you
//      cannot test is a shader you debug by looking at a phone screen.
//   2. It is the specification.  When the GLES and Vulkan backends disagree about
//      how a surface looks, this file is the arbiter.
//   3. It computes the values that must be precomputed for image-based lighting
//      (the split-sum environment BRDF), which is a build-time step in the asset
//      store, not a runtime cost.
//
// Everything here is the standard metallic/roughness workflow: Cook-Torrance
// specular with a GGX normal distribution, Smith geometry and Schlick fresnel.
#pragma once

#include "../core/Types.h"
#include "../math/Math.h"

#include <cmath>

namespace se {

// ---------------------------------------------------------------------------
// Material and light descriptions.
//
// These mirror the engine's component data.  `Light` here carries a `kind` that
// includes Spot, which the Kotlin runtime does not yet expose -- the shading
// maths is identical for all three kinds, only the attenuation term differs, so
// adding the enum value is what closes the gap rather than any new maths.
// ---------------------------------------------------------------------------

struct PbrMaterial {
    Vec3 albedo{ 0.8f, 0.8f, 0.8f };   ///< linear reflectance, 0..1
    f32  metallic  = 0.0f;             ///< 0 dielectric, 1 metal
    f32  roughness = 0.5f;             ///< 0 mirror, 1 fully diffuse
    f32  ao        = 1.0f;             ///< ambient occlusion
    f32  emissive  = 0.0f;             ///< added after lighting, in nits-ish units

    /// Roughness remapped to the alpha used by GGX.  Squaring is what makes the
    /// perceptual slider linear: without it the top half of the range is
    /// visually indistinguishable and the bottom half snaps.
    f32 alpha() const { const f32 r = clamp01(roughness); return r * r; }

    /// Specular reflectance at normal incidence.  Dielectrics sit at 0.04;
    /// metals have no diffuse and tint their specular with the albedo.
    Vec3 f0() const {
        const Vec3 dielectric{ 0.04f, 0.04f, 0.04f };
        return dielectric * (1.0f - metallic) + albedo * metallic;
    }

    static f32 clamp01(f32 v) { return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v); }
};

enum class LightKind : u8 { Directional = 0, Point = 1, Spot = 2 };

struct LightDesc {
    LightKind kind = LightKind::Directional;
    Vec3  direction{ 0.0f, -1.0f, 0.0f };  ///< direction light travels (directional)
    Vec3  position{ 0.0f, 0.0f, 0.0f };    ///< world position (point / spot)
    Vec3  color{ 1.0f, 1.0f, 1.0f };       ///< linear
    f32   intensity = 1.0f;                ///< lux for directional, candela otherwise
    f32   range     = 10.0f;               ///< point / spot falloff distance
    f32   innerConeDeg = 25.0f;            ///< spot: full intensity inside this
    f32   outerConeDeg = 35.0f;            ///< spot: zero outside this

    /// Vector from the shaded surface TO the light, plus its distance.
    /// Directional lights are infinitely far, so the direction is constant and
    /// the distance is irrelevant to attenuation.
    Vec3 vectorToLight(Vec3 surface) const {
        if (kind == LightKind::Directional) return -direction.normalized();
        const Vec3 d = position - surface;
        return d;
    }
};

// ---------------------------------------------------------------------------
// BRDF terms.
// ---------------------------------------------------------------------------

/// GGX / Trowbridge-Reitz normal distribution function.
///
/// Describes what fraction of microfacets are aligned with the half vector.  GGX
/// is used over Blinn-Phong because its long tails produce the highlight shape
/// real materials have: Blinn-Phong energy falls off too fast and reads as plastic.
inline f32 distributionGGX(f32 nDotH, f32 alpha) {
    const f32 a2 = alpha * alpha;
    const f32 d  = nDotH * nDotH * (a2 - 1.0f) + 1.0f;
    return a2 / (kPi * d * d + 1e-7f);
}

/// Smith's geometry term with the GGX-correlated visibility function.
///
/// Accounts for microfacets shadowing or masking each other.  Using the
/// correlated form rather than the uncorrelated `G1(l) * G1(v)` product is both
/// cheaper and more accurate at grazing angles.
inline f32 geometrySmith(f32 nDotV, f32 nDotL, f32 alpha) {
    const f32 a2 = alpha * alpha;
    const f32 gv = nDotL * std::sqrt(nDotV * nDotV * (1.0f - a2) + a2);
    const f32 gl = nDotV * std::sqrt(nDotL * nDotL * (1.0f - a2) + a2);
    const f32 denom = gv + gl;
    return denom > 1e-6f ? 2.0f * nDotL * nDotV / denom : 0.0f;
}

/// Schlick's approximation to the Fresnel equations.
///
/// Reflectance rises toward 1 at grazing angles for every material, which is why
/// edges catch light.  `f0` is the reflectance at normal incidence.
inline Vec3 fresnelSchlick(f32 cosTheta, Vec3 f0) {
    const f32 t = 1.0f - (cosTheta < 0.0f ? 0.0f : (cosTheta > 1.0f ? 1.0f : cosTheta));
    const f32 t2 = t * t;
    const f32 f  = t2 * t2 * t;                       // (1-cos)^5
    return f0 + (Vec3{ 1.0f, 1.0f, 1.0f } - f0) * f;
}

/// Fresnel with roughness, for the ambient/IBL term.
///
/// The plain Schlick term drives specular to full strength at grazing angles even
/// on a surface so rough it should scatter light in all directions.  Capping the
/// target by roughness is the standard correction and is what stops rough
/// silhouettes glowing.
inline Vec3 fresnelSchlickRoughness(f32 cosTheta, Vec3 f0, f32 roughness) {
    const f32 t = 1.0f - (cosTheta < 0.0f ? 0.0f : (cosTheta > 1.0f ? 1.0f : cosTheta));
    const f32 t2 = t * t;
    const f32 f  = t2 * t2 * t;
    const f32 maxReflect = PbrMaterial::clamp01(1.0f - roughness);
    const Vec3 target{ f0.x < maxReflect ? maxReflect : f0.x,
                       f0.y < maxReflect ? maxReflect : f0.y,
                       f0.z < maxReflect ? maxReflect : f0.z };
    return f0 + (target - f0) * f;
}

/// Lambertian diffuse.  The 1/pi keeps it energy conserving over the hemisphere.
inline Vec3 lambert(Vec3 albedo) { return albedo * (1.0f / kPi); }

// ---------------------------------------------------------------------------
// Direct lighting.
// ---------------------------------------------------------------------------

/// Distance attenuation with a windowing function.
///
/// Pure inverse-square falls off forever, so a light with a finite `range` still
/// contributes at arbitrary distance and you end up drawing it everywhere.  The
/// squared window term drives the contribution smoothly to exactly zero at
/// `range`, which is what makes the per-frame "nearest N lights" cull correct
/// rather than a visible pop.
inline f32 attenuation(f32 dist, f32 range) {
    if (dist <= 0.0f) return 1.0f;
    if (range <= 0.0f) return 1.0f / (dist * dist);
    const f32 t = dist / range;
    if (t >= 1.0f) return 0.0f;
    const f32 w = 1.0f - t * t;
    return (w * w) / (dist * dist + 1e-4f);
}

/// Spot cone falloff: full intensity inside the inner cone, zero outside the
/// outer, smooth between.  The smoothstep is what prevents a visible hard ring
/// at the cone edge.
inline f32 spotFalloff(const LightDesc& l, Vec3 toSurfaceDir) {
    if (l.kind != LightKind::Spot) return 1.0f;
    const f32 cosOuter = std::cos(l.outerConeDeg * kDeg2Rad);
    const f32 cosInner = std::cos(l.innerConeDeg * kDeg2Rad);
    const f32 cosAngle = l.direction.normalized().dot(toSurfaceDir.normalized());
    if (cosAngle <= cosOuter) return 0.0f;
    if (cosAngle >= cosInner) return 1.0f;
    const f32 t = (cosAngle - cosOuter) / (cosInner - cosOuter + 1e-6f);
    return t * t * (3.0f - 2.0f * t);
}

/// Radiance arriving at `surface` from `light`.
inline Vec3 lightRadiance(const LightDesc& light, Vec3 surface) {
    const Vec3 toLight = light.vectorToLight(surface);
    const f32  dist    = toLight.length();
    const Vec3 L       = (light.kind == LightKind::Directional)
                             ? toLight
                             : toLight * (1.0f / (dist > 1e-6f ? dist : 1.0f));

    f32 att = 1.0f;
    if (light.kind == LightKind::Point) {
        att = attenuation(dist, light.range);
    } else if (light.kind == LightKind::Spot) {
        att = attenuation(dist, light.range) * spotFalloff(light, -toLight);
    }
    return light.color * (light.intensity * att);
}

/// Cook-Torrance BRDF times the cosine term, for one light.
///
/// Returns the outgoing radiance contribution.  Both the diffuse and specular
/// lobes are scaled by `nDotL`, which is the Lambert cosine law: a surface lit at
/// a glancing angle spreads the same energy over a larger area.
inline Vec3 shadeDirect(const PbrMaterial& mat, Vec3 N, Vec3 V, Vec3 L, Vec3 radiance) {
    const Vec3 H = (V + L).normalized();
    const f32 nDotV = N.dot(V) < 0.0f ? 0.0f : N.dot(V);
    const f32 nDotL = N.dot(L);
    if (nDotL <= 0.0f) return Vec3{ 0.0f, 0.0f, 0.0f };   // light is behind the surface
    const f32 nDotH = N.dot(H) < 0.0f ? 0.0f : N.dot(H);
    const f32 hDotV = H.dot(V) < 0.0f ? 0.0f : H.dot(V);

    const f32 a  = mat.alpha();
    const f32 D  = distributionGGX(nDotH, a);
    const f32 G  = geometrySmith(nDotV, nDotL, a);
    const Vec3 F = fresnelSchlick(hDotV, mat.f0());

    const f32 denom = 4.0f * nDotV * nDotL + 1e-4f;
    const Vec3 spec{ F.x * D * G / denom, F.y * D * G / denom, F.z * D * G / denom };

    // Metals have no diffuse response: their electrons are free, so all the
    // energy is reflected rather than scattered.  Leaving this term out is the
    // most common PBR bug and shows up as metal looking painted.
    const Vec3 kD = (Vec3{ 1.0f, 1.0f, 1.0f } - F) * (1.0f - mat.metallic);
    const Vec3 diffuse = lambert(mat.albedo);

    const Vec3 kd{ kD.x * diffuse.x, kD.y * diffuse.y, kD.z * diffuse.z };
    const Vec3 brdf{ kd.x + spec.x, kd.y + spec.y, kd.z + spec.z };

    return Vec3{ brdf.x * radiance.x * nDotL,
                 brdf.y * radiance.y * nDotL,
                 brdf.z * radiance.z * nDotL };
}

// ---------------------------------------------------------------------------
// Image-based lighting (split sum).
// ---------------------------------------------------------------------------

/// The analytic environment-BRDF term of the split-sum approximation.
///
/// The full term is an integral over the hemisphere, tabulated once into a 2D LUT
/// indexed by (nDotV, roughness).  This closed form is accurate to within a
/// percent over the useful range and avoids shipping a texture, which matters
/// when the whole engine has to fit in an APK.
inline Vec2 envBrdf(f32 nDotV, f32 roughness) {
    const f32 c = nDotV < 0.0f ? 0.0f : (nDotV > 1.0f ? 1.0f : nDotV);
    const f32 r = PbrMaterial::clamp01(roughness);
    // scale + bias fitted to the GGX LUT
    const f32 scale = (-0.1688f * r + 0.5515f) * c + (0.1618f * r - 0.1618f) * c * c + 0.05f;
    const f32 bias  = (-0.3218f * r + 0.2805f) * c + (0.4426f * r - 0.4426f) * c * c;
    const f32 s = scale < 0.0f ? 0.0f : scale;
    const f32 b = bias  > 0.0f ? 0.0f : bias;
    return Vec2{ s, b };
}

/// Ambient contribution from a uniform irradiance environment.
inline Vec3 shadeAmbient(const PbrMaterial& mat, Vec3 N, Vec3 V, Vec3 irradiance,
                         Vec3 prefilteredEnv) {
    const f32 nDotV = N.dot(V) < 0.0f ? 0.0f : N.dot(V);
    const Vec3 F = fresnelSchlickRoughness(nDotV, mat.f0(), mat.roughness);
    const Vec2 brdf = envBrdf(nDotV, mat.roughness);

    const Vec3 kD = (Vec3{ 1.0f, 1.0f, 1.0f } - F) * (1.0f - mat.metallic);
    const Vec3 diffuse = lambert(mat.albedo);

    const Vec3 spec{ prefilteredEnv.x * (F.x * brdf.x + brdf.y),
                     prefilteredEnv.y * (F.y * brdf.x + brdf.y),
                     prefilteredEnv.z * (F.z * brdf.x + brdf.y) };

    const f32 ao = mat.ao;
    return Vec3{ (kD.x * diffuse.x * irradiance.x + spec.x) * ao,
                 (kD.y * diffuse.y * irradiance.y + spec.y) * ao,
                 (kD.z * diffuse.z * irradiance.z + spec.z) * ao };
}

/// Full shading: every direct light plus ambient plus emissive.
template <int MaxLights>
inline Vec3 shade(const PbrMaterial& mat, Vec3 N, Vec3 V, Vec3 surface,
                  const LightDesc* lights, int lightCount,
                  Vec3 irradiance, Vec3 prefilteredEnv) {
    Vec3 outCol{ mat.albedo.x * mat.emissive,
                 mat.albedo.y * mat.emissive,
                 mat.albedo.z * mat.emissive };

    const int n = lightCount > MaxLights ? MaxLights : lightCount;
    for (int i = 0; i < n; ++i) {
        const LightDesc& l = lights[i];
        const Vec3 toLight = l.vectorToLight(surface);
        const Vec3 L = (l.kind == LightKind::Directional)
                           ? toLight
                           : toLight.normalized();
        outCol += shadeDirect(mat, N, V, L, lightRadiance(l, surface));
    }
    outCol += shadeAmbient(mat, N, V, irradiance, prefilteredEnv);
    return outCol;
}

/// Reinhard tone mapping followed by the sRGB transfer function.
///
/// Lighting maths is linear; displays are not.  Skipping this step is why a
/// renderer looks washed out and blown highlights at the same time.
inline Vec3 toDisplay(Vec3 linear) {
    const Vec3 mapped{ linear.x / (1.0f + linear.x),
                       linear.y / (1.0f + linear.y),
                       linear.z / (1.0f + linear.z) };
    auto gamma = [](f32 c) -> f32 {
        const f32 v = c < 0.0f ? 0.0f : c;
        return v <= 0.0031308f ? v * 12.92f
                               : 1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f;
    };
    return Vec3{ gamma(mapped.x), gamma(mapped.y), gamma(mapped.z) };
}

}  // namespace se
