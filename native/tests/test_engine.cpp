// SPDX-License-Identifier: MIT
// S Engine 2.0 -- host test suite.
//
// Built by native/Makefile.host and run on the developer machine (no Android,
// no GPU).  These are the invariants the on-device engine depends on, so they
// run in CI on every push.
#include "../engine/core/Handle.h"
#include "../engine/core/Hash.h"
#include "../engine/core/Memory.h"
#include "../engine/ecs/World.h"
#include "../engine/render/NullRHI.h"
#include "../engine/render/Pbr.h"
#include "../engine/render/ShadowCascades.h"
#include "../engine/physics/PhysicsWorld.h"
#include "../engine/scene/SceneGraph.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {

int g_checks = 0;
int g_failures = 0;
const char* g_section = "";

void section(const char* name) {
    g_section = name;
    std::printf("\n== %s ==\n", name);
}

void check(bool cond, const std::string& what) {
    ++g_checks;
    if (cond) {
        std::printf("  ok   %s\n", what.c_str());
    } else {
        ++g_failures;
        std::printf("  FAIL %s   [%s]\n", what.c_str(), g_section);
    }
}

bool nearEq(float a, float b, float eps = 1e-4f) { return std::fabs(a - b) <= eps; }

// ---------------------------------------------------------------------------
// Test components (POD, as the ECS requires).
// ---------------------------------------------------------------------------
struct Transform { se::Vec3 pos; se::Vec3 scale; };
struct Velocity   { se::Vec3 v; };
struct Health     { int hp; int max; };
struct Tag        { se::u32 id; };

// ---------------------------------------------------------------------------
void testLinearAllocator() {
    section("LinearAllocator");
    se::LinearAllocator a(4096);

    void* p1 = a.alloc(100);
    void* p2 = a.alloc(100);
    check(p1 && p2 && p1 != p2, "two allocations return distinct pointers");
    check(a.used() >= 200, "used() accounts for both allocations");

    // 64-byte alignment must be honoured.
    void* aligned = a.alloc(16, 64);
    check(reinterpret_cast<uintptr_t>(aligned) % 64 == 0, "respects 64-byte alignment");

    const se::u64 mark = a.mark();
    a.alloc(512);
    check(a.used() > mark, "allocation advances the bump pointer");
    a.rewind(mark);
    check(a.used() == mark, "rewind restores the exact mark");

    a.reset();
    check(a.used() == 0, "reset empties the arena");
    check(a.stats().bytesPeak > 0, "peak usage is tracked across resets");

    // create<T> must honour alignof.
    struct alignas(64) Aligned { int x; };
    Aligned* obj = a.create<Aligned>();
    check(reinterpret_cast<uintptr_t>(obj) % 64 == 0, "create<T> respects alignof(T)");
    obj->x = 42;
    check(obj->x == 42, "placement-constructed object is usable");
}

void testPoolAllocator() {
    section("PoolAllocator");
    se::PoolAllocator pool(64, 16);

    std::vector<void*> blocks;
    for (int i = 0; i < 40; ++i) blocks.push_back(pool.alloc());   // forces 3 chunks
    check(pool.allocatedBlocks() >= 40, "grows past its first chunk");

    bool unique = true;
    for (size_t i = 0; i < blocks.size(); ++i)
        for (size_t j = i + 1; j < blocks.size(); ++j)
            if (blocks[i] == blocks[j]) unique = false;
    check(unique, "no block is handed out twice while live");
    check(pool.liveBlocks() == 40, "liveBlocks tracks outstanding allocations");

    // Free everything, then reallocate: the free list must recycle, not grow.
    const se::u64 allocatedBefore = pool.allocatedBlocks();
    for (void* b : blocks) pool.free(b);
    check(pool.liveBlocks() == 0, "freeing every block returns liveBlocks to 0");

    void* recycled = pool.alloc();
    check(recycled != nullptr, "allocation after a full free still succeeds");
    check(pool.allocatedBlocks() == allocatedBefore, "recycled from the free list without growing");

    // Blocks must be writable across their full size.
    std::memset(recycled, 0xAB, 64);
    check(static_cast<se::u8*>(recycled)[63] == 0xAB, "full block size is writable");
}

void testBucketAllocator() {
    section("BucketAllocator");
    se::BucketAllocator b;

    check(se::BucketAllocator::classIndex(1) == 0, "1 byte -> class 0 (16 B)");
    check(se::BucketAllocator::classIndex(16) == 0, "16 bytes -> class 0");
    check(se::BucketAllocator::classIndex(17) == 1, "17 bytes -> class 1 (32 B)");
    check(se::BucketAllocator::classIndex(512) == 5, "512 bytes -> class 5");
    check(se::BucketAllocator::classIndex(513) == se::kInvalidIndex, "513 bytes falls back to the system");

    void* small = b.alloc(24);
    void* big = b.alloc(4096);
    check(small && big, "both pooled and oversized allocations succeed");
    b.free(small, 24);
    b.free(big, 4096);
    check(b.stats().frees == 2, "free is accounted for both paths");
}

void testFrameStack() {
    section("FrameStack");
    se::FrameStack stack(3, 1024);
    se::LinearAllocator* f0 = stack.scratch();
    f0->alloc(128);
    stack.beginFrame();
    se::LinearAllocator* f1 = stack.scratch();
    check(f0 != f1, "consecutive frames get different arenas");
    check(f0->used() == 128, "the previous frame's scratch is untouched");
    stack.beginFrame();
    stack.beginFrame();
    check(stack.scratch() == f0, "the ring wraps back to the first arena");
}

void testHandles() {
    section("Generational handles");
    se::HandleTable<int> table;

    se::Handle a = table.create(7);
    se::Handle b = table.create(9);
    check(table.alive(a) && table.alive(b), "created handles are alive");
    check(*table.get(a) == 7 && *table.get(b) == 9, "handles resolve to their values");

    check(table.destroy(a), "destroy reports success for a live handle");
    check(!table.alive(a), "destroyed handle is no longer alive");
    check(table.get(a) == nullptr, "get on a destroyed handle returns null");
    check(!table.destroy(a), "destroying twice reports failure");

    // The recycled slot must not resurrect the stale handle.
    se::Handle c = table.create(11);
    check(c.index == a.index, "the slot index was reused");
    check(c.generation != a.generation, "but the generation was bumped");
    check(!table.alive(a), "the stale handle stays dead after reuse");
    check(*table.get(c) == 11, "the new handle resolves correctly");

    int seen = 0;
    table.forEach([&](se::Handle, int&) { ++seen; });
    check(seen == 2, "forEach visits exactly the live slots");
}

void testInterner() {
    section("String interning");
    se::StringInterner si;

    check(se::kInvalidIntern == 0, "id 0 is reserved for the empty string");
    const se::InternId a = si.intern("Transform");
    const se::InternId b = si.intern("Velocity");
    const se::InternId a2 = si.intern("Transform");
    check(a == a2 && a != b, "identical strings share an id, distinct strings do not");
    check(std::strcmp(si.resolve(a), "Transform") == 0, "resolve round-trips");
    check(si.find("Transform") == a, "find locates a known string");
    check(si.find("NotRegistered") == se::kInvalidIntern, "find returns invalid for an unknown string");
    check(si.resolve(se::kInvalidIntern)[0] == '\0', "id 0 resolves to the empty string");
    check(si.resolve(9999) == nullptr, "an out-of-range id resolves to null");
    check(si.size() == 2, "interning the same string twice does not grow the table");

    // Hash distribution sanity: these two must not collide.
    check(se::hashString("Transform") != se::hashString("Velocity"), "FNV-1a separates distinct names");
}

// ---------------------------------------------------------------------------
void testEcsBasics() {
    section("ECS: entities and components");
    se::World w;

    const se::ComponentTypeId tId = w.registerComponent<Transform>("Transform");
    const se::ComponentTypeId vId = w.registerComponent<Velocity>("Velocity");
    const se::ComponentTypeId hId = w.registerComponent<Health>("Health");
    check(tId != vId && vId != hId, "component ids are unique");
    check(w.componentIdByName("Transform") == tId, "component id is resolvable by name");
    check(w.componentIdByName("Nope") == se::kInvalidComponent, "unknown component name -> invalid id");
    check(w.meta(tId)->size == sizeof(Transform), "component metadata records the size");

    se::Entity e = w.create();
    check(w.alive(e), "created entity is alive");
    check(w.entityCount() == 1, "entity count is 1");

    Transform* t = w.add<Transform>(e, tId, Transform{ {1, 2, 3}, {1, 1, 1} });
    check(t && t->pos.x == 1.0f, "add returns a usable component pointer");
    check(w.has<Transform>(e, tId), "has<> sees the added component");
    check(!w.has<Velocity>(e, vId), "has<> is false for an absent component");

    Transform* again = w.add<Transform>(e, tId, Transform{ {9, 9, 9}, {1, 1, 1} });
    check(again == t, "adding an existing component returns the same pointer");
    check(again->pos.x == 1.0f, "and does not overwrite the stored value");

    t->pos.y = 42.0f;
    check(w.get<Transform>(e, tId)->pos.y == 42.0f, "writes through the pointer are visible via get<>");

    check(w.remove(e, tId), "remove reports success");
    check(!w.has<Transform>(e, tId), "the component is gone after remove");
    check(!w.remove(e, tId), "removing twice reports failure");

    w.destroy(e);
    check(!w.alive(e), "destroyed entity is not alive");
    check(w.get<Transform>(e, tId) == nullptr, "get on a dead entity returns null");

    se::Entity e2 = w.create();
    check(e2.index == e.index && e2.generation != e.generation, "recycled index gets a new generation");
}

void testEcsArchetypes() {
    section("ECS: archetype storage and migration");
    se::World w;
    const se::ComponentTypeId tId = w.registerComponent<Transform>("Transform");
    const se::ComponentTypeId vId = w.registerComponent<Velocity>("Velocity");
    const se::ComponentTypeId hId = w.registerComponent<Health>("Health");

    // A: Transform only.  B: Transform + Velocity.  C: all three.
    se::Entity a = w.create(); w.add<Transform>(a, tId, Transform{ {1, 0, 0}, {1, 1, 1} });
    se::Entity b = w.create(); w.add<Transform>(b, tId, Transform{ {2, 0, 0}, {1, 1, 1} });
    w.add<Velocity>(b, vId, Velocity{ {0, 5, 0} });
    se::Entity c = w.create(); w.add<Transform>(c, tId, Transform{ {3, 0, 0}, {1, 1, 1} });
    w.add<Velocity>(c, vId, Velocity{ {0, 6, 0} });
    w.add<Health>(c, hId, Health{ 100, 100 });

    const se::WorldStats st = w.stats();
    check(st.entities == 3, "three entities live");
    check(st.archetypes == 4, "empty + 3 distinct archetypes were created");

    // Migration must preserve the surviving components' values.
    se::Entity d = w.create();
    w.add<Transform>(d, tId, Transform{ {7, 8, 9}, {2, 2, 2} });
    w.add<Health>(d, hId, Health{ 55, 60 });
    w.add<Velocity>(d, vId, Velocity{ {1, 2, 3} });
    check(w.get<Transform>(d, tId)->pos.y == 8.0f, "Transform survives adding Velocity");
    check(w.get<Health>(d, hId)->hp == 55, "Health survives the migration");
    check(w.get<Velocity>(d, vId)->v.z == 3.0f, "Velocity is stored after the move");

    // Removing a component migrates back without losing the others.
    w.remove(d, vId);
    check(!w.has<Velocity>(d, vId), "Velocity removed");
    check(w.get<Transform>(d, tId)->pos.y == 8.0f, "Transform still intact after removing Velocity");
    check(w.get<Health>(d, hId)->max == 60, "Health still intact after removing Velocity");

    // Destroying a middle row swap-removes; the survivor must stay addressable.
    w.destroy(b);
    check(!w.alive(b), "b destroyed");
    check(w.alive(c) && w.get<Transform>(c, tId)->pos.x == 3.0f, "c is unaffected by b's destruction");
    check(w.alive(a) && w.get<Transform>(a, tId)->pos.x == 1.0f, "a is unaffected by b's destruction");
}

void testEcsIteration() {
    section("ECS: queries and iteration");
    se::World w;
    const se::ComponentTypeId tId = w.registerComponent<Transform>("Transform");
    const se::ComponentTypeId vId = w.registerComponent<Velocity>("Velocity");
    const se::ComponentTypeId tagId = w.registerComponent<Tag>("Tag");

    constexpr int kCount = 50;
    for (int i = 0; i < kCount; ++i) {
        se::Entity e = w.create();
        w.add<Transform>(e, tId, Transform{ {(float)i, 0, 0}, {1, 1, 1} });
        w.add<Velocity>(e, vId, Velocity{ {1, 0, 0} });
        if (i % 5 == 0) w.add<Tag>(e, tagId, Tag{ (se::u32)i });
    }
    // A transform-only entity: it must not appear in the two-component query.
    se::Entity lonely = w.create();
    w.add<Transform>(lonely, tId, Transform{ {-1, 0, 0}, {1, 1, 1} });

    int visited = 0;
    float sum = 0;
    for (auto row : se::each<Transform, Velocity>(w, tId, vId)) {
        row.a->pos += row.b->v;          // systems write through the query
        sum += row.a->pos.x;
        ++visited;
    }
    check(visited == kCount, "the two-component query visits exactly the matching entities");
    const float expected = 0;
    for (int i = 0; i < kCount; ++i) (void)i;
    check(nearEq(sum, (float)(kCount - 1) * kCount / 2.0f + kCount), "every entity was advanced by its velocity");
    (void)expected;

    int oneVisits = 0;
    for (auto row : se::eachOne<Transform>(w, tId)) { (void)row; ++oneVisits; }
    check(oneVisits == kCount + 1, "the one-component query also sees the transform-only entity");

    // Destroy inside a query is a structural change: it must be deferred.
    se::QueryGuard guard(w);
    check(w.iterating(), "QueryGuard marks the world as iterating");
    int killed = 0;
    for (auto row : se::each<Transform, Tag>(w, tId, tagId)) {
        if (row.b->id % 10 == 0) { w.commands().destroy(row.e); ++killed; }
    }
    check(killed == 5, "five tagged entities were queued for destruction");
    check(w.entityCount() == (se::u64)kCount + 1, "nothing died before the flush");

    w.flushCommands();
    check(w.entityCount() == (se::u64)kCount + 1 - killed, "the flush applied the queued destroys");
    check(w.alive(lonely), "the untagged entity survived");
}

void testCommandBuffer() {
    section("ECS: command buffer");
    se::World w;
    const se::ComponentTypeId tId = w.registerComponent<Transform>("Transform");
    const se::ComponentTypeId hId = w.registerComponent<Health>("Health");

    se::Entity e = w.create();
    w.add<Transform>(e, tId, Transform{ {0, 0, 0}, {1, 1, 1} });

    w.commands().add<Health>(e, hId, Health{ 30, 40 });
    check(!w.has<Health>(e, hId), "a queued add is not visible before the flush");
    w.flushCommands();
    check(w.has<Health>(e, hId), "the queued add is applied by the flush");
    check(w.get<Health>(e, hId)->hp == 30, "and the payload was copied across");

    // Entities created through the command buffer are resolved on flush.
    se::Entity placeholder = w.commands().create();
    const se::u64 before = w.entityCount();
    auto created = w.commands().flush();
    check(w.entityCount() == before + 1, "flush materialised the deferred entity");
    check(created.size() == 1 && w.alive(created[0]), "flush returned a live entity handle");
    w.commands().add<Transform>(created[0], tId, Transform{ {5, 5, 5}, {1, 1, 1} });
    w.flushCommands();
    check(w.get<Transform>(created[0], tId)->pos.x == 5.0f,
          "commands referencing a deferred entity apply after it exists");
    (void)placeholder;

    w.commands().remove(e, hId);
    w.commands().destroy(e);
    w.flushCommands();
    check(!w.alive(e), "a queued destroy is applied");
    check(w.commands().pending() == 0, "the buffer is empty after a flush");
}

// ---------------------------------------------------------------------------
void testSceneGraph() {
    section("Scene graph: hierarchy and dirty propagation");
    se::SceneGraph g;

    se::NodeId root = g.create("Root");
    se::NodeId child = g.create("Child", root);
    se::NodeId leaf = g.create("Leaf", child);

    g.setPosition(root, {10, 0, 0});
    g.setPosition(child, {0, 5, 0});
    g.setPosition(leaf, {0, 0, 2});
    g.update();

    se::Vec3 leafWorld = g.worldPosition(leaf);
    check(nearEq(leafWorld.x, 10) && nearEq(leafWorld.y, 5) && nearEq(leafWorld.z, 2),
          "world position accumulates down the hierarchy (10,5,2)");

    // Moving the root must move the leaf, and only dirty nodes recompute.
    g.setPosition(root, {20, 0, 0});
    check(g.node(root)->dirty, "the root is marked dirty");
    check(g.node(leaf)->dirty, "the dirty flag propagated to the leaf");
    g.update();
    check(nearEq(g.worldPosition(leaf).x, 20), "the leaf followed the root");
    check(!g.node(leaf)->dirty, "update cleared the dirty flag");

    // Rotation composes: 90 deg yaw about Y sends +X to -Z.
    g.setRotation(root, se::Quat::fromAxisAngle({0, 1, 0}, 90.0f * se::kDeg2Rad));
    g.setPosition(child, {4, 0, 0});
    g.setPosition(leaf, {0, 0, 0});
    g.update();
    se::Vec3 p = g.worldPosition(leaf);
    check(nearEq(p.x, 20, 1e-3f) && nearEq(p.z, -4, 1e-3f),
          "parent rotation is applied to the child's offset");

    // Scale multiplies down the chain.
    g.setScale(root, {2, 2, 2});
    g.setScale(child, {3, 3, 3});
    g.update();
    se::Vec3 s = g.worldScale(child);
    check(nearEq(s.x, 6), "nested scale multiplies (2 * 3 = 6)");

    check(g.depth(leaf) == 2, "depth counts ancestors");
    check(g.isAncestor(root, leaf), "root is an ancestor of leaf");
    check(!g.isAncestor(leaf, root), "the relation is not symmetric");
    check(!g.setParent(root, leaf, true), "reparenting a node under its own descendant is rejected");
    check(g.childrenOf(child).size() == 1, "children list is intact after the rejected reparent");

    // Reparent with keepWorld must preserve the world transform.
    const se::Vec3 beforeWorld = g.worldPosition(leaf);
    se::NodeId other = g.create("Other");
    g.setPosition(other, {-5, 0, 0});
    g.update();
    check(g.setParent(leaf, other, true), "reparent succeeds to an unrelated node");
    g.update();
    const se::Vec3 afterWorld = g.worldPosition(leaf);
    check(nearEq(beforeWorld.x, afterWorld.x, 1e-3f) &&
          nearEq(beforeWorld.y, afterWorld.y, 1e-3f) &&
          nearEq(beforeWorld.z, afterWorld.z, 1e-3f),
          "keepWorld preserved the world position across the reparent");

    // Active state ANDs up the chain.
    check(g.activeInHierarchy(leaf), "leaf is active");
    g.node(child)->active = false;
    g.node(other)->active = true;
    check(g.activeInHierarchy(leaf), "leaf is active under its new (active) parent");
    g.node(other)->active = false;
    check(!g.activeInHierarchy(leaf), "deactivating the parent deactivates the subtree");

    // Destruction cascades and leaves no dangling parents.
    const se::u64 before = g.nodeCount();
    g.destroy(other);                                  // `other` still has `leaf`
    check(g.nodeCount() == before - 2, "destroying a subtree removes the node and its child");
    check(!g.valid(leaf), "the child was destroyed with its parent");
    check(!g.valid(other), "the parent is gone");
}

void testMath() {
    section("Math: matrices, quaternions, decomposition");

    // invertRigid must round-trip a point.
    const se::Mat4 m = se::Mat4::trs({3, -2, 7},
                                     se::Quat::fromAxisAngle({0, 1, 0}, 40.0f * se::kDeg2Rad),
                                     {1, 1, 1});
    const se::Mat4 inv = se::invertRigid(m);
    const se::Vec3 original{1.5f, -4.0f, 2.25f};
    const se::Vec3 back = inv.transformPoint(m.transformPoint(original));
    check(nearEq(back.x, original.x) && nearEq(back.y, original.y) && nearEq(back.z, original.z),
          "invertRigid round-trips a point");

    // decompose must recover the TRS it was built from.
    const se::Vec3 pos{5, 6, 7};
    const se::Quat rot = se::Quat::fromEuler(30.0f, 45.0f, 15.0f);
    const se::Vec3 scale{2, 3, 4};
    se::Vec3 dp; se::Quat dr; se::Vec3 ds;
    se::decomposeRigid(se::Mat4::trs(pos, rot, scale), dp, dr, ds);
    check(nearEq(dp.x, pos.x) && nearEq(dp.y, pos.y) && nearEq(dp.z, pos.z),
          "decomposeRigid recovers translation");
    check(nearEq(ds.x, scale.x) && nearEq(ds.y, scale.y) && nearEq(ds.z, scale.z),
          "decomposeRigid recovers scale");
    const se::Vec3 probe{1, 0, 0};
    const se::Vec3 r1 = rot.rotate(probe), r2 = dr.rotate(probe);
    check(nearEq(r1.x, r2.x, 1e-3f) && nearEq(r1.y, r2.y, 1e-3f) && nearEq(r1.z, r2.z, 1e-3f),
          "decomposeRigid recovers an equivalent rotation");

    // Quaternion rotation must agree with the matrix built from it.
    const se::Quat q = se::Quat::fromAxisAngle(se::Vec3{0, 1, 0}.normalized(), 90.0f * se::kDeg2Rad);
    const se::Vec3 rotated = q.rotate(se::Vec3{1, 0, 0});
    const se::Vec3 byMatrix = se::Mat4::fromQuat(q).transformDirection(se::Vec3{1, 0, 0});
    check(nearEq(rotated.x, byMatrix.x) && nearEq(rotated.z, byMatrix.z),
          "Quat::rotate agrees with Mat4::fromQuat");
    check(nearEq(rotated.z, -1.0f, 1e-4f), "90 deg yaw sends +X to -Z");

    // fromEuler axis mapping: each axis alone must rotate about that axis.
    const se::Vec3 probe2{1, 0, 0};
    const se::Vec3 yawed = se::Quat::fromEuler(0, 90, 0).rotate(probe2);
    check(nearEq(yawed.z, -1.0f, 1e-4f) && nearEq(yawed.y, 0.0f, 1e-4f),
          "fromEuler yaw rotates about Y (+X -> -Z)");
    const se::Vec3 pitched = se::Quat::fromEuler(90, 0, 0).rotate(se::Vec3{0, 0, 1});
    check(nearEq(pitched.y, -1.0f, 1e-4f), "fromEuler pitch rotates about X (+Z -> -Y)");
    const se::Vec3 rolled = se::Quat::fromEuler(0, 0, 90).rotate(se::Vec3{1, 0, 0});
    check(nearEq(rolled.y, 1.0f, 1e-4f), "fromEuler roll rotates about Z (+X -> +Y)");

    // slerp endpoints and midpoint.
    const se::Quat a = se::Quat::identity();
    const se::Quat b = se::Quat::fromAxisAngle({0, 1, 0}, 90.0f * se::kDeg2Rad);
    const se::Vec3 mid = se::Quat::slerp(a, b, 0.5f).rotate(se::Vec3{1, 0, 0});
    check(nearEq(mid.x, 0.7071f, 1e-3f) && nearEq(mid.z, -0.7071f, 1e-3f),
          "slerp(0.5) lands halfway between the endpoints");

    // Perspective must map the near plane to depth 0 and the far plane to 1.
    const se::Mat4 proj = se::Mat4::perspective(60.0f, 16.0f / 9.0f, 0.1f, 100.0f);
    const se::Vec4 n = proj * se::Vec4{0, 0, -0.1f, 1};
    const se::Vec4 f = proj * se::Vec4{0, 0, -100.0f, 1};
    check(nearEq(n.z / n.w, 0.0f, 1e-4f), "perspective maps the near plane to depth 0");
    check(nearEq(f.z / f.w, 1.0f, 1e-3f), "perspective maps the far plane to depth 1");

    // lookAt must place the eye at the origin looking down -Z.
    const se::Mat4 view = se::Mat4::lookAt({0, 0, 5}, {0, 0, 0}, {0, 1, 0});
    const se::Vec3 targetInView = view.transformPoint({0, 0, 0});
    check(targetInView.z < 0, "lookAt puts the target in front of the camera (-Z)");

    se::AABB box;
    box.expand({-1, -1, -1});
    box.expand({1, 1, 1});
    check(box.contains({0, 0, 0}), "AABB contains its centre");
    check(!box.contains({2, 0, 0}), "AABB excludes a point outside");
    se::AABB other;
    other.expand({0.5f, 0.5f, 0.5f});
    other.expand({3, 3, 3});
    check(box.intersects(other), "overlapping AABBs intersect");
}

// ---------------------------------------------------------------------------
void testNullRHI() {
    section("NullRHI: resource lifetime and draw contract");
    se::NullRHIConfig cfg;
    cfg.validateStateOrder = false;     // count violations instead of aborting
    se::NullRHIDevice device(cfg);

    se::TextureDesc td;
    td.width = 256; td.height = 256; td.mipLevels = 1; td.format = se::Format::R8G8B8A8_UNorm;
    se::RHIHandle tex = device.createTexture(td);
    check(device.owns(tex), "texture handle is live");
    check(device.vramBytes() == 256u * 256u * 4u, "VRAM accounting matches 256x256 RGBA8");

    se::TextureDesc dtd;
    dtd.width = 512; dtd.height = 512; dtd.mipLevels = 9;
    se::RHIHandle mip = device.createTexture(dtd);
    check(device.vramBytes() > 512u * 512u * 4u, "a full mip chain costs more than its base level");

    se::BufferDesc bd;
    bd.size = 1024; bd.vertex = true;
    se::RHIHandle vb = device.createBuffer(bd);

    se::PipelineDesc pd;
    pd.vertexSource = "shaders/pbr.vert.spv";
    pd.fragmentSource = "shaders/pbr.frag.spv";
    pd.vertexStride = 32;
    se::RHIHandle pipe = device.createPipeline(pd);
    check(device.violations() == 0, "a well-formed pipeline raises no violation");

    // A pipeline with no shader source must be flagged.
    se::PipelineDesc bad;
    bad.vertexStride = 32;
    device.createPipeline(bad);
    check(device.violations() == 1, "an empty shader stage is flagged");

    se::RHICommandBuffer* cb = device.createCommandBuffer();

    // Drawing outside a pass is a contract violation.
    cb->bindPipeline(pipe);
    cb->draw(3);
    check(device.violations() == 2, "drawing outside a render pass is flagged");

    // A well-formed pass.
    se::RenderPassDesc rp;
    se::AttachmentDesc color;
    color.texture = tex;
    rp.colors.push_back(color);
    rp.width = 256; rp.height = 256;
    cb->beginRenderPass(rp);
    cb->setViewport(0, 0, 256, 256);
    cb->bindPipeline(pipe);
    cb->bindVertexBuffer(vb);
    cb->drawIndexed(300);            // 100 triangles
    cb->drawIndexed(60);             // 20 triangles
    cb->endRenderPass();
    device.submit(cb);

    const se::FrameStats fs = device.frameStats();
    check(fs.renderPasses == 1, "one render pass was recorded");
    check(fs.drawCalls == 2, "two draw calls were submitted");
    check(fs.triangles == 120, "triangle count is 100 + 20");
    check(fs.pipelineBinds >= 2, "pipeline binds were counted");

    // Buffer overrun must be caught.
    const se::u32 payload[4] = {1, 2, 3, 4};
    device.updateBuffer(vb, 1020, payload, sizeof(payload));
    check(device.violations() == 3, "writing past the end of a buffer is flagged");
    device.updateBuffer(vb, 0, payload, sizeof(payload));
    check(device.violations() == 3, "an in-bounds write is fine");

    // Double free must be caught.
    device.destroy(tex);
    check(!device.owns(tex), "the texture is gone");
    device.destroy(tex);
    check(device.violations() == 4, "a double free is flagged");

    check(device.liveResources() == 4,
          "four resources remain live (mip texture, vertex buffer, both pipelines)");
}

// ---------------------------------------------------------------------------
void testPbr() {
    section("PBR: Cook-Torrance shading");
    using namespace se;

    PbrMaterial dielectric;                      // white plastic
    dielectric.albedo = Vec3{0.8f, 0.2f, 0.2f};
    dielectric.metallic = 0.0f;
    dielectric.roughness = 0.5f;

    PbrMaterial metal;
    metal.albedo = Vec3{1.0f, 0.766f, 0.336f};   // gold
    metal.metallic = 1.0f;
    metal.roughness = 0.2f;

    // --- f0: dielectrics sit at 0.04, metals take the albedo.
    check(nearEq(dielectric.f0().x, 0.04f), "dielectric F0 is 0.04");
    check(nearEq(metal.f0().x, 1.0f) && nearEq(metal.f0().y, 0.766f),
          "metal F0 is tinted by the albedo");

    // --- roughness remap squares, so the perceptual slider is linear.
    PbrMaterial r = dielectric;
    r.roughness = 0.5f;
    check(nearEq(r.alpha(), 0.25f), "alpha is roughness squared");

    // --- GGX NDF: peaks at nDotH = 1 and is positive everywhere.
    const f32 peak = distributionGGX(1.0f, 0.25f);
    const f32 side = distributionGGX(0.5f, 0.25f);
    check(peak > side, "GGX peaks when H is aligned with N");
    check(distributionGGX(0.0f, 0.25f) >= 0.0f, "GGX is non-negative at the horizon");

    // --- Smith geometry is in (0,1].
    const f32 G = geometrySmith(0.7f, 0.6f, 0.25f);
    check(G > 0.0f && G <= 1.0f, "Smith geometry term is in (0,1]");

    // --- Fresnel: f0 straight on, approaches white at grazing angles.
    const Vec3 f0{0.04f, 0.04f, 0.04f};
    check(nearEq(fresnelSchlick(1.0f, f0).x, 0.04f), "Fresnel returns F0 at normal incidence");
    const f32 grazing = fresnelSchlick(0.0f, f0).x;
    check(nearEq(grazing, 1.0f), "Fresnel approaches 1 at grazing angles");

    // Roughness-capped Fresnel must NOT reach 1 on a rough surface -- that cap is
    // what stops rough silhouettes from glowing.
    const f32 capped = fresnelSchlickRoughness(0.0f, f0, 0.9f).x;
    check(capped < 1.0f, "roughness caps the grazing Fresnel");

    // --- light behind the surface contributes nothing.
    const Vec3 N{0,1,0}, V{0,1,0};
    const Vec3 below{0,-1,0};
    const Vec3 zero = shadeDirect(dielectric, N, V, below, Vec3{1,1,1});
    check(zero.x == 0.0f && zero.y == 0.0f && zero.z == 0.0f,
          "a light behind the surface contributes nothing");

    // --- metals have no diffuse: the red plastic and a red metal differ.
    //
    // Compared at roughness 1 on purpose.  At roughness 0.2 (alpha = 0.04) the
    // GGX peak is D ~ 1/(pi * alpha^2) = 199, so the specular term swamps the
    // diffuse term this test is about and the metal legitimately comes out far
    // brighter -- 39 vs 0.30.  Fully rough surfaces have no peak to hide behind.
    PbrMaterial roughPlastic = dielectric; roughPlastic.roughness = 1.0f;
    PbrMaterial roughMetal   = metal;      roughMetal.roughness   = 1.0f;
    roughMetal.albedo = Vec3{0.8f, 0.2f, 0.2f};
    const Vec3 litPlastic = shadeDirect(roughPlastic, N, V, Vec3{0,1,0}, Vec3{1,1,1});
    const Vec3 litMetal   = shadeDirect(roughMetal,   N, V, Vec3{0,1,0}, Vec3{1,1,1});
    check(litPlastic.y > litMetal.y,
          "metal suppresses the diffuse lobe (green channel is lower)");

    // --- attenuation: zero beyond range, so light culling is correct.
    check(attenuation(20.0f, 10.0f) == 0.0f, "attenuation is exactly zero past range");
    check(attenuation(1.0f, 10.0f) > attenuation(5.0f, 10.0f),
          "attenuation falls with distance");

    // --- spot cone: full inside, zero outside, smooth between.
    LightDesc spot;
    spot.kind = LightKind::Spot;
    spot.direction = Vec3{0,-1,0};
    spot.innerConeDeg = 20.0f; spot.outerConeDeg = 30.0f;
    check(nearEq(spotFalloff(spot, Vec3{0,-1,0}), 1.0f), "spot is full intensity on axis");
    const f32 outside = std::cos(45.0f * kDeg2Rad);
    check(spotFalloff(spot, Vec3{outside,-outside,0}) == 0.0f,
          "spot is zero outside the outer cone");
    // A direction 25 degrees off the -Y axis is (sin 25, -cos 25, 0).  Using
    // (cos 25, -cos 25, 0) instead -- equal components -- points 45 degrees off
    // axis, which is outside the outer cone and correctly returns exactly zero.
    const f32 off = 25.0f * kDeg2Rad;
    const f32 between = spotFalloff(spot, Vec3{ std::sin(off), -std::cos(off), 0.0f });
    check(between > 0.0f && between < 1.0f, "spot falls off smoothly between the cones");

    // --- energy conservation: the BRDF must not create light.  A perfect white
    // diffuse surface lit by unit radiance cannot exceed 1/pi per channel.
    PbrMaterial white = dielectric;
    white.albedo = Vec3{1,1,1}; white.metallic = 0.0f; white.roughness = 1.0f;
    const Vec3 diffuse = shadeDirect(white, N, V, Vec3{0,1,0}, Vec3{1,1,1});
    check(diffuse.x <= 1.0f / kPi + 1e-4f,
          "a white diffuse surface stays within the Lambert limit");

    // --- reciprocity: swapping V and L leaves the BRDF unchanged.
    const Vec3 L1 = Vec3{0.5f, 1.0f, 0.2f}.normalized();
    const Vec3 V1 = Vec3{-0.3f, 1.0f, 0.4f}.normalized();
    const Vec3 a = shadeDirect(dielectric, N, V1, L1, Vec3{1,1,1});
    const Vec3 b = shadeDirect(dielectric, N, L1, V1, Vec3{1,1,1});
    // Both include the same nDotL cosine, so compare the BRDF with it divided out.
    const f32 cos1 = N.dot(L1), cos2 = N.dot(V1);
    check(nearEq(a.x / cos1, b.x / cos2, 1e-3f), "the BRDF is reciprocal (Helmholtz)");

    // --- env BRDF: scale must be positive and bias non-positive.
    const Vec2 eb = envBrdf(0.5f, 0.4f);
    check(eb.x > 0.0f && eb.y <= 0.0f, "environment BRDF scale/bias are in range");

    // --- tone mapping is monotonic and stays inside [0,1).
    const Vec3 dim  = toDisplay(Vec3{0.2f, 0.2f, 0.2f});
    const Vec3 mid  = toDisplay(Vec3{1.0f, 1.0f, 1.0f});
    const Vec3 hot  = toDisplay(Vec3{100.0f, 100.0f, 100.0f});
    check(dim.x < mid.x && mid.x < hot.x, "tone mapping is monotonic");
    check(hot.x < 1.0f, "tone mapping never exceeds 1");
    check(nearEq(toDisplay(Vec3{0,0,0}).x, 0.0f), "black maps to black");
}

// ---------------------------------------------------------------------------
void testShadowCascades() {
    section("Shadow cascades: splits, matrices, stability");
    using namespace se;

    // --- splits span [near, far] exactly and ascend monotonically.
    const f32 zn = 0.1f, zf = 200.0f;
    const ShadowSplitPlan plan = computeSplits(zn, zf, 0.5f, 3);
    check(plan.cascades == 3, "three cascades");
    check(nearEq(plan.boundaries[0], zn), "split 0 is the near plane");
    check(nearEq(plan.boundaries[3], zf), "the last split is the far plane");
    bool ascending = true;
    for (int i = 1; i <= plan.cascades; ++i)
        if (!(plan.boundaries[i] > plan.boundaries[i-1])) ascending = false;
    check(ascending, "split boundaries ascend strictly");

    // --- lambda skews resolution toward the near field.  Logarithmic splits put
    // the near boundaries CLOSE together, so a higher lambda yields a THINNER
    // first slice -- less depth per cascade is precisely what buys finer texels
    // where the player is looking.  Measured: 4.43 units at lambda 0.95 versus
    // 63.36 at lambda 0.05.
    const ShadowSplitPlan logPlan  = computeSplits(zn, zf, 0.95f, 3);
    const ShadowSplitPlan uniPlan  = computeSplits(zn, zf, 0.05f, 3);
    check(logPlan.boundaries[1] < uniPlan.boundaries[1],
          "a higher lambda gives the near cascade a thinner slice (finer texels)");

    // --- degenerate input must not produce NaN or a reversed plan.
    const ShadowSplitPlan degen = computeSplits(5.0f, 5.0f, 0.5f, 3);
    bool finitePlan = true;
    for (int i = 0; i <= 3; ++i) if (!std::isfinite(degen.boundaries[i])) finitePlan = false;
    check(finitePlan, "a degenerate near==far plan stays finite");

    // --- build a real camera and invert its view-projection.  A perspective
    // projection is not a rigid transform, so invertRigid() would be wrong here;
    // this is exactly why Mat4::inverse() exists.
    const Mat4 view = Mat4::lookAt(Vec3{0,2,8}, Vec3{0,0,0}, Vec3{0,1,0});
    const Mat4 proj = Mat4::perspective(60.0f, 16.0f/9.0f, zn, zf);
    const Mat4 viewProj = proj * view;

    Mat4 viewProjInv;
    check(Mat4::inverse(viewProj, viewProjInv), "the view-projection matrix inverts");

    // --- M * M^-1 == I, which is the property the unprojection depends on.
    const Mat4 id = viewProj * viewProjInv;
    bool identity = true;
    for (int r = 0; r < 4; ++r)
        for (int c = 0; c < 4; ++c) {
            const f32 want = (r == c) ? 1.0f : 0.0f;
            if (!nearEq(id.at(r,c), want, 1e-3f)) identity = false;
        }
    check(identity, "M * M^-1 is the identity");

    // --- a singular matrix must be reported, not silently inverted.
    Mat4 singular{};
    for (int i = 0; i < 16; ++i) singular.m[i] = 0.0f;
    Mat4 unused;
    check(!Mat4::inverse(singular, unused), "a singular matrix is refused");

    // --- unprojected frustum corners must round-trip back to the NDC cube.
    const FrustumCorners corners = frustumSliceCorners(viewProjInv, -1.0f, 1.0f);
    bool inCube = true, spread = false;
    Vec3 lo{ 1e30f,1e30f,1e30f }, hi{ -1e30f,-1e30f,-1e30f };
    for (int i = 0; i < 8; ++i) {
        const Vec4 c = viewProj * Vec4(corners.p[i], 1.0f);
        const f32 invW = std::fabs(c.w) > 1e-6f ? 1.0f / c.w : 0.0f;
        const f32 nx = c.x*invW, ny = c.y*invW;
        if (nx < -1.05f || nx > 1.05f || ny < -1.05f || ny > 1.05f) inCube = false;
        lo.x = std::min(lo.x, corners.p[i].x); hi.x = std::max(hi.x, corners.p[i].x);
        lo.y = std::min(lo.y, corners.p[i].y); hi.y = std::max(hi.y, corners.p[i].y);
        lo.z = std::min(lo.z, corners.p[i].z); hi.z = std::max(hi.z, corners.p[i].z);
    }
    check(inCube, "unprojected corners stay inside the NDC cube");
    spread = (hi.x - lo.x) > 1.0f && (hi.z - lo.z) > 1.0f;
    check(spread, "the frustum has real volume");

    // --- the near slice must be smaller than the far slice: that is the entire
    // point of cascading.
    const FrustumCorners nearSlice =
        frustumSliceCorners(viewProjInv, viewDepthToNdc(zn, zn, zf), viewDepthToNdc(5.0f, zn, zf));
    const FrustumCorners farSlice =
        frustumSliceCorners(viewProjInv, viewDepthToNdc(100.0f, zn, zf), viewDepthToNdc(zf, zn, zf));
    const CascadeMatrices nearC = buildCascade(nearSlice, Vec3{-0.4f,-1.0f,-0.2f});
    const CascadeMatrices farC  = buildCascade(farSlice,  Vec3{-0.4f,-1.0f,-0.2f});
    check(nearC.sphereRadius < farC.sphereRadius,
          "the near cascade covers a smaller volume than the far one");
    check(nearC.texelSize < farC.texelSize,
          "the near cascade gets finer texels (sharper shadows)");
    check(nearC.texelSize > 0.0f && farC.texelSize > 0.0f, "texel sizes are positive");

    // --- STABILITY.  Nudging the camera must not move the shadow map by a
    // fraction of a texel, or every shadow edge crawls.  This is the property
    // most shadow implementations get wrong.
    const Mat4 viewA = Mat4::lookAt(Vec3{0,2,8}, Vec3{0,0,0}, Vec3{0,1,0});
    const Mat4 viewB = Mat4::lookAt(Vec3{0.01f,2.01f,8.01f}, Vec3{0,0,0}, Vec3{0,1,0});
    Mat4 invA, invB;
    Mat4::inverse(proj * viewA, invA);
    Mat4::inverse(proj * viewB, invB);
    const FrustumCorners cA = frustumSliceCorners(invA, -1.0f, 1.0f);
    const FrustumCorners cB = frustumSliceCorners(invB, -1.0f, 1.0f);
    const CascadeMatrices stableA = buildCascade(cA, Vec3{-0.4f,-1.0f,-0.2f}, true);
    const CascadeMatrices stableB = buildCascade(cB, Vec3{-0.4f,-1.0f,-0.2f}, true);
    bool projUnchanged = true;
    for (int i = 0; i < 16; ++i)
        if (!nearEq(stableA.lightProj.m[i], stableB.lightProj.m[i], 1e-3f)) projUnchanged = false;
    check(projUnchanged,
          "stabilisation keeps the projection fixed under a sub-texel camera move");

    // --- cascade selection is monotonic in depth.
    bool monotonic = true;
    int prev = -1;
    for (f32 d = zn; d <= zf; d += 1.0f) {
        const int sel = selectCascade(d, plan);
        if (sel < prev) monotonic = false;
        prev = sel;
    }
    check(monotonic, "cascade selection never goes backwards as depth increases");
    check(selectCascade(zn, plan) == 0, "the near plane selects cascade 0");
    check(selectCascade(zf, plan) == plan.cascades - 1,
          "the far plane selects the last cascade");
    check(selectCascade(zf * 10.0f, plan) == plan.cascades - 1,
          "beyond the far plane still selects the last cascade, not nothing");

    // --- buildAllCascades fills every slot.
    CascadeMatrices all[kShadowCascades];
    buildAllCascades(viewProj, viewProjInv, zn, zf, Vec3{-0.4f,-1.0f,-0.2f}, plan, all);
    bool allValid = true;
    for (int i = 0; i < plan.cascades; ++i)
        if (!(all[i].sphereRadius > 0.0f) || !(all[i].texelSize > 0.0f)) allValid = false;
    check(allValid, "buildAllCascades produces a valid matrix set");
}

// ---------------------------------------------------------------------------
void testPhysicsBasics() {
    section("Physics: integration and gravity");
    using namespace se;

    PhysicsWorld w(Vec3{0, -9.81f, 0});

    RigidBody b;
    b.shape.kind = ShapeKind::Sphere;
    b.shape.radius = 0.5f;
    b.mass = 1.0f;
    b.position = Vec3{0, 10, 0};
    b.linearDamping = 0.0f;
    const u32 id = w.createBody(b);

    // --- free fall: v = g*t, s = 0.5*g*t^2.
    //
    // Stepped as 60 individual calls rather than step(1.0f).  A single call is
    // capped at 8 fixed substeps (0.1333 s) so that one long frame cannot send
    // the solver into a spiral, so step(1.0f) would simulate an eighth of a
    // second and silently drop the rest.
    for (int i = 0; i < 60; ++i) w.step(1.0f / 60.0f);
    const RigidBody* after = w.body(id);
    check(nearEq(after->linearVelocity.y, -9.81f, 0.05f),
          "one second of free fall reaches -9.81 m/s");
    // Semi-implicit Euler integrates velocity before position, so it lands
    // slightly short of the analytic 4.905 m; the tolerance covers that.
    check(nearEq(after->position.y, 10.0f - 0.5f * 9.81f, 0.15f),
          "one second of free fall drops ~4.9 m");

    // --- gravityScale 0 means no fall (the trick for floating props).
    PhysicsWorld w2(Vec3{0, -9.81f, 0});
    RigidBody f = b; f.gravityScale = 0.0f; f.linearVelocity = Vec3{0,0,0};
    const u32 fid = w2.createBody(f);
    for (int i = 0; i < 30; ++i) w2.step(1.0f / 60.0f);
    check(nearEq(w2.body(fid)->linearVelocity.y, 0.0f, 1e-4f),
          "gravityScale 0 does not fall");

    // --- static bodies never move.
    PhysicsWorld w3;
    RigidBody st = b; st.type = BodyType::Static;
    const u32 sid = w3.createBody(st);
    const Vec3 before = w3.body(sid)->position;
    w3.step(1.0f);
    check(w3.body(sid)->position == before, "a static body does not move");
    check(w3.body(sid)->invMass == 0.0f, "a static body has zero inverse mass");

    // --- kinematic bodies follow targetVelocity and ignore gravity.
    PhysicsWorld w4;
    RigidBody k = b; k.type = BodyType::Kinematic; k.targetVelocity = Vec3{2, 0, 0};
    k.position = Vec3{0,0,0};
    const u32 kid = w4.createBody(k);
    for (int i = 0; i < 30; ++i) w4.step(1.0f / 60.0f);
    check(nearEq(w4.body(kid)->position.x, 1.0f, 0.05f),
          "a kinematic body follows its target velocity");
    check(nearEq(w4.body(kid)->linearVelocity.y, 0.0f, 1e-4f),
          "a kinematic body ignores gravity");

    // --- damping must not reverse velocity.  `v *= (1 - damping*h)` goes
    // negative once damping*h > 1, which would fling the body backwards.
    PhysicsWorld w5(Vec3{0,0,0});
    RigidBody d = b; d.linearDamping = 50.0f; d.linearVelocity = Vec3{10, 0, 0};
    d.position = Vec3{0,0,0};
    const u32 did = w5.createBody(d);
    w5.step(0.1f);
    const f32 vx = w5.body(did)->linearVelocity.x;
    check(vx >= 0.0f, "heavy damping never reverses velocity");
    check(vx < 10.0f, "heavy damping actually slows the body");

    // --- mass changes must be picked up after refreshMass().
    PhysicsWorld w6(Vec3{0,0,0});
    RigidBody m = b; m.mass = 1.0f;
    const u32 mid = w6.createBody(m);
    check(nearEq(w6.body(mid)->invMass, 1.0f), "invMass is 1 for mass 1");
    w6.body(mid)->mass = 4.0f;
    check(nearEq(w6.body(mid)->invMass, 1.0f),
          "invMass is stale until refreshMass (documented behaviour)");
    w6.refreshMass(mid);
    check(nearEq(w6.body(mid)->invMass, 0.25f), "refreshMass recomputes invMass");
}

// ---------------------------------------------------------------------------
void testPhysicsCollision() {
    section("Physics: collision response");
    using namespace se;

    // --- momentum is conserved in a collision with no external forces.
    PhysicsWorld w(Vec3{0, 0, 0});
    RigidBody a; a.shape.kind = ShapeKind::Sphere; a.shape.radius = 0.5f;
    a.mass = 1.0f; a.position = Vec3{-2, 0, 0}; a.linearVelocity = Vec3{4, 0, 0};
    a.linearDamping = 0.0f; a.restitution = 0.0f; a.friction = 0.0f;
    RigidBody b = a;
    b.position = Vec3{2, 0, 0}; b.linearVelocity = Vec3{-4, 0, 0};
    const u32 ia = w.createBody(a), ib = w.createBody(b);

    const Vec3 p0 = w.totalMomentum();
    for (int i = 0; i < 60; ++i) w.step(1.0f / 60.0f);
    const Vec3 p1 = w.totalMomentum();
    check(nearEq(p0.x, p1.x, 0.05f) && nearEq(p0.y, p1.y, 0.05f),
          "linear momentum is conserved through the collision");

    // --- equal masses, equal and opposite, inelastic: both should stop.
    check(std::fabs(w.body(ia)->linearVelocity.x) < 0.6f &&
          std::fabs(w.body(ib)->linearVelocity.x) < 0.6f,
          "a perfectly inelastic head-on collision between equals stops both");

    // --- restitution: a bouncy ball leaves faster than a dead one.
    auto drop = [](f32 e) {
        PhysicsWorld ww(Vec3{0, -9.81f, 0});
        RigidBody ball; ball.shape.kind = ShapeKind::Sphere; ball.shape.radius = 0.5f;
        ball.mass = 1.0f; ball.position = Vec3{0, 3, 0};
        ball.linearDamping = 0.0f; ball.restitution = e; ball.friction = 0.0f;
        ww.createBody(ball);
        RigidBody floor; floor.shape.kind = ShapeKind::Box;
        floor.shape.halfExtents = Vec3{20, 0.5f, 20};
        floor.type = BodyType::Static; floor.position = Vec3{0, -0.5f, 0};
        floor.restitution = e;
        ww.createBody(floor);
        f32 peak = 0.0f;
        for (int i = 0; i < 120; ++i) {
            ww.step(1.0f / 60.0f);
            const f32 vy = ww.body(0)->linearVelocity.y;
            if (vy > peak) peak = vy;          // peak upward speed after the bounce
        }
        return peak;
    };
    const f32 deadVy = drop(0.0f);
    const f32 bouncyVy = drop(0.8f);
    check(bouncyVy > deadVy + 1.0f, "a higher restitution leaves the ground faster");
    check(deadVy < 0.05f, "restitution 0 does not bounce");

    // --- a resting body must not jitter.  This is the failure that shows up as
    // a crate buzzing on the floor, and it is why kLinearSlop exists.
    PhysicsWorld wr(Vec3{0, -9.81f, 0});
    RigidBody crate; crate.shape.kind = ShapeKind::Box;
    crate.shape.halfExtents = Vec3{0.5f, 0.5f, 0.5f};
    crate.mass = 1.0f; crate.position = Vec3{0, 1.0f, 0};
    crate.linearDamping = 0.1f; crate.friction = 0.6f; crate.restitution = 0.0f;
    const u32 ci = wr.createBody(crate);
    RigidBody gnd; gnd.shape.kind = ShapeKind::Box;
    gnd.shape.halfExtents = Vec3{20, 0.5f, 20};
    gnd.type = BodyType::Static; gnd.position = Vec3{0, -0.5f, 0};
    wr.createBody(gnd);
    for (int i = 0; i < 180; ++i) wr.step(1.0f / 60.0f);   // three seconds
    const Vec3 settled = wr.body(ci)->position;
    f32 maxDrift = 0.0f;
    for (int i = 0; i < 60; ++i) {
        wr.step(1.0f / 60.0f);
        const f32 d = std::fabs(wr.body(ci)->position.y - settled.y);
        if (d > maxDrift) maxDrift = d;
    }
    check(maxDrift < 0.02f, "a settled crate does not jitter");
    check(settled.y > 0.4f && settled.y < 0.7f,
          "the crate rests on the floor rather than sinking through it");

    // --- a stack must not sink.  Velocity-level correction alone lets tall
    // stacks slowly collapse, which is what the position pass prevents.
    PhysicsWorld ws(Vec3{0, -9.81f, 0});
    RigidBody g2; g2.shape.kind = ShapeKind::Box;
    g2.shape.halfExtents = Vec3{10, 0.5f, 10};
    g2.type = BodyType::Static; g2.position = Vec3{0, -0.5f, 0};
    ws.createBody(g2);
    for (int i = 0; i < 4; ++i) {
        RigidBody c2; c2.shape.kind = ShapeKind::Box;
        c2.shape.halfExtents = Vec3{0.5f, 0.5f, 0.5f};
        c2.mass = 1.0f; c2.friction = 0.8f; c2.restitution = 0.0f;
        c2.linearDamping = 0.2f;
        c2.position = Vec3{0, 0.6f + i * 1.05f, 0};
        ws.createBody(c2);
    }
    for (int i = 0; i < 300; ++i) ws.step(1.0f / 60.0f);   // five seconds
    const f32 topY = ws.body(4)->position.y;
    check(topY > 2.5f, "the top of a 4-crate stack stays up (no sinking)");

    // --- overlap query without stepping.
    PhysicsWorld wo(Vec3{0,0,0});
    RigidBody s1; s1.shape.kind = ShapeKind::Sphere; s1.shape.radius = 0.5f;
    s1.position = Vec3{0,0,0};
    RigidBody s2 = s1; s2.position = Vec3{0.6f, 0, 0};
    RigidBody s3 = s1; s3.position = Vec3{5.0f, 0, 0};
    const u32 q1 = wo.createBody(s1), q2 = wo.createBody(s2), q3 = wo.createBody(s3);
    check(wo.overlaps(q1, q2), "overlapping spheres report overlap");
    check(!wo.overlaps(q1, q3), "separated spheres do not");
}

// ---------------------------------------------------------------------------
void testPhysicsRaycastAndJoints() {
    section("Physics: raycasting and joints");
    using namespace se;

    PhysicsWorld w(Vec3{0,0,0});
    RigidBody sph; sph.shape.kind = ShapeKind::Sphere; sph.shape.radius = 1.0f;
    sph.position = Vec3{0, 0, -5}; sph.mass = 1.0f;
    const u32 sid = w.createBody(sph);

    RigidBody box; box.shape.kind = ShapeKind::Box;
    box.shape.halfExtents = Vec3{1, 1, 1};
    box.position = Vec3{0, 0, 5}; box.mass = 1.0f;
    const u32 bid = w.createBody(box);

    // --- ray hits the sphere at the right distance (5 - radius = 4).
    const RayHit h1 = w.raycast(Vec3{0,0,0}, Vec3{0,0,-1}, 100.0f);
    check(h1.hit, "a ray toward the sphere hits");
    check(nearEq(h1.distance, 4.0f, 0.01f), "the sphere is hit at distance 4");
    check(h1.body == sid, "the hit reports the right body");
    check(nearEq(h1.normal.z, 1.0f, 0.01f), "the sphere normal faces the ray origin");

    // --- a ray the other way hits the box, and reports the nearer face.
    const RayHit h2 = w.raycast(Vec3{0,0,0}, Vec3{0,0,1}, 100.0f);
    check(h2.hit && h2.body == bid, "a ray toward the box hits the box");
    check(nearEq(h2.distance, 4.0f, 0.01f), "the box face is hit at distance 4");

    // --- maxDistance is respected.
    const RayHit h3 = w.raycast(Vec3{0,0,0}, Vec3{0,0,-1}, 2.0f);
    check(!h3.hit, "a ray shorter than the distance does not hit");

    // --- a ray that misses hits nothing.
    const RayHit h4 = w.raycast(Vec3{0,0,0}, Vec3{1,0,0}, 100.0f);
    check(!h4.hit, "a ray perpendicular to both bodies misses");

    // --- a ray from inside the sphere exits rather than reporting distance 0.
    const RayHit h5 = w.raycast(Vec3{0,0,-5}, Vec3{0,0,-1}, 100.0f);
    check(h5.hit && h5.distance > 0.5f, "a ray from inside a sphere reports the exit");

    // --- distance joint holds two bodies a fixed separation under load.
    PhysicsWorld wj(Vec3{0, -9.81f, 0});
    RigidBody anchor; anchor.shape.kind = ShapeKind::Sphere; anchor.shape.radius = 0.2f;
    anchor.type = BodyType::Static; anchor.position = Vec3{0, 5, 0};
    const u32 ja = wj.createBody(anchor);

    RigidBody weight; weight.shape.kind = ShapeKind::Sphere; weight.shape.radius = 0.3f;
    weight.mass = 2.0f; weight.position = Vec3{0, 3, 0};
    weight.linearDamping = 0.4f;
    const u32 jb = wj.createBody(weight);

    Joint j;
    j.kind = JointKind::Distance;
    j.a = ja; j.b = jb;
    j.length = 2.0f;
    j.stiffness = 0.0f;                 // rigid
    const u32 ji = wj.createJoint(j);

    for (int i = 0; i < 300; ++i) wj.step(1.0f / 60.0f);   // five seconds of gravity
    const Vec3 pa = wj.body(ja)->position;
    const Vec3 pb = wj.body(jb)->position;
    const f32 sep = (pb - pa).length();
    check(std::fabs(sep - 2.0f) < 0.25f,
          "a rigid distance joint holds its length against gravity");
    check(wj.joint(ji) != nullptr, "the joint handle stays valid");

    // --- a spring joint oscillates rather than locking, which is how you tell a
    // spring from a rigid constraint.
    PhysicsWorld wk(Vec3{0, -9.81f, 0});
    RigidBody a2 = anchor; const u32 ka = wk.createBody(a2);
    RigidBody w2 = weight; w2.linearDamping = 0.0f;
    w2.position = Vec3{0, 3, 0};
    const u32 kb = wk.createBody(w2);
    Joint spring;
    spring.kind = JointKind::Distance;
    spring.a = ka; spring.b = kb;
    spring.length = 2.0f;
    spring.stiffness = 60.0f;
    spring.damping = 0.5f;
    wk.createJoint(spring);
    f32 minY = 1e30f, maxY = -1e30f;
    for (int i = 0; i < 240; ++i) {
        wk.step(1.0f / 60.0f);
        const f32 y = wk.body(kb)->position.y;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }
    check(maxY - minY > 0.1f, "a spring joint oscillates instead of locking");
    check(minY > -50.0f, "a spring joint does not diverge");

    // --- point-to-point welds two anchors together.
    PhysicsWorld wp(Vec3{0, -9.81f, 0});
    RigidBody base = anchor; const u32 p1 = wp.createBody(base);
    RigidBody hang = weight; hang.position = Vec3{1.0f, 4.0f, 0};
    hang.linearDamping = 0.5f;
    const u32 p2 = wp.createBody(hang);
    Joint weld;
    weld.kind = JointKind::PointToPoint;
    weld.a = p1; weld.b = p2;
    weld.localAnchorA = Vec3{1.0f, -1.0f, 0};
    weld.localAnchorB = Vec3{0, 0, 0};
    wp.createJoint(weld);
    for (int i = 0; i < 300; ++i) wp.step(1.0f / 60.0f);
    const Vec3 wa = wp.body(p1)->position + Vec3{1.0f, -1.0f, 0};
    const Vec3 wb = wp.body(p2)->position;
    check((wb - wa).length() < 0.35f, "a point-to-point joint welds its anchors");
}

} // namespace

int main() {
    std::printf("S Engine 2.0 -- native core test suite\n");

    testLinearAllocator();
    testPoolAllocator();
    testBucketAllocator();
    testFrameStack();
    testHandles();
    testInterner();
    testEcsBasics();
    testEcsArchetypes();
    testEcsIteration();
    testCommandBuffer();
    testSceneGraph();
    testMath();
    testNullRHI();
    testPbr();
    testShadowCascades();
    testPhysicsBasics();
    testPhysicsCollision();
    testPhysicsRaycastAndJoints();

    std::printf("\n---------------------------------------------\n");
    std::printf("%d checks, %d failures\n", g_checks, g_failures);
    std::printf(g_failures == 0 ? "RESULT: PASS\n" : "RESULT: FAIL\n");
    return g_failures == 0 ? 0 : 1;
}
