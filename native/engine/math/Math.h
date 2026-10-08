// SPDX-License-Identifier: MIT
// S Engine 2.0 -- SIMD-friendly scalar math.
//
// Column-major Mat4 to match GLSL/Vulkan convention, so a matrix built here can
// be uploaded to a uniform buffer without transposing.  Row-vector-on-the-left
// is NOT used: transforms apply as M * v.
#pragma once

#include "../core/Types.h"

#include <cmath>

namespace se {

inline constexpr f32 kPi      = 3.14159265358979323846f;
inline constexpr f32 kDeg2Rad = kPi / 180.0f;
inline constexpr f32 kRad2Deg = 180.0f / kPi;

template <typename T> inline constexpr T clampT(T v, T lo, T hi) { return v < lo ? lo : (v > hi ? hi : v); }
template <typename T> inline constexpr T lerpT(T a, T b, T t) { return a + (b - a) * t; }

struct Vec2 {
    f32 x = 0, y = 0;
    Vec2() = default;
    Vec2(f32 x_, f32 y_) : x(x_), y(y_) {}
    Vec2 operator+(Vec2 o) const { return { x + o.x, y + o.y }; }
    Vec2 operator-(Vec2 o) const { return { x - o.x, y - o.y }; }
    Vec2 operator*(f32 s) const { return { x * s, y * s }; }
    Vec2& operator+=(Vec2 o) { x += o.x; y += o.y; return *this; }
    Vec2& operator-=(Vec2 o) { x -= o.x; y -= o.y; return *this; }
    bool operator==(Vec2 o) const { return x == o.x && y == o.y; }
    f32 lengthSq() const { return x * x + y * y; }
    f32 length() const { return std::sqrt(lengthSq()); }
};

struct Vec3 {
    f32 x = 0, y = 0, z = 0;
    Vec3() = default;
    Vec3(f32 x_, f32 y_, f32 z_) : x(x_), y(y_), z(z_) {}
    explicit Vec3(f32 s) : x(s), y(s), z(s) {}

    Vec3 operator+(Vec3 o) const { return { x + o.x, y + o.y, z + o.z }; }
    Vec3 operator-(Vec3 o) const { return { x - o.x, y - o.y, z - o.z }; }
    Vec3 operator-() const { return { -x, -y, -z }; }
    Vec3 operator*(f32 s) const { return { x * s, y * s, z * s }; }
    Vec3 operator*(Vec3 o) const { return { x * o.x, y * o.y, z * o.z }; }
    Vec3& operator+=(Vec3 o) { x += o.x; y += o.y; z += o.z; return *this; }
    Vec3& operator-=(Vec3 o) { x -= o.x; y -= o.y; z -= o.z; return *this; }
    Vec3& operator*=(f32 s) { x *= s; y *= s; z *= s; return *this; }
    bool operator==(Vec3 o) const { return x == o.x && y == o.y && z == o.z; }

    f32 dot(Vec3 o) const { return x * o.x + y * o.y + z * o.z; }
    Vec3 cross(Vec3 o) const {
        return { y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x };
    }
    f32 lengthSq() const { return dot(*this); }
    f32 length() const { return std::sqrt(lengthSq()); }
    Vec3 normalized() const {
        const f32 l = length();
        return l > 1e-8f ? Vec3{ x / l, y / l, z / l } : Vec3{ 0, 0, 0 };
    }
};

inline Vec3 operator*(f32 s, Vec3 v) { return v * s; }

struct Vec4 {
    f32 x = 0, y = 0, z = 0, w = 0;
    Vec4() = default;
    Vec4(f32 x_, f32 y_, f32 z_, f32 w_) : x(x_), y(y_), z(z_), w(w_) {}
    Vec4(Vec3 v, f32 w_) : x(v.x), y(v.y), z(v.z), w(w_) {}
    Vec3 xyz() const { return { x, y, z }; }
    Vec4 operator*(f32 s) const { return { x * s, y * s, z * s, w * s }; }
};

struct Quat {
    f32 x = 0, y = 0, z = 0, w = 1;

    static Quat identity() { return { 0, 0, 0, 1 }; }
    static Quat fromAxisAngle(Vec3 axis, f32 radians) {
        const f32 h = radians * 0.5f;
        const f32 s = std::sin(h);
        const Vec3 a = axis.normalized();
        return { a.x * s, a.y * s, a.z * s, std::cos(h) };
    }
    /// Yaw about Y, then pitch about X, then roll about Z (YXZ order).
    /// Composed from axis-angle rather than written as a closed form, because
    /// the closed form is where the axis mapping silently goes wrong.
    static Quat fromEuler(f32 pitchDeg, f32 yawDeg, f32 rollDeg = 0) {
        return fromAxisAngle(Vec3{ 0, 1, 0 }, yawDeg   * kDeg2Rad)
             * fromAxisAngle(Vec3{ 1, 0, 0 }, pitchDeg * kDeg2Rad)
             * fromAxisAngle(Vec3{ 0, 0, 1 }, rollDeg  * kDeg2Rad);
    }
    Quat operator*(const Quat& o) const {
        return {
            w * o.x + x * o.w + y * o.z - z * o.y,
            w * o.y - x * o.z + y * o.w + z * o.x,
            w * o.z + x * o.y - y * o.x + z * o.w,
            w * o.w - x * o.x - y * o.y - z * o.z
        };
    }
    f32 lengthSq() const { return x * x + y * y + z * z + w * w; }
    Quat normalized() const {
        const f32 l = std::sqrt(lengthSq());
        if (l < 1e-8f) return identity();
        const f32 inv = 1.0f / l;
        return { x * inv, y * inv, z * inv, w * inv };
    }
    Quat conjugate() const { return { -x, -y, -z, w }; }
    Vec3 rotate(Vec3 v) const {
        // q * v * q^-1, expanded to avoid building two temporaries.
        const Vec3 u{ x, y, z };
        const f32 s = w;
        const Vec3 t = u.cross(v) * 2.0f;
        return v + t * s + u.cross(t);
    }
    static Quat slerp(const Quat& a, const Quat& b, f32 t) {
        f32 cosTheta = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        Quat bb = b;
        if (cosTheta < 0.0f) {   // take the short way round
            bb = { -b.x, -b.y, -b.z, -b.w };
            cosTheta = -cosTheta;
        }
        if (cosTheta > 0.9995f)  // near-parallel: nlerp is cheaper and stable
            return Quat{ lerpT(a.x, bb.x, t), lerpT(a.y, bb.y, t),
                         lerpT(a.z, bb.z, t), lerpT(a.w, bb.w, t) }.normalized();
        const f32 theta = std::acos(clampT(cosTheta, -1.0f, 1.0f));
        const f32 sinTheta = std::sin(theta);
        const f32 wa = std::sin((1.0f - t) * theta) / sinTheta;
        const f32 wb = std::sin(t * theta) / sinTheta;
        return Quat{ wa * a.x + wb * bb.x, wa * a.y + wb * bb.y,
                     wa * a.z + wb * bb.z, wa * a.w + wb * bb.w };
    }
};

// ---------------------------------------------------------------------------
// Mat4 -- column major: m[col * 4 + row].
// ---------------------------------------------------------------------------
struct Mat4 {
    f32 m[16] = { 1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1 };

    static Mat4 identity() { return Mat4{}; }

    f32& at(int row, int col) { return m[col * 4 + row]; }
    f32  at(int row, int col) const { return m[col * 4 + row]; }

    Mat4 operator*(const Mat4& o) const {
        Mat4 r;
        for (int c = 0; c < 4; ++c)
            for (int row = 0; row < 4; ++row) {
                f32 sum = 0;
                for (int k = 0; k < 4; ++k) sum += at(row, k) * o.at(k, c);
                r.at(row, c) = sum;
            }
        return r;
    }

    Vec4 operator*(const Vec4& v) const {
        return {
            at(0,0)*v.x + at(0,1)*v.y + at(0,2)*v.z + at(0,3)*v.w,
            at(1,0)*v.x + at(1,1)*v.y + at(1,2)*v.z + at(1,3)*v.w,
            at(2,0)*v.x + at(2,1)*v.y + at(2,2)*v.z + at(2,3)*v.w,
            at(3,0)*v.x + at(3,1)*v.y + at(3,2)*v.z + at(3,3)*v.w
        };
    }

    Vec3 transformPoint(Vec3 p) const {
        const Vec4 r = (*this) * Vec4(p, 1.0f);
        return r.xyz();
    }
    Vec3 transformDirection(Vec3 d) const {
        const Vec4 r = (*this) * Vec4(d, 0.0f);
        return r.xyz();
    }

    static Mat4 translation(Vec3 t) {
        Mat4 r; r.at(0,3) = t.x; r.at(1,3) = t.y; r.at(2,3) = t.z; return r;
    }
    static Mat4 scale(Vec3 s) {
        Mat4 r; r.at(0,0) = s.x; r.at(1,1) = s.y; r.at(2,2) = s.z; return r;
    }
    static Mat4 fromQuat(const Quat& q) {
        const f32 xx = q.x*q.x, yy = q.y*q.y, zz = q.z*q.z;
        const f32 xy = q.x*q.y, xz = q.x*q.z, yz = q.y*q.z;
        const f32 wx = q.w*q.x, wy = q.w*q.y, wz = q.w*q.z;
        Mat4 r;
        r.at(0,0) = 1 - 2*(yy+zz); r.at(0,1) = 2*(xy-wz);     r.at(0,2) = 2*(xz+wy);
        r.at(1,0) = 2*(xy+wz);     r.at(1,1) = 1 - 2*(xx+zz); r.at(1,2) = 2*(yz-wx);
        r.at(2,0) = 2*(xz-wy);     r.at(2,1) = 2*(yz+wx);     r.at(2,2) = 1 - 2*(xx+yy);
        return r;
    }
    static Mat4 trs(Vec3 t, const Quat& r, Vec3 s) {
        return translation(t) * fromQuat(r) * scale(s);
    }

    /// Right-handed perspective, depth mapped to [0,1] (Vulkan / GL ES with
    /// GL_CLIP_ORIGIN corrected).  fovy in degrees.
    static Mat4 perspective(f32 fovyDeg, f32 aspect, f32 zNear, f32 zFar) {
        const f32 f = 1.0f / std::tan(fovyDeg * kDeg2Rad * 0.5f);
        Mat4 r{};
        for (int i = 0; i < 16; ++i) r.m[i] = 0;
        r.at(0,0) = f / aspect;
        r.at(1,1) = f;
        r.at(2,2) = zFar / (zNear - zFar);
        r.at(2,3) = (zNear * zFar) / (zNear - zFar);
        r.at(3,2) = -1.0f;
        return r;
    }

    static Mat4 ortho(f32 l, f32 r, f32 b, f32 t, f32 n, f32 f) {
        Mat4 o{};
        for (int i = 0; i < 16; ++i) o.m[i] = 0;
        o.at(0,0) = 2.0f / (r - l);
        o.at(1,1) = 2.0f / (t - b);
        o.at(2,2) = -1.0f / (f - n);
        o.at(0,3) = -(r + l) / (r - l);
        o.at(1,3) = -(t + b) / (t - b);
        o.at(2,3) = -n / (f - n);
        o.at(3,3) = 1.0f;
        return o;
    }

    /// General 4x4 inverse by cofactors.
    ///
    /// Needed for anything that is not a pure rigid transform -- in practice,
    /// view-projection matrices, which carry the perspective divide and so cannot
    /// use invertRigid().  Returns false and leaves `out` untouched when the
    /// matrix is singular, because silently returning garbage from an uninvertible
    /// projection is what turns into shadows or picking being wrong everywhere.
    static bool inverse(const Mat4& m, Mat4& out) {
        const f32* s = m.m;
        // Cofactor expansion, column-major indexing: element (row, col) is
        // s[col * 4 + row], matching at().
        f32 c[16];
        for (int col = 0; col < 4; ++col) {
            for (int row = 0; row < 4; ++row) {
                // Build the 3x3 minor by skipping `row` and `col`.
                f32 minor[9];
                int k = 0;
                for (int cc = 0; cc < 4; ++cc) {
                    if (cc == col) continue;
                    for (int rr = 0; rr < 4; ++rr) {
                        if (rr == row) continue;
                        minor[k++] = s[cc * 4 + rr];
                    }
                }
                const f32 det3 = minor[0] * (minor[4] * minor[8] - minor[5] * minor[7])
                               - minor[1] * (minor[3] * minor[8] - minor[5] * minor[6])
                               + minor[2] * (minor[3] * minor[7] - minor[4] * minor[6]);
                const f32 sign = ((row + col) & 1) ? -1.0f : 1.0f;
                // Adjugate is the transpose of the cofactor matrix.
                c[row * 4 + col] = sign * det3;
            }
        }
        // det = sum over a row of (element * its cofactor)
        f32 det = 0.0f;
        for (int col = 0; col < 4; ++col) det += s[col * 4 + 0] * c[col * 4 + 0];
        if (std::fabs(det) < 1e-12f) return false;

        const f32 invDet = 1.0f / det;
        for (int i = 0; i < 16; ++i) out.m[i] = c[i] * invDet;
        return true;
    }

    static Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
        const Vec3 f = (target - eye).normalized();
        const Vec3 s = f.cross(up).normalized();
        const Vec3 u = s.cross(f);
        Mat4 r = identity();
        r.at(0,0) = s.x;  r.at(0,1) = s.y;  r.at(0,2) = s.z;  r.at(0,3) = -s.dot(eye);
        r.at(1,0) = u.x;  r.at(1,1) = u.y;  r.at(1,2) = u.z;  r.at(1,3) = -u.dot(eye);
        r.at(2,0) = -f.x; r.at(2,1) = -f.y; r.at(2,2) = -f.z; r.at(2,3) = f.dot(eye);
        return r;
    }
};

// ---------------------------------------------------------------------------
// Axis-aligned bounding box, used for culling, broadphase and picking.
// ---------------------------------------------------------------------------
struct AABB {
    Vec3 min{ 1e30f, 1e30f, 1e30f };
    Vec3 max{ -1e30f, -1e30f, -1e30f };

    bool empty() const { return min.x > max.x; }
    Vec3 center() const { return (min + max) * 0.5f; }
    Vec3 extents() const { return (max - min) * 0.5f; }

    void expand(Vec3 p) {
        min.x = p.x < min.x ? p.x : min.x;
        min.y = p.y < min.y ? p.y : min.y;
        min.z = p.z < min.z ? p.z : min.z;
        max.x = p.x > max.x ? p.x : max.x;
        max.y = p.y > max.y ? p.y : max.y;
        max.z = p.z > max.z ? p.z : max.z;
    }
    void expand(const AABB& o) { expand(o.min); expand(o.max); }

    bool intersects(const AABB& o) const {
        return min.x <= o.max.x && max.x >= o.min.x &&
               min.y <= o.max.y && max.y >= o.min.y &&
               min.z <= o.max.z && max.z >= o.min.z;
    }
    bool contains(Vec3 p) const {
        return p.x >= min.x && p.x <= max.x &&
               p.y >= min.y && p.y <= max.y &&
               p.z >= min.z && p.z <= max.z;
    }
};

// ---------------------------------------------------------------------------
// Rigid-transform helpers.  Scene-graph nodes only ever hold translation,
// rotation and (uniform-ish) scale, so these are far cheaper and numerically
// stabler than a general 4x4 inverse.
// ---------------------------------------------------------------------------
inline void decomposeRigid(const Mat4& m, Vec3& pos, Quat& rot, Vec3& scale) {
    const Vec3 c0{ m.at(0,0), m.at(1,0), m.at(2,0) };
    const Vec3 c1{ m.at(0,1), m.at(1,1), m.at(2,1) };
    const Vec3 c2{ m.at(0,2), m.at(1,2), m.at(2,2) };

    scale.x = c0.length();
    scale.y = c1.length();
    scale.z = c2.length();
    pos = Vec3{ m.at(0,3), m.at(1,3), m.at(2,3) };

    if (scale.x < 1e-8f || scale.y < 1e-8f || scale.z < 1e-8f) {
        rot = Quat::identity();
        return;
    }
    // A negative determinant means a mirrored basis; fold the sign into scale.x
    // so the remaining 3x3 is a pure rotation.
    const Vec3 r0 = c0 * (1.0f / scale.x);
    const Vec3 r1 = c1 * (1.0f / scale.y);
    const Vec3 r2 = c2 * (1.0f / scale.z);
    if (r0.cross(r1).dot(r2) < 0.0f) {
        scale.x = -scale.x;
    }

    // Shepperd's method: pick the largest diagonal term to avoid division by a
    // near-zero number.
    // r0/r1/r2 are the ROTATION MATRIX COLUMNS, so element m[row][col] is
    // r{col}.{row}: m01 = r1.x, m12 = r2.y, m21 = r1.z, and so on.
    const f32 t = r0.x + r1.y + r2.z;
    if (t > 0.0f) {
        const f32 s = std::sqrt(t + 1.0f) * 2.0f;
        rot = Quat{ (r1.z - r2.y) / s, (r2.x - r0.z) / s, (r0.y - r1.x) / s, 0.25f * s };
    } else if (r0.x > r1.y && r0.x > r2.z) {
        const f32 s = std::sqrt(1.0f + r0.x - r1.y - r2.z) * 2.0f;
        rot = Quat{ 0.25f * s, (r1.x + r0.y) / s, (r2.x + r0.z) / s, (r1.z - r2.y) / s };
    } else if (r1.y > r2.z) {
        const f32 s = std::sqrt(1.0f + r1.y - r0.x - r2.z) * 2.0f;
        rot = Quat{ (r1.x + r0.y) / s, 0.25f * s, (r2.y + r1.z) / s, (r2.x - r0.z) / s };
    } else {
        const f32 s = std::sqrt(1.0f + r2.z - r0.x - r1.y) * 2.0f;
        rot = Quat{ (r2.x + r0.z) / s, (r2.y + r1.z) / s, 0.25f * s, (r0.y - r1.x) / s };
    }
    rot = rot.normalized();
}

inline Mat4 invertRigid(const Mat4& m) {
    // For M = [R | t], M^-1 = [R^T | -R^T t].  Ignores scale; callers that need
    // a scaled inverse must decompose first.
    Mat4 r{};
    for (int i = 0; i < 3; ++i)
        for (int j = 0; j < 3; ++j)
            r.at(i, j) = m.at(j, i);
    const Vec3 t{ m.at(0,3), m.at(1,3), m.at(2,3) };
    r.at(0,3) = -(r.at(0,0)*t.x + r.at(0,1)*t.y + r.at(0,2)*t.z);
    r.at(1,3) = -(r.at(1,0)*t.x + r.at(1,1)*t.y + r.at(1,2)*t.z);
    r.at(2,3) = -(r.at(2,0)*t.x + r.at(2,1)*t.y + r.at(2,2)*t.z);
    r.at(3,0) = r.at(3,1) = r.at(3,2) = 0.0f;
    r.at(3,3) = 1.0f;
    return r;
}

} // namespace se
