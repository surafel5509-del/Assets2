// SPDX-License-Identifier: MIT
// S Engine 2.0 -- archetypal Entity Component System.
//
// Layout
// ------
// Every distinct set of component types is an *archetype*.  Inside an archetype
// each component type owns one contiguous array (structure-of-arrays), so a
// system that touches Transform + Velocity streams two cache lines at a time
// instead of chasing pointers through a scene graph.
//
//   Archetype { Transform, Velocity }        Archetype { Transform }
//   ┌────────────────────────────┐           ┌──────────────────┐
//   │ Transform[]  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │           │ Transform[] ▓▓▓▓ │
//   │ Velocity []  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │           └──────────────────┘
//   └────────────────────────────┘
//
// Adding or removing a component *moves* the entity to another archetype.  That
// is a structural change and is illegal while a query is iterating, so systems
// queue it in a CommandBuffer that the World flushes between systems.
//
// Component ids are dense u32s from registerComponent<T>().  Components must be
// trivially relocatable (see isComponentV) because rows move with memcpy and
// never run a destructor on relocation.
#pragma once

#include "../core/Hash.h"
#include "../core/Types.h"
#include "../math/Math.h"

#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

namespace se {

// ---------------------------------------------------------------------------
struct Entity {
    u32 index      = kInvalidIndex;
    u32 generation = 0;

    bool valid() const { return index != kInvalidIndex; }
    bool operator==(const Entity& o) const { return index == o.index && generation == o.generation; }
    bool operator!=(const Entity& o) const { return !(*this == o); }
};
static_assert(sizeof(Entity) == 8, "Entity is passed by value everywhere; keep it 64 bits");

inline constexpr Entity kNullEntity{};

struct EntityHash {
    u64 operator()(const Entity& e) const {
        return hashCombine(e.index, e.generation);
    }
};

using ComponentTypeId = u32;
inline constexpr ComponentTypeId kInvalidComponent = kInvalidIndex;

struct ComponentMeta {
    InternId name = kInvalidIntern;
    u32 size  = 0;
    u32 align = 1;
};

// ---------------------------------------------------------------------------
// One archetype: a sorted component set plus its columnar storage.
// ---------------------------------------------------------------------------
class Archetype {
public:
    Archetype() = default;
    Archetype(std::vector<ComponentTypeId> types, const std::vector<ComponentMeta>& metas);
    ~Archetype();
    Archetype(Archetype&&) noexcept;
    Archetype& operator=(Archetype&&) noexcept;
    Archetype(const Archetype&) = delete;
    Archetype& operator=(const Archetype&) = delete;

    const std::vector<ComponentTypeId>& types() const { return m_types; }
    u32 count() const { return m_count; }
    u32 capacity() const { return m_capacity; }
    bool empty() const { return m_count == 0; }

    /// Index of `t` in this archetype, or kInvalidIndex when absent.
    u32 columnOf(ComponentTypeId t) const;
    bool has(ComponentTypeId t) const { return columnOf(t) != kInvalidIndex; }
    bool hasAll(const ComponentTypeId* types, u32 n) const;

    void* component(u32 column, u32 row) { return m_columns[column] + (u64)row * m_strides[column]; }
    const void* component(u32 column, u32 row) const { return m_columns[column] + (u64)row * m_strides[column]; }
    void* columnData(u32 column) { return m_columns[column]; }
    u32   stride(u32 column) const { return m_strides[column]; }

    u32 pushRow(Entity e);
    /// Removes `row` by swapping the last row into its place.  Returns the
    /// entity that moved (kNullEntity when `row` was already last).
    Entity popRow(u32 row);
    Entity entityAt(u32 row) const { return m_entities[row]; }
    const std::vector<Entity>& entities() const { return m_entities; }

    /// Copies `src`'s row into `row`, for every component both archetypes share.
    void copySharedFrom(const Archetype& src, u32 srcRow, u32 row);

    u64 memoryBytes() const { return m_bytes; }

private:
    void ensureCapacity(u32 rows);

    std::vector<ComponentTypeId> m_types;
    std::vector<u8*>             m_columns;
    std::vector<u32>             m_strides;
    std::vector<Entity>          m_entities;
    u32 m_count    = 0;
    u32 m_capacity = 0;
    u64 m_bytes    = 0;
};

// ---------------------------------------------------------------------------
struct WorldStats {
    u64 entities     = 0;
    u64 archetypes   = 0;
    u64 componentRows = 0;
    u64 archetypeBytes = 0;
    u64 structuralChanges = 0;
};

// ---------------------------------------------------------------------------
class CommandBuffer;

class World {
public:
    World();
    ~World();
    World(const World&) = delete;
    World& operator=(const World&) = delete;

    // ---- component registration ------------------------------------------
    template <typename T>
    ComponentTypeId registerComponent(const char* name) {
        static_assert(isComponentV<T>, "ECS components must be trivially relocatable");
        const InternId nid = m_interner.intern(name);
        ComponentMeta meta{ nid, (u32)sizeof(T), (u32)alignof(T) };
        m_metas.push_back(meta);
        m_metaById.insert_or_assign(nid, (ComponentTypeId)m_metas.size() - 1);
        return (ComponentTypeId)m_metas.size() - 1;
    }

    /// Registration is by name, so a component registered in two translation
    /// units under the same name gets the same id.
    template <typename T>
    ComponentTypeId componentId(const char* name) {
        const ComponentTypeId existing = componentIdByName(name);
        return existing != kInvalidComponent ? existing : registerComponent<T>(name);
    }
    ComponentTypeId componentIdByName(const char* name) const;
    const ComponentMeta* meta(ComponentTypeId t) const {
        return t < m_metas.size() ? &m_metas[t] : nullptr;
    }
    u32 componentTypeCount() const { return (u32)m_metas.size(); }

    StringInterner& interner() { return m_interner; }

    // ---- entity lifetime --------------------------------------------------
    Entity create();
    void   destroy(Entity e);
    bool   alive(Entity e) const;
    u64    entityCount() const { return m_live; }

    // ---- components -------------------------------------------------------
    /// Get-or-add.  When the entity already has the component the stored value
    /// is left untouched and the existing pointer is returned -- a system that
    /// re-adds a component every frame must not silently reset it.  Use get<T>
    /// + explicit assignment, or CommandBuffer::add, to force a value.
    template <typename T>
    T* add(Entity e, ComponentTypeId type, const T& value = T{}) {
        static_assert(isComponentV<T>, "ECS components must be trivially relocatable");
        bool created = false;
        void* p = addRaw(e, type, sizeof(T), &created);
        if (!p) return nullptr;
        if (created) new (p) T(value);
        return static_cast<T*>(p);
    }

    template <typename T>
    T* get(Entity e, ComponentTypeId type) {
        void* p = getRaw(e, type);
        return static_cast<T*>(p);
    }
    template <typename T>
    const T* get(Entity e, ComponentTypeId type) const {
        return static_cast<const T*>(const_cast<World*>(this)->getRaw(e, type));
    }

    template <typename T>
    bool has(Entity e, ComponentTypeId type) const { return getRaw(e, type) != nullptr; }

    bool remove(Entity e, ComponentTypeId type);

    /// `createdOut`, when non-null, reports whether a new component row was
    /// allocated (true) or an existing one returned (false).
    void* addRaw(Entity e, ComponentTypeId type, u32 size, bool* createdOut = nullptr);
    void* getRaw(Entity e, ComponentTypeId type) const;

    // ---- archetype queries ------------------------------------------------
    /// Archetype indices that contain every listed component.  The returned
    /// pointer stays valid until the next structural change.
    std::vector<u32> archetypesWith(const ComponentTypeId* types, u32 n) const;
    u32 archetypeCount() const { return (u32)m_archetypes.size(); }
    Archetype& archetype(u32 index) { return *m_archetypes[index]; }
    const Archetype& archetype(u32 index) const { return *m_archetypes[index]; }

    /// Convenience: (archetype, component column) pairs for one type.
    struct Column { Archetype* arch; u32 column; };
    std::vector<Column> columnsFor(ComponentTypeId type);

    // ---- deferred structural changes --------------------------------------
    CommandBuffer& commands() { return *m_commands; }
    void flushCommands();
    bool iterating() const { return m_iterating; }

    WorldStats stats() const;

private:
    struct Record { u32 archetype = kInvalidIndex; u32 row = kInvalidIndex; u32 generation = 0; bool alive = false; };

    u32 archetypeFor(const std::vector<ComponentTypeId>& sortedTypes);
    void moveTo(Entity e, u32 targetArchetype);

    std::vector<ComponentMeta>                        m_metas;
    std::unordered_map<InternId, ComponentTypeId>     m_metaById;
    std::vector<Archetype*>                           m_archetypes;
    std::unordered_map<u64, u32>                      m_archetypeIndex;
    std::vector<Record>                               m_records;
    std::vector<u32>                                  m_freeList;
    CommandBuffer*                                    m_commands = nullptr;
    StringInterner                                    m_interner;
    u64 m_live = 0;
    u64 m_structuralChanges = 0;
    bool m_iterating = false;

    friend class CommandBuffer;
    friend class QueryGuard;
};

// ---------------------------------------------------------------------------
// RAII guard that blocks structural changes for the duration of a query.
// ---------------------------------------------------------------------------
class QueryGuard {
public:
    explicit QueryGuard(World& w) : m_world(w) { m_world.m_iterating = true; }
    ~QueryGuard() { m_world.m_iterating = false; }
    QueryGuard(const QueryGuard&) = delete;
    QueryGuard& operator=(const QueryGuard&) = delete;
private:
    World& m_world;
};

// ---------------------------------------------------------------------------
// CommandBuffer -- queues structural changes so systems stay iteration-safe.
// ---------------------------------------------------------------------------
class CommandBuffer {
public:
    explicit CommandBuffer(World& world) : m_world(world) {}

    Entity create() {
        const u32 slot = (u32)m_creates.size();
        m_creates.push_back(nullptr);
        return Entity{ kInvalidIndex, slot };   // placeholder, resolved on flush
    }

    template <typename T>
    void add(Entity e, ComponentTypeId type, const T& value = T{}) {
        static_assert(isComponentV<T>, "ECS components must be trivially relocatable");
        Cmd c{};
        c.op = Op::Add; c.entity = e; c.type = type; c.size = (u32)sizeof(T);
        c.dataOffset = (u32)m_blob.size();
        m_blob.resize(m_blob.size() + sizeof(T));
        std::memcpy(m_blob.data() + c.dataOffset, &value, sizeof(T));
        m_cmds.push_back(c);
    }

    void remove(Entity e, ComponentTypeId type) {
        Cmd c{}; c.op = Op::Remove; c.entity = e; c.type = type;
        m_cmds.push_back(c);
    }
    void destroy(Entity e) {
        Cmd c{}; c.op = Op::Destroy; c.entity = e;
        m_cmds.push_back(c);
    }

    /// Applies every queued command in order and empties the buffer.
    /// Returns the real entities for the placeholders create() handed out.
    std::vector<Entity> flush();

    u64 pending() const { return m_cmds.size() + m_creates.size(); }

private:
    enum class Op : u8 { Create, Add, Remove, Destroy };
    struct Cmd { Op op; Entity entity; ComponentTypeId type; u32 size; u32 dataOffset; };

    World&              m_world;
    std::vector<Cmd>    m_cmds;
    std::vector<u8>     m_blob;
    std::vector<void*>  m_creates;
};

// ---------------------------------------------------------------------------
// Typed iteration helper.
//
//   for (auto [e, t, v] : world.each<Transform, Velocity>(tId, vId)) { ... }
//
// The ids are passed explicitly because component types are registered at
// runtime (the editor adds components by name), so the id cannot live purely in
// the C++ type system.
// ---------------------------------------------------------------------------
template <typename A, typename B>
class Each {
public:
    struct Row { Entity e; A* a; B* b; };

    Each(World& world, ComponentTypeId a, ComponentTypeId b)
        : m_world(world), m_a(a), m_b(b) {
        const ComponentTypeId types[2] = { a, b };
        m_archetypes = world.archetypesWith(types, 2);
        for (u32 ai : m_archetypes) {
            Archetype& ar = world.archetype(ai);
            m_columns.push_back({ ar.columnOf(a), ar.columnOf(b) });
        }
    }

    class Iterator {
    public:
        Iterator(Each* self, size_t ai, u32 row) : m_self(self), m_ai(ai), m_row(row) { skipEmpty(); }

        bool operator!=(const Iterator& o) const { return m_ai != o.m_ai || m_row != o.m_row; }
        void operator++() { ++m_row; skipEmpty(); }

        Row operator*() const {
            Archetype& ar = m_self->m_world.archetype(m_self->m_archetypes[m_ai]);
            const auto& cols = m_self->m_columns[m_ai];
            return Row{
                ar.entityAt(m_row),
                static_cast<A*>(ar.component(cols.first,  m_row)),
                static_cast<B*>(ar.component(cols.second, m_row))
            };
        }

    private:
        void skipEmpty() {
            while (m_ai < m_self->m_archetypes.size() &&
                   m_row >= m_self->m_world.archetype(m_self->m_archetypes[m_ai]).count()) {
                ++m_ai; m_row = 0;
            }
        }
        Each* m_self;
        size_t m_ai;
        u32 m_row;
    };

    Iterator begin() { return Iterator(this, 0, 0); }
    Iterator end()   { return Iterator(this, m_archetypes.size(), 0); }

    u64 archetypeMatches() const { return m_archetypes.size(); }

private:
    World& m_world;
    ComponentTypeId m_a, m_b;
    std::vector<u32> m_archetypes;
    std::vector<std::pair<u32, u32>> m_columns;
};

template <typename A, typename B>
Each<A, B> each(World& world, ComponentTypeId a, ComponentTypeId b) {
    return Each<A, B>(world, a, b);
}

/// Single-component iteration; the common case for transform/particle passes.
template <typename A>
class EachOne {
public:
    struct Row { Entity e; A* a; };

    EachOne(World& world, ComponentTypeId a) : m_world(world), m_a(a) {
        m_archetypes = world.archetypesWith(&a, 1);
        for (u32 ai : m_archetypes) m_columns.push_back(world.archetype(ai).columnOf(a));
    }

    class Iterator {
    public:
        Iterator(EachOne* self, size_t ai, u32 row) : m_self(self), m_ai(ai), m_row(row) { skipEmpty(); }
        bool operator!=(const Iterator& o) const { return m_ai != o.m_ai || m_row != o.m_row; }
        void operator++() { ++m_row; skipEmpty(); }
        Row operator*() const {
            Archetype& ar = m_self->m_world.archetype(m_self->m_archetypes[m_ai]);
            return Row{ ar.entityAt(m_row),
                        static_cast<A*>(ar.component(m_self->m_columns[m_ai], m_row)) };
        }
    private:
        void skipEmpty() {
            while (m_ai < m_self->m_archetypes.size() &&
                   m_row >= m_self->m_world.archetype(m_self->m_archetypes[m_ai]).count()) {
                ++m_ai; m_row = 0;
            }
        }
        EachOne* m_self; size_t m_ai; u32 m_row;
    };

    Iterator begin() { return Iterator(this, 0, 0); }
    Iterator end()   { return Iterator(this, m_archetypes.size(), 0); }

private:
    World& m_world;
    ComponentTypeId m_a;
    std::vector<u32> m_archetypes;
    std::vector<u32> m_columns;
};

template <typename A>
EachOne<A> eachOne(World& world, ComponentTypeId a) { return EachOne<A>(world, a); }

} // namespace se
