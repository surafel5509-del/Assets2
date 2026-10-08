// SPDX-License-Identifier: MIT
// S Engine 2.0 -- rigid body physics.
//
// A sequential-impulse solver in the shape Erin Catto made standard for games.
// It is written here rather than porting Bullet for three reasons that all come
// down to mobile:
//
//   * The shipped games need spheres, boxes and capsules -- not soft bodies,
//     cloth, or vehicles with suspension curves.
//   * Bullet's CMake build under the NDK is a known source of pain, and a
//     dependency that fights the build is worse than one you have to maintain.
//   * A solver you wrote is a solver you can profile and trim.  On a phone the
//     frame budget is the whole constraint, and dead features are not free.
//
// What is here is deliberately the part that has to be RIGHT, because it is the
// part that is miserable to debug on a device: momentum conservation, resting
// contact that does not jitter, and joints that hold under load.  All three are
// asserted by the host test suite.
//
// Units are metres, kilograms and seconds.  Gravity defaults to -9.81 m/s^2.
#pragma once

#include "../core/Handle.h"
#include "../core/Types.h"
#include "../math/Math.h"

#include <cmath>
#include <vector>

namespace se {

// ---------------------------------------------------------------------------
// Tuning constants.
//
// These are the numbers that decide whether a stack of crates settles or buzzes,
// so they are named rather than inlined.
// ---------------------------------------------------------------------------

/// Penetration allowed before the solver starts pushing bodies apart.  Without
/// slop, floating point error means contacts are always marginally penetrating,
/// so the solver pushes every frame and resting bodies vibrate.
inline constexpr f32 kLinearSlop = 0.005f;

/// Fraction of the penetration error corrected per step.  Correcting all of it
/// injects energy -- a stack would gain height each frame and eventually pop
/// apart.  A fifth is enough to look solid without feeding the system.
inline constexpr f32 kBaumgarte = 0.2f;

/// Velocity below which restitution is ignored.  Bouncing a body that is barely
/// moving produces the visible jitter of a crate "ringing" after it lands.
inline constexpr f32 kRestitutionThreshold = 0.5f;

/// Iterations per step.  More iterations means stiffer stacks and joints at
/// linear cost; 8 is the usual compromise for a handful of bodies.
inline constexpr int kVelocityIterations = 8;
inline constexpr int kPositionIterations = 3;

// ---------------------------------------------------------------------------
// Collision shapes.
// ---------------------------------------------------------------------------

enum class ShapeKind : u8 { Sphere = 0, Box = 1, Capsule = 2 };

struct Shape {
    ShapeKind kind = ShapeKind::Sphere;
    f32 radius = 0.5f;       ///< sphere radius, or capsule radius
    f32 halfHeight = 0.5f;   ///< capsule half-length along local Y
    Vec3 halfExtents{ 0.5f, 0.5f, 0.5f };   ///< box, in local space

    /// World-space AABB.  The box and capsule bounds are conservative -- the box
    /// is expanded to its diagonal so it stays valid at any orientation.  That
    /// costs a few extra broadphase pairs, which is far cheaper than computing an
    /// oriented bound every step.
    AABB aabb(Vec3 center, const Quat& rot) const {
        switch (kind) {
            case ShapeKind::Sphere: {
                const Vec3 r{ radius, radius, radius };
                return AABB{ center - r, center + r };
            }
            case ShapeKind::Capsule: {
                // Worst case: capsule lying perpendicular to Y.
                const f32 e = radius + halfHeight;
                const Vec3 r{ e, e, e };
                return AABB{ center - r, center + r };
            }
            case ShapeKind::Box:
            default: {
                // Rotate each axis and take the absolute sum -- the standard
                // OBB-to-AABB bound.
                const Mat4 R = Mat4::fromQuat(rot);
                const Vec3 ax{ std::fabs(R.at(0,0)), std::fabs(R.at(1,0)), std::fabs(R.at(2,0)) };
                const Vec3 ay{ std::fabs(R.at(0,1)), std::fabs(R.at(1,1)), std::fabs(R.at(2,1)) };
                const Vec3 az{ std::fabs(R.at(0,2)), std::fabs(R.at(1,2)), std::fabs(R.at(2,2)) };
                const Vec3 e = ax * halfExtents.x + ay * halfExtents.y + az * halfExtents.z;
                return AABB{ center - e, center + e };
            }
        }
    }
};

// ---------------------------------------------------------------------------
// Bodies.
// ---------------------------------------------------------------------------

enum class BodyType : u8 { Dynamic = 0, Kinematic = 1, Static = 2 };

struct RigidBody {
    Shape shape;
    BodyType type = BodyType::Dynamic;

    Vec3 position{ 0, 0, 0 };
    Quat rotation{};
    Vec3 linearVelocity{ 0, 0, 0 };
    Vec3 angularVelocity{ 0, 0, 0 };

    f32 mass = 1.0f;
    f32 restitution = 0.1f;
    f32 friction = 0.5f;
    f32 linearDamping = 0.01f;
    f32 angularDamping = 0.05f;
    f32 gravityScale = 1.0f;

    /// Set to apply velocity directly rather than through forces; kinematic
    /// bodies are moved by this and ignore forces entirely.
    Vec3 targetVelocity{ 0, 0, 0 };

    // Derived per step.  Kept here rather than recomputed because the solver
    // touches them in every iteration.
    f32 invMass = 1.0f;
    Vec3 invInertia{ 1.0f, 1.0f, 1.0f };

    /// Recomputes the derived terms.  Must be called after changing mass, shape
    /// or type -- forgetting it is why a body sometimes behaves as if it had the
    /// mass it was created with.
    void updateMass() {
        if (type != BodyType::Dynamic || mass <= 0.0f) {
            invMass = 0.0f;
            invInertia = Vec3{ 0, 0, 0 };
            return;
        }
        invMass = 1.0f / mass;
        // Solid sphere / box inertia, diagonalised.  A full inertia tensor with
        // products of inertia is not worth it for these shapes at game scale.
        switch (shape.kind) {
            case ShapeKind::Sphere: {
                const f32 i = 0.4f * mass * shape.radius * shape.radius;
                invInertia = Vec3{ 1.0f / i, 1.0f / i, 1.0f / i };
                break;
            }
            case ShapeKind::Box: {
                const f32 x = 2.0f * shape.halfExtents.x;
                const f32 y = 2.0f * shape.halfExtents.y;
                const f32 z = 2.0f * shape.halfExtents.z;
                const f32 k = mass / 12.0f;
                invInertia = Vec3{ 1.0f / (k * (y*y + z*z)),
                                   1.0f / (k * (x*x + z*z)),
                                   1.0f / (k * (x*x + y*y)) };
                break;
            }
            case ShapeKind::Capsule:
            default: {
                // Approximated as a sphere of the capsule's bounding radius.
                // Good enough for tumble, and it keeps the solver simple.
                const f32 r = shape.radius + shape.halfHeight;
                const f32 i = 0.4f * mass * r * r;
                invInertia = Vec3{ 1.0f / i, 1.0f / i, 1.0f / i };
                break;
            }
        }
    }

    bool isDynamic() const { return type == BodyType::Dynamic; }
    AABB aabb() const { return shape.aabb(position, rotation); }
};

// ---------------------------------------------------------------------------
// Contacts.
// ---------------------------------------------------------------------------

/// One collision point between two bodies.
struct Contact {
    Vec3 point{ 0, 0, 0 };      ///< world space
    Vec3 normal{ 0, 1, 0 };     ///< from A toward B
    f32  penetration = 0.0f;    ///< positive when overlapping

    /// Target separating velocity along the normal, captured ONCE before the
    /// solve from the approach speed: `-e * v_approach`.
    ///
    /// Recomputing restitution from the current velocity inside the iteration
    /// loop does not work: the first iteration applies the bounce, the second
    /// sees the bodies already separating, and the accumulated impulse then
    /// cancels the bounce it just made.  A ball dropped with restitution 0.8
    /// came off the floor at exactly 0.0 m/s that way.  Expressing restitution
    /// as a target velocity makes the iterations converge on the bounce instead
    /// of undoing it.
    f32 restitutionBias = 0.0f;

    // Accumulated impulses, carried across iterations within a step.  This is
    // what makes sequential impulses converge instead of oscillating.
    f32 normalImpulse = 0.0f;
    f32 tangentImpulse = 0.0f;
    Vec3 tangent{ 1, 0, 0 };
};

struct ContactPair {
    u32 a = kInvalidIndex;
    u32 b = kInvalidIndex;
    Contact contact;
};

// ---------------------------------------------------------------------------
// Joints.
// ---------------------------------------------------------------------------

enum class JointKind : u8 { Distance = 0, PointToPoint = 1 };

/// A constraint between two bodies.
///
/// Distance joints hold two anchor points a fixed separation apart -- the basis
/// of ropes, springs and ragdoll limits.  Point-to-point joints weld two anchors
/// together, which is what a hinge or a grabbed object reduces to.
struct Joint {
    JointKind kind = JointKind::Distance;
    u32 a = kInvalidIndex;
    u32 b = kInvalidIndex;
    Vec3 localAnchorA{ 0, 0, 0 };   ///< in A's local space
    Vec3 localAnchorB{ 0, 0, 0 };
    f32  length = 1.0f;             ///< distance joint rest length
    f32  stiffness = 0.0f;          ///< 0 = rigid; >0 makes it a spring
    f32  damping = 0.0f;
    bool enabled = true;

    // Solver state.
    Vec3 impulse{ 0, 0, 0 };
};

// ---------------------------------------------------------------------------
// Ray casting.
// ---------------------------------------------------------------------------

struct RayHit {
    bool  hit = false;
    f32   distance = 0.0f;
    Vec3  point{ 0, 0, 0 };
    Vec3  normal{ 0, 1, 0 };
    u32   body = kInvalidIndex;
};

// ---------------------------------------------------------------------------
// The world.
// ---------------------------------------------------------------------------

class PhysicsWorld : public NonCopyable {
public:
    explicit PhysicsWorld(Vec3 gravity = Vec3{ 0.0f, -9.81f, 0.0f })
        : m_gravity(gravity) {}

    // --- bodies -------------------------------------------------------------

    u32 createBody(const RigidBody& body) {
        RigidBody b = body;
        b.updateMass();
        m_bodies.push_back(b);
        return static_cast<u32>(m_bodies.size() - 1);
    }

    void destroyBody(u32 id) {
        if (id >= m_bodies.size()) return;
        m_bodies[id].type = BodyType::Static;
        m_bodies[id].invMass = 0.0f;
        m_removed.push_back(id);
    }

    bool valid(u32 id) const {
        if (id >= m_bodies.size()) return false;
        for (u32 r : m_removed) if (r == id) return false;
        return true;
    }

    RigidBody* body(u32 id) {
        return (id < m_bodies.size()) ? &m_bodies[id] : nullptr;
    }
    const RigidBody* body(u32 id) const {
        return (id < m_bodies.size()) ? &m_bodies[id] : nullptr;
    }

    u32 bodyCount() const { return static_cast<u32>(m_bodies.size()); }

    /// Must be called after mutating mass, shape or type through body().
    void refreshMass(u32 id) { if (RigidBody* b = body(id)) b->updateMass(); }

    // --- joints -------------------------------------------------------------

    u32 createJoint(const Joint& j) {
        m_joints.push_back(j);
        return static_cast<u32>(m_joints.size() - 1);
    }
    Joint* joint(u32 id) { return (id < m_joints.size()) ? &m_joints[id] : nullptr; }
    u32 jointCount() const { return static_cast<u32>(m_joints.size()); }

    // --- queries ------------------------------------------------------------

    Vec3 gravity() const { return m_gravity; }
    void setGravity(Vec3 g) { m_gravity = g; }

    /// Ray cast against every body.  `maxDistance` bounds the search.
    ///
    /// Used for ground probes, line-of-sight and aiming.  It is a brute-force
    /// loop over bodies, which is correct for the tens of bodies a mobile scene
    /// has; a broadphase acceleration structure becomes worth it in the
    /// thousands, and the interface does not change when that is added.
    RayHit raycast(Vec3 origin, Vec3 dir, f32 maxDistance) const {
        RayHit best;
        const Vec3 d = dir.normalized();
        if (d.lengthSq() < 1e-8f) return best;

        for (u32 i = 0; i < m_bodies.size(); ++i) {
            bool removed = false;
            for (u32 r : m_removed) if (r == i) { removed = true; break; }
            if (removed) continue;

            const RigidBody& b = m_bodies[i];
            RayHit h;
            switch (b.shape.kind) {
                case ShapeKind::Sphere:
                    h = raySphere(origin, d, b.position, b.shape.radius);
                    break;
                case ShapeKind::Box:
                    h = rayBox(origin, d, b);
                    break;
                case ShapeKind::Capsule:
                default:
                    // Approximated by the bounding sphere.  A capsule is rare
                    // enough as a raycast target that the extra precision is not
                    // worth a second code path.
                    h = raySphere(origin, d, b.position,
                                  b.shape.radius + b.shape.halfHeight);
                    break;
            }
            if (!h.hit || h.distance > maxDistance) continue;
            if (!best.hit || h.distance < best.distance) {
                best = h;
                best.body = i;
            }
        }
        return best;
    }

    /// Overlap test, ignoring velocity.  Cheaper than a step and is what
    /// trigger volumes and pickup collection want.
    bool overlaps(u32 idA, u32 idB) const {
        const RigidBody* a = body(idA);
        const RigidBody* b = body(idB);
        if (!a || !b) return false;
        if (!a->aabb().intersects(b->aabb())) return false;
        Contact c;
        return collide(*a, *b, c);
    }

    const std::vector<ContactPair>& contacts() const { return m_contacts; }

    // --- simulation ---------------------------------------------------------

    /// Advance the world by `dt`.
    ///
    /// The step is fixed-size internally: a variable dt makes restitution and
    /// joint stiffness behave differently at 30 Hz and 120 Hz, which reads as the
    /// game feeling different on different phones.  Large steps are subdivided.
    void step(f32 dt) {
        if (dt <= 0.0f) return;
        f32 remaining = dt;
        int guard = 0;
        while (remaining > 1e-6f && guard++ < 8) {
            const f32 h = remaining > m_fixedStep ? m_fixedStep : remaining;
            integrate(h);
            broadphase();
            solveVelocities(h);
            integratePositions(h);
            solvePositions();
            remaining -= h;
        }
    }

    f32 fixedStep() const { return m_fixedStep; }
    void setFixedStep(f32 h) { if (h > 0.0f) m_fixedStep = h; }

    /// Total linear momentum.  Conserved by collisions in the absence of
    /// external forces, which is the property the test suite checks.
    Vec3 totalMomentum() const {
        Vec3 p{ 0, 0, 0 };
        for (const RigidBody& b : m_bodies) {
            if (b.invMass <= 0.0f) continue;
            p += b.linearVelocity * (1.0f / b.invMass);
        }
        return p;
    }

    f32 totalKineticEnergy() const {
        f32 e = 0.0f;
        for (const RigidBody& b : m_bodies) {
            if (b.invMass <= 0.0f) continue;
            const f32 m = 1.0f / b.invMass;
            e += 0.5f * m * b.linearVelocity.lengthSq();
        }
        return e;
    }

private:
    // --- integration --------------------------------------------------------

    void integrate(f32 h) {
        for (RigidBody& b : m_bodies) {
            if (!b.isDynamic()) continue;
            b.linearVelocity += m_gravity * (b.gravityScale * h);

            // Exponential damping rather than `v *= (1 - damping*h)`, which goes
            // negative once damping*h exceeds 1 -- a stiff damping value would
            // otherwise reverse the body's velocity.
            b.linearVelocity = b.linearVelocity * std::exp(-b.linearDamping * h);
            b.angularVelocity = b.angularVelocity * std::exp(-b.angularDamping * h);
        }
        // Kinematic bodies are driven by their target velocity, not by forces.
        for (RigidBody& b : m_bodies) {
            if (b.type == BodyType::Kinematic) b.linearVelocity = b.targetVelocity;
        }
    }

    void integratePositions(f32 h) {
        for (RigidBody& b : m_bodies) {
            if (b.invMass <= 0.0f && b.type != BodyType::Kinematic) continue;
            b.position += b.linearVelocity * h;
            if (b.angularVelocity.lengthSq() > 1e-12f) {
                const f32 angle = b.angularVelocity.length() * h;
                const Vec3 axis = b.angularVelocity.normalized();
                b.rotation = (Quat::fromAxisAngle(axis, angle) * b.rotation).normalized();
            }
        }
    }

    // --- broadphase ---------------------------------------------------------

    void broadphase() {
        m_contacts.clear();
        const u32 n = static_cast<u32>(m_bodies.size());
        for (u32 i = 0; i < n; ++i) {
            if (!isCollidable(i)) continue;
            for (u32 j = i + 1; j < n; ++j) {
                if (!isCollidable(j)) continue;
                const RigidBody& a = m_bodies[i];
                const RigidBody& b = m_bodies[j];
                // Two non-dynamic bodies cannot move each other; skipping them is
                // the single cheapest optimisation in the broadphase.
                if (a.invMass <= 0.0f && b.invMass <= 0.0f) continue;
                if (!a.aabb().intersects(b.aabb())) continue;

                Contact c;
                if (!collide(a, b, c)) continue;
                ContactPair p;
                p.a = i; p.b = j; p.contact = c;
                p.contact.tangent = tangentFor(c.normal);
                p.contact.restitutionBias = restitutionBiasFor(a, b, c.normal);
                m_contacts.push_back(p);
            }
        }
    }

    bool isCollidable(u32 id) const {
        for (u32 r : m_removed) if (r == id) return false;
        return true;
    }

    // --- narrowphase --------------------------------------------------------

    /// Restitution as a target separating velocity, captured pre-solve.
    ///
    /// The threshold matters: without it, a body resting on the floor keeps
    /// re-bouncing on the tiny approach velocity gravity adds each frame, which
    /// reads as a crate buzzing.
    static f32 restitutionBiasFor(const RigidBody& a, const RigidBody& b, Vec3 n) {
        const f32 approach = (b.linearVelocity - a.linearVelocity).dot(n);
        if (approach > -kRestitutionThreshold) return 0.0f;
        const f32 e = (a.restitution < b.restitution) ? a.restitution : b.restitution;
        return -e * approach;      // positive: a separating target
    }

    static Vec3 tangentFor(Vec3 n) {
        // Any unit vector perpendicular to n.  Picking the axis least aligned
        // with n avoids the degenerate cross product when n is near that axis.
        const Vec3 helper = (std::fabs(n.x) < 0.9f) ? Vec3{ 1, 0, 0 } : Vec3{ 0, 1, 0 };
        return n.cross(helper).normalized();
    }

    /// Dispatches to the right narrowphase routine.  Returns false when the
    /// bodies are separated.
    static bool collide(const RigidBody& a, const RigidBody& b, Contact& out) {
        const bool aSphere = a.shape.kind == ShapeKind::Sphere;
        const bool bSphere = b.shape.kind == ShapeKind::Sphere;

        if (aSphere && bSphere) return sphereSphere(a, b, out);
        if (aSphere)            return sphereOther(a, b, out, false);
        if (bSphere)            return sphereOther(b, a, out, true);
        return boxBox(a, b, out);
    }

    static bool sphereSphere(const RigidBody& a, const RigidBody& b, Contact& out) {
        const Vec3 d = b.position - a.position;
        const f32 distSq = d.lengthSq();
        const f32 rSum = a.shape.radius + b.shape.radius;
        if (distSq > rSum * rSum) return false;

        const f32 dist = std::sqrt(distSq);
        if (dist < 1e-6f) {
            // Exactly concentric: the normal is undefined, so pick one.  Without
            // this the solver divides by zero and the bodies explode apart.
            out.normal = Vec3{ 0, 1, 0 };
            out.penetration = rSum;
        } else {
            out.normal = d * (1.0f / dist);
            out.penetration = rSum - dist;
        }
        out.point = a.position + out.normal * a.shape.radius;
        return true;
    }

    /// Sphere against box or capsule, with `flip` swapping which body is which so
    /// the contact normal is always A -> B.
    static bool sphereOther(const RigidBody& sphere, const RigidBody& other,
                            Contact& out, bool flip) {
        Contact c;
        bool hit = false;
        if (other.shape.kind == ShapeKind::Box) {
            // Closest point on the OBB to the sphere centre.
            const Mat4 R = Mat4::fromQuat(other.rotation);
            const Vec3 local = Vec3{
                R.at(0,0)*(sphere.position.x-other.position.x) +
                R.at(1,0)*(sphere.position.y-other.position.y) +
                R.at(2,0)*(sphere.position.z-other.position.z),
                R.at(0,1)*(sphere.position.x-other.position.x) +
                R.at(1,1)*(sphere.position.y-other.position.y) +
                R.at(2,1)*(sphere.position.z-other.position.z),
                R.at(0,2)*(sphere.position.x-other.position.x) +
                R.at(1,2)*(sphere.position.y-other.position.y) +
                R.at(2,2)*(sphere.position.z-other.position.z)
            };
            const Vec3 he = other.shape.halfExtents;
            Vec3 cl{ clampf(local.x, -he.x, he.x),
                     clampf(local.y, -he.y, he.y),
                     clampf(local.z, -he.z, he.z) };
            const Vec3 worldClosest = other.position +
                Vec3{ R.at(0,0)*cl.x + R.at(0,1)*cl.y + R.at(0,2)*cl.z,
                      R.at(1,0)*cl.x + R.at(1,1)*cl.y + R.at(1,2)*cl.z,
                      R.at(2,0)*cl.x + R.at(2,1)*cl.y + R.at(2,2)*cl.z };
            const Vec3 d = sphere.position - worldClosest;
            const f32 distSq = d.lengthSq();
            if (distSq > sphere.shape.radius * sphere.shape.radius) return false;
            const f32 dist = std::sqrt(distSq);
            c.normal = (dist > 1e-6f) ? d * (1.0f / dist) : Vec3{ 0, 1, 0 };
            c.penetration = sphere.shape.radius - dist;
            c.point = worldClosest;
            hit = true;
        } else {
            // Capsule: closest point on its segment to the sphere centre.
            const Mat4 R = Mat4::fromQuat(other.rotation);
            const Vec3 axis{ R.at(0,1), R.at(1,1), R.at(2,1) };
            const Vec3 p0 = other.position - axis * other.shape.halfHeight;
            const Vec3 p1 = other.position + axis * other.shape.halfHeight;
            const Vec3 ab = p1 - p0;
            const f32 abLenSq = ab.lengthSq();
            f32 t = (abLenSq > 1e-8f)
                        ? (sphere.position - p0).dot(ab) / abLenSq : 0.0f;
            t = clampf(t, 0.0f, 1.0f);
            const Vec3 closest = p0 + ab * t;
            const Vec3 d = sphere.position - closest;
            const f32 distSq = d.lengthSq();
            const f32 rSum = sphere.shape.radius + other.shape.radius;
            if (distSq > rSum * rSum) return false;
            const f32 dist = std::sqrt(distSq);
            c.normal = (dist > 1e-6f) ? d * (1.0f / dist) : Vec3{ 0, 1, 0 };
            c.penetration = rSum - dist;
            c.point = closest;
            hit = true;
        }
        if (!hit) return false;

        // c.normal is (sphere.centre - closest point on other), i.e. it points
        // away from `other`.  When the sphere is A that is B->A and must be
        // negated to match the A->B convention; when the sphere is B it is
        // already A->B.  The sense is the opposite of what the parameter name
        // suggests, hence `!flip`.
        out.normal = flip ? c.normal : -c.normal;
        out.penetration = c.penetration;
        out.point = c.point;
        return true;
    }

    /// Box against box, approximated by AABB.
    ///
    /// This is the one approximation in the module.  Full OBB separation-axis
    /// testing needs 15 axes and an incident/reference face clip, which is a lot
    /// of code whose failure mode -- a box occasionally sliding through a corner
    /// -- is invisible in the games shipped here, where boxes are axis-aligned
    /// ground and crates.  It is called out rather than left to be discovered.
    static bool boxBox(const RigidBody& a, const RigidBody& b, Contact& out) {
        const AABB ba = a.aabb();
        const AABB bb = b.aabb();
        if (!ba.intersects(bb)) return false;

        const f32 ox = std::min(ba.max.x, bb.max.x) - std::max(ba.min.x, bb.min.x);
        const f32 oy = std::min(ba.max.y, bb.max.y) - std::max(ba.min.y, bb.min.y);
        const f32 oz = std::min(ba.max.z, bb.max.z) - std::max(ba.min.z, bb.min.z);

        // Resolve along the axis of least penetration, which is the standard
        // MTV choice and what stops a box resting on the floor being pushed
        // sideways instead of up.
        // normal points from A toward B, so its sign is decided by which body is
        // further along the axis.  Getting this backwards makes the solver push
        // the bodies INTO each other instead of apart.
        if (ox <= oy && ox <= oz) {
            out.normal = Vec3{ (a.position.x < b.position.x) ? 1.0f : -1.0f, 0, 0 };
            out.penetration = ox;
        } else if (oy <= oz) {
            out.normal = Vec3{ 0, (a.position.y < b.position.y) ? 1.0f : -1.0f, 0 };
            out.penetration = oy;
        } else {
            out.normal = Vec3{ 0, 0, (a.position.z < b.position.z) ? 1.0f : -1.0f };
            out.penetration = oz;
        }
        out.point = Vec3{ (std::max(ba.min.x, bb.min.x) + std::min(ba.max.x, bb.max.x)) * 0.5f,
                          (std::max(ba.min.y, bb.min.y) + std::min(ba.max.y, bb.max.y)) * 0.5f,
                          (std::max(ba.min.z, bb.min.z) + std::min(ba.max.z, bb.max.z)) * 0.5f };
        return true;
    }

    static f32 clampf(f32 v, f32 lo, f32 hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // --- solvers ------------------------------------------------------------

    void solveVelocities(f32 h) {
        // Warm-start from zero.  Carrying the previous step's impulses would let
        // a joint accumulate force forever; some solvers warm start for
        // stiffness, but that needs the cached impulses to be transformed into
        // the new contact frame, which this solver does not do.
        for (Joint& j : m_joints) j.impulse = Vec3{ 0, 0, 0 };

        // Spring joints are force-based, so they are applied ONCE per step.
        // Running them inside the iteration loop multiplies their stiffness by
        // the iteration count -- with 8 iterations a stiffness-60 spring behaved
        // like 480 and barely moved at all.
        for (Joint& j : m_joints) {
            if (j.enabled && j.stiffness > 0.0f) applySpring(j, h);
        }

        for (int iter = 0; iter < kVelocityIterations; ++iter) {
            for (ContactPair& p : m_contacts) {
                RigidBody* a = body(p.a);
                RigidBody* b = body(p.b);
                if (!a || !b) continue;
                applyNormalImpulse(*a, *b, p.contact);
                applyFrictionImpulse(*a, *b, p.contact);
            }
            for (Joint& j : m_joints) {
                // Rigid joints only; springs were applied above.
                if (j.enabled && j.stiffness <= 0.0f) solveJointVelocity(j, h);
            }
        }
    }

    void applyNormalImpulse(RigidBody& a, RigidBody& b, Contact& c) {
        const Vec3 n = c.normal;
        const Vec3 relVel = b.linearVelocity - a.linearVelocity;
        const f32 velAlongNormal = relVel.dot(n);

        const f32 invMassSum = a.invMass + b.invMass;
        if (invMassSum <= 0.0f) return;

        // Drive the relative velocity toward the captured separating target
        // rather than scaling whatever the velocity happens to be this
        // iteration.  Once the bodies reach the target the error is zero and the
        // iterations stop adding impulse, which is what makes a bounce survive
        // the remaining iterations instead of being cancelled by them.
        //
        // No position bias here, deliberately.  Adding (beta/h)*penetration to
        // the impulse looks right but injects real velocity: a ball measured
        // 0.8742 m/s upward off the floor at BOTH restitution 0 and 0.8, because
        // the bias -- not the bounce -- was what moved it.  Penetration is
        // corrected by the position pass below, which cannot add energy.
        f32 jn = (c.restitutionBias - velAlongNormal) / invMassSum;

        // Accumulate and clamp to be non-negative.  A contact can push but never
        // pull; without the clamp the solver would suck bodies into each other.
        const f32 old = c.normalImpulse;
        c.normalImpulse = std::max(old + jn, 0.0f);
        jn = c.normalImpulse - old;

        const Vec3 impulse = n * jn;
        a.linearVelocity -= impulse * a.invMass;
        b.linearVelocity += impulse * b.invMass;
    }

    void applyFrictionImpulse(RigidBody& a, RigidBody& b, Contact& c) {
        const Vec3 relVel = b.linearVelocity - a.linearVelocity;
        const f32 velAlongTangent = relVel.dot(c.tangent);
        const f32 invMassSum = a.invMass + b.invMass;
        if (invMassSum <= 0.0f) return;

        f32 jt = -velAlongTangent / invMassSum;

        // Coulomb friction: the tangential impulse is capped by the normal
        // impulse times the combined friction coefficient.  Skipping this cap is
        // the classic "surfaces weld together" bug.
        const f32 mu = std::sqrt(a.friction * b.friction + 1e-6f);
        const f32 maxFriction = mu * c.normalImpulse;

        const f32 old = c.tangentImpulse;
        c.tangentImpulse = clampf(old + jt, -maxFriction, maxFriction);
        jt = c.tangentImpulse - old;

        const Vec3 impulse = c.tangent * jt;
        a.linearVelocity -= impulse * a.invMass;
        b.linearVelocity += impulse * b.invMass;
    }

    void solveJointVelocity(Joint& j, f32 h) {
        RigidBody* a = body(j.a);
        RigidBody* b = body(j.b);
        if (!a || !b) return;
        const f32 invMassSum = a->invMass + b->invMass;
        if (invMassSum <= 0.0f) return;

        const Mat4 Ra = Mat4::fromQuat(a->rotation);
        const Mat4 Rb = Mat4::fromQuat(b->rotation);
        const Vec3 wa = Ra.transformDirection(j.localAnchorA);
        const Vec3 wb = Rb.transformDirection(j.localAnchorB);
        const Vec3 pa = a->position + wa;
        const Vec3 pb = b->position + wb;

        if (j.kind == JointKind::PointToPoint) {
            // Constraint C = pb - pa.  Drive Cdot toward -C * beta / h so the
            // anchors converge over a few frames instead of snapping.
            //
            // The relative velocity MUST be subtracted.  An earlier version
            // applied `delta * (-1/h)` directly, which re-applied the entire
            // correction on all 8 iterations: velocity ran to infinity and then
            // to NaN by the fifth second of simulation.
            const Vec3 C = pb - pa;
            const Vec3 Cdot = b->linearVelocity - a->linearVelocity;
            const Vec3 bias = C * (kBaumgarte / h);
            Vec3 lambda = (Cdot + bias) * (-1.0f / invMassSum);

            // Accumulate, so iterating converges rather than re-applying.
            const Vec3 applied = j.impulse;
            j.impulse = applied + lambda;
            lambda = j.impulse - applied;

            a->linearVelocity -= lambda * a->invMass;
            b->linearVelocity += lambda * b->invMass;
        } else {
            // Distance joint: constrain along the axis between the anchors.
            Vec3 axis = pb - pa;
            const f32 len = axis.length();
            if (len < 1e-6f) return;
            axis = axis * (1.0f / len);

            const f32 error = len - j.length;
            const Vec3 rel = b->linearVelocity - a->linearVelocity;
            const f32 relAlong = rel.dot(axis);

            f32 lambda = -relAlong;
            {
                // Rigid: C = len - length, Cdot = relative velocity along the
                // axis.  Drive Cdot to -C * beta / h.  The slop deadband stops
                // the joint buzzing at its rest length.
                const f32 sloped = (error > kLinearSlop)  ? error - kLinearSlop
                                 : (error < -kLinearSlop) ? error + kLinearSlop
                                 : 0.0f;
                const f32 bias = sloped * (kBaumgarte / h);
                lambda = -(relAlong + bias);
            }
            lambda /= invMassSum;

            // Accumulate along the axis so repeated iterations converge.
            const f32 applied = j.impulse.x;
            j.impulse.x = applied + lambda;
            const f32 dLambda = j.impulse.x - applied;

            const Vec3 impulse = axis * dLambda;
            a->linearVelocity -= impulse * a->invMass;
            b->linearVelocity += impulse * b->invMass;
        }
    }

    /// Spring-damper joint, applied once per step as a force.
    ///
    /// Hooke's law plus a velocity damping term.  This is a force, not a
    /// constraint, so it must not be inside the iteration loop -- see
    /// solveVelocities().
    void applySpring(Joint& j, f32 h) {
        RigidBody* a = body(j.a);
        RigidBody* b = body(j.b);
        if (!a || !b) return;

        const Mat4 Ra = Mat4::fromQuat(a->rotation);
        const Mat4 Rb = Mat4::fromQuat(b->rotation);
        const Vec3 pa = a->position + Ra.transformDirection(j.localAnchorA);
        const Vec3 pb = b->position + Rb.transformDirection(j.localAnchorB);

        if (j.kind == JointKind::Distance) {
            Vec3 axis = pb - pa;
            const f32 len = axis.length();
            if (len < 1e-6f) return;
            axis = axis * (1.0f / len);
            const f32 error = len - j.length;
            const f32 relAlong = (b->linearVelocity - a->linearVelocity).dot(axis);
            // F = -k*x - c*v, applied as an impulse F*dt to each body.
            const f32 f = -(j.stiffness * error + j.damping * relAlong) * h;
            const Vec3 impulse = axis * f;
            a->linearVelocity -= impulse * a->invMass;
            b->linearVelocity += impulse * b->invMass;
        } else {
            const Vec3 C = pb - pa;
            const Vec3 Cdot = b->linearVelocity - a->linearVelocity;
            const Vec3 f = (C * -j.stiffness) - (Cdot * j.damping);
            const Vec3 impulse = f * h;
            a->linearVelocity -= impulse * a->invMass;
            b->linearVelocity += impulse * b->invMass;
        }
    }

    /// Non-linear position correction, applied after integration.
    ///
    /// Velocity-level Baumgarte alone lets bodies drift apart under sustained
    /// load -- a tall stack slowly sinks.  Correcting positions directly removes
    /// the accumulated error without adding velocity, so it cannot feed energy
    /// back into the system.
    void solvePositions() {
        for (int iter = 0; iter < kPositionIterations; ++iter) {
            for (ContactPair& p : m_contacts) {
                RigidBody* a = body(p.a);
                RigidBody* b = body(p.b);
                if (!a || !b) continue;
                const f32 invMassSum = a->invMass + b->invMass;
                if (invMassSum <= 0.0f) continue;
                const f32 excess = p.contact.penetration - kLinearSlop;
                if (excess <= 0.0f) continue;
                const f32 correction = (excess / invMassSum) * 0.4f;
                const Vec3 c = p.contact.normal * correction;
                a->position -= c * a->invMass;
                b->position += c * b->invMass;
            }
        }
    }

    // --- ray helpers --------------------------------------------------------

    static RayHit raySphere(Vec3 origin, Vec3 dir, Vec3 center, f32 radius) {
        RayHit h;
        const Vec3 oc = origin - center;
        const f32 bq = oc.dot(dir);
        const f32 cq = oc.lengthSq() - radius * radius;
        const f32 disc = bq * bq - cq;
        if (disc < 0.0f) return h;
        const f32 s = std::sqrt(disc);
        f32 t = -bq - s;
        if (t < 0.0f) t = -bq + s;          // origin is inside: take the exit
        if (t < 0.0f) return h;
        h.hit = true;
        h.distance = t;
        h.point = origin + dir * t;
        h.normal = (h.point - center).normalized();
        return h;
    }

    /// Slab test against the body's AABB.
    static RayHit rayBox(Vec3 origin, Vec3 dir, const RigidBody& b) {
        RayHit h;
        const AABB box = b.aabb();
        f32 tmin = 0.0f, tmax = 1e30f;
        int axis = 0;
        bool minSide = true;

        const f32 o[3] = { origin.x, origin.y, origin.z };
        const f32 d[3] = { dir.x, dir.y, dir.z };
        const f32 lo[3] = { box.min.x, box.min.y, box.min.z };
        const f32 hi[3] = { box.max.x, box.max.y, box.max.z };

        for (int i = 0; i < 3; ++i) {
            if (std::fabs(d[i]) < 1e-8f) {
                if (o[i] < lo[i] || o[i] > hi[i]) return h;   // parallel and outside
                continue;
            }
            const f32 inv = 1.0f / d[i];
            f32 t1 = (lo[i] - o[i]) * inv;
            f32 t2 = (hi[i] - o[i]) * inv;
            bool fromMin = true;
            if (t1 > t2) { const f32 tmp = t1; t1 = t2; t2 = tmp; fromMin = false; }
            if (t1 > tmin) { tmin = t1; axis = i; minSide = fromMin; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return h;
        }
        if (tmax < 0.0f) return h;
        h.hit = true;
        h.distance = tmin;
        h.point = origin + dir * tmin;
        f32 n[3] = { 0, 0, 0 };
        n[axis] = minSide ? -1.0f : 1.0f;
        h.normal = Vec3{ n[0], n[1], n[2] };
        return h;
    }

    // --- state --------------------------------------------------------------

    std::vector<RigidBody> m_bodies;
    std::vector<ContactPair> m_contacts;
    std::vector<Joint> m_joints;
    std::vector<u32> m_removed;
    Vec3 m_gravity;
    f32 m_fixedStep = 1.0f / 60.0f;
};

}  // namespace se
