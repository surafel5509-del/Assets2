// SPDX-License-Identifier: MIT
// S Engine 2.0 -- scene graph (parent/child transform hierarchy).
//
// The ECS owns components; the scene graph owns *relationships*.  A node holds
// a local TRS and a cached world matrix.  Touching a node's local transform
// marks its whole subtree dirty, and update() recomputes only dirty subtrees,
// so moving one leaf in a 5000-node scene costs one matrix.
//
// Nodes live in a flat vector with a free list; indices are stable for the life
// of the graph, which is what the editor's Hierarchy panel and undo stack need
// (they reference nodes by id, not by pointer).
#pragma once

#include "../core/Hash.h"
#include "../core/Types.h"
#include "../ecs/World.h"
#include "../math/Math.h"

#include <vector>

namespace se {

using NodeId = u32;
inline constexpr NodeId kNullNode = kInvalidIndex;

struct SceneNode {
    InternId name = kInvalidIntern;
    Entity   entity;
    NodeId   parent   = kNullNode;
    std::vector<NodeId> children;
    Vec3     localPos{ 0, 0, 0 };
    Quat     localRot = Quat::identity();
    Vec3     localScale{ 1, 1, 1 };
    Mat4     local = Mat4::identity();
    Mat4     world = Mat4::identity();
    u32      order = 0;          // sibling draw order (2D sorting)
    u16      layer = 0;          // render layer / camera cull mask bit
    bool     active = true;      // self-active; effective active ANDs ancestors
    bool     dirty  = true;
    bool     alive  = false;     // slot occupancy, distinct from `active`
};

class SceneGraph : NonCopyable {
public:
    SceneGraph() = default;

    /// Creates a node parented to `parent` (kNullNode for a root).
    NodeId create(const char* name, NodeId parent = kNullNode);
    void   destroy(NodeId n);
    bool   valid(NodeId n) const;

    SceneNode*       node(NodeId n)       { return valid(n) ? &m_nodes[n] : nullptr; }
    const SceneNode* node(NodeId n) const { return valid(n) ? &m_nodes[n] : nullptr; }

    // ---- hierarchy --------------------------------------------------------
    /// Reparents `child` under `newParent`, preserving its *world* transform
    /// when `keepWorld` is set (what the editor's drag-to-reparent wants).
    bool setParent(NodeId child, NodeId newParent, bool keepWorld = true);
    NodeId parentOf(NodeId n) const;
    const std::vector<NodeId>& childrenOf(NodeId n) const;
    bool isAncestor(NodeId maybeAncestor, NodeId n) const;
    u64  depth(NodeId n) const;
    void roots(std::vector<NodeId>& out) const;
    u64  nodeCount() const { return m_live; }

    // ---- transforms -------------------------------------------------------
    void setPosition(NodeId n, Vec3 p);
    void setRotation(NodeId n, const Quat& r);
    void setScale(NodeId n, Vec3 s);
    void setLocalTRS(NodeId n, Vec3 p, const Quat& r, Vec3 s);
    void setWorldPosition(NodeId n, Vec3 worldPos);

    Vec3 worldPosition(NodeId n);
    Quat worldRotation(NodeId n);
    Vec3 worldScale(NodeId n);
    const Mat4& worldMatrix(NodeId n);
    Vec3 forward(NodeId n);      // -Z in view convention
    Vec3 right(NodeId n);
    Vec3 up(NodeId n);

    /// Recomputes dirty subtrees.  Must run after systems write transforms and
    /// before culling/rendering reads world matrices.
    void update();

    /// True when the node and all its ancestors are active.
    bool activeInHierarchy(NodeId n) const;

    /// Depth-first walk; the callback returns false to prune the subtree.
    template <typename Fn>
    void walk(NodeId root, Fn&& fn) {
        if (root != kNullNode && !valid(root)) return;
        walkImpl(root, fn);
    }

private:
    template <typename Fn>
    void walkImpl(NodeId n, Fn& fn) {
        if (n != kNullNode) {
            if (!fn(n)) return;
            const auto kids = m_nodes[n].children;   // copy: fn may reparent
            for (NodeId c : kids) walkImpl(c, fn);
        } else {
            const auto rs = m_roots;
            for (NodeId r : rs) walkImpl(r, fn);
        }
    }

    void markDirty(NodeId n);
    void updateSubtree(NodeId n);
    void detach(NodeId child);

    std::vector<SceneNode>      m_nodes;
    std::vector<NodeId>         m_freeList;
    std::vector<NodeId>         m_roots;
    StringInterner              m_names;
    u64 m_live = 0;

public:
    StringInterner& names() { return m_names; }
    const char* nameOf(NodeId n) const {
        return valid(n) ? m_names.resolve(m_nodes[n].name) : nullptr;
    }
};

} // namespace se
