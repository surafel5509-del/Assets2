// SPDX-License-Identifier: MIT
#include "SceneGraph.h"

#include <algorithm>

namespace se {

bool SceneGraph::valid(NodeId n) const {
    return n != kNullNode && n < m_nodes.size() && m_nodes[n].alive;
}

NodeId SceneGraph::create(const char* name, NodeId parent) {
    NodeId id;
    if (!m_freeList.empty()) {
        id = m_freeList.back();
        m_freeList.pop_back();
        m_nodes[id] = SceneNode{};
    } else {
        id = (NodeId)m_nodes.size();
        m_nodes.push_back(SceneNode{});
    }
    SceneNode& n = m_nodes[id];
    n.name = name ? m_names.intern(name) : kInvalidIntern;
    n.dirty = true;
    n.alive = true;
    ++m_live;

    if (parent == kNullNode) {
        m_roots.push_back(id);
    } else {
        SE_ASSERT(valid(parent));
        n.parent = parent;
        m_nodes[parent].children.push_back(id);
    }
    markDirty(id);
    return id;
}

void SceneGraph::destroy(NodeId n) {
    if (!valid(n)) return;
    // Children are destroyed first: a dangling parent pointer in the editor's
    // Hierarchy is worse than an explicit cascade.
    const auto kids = m_nodes[n].children;
    for (NodeId c : kids) destroy(c);

    detach(n);
    SceneNode& node = m_nodes[n];
    node.alive = false;
    node.entity = kNullEntity;
    node.children.clear();
    m_freeList.push_back(n);
    --m_live;
}

void SceneGraph::detach(NodeId child) {
    SceneNode& c = m_nodes[child];
    if (c.parent == kNullNode) {
        m_roots.erase(std::remove(m_roots.begin(), m_roots.end(), child), m_roots.end());
    } else {
        auto& sibs = m_nodes[c.parent].children;
        sibs.erase(std::remove(sibs.begin(), sibs.end(), child), sibs.end());
    }
    c.parent = kNullNode;
}

bool SceneGraph::setParent(NodeId child, NodeId newParent, bool keepWorld) {
    if (!valid(child) || child == newParent) return false;
    if (newParent != kNullNode && (!valid(newParent) || isAncestor(child, newParent)))
        return false;   // would create a cycle

    const Mat4 oldWorld = keepWorld ? worldMatrix(child) : Mat4::identity();
    detach(child);

    SceneNode& c = m_nodes[child];
    if (newParent == kNullNode) {
        m_roots.push_back(child);
    } else {
        c.parent = newParent;
        m_nodes[newParent].children.push_back(child);
    }

    if (keepWorld) {
        // Re-express the preserved world transform relative to the new parent.
        Mat4 parentWorld = newParent == kNullNode ? Mat4::identity() : worldMatrix(newParent);
        Mat4 inv = invertRigid(parentWorld);
        const Mat4 local = inv * oldWorld;
        decomposeRigid(local, c.localPos, c.localRot, c.localScale);
    }
    markDirty(child);
    return true;
}

NodeId SceneGraph::parentOf(NodeId n) const { return valid(n) ? m_nodes[n].parent : kNullNode; }

const std::vector<NodeId>& SceneGraph::childrenOf(NodeId n) const {
    static const std::vector<NodeId> kEmpty;
    return valid(n) ? m_nodes[n].children : kEmpty;
}

bool SceneGraph::isAncestor(NodeId maybeAncestor, NodeId n) const {
    NodeId cur = valid(n) ? m_nodes[n].parent : kNullNode;
    while (cur != kNullNode) {
        if (cur == maybeAncestor) return true;
        cur = m_nodes[cur].parent;
    }
    return false;
}

u64 SceneGraph::depth(NodeId n) const {
    u64 d = 0;
    NodeId cur = valid(n) ? m_nodes[n].parent : kNullNode;
    while (cur != kNullNode) { ++d; cur = m_nodes[cur].parent; }
    return d;
}

void SceneGraph::roots(std::vector<NodeId>& out) const { out = m_roots; }

// ---------------------------------------------------------------------------
void SceneGraph::setPosition(NodeId n, Vec3 p) {
    if (!valid(n)) return;
    m_nodes[n].localPos = p;
    markDirty(n);
}

void SceneGraph::setRotation(NodeId n, const Quat& r) {
    if (!valid(n)) return;
    m_nodes[n].localRot = r;
    markDirty(n);
}

void SceneGraph::setScale(NodeId n, Vec3 s) {
    if (!valid(n)) return;
    m_nodes[n].localScale = s;
    markDirty(n);
}

void SceneGraph::setLocalTRS(NodeId n, Vec3 p, const Quat& r, Vec3 s) {
    if (!valid(n)) return;
    SceneNode& node = m_nodes[n];
    node.localPos = p; node.localRot = r; node.localScale = s;
    markDirty(n);
}

void SceneGraph::setWorldPosition(NodeId n, Vec3 worldPos) {
    if (!valid(n)) return;
    const NodeId p = m_nodes[n].parent;
    if (p == kNullNode) { setPosition(n, worldPos); return; }
    setPosition(n, invertRigid(worldMatrix(p)).transformPoint(worldPos));
}

Vec3 SceneGraph::worldPosition(NodeId n) { return worldMatrix(n).transformPoint(Vec3{0,0,0}); }
Quat SceneGraph::worldRotation(NodeId n) {
    Vec3 p; Quat r; Vec3 s;
    decomposeRigid(worldMatrix(n), p, r, s);
    return r;
}
Vec3 SceneGraph::worldScale(NodeId n) {
    Vec3 p; Quat r; Vec3 s;
    decomposeRigid(worldMatrix(n), p, r, s);
    return s;
}

const Mat4& SceneGraph::worldMatrix(NodeId n) {
    SE_ASSERT(valid(n));
    if (m_nodes[n].dirty) {
        const NodeId p = m_nodes[n].parent;
        if (p != kNullNode) worldMatrix(p);   // parents first
        updateSubtree(n);
    }
    return m_nodes[n].world;
}

Vec3 SceneGraph::forward(NodeId n) { return worldMatrix(n).transformDirection(Vec3{0,0,-1}).normalized(); }
Vec3 SceneGraph::right(NodeId n)   { return worldMatrix(n).transformDirection(Vec3{1,0,0}).normalized(); }
Vec3 SceneGraph::up(NodeId n)      { return worldMatrix(n).transformDirection(Vec3{0,1,0}).normalized(); }

void SceneGraph::markDirty(NodeId n) {
    if (!valid(n) || m_nodes[n].dirty) return;
    m_nodes[n].dirty = true;
    for (NodeId c : m_nodes[n].children) markDirty(c);
}

void SceneGraph::updateSubtree(NodeId n) {
    SceneNode& node = m_nodes[n];
    if (!node.dirty) return;
    node.local = Mat4::trs(node.localPos, node.localRot, node.localScale);
    node.world = node.parent == kNullNode ? node.local : m_nodes[node.parent].world * node.local;
    node.dirty = false;
    for (NodeId c : node.children) updateSubtree(c);
}

void SceneGraph::update() {
    for (NodeId r : m_roots) updateSubtree(r);
}

bool SceneGraph::activeInHierarchy(NodeId n) const {
    NodeId cur = n;
    while (cur != kNullNode) {
        if (!m_nodes[cur].active) return false;
        cur = m_nodes[cur].parent;
    }
    return n != kNullNode;
}

} // namespace se
