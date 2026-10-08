// SPDX-License-Identifier: MIT
#include "World.h"

#include <algorithm>
#include <cstdlib>

namespace se {

namespace {
inline u64 alignUp(u64 v, u64 a) { return (v + (a - 1)) & ~(a - 1); }

u64 archetypeKey(const std::vector<ComponentTypeId>& sortedTypes) {
    u64 h = 1469598103934665603ull;
    for (ComponentTypeId t : sortedTypes) h = hashCombine(h, t);
    return h;
}
} // namespace

// ---------------------------------------------------------------------------
// Archetype
// ---------------------------------------------------------------------------
Archetype::Archetype(std::vector<ComponentTypeId> types, const std::vector<ComponentMeta>& metas)
    : m_types(std::move(types)) {
    m_columns.resize(m_types.size(), nullptr);
    m_strides.resize(m_types.size(), 0);
    for (size_t i = 0; i < m_types.size(); ++i)
        m_strides[i] = metas[m_types[i]].size;
}

Archetype::~Archetype() {
    for (u8* c : m_columns) std::free(c);
}

Archetype::Archetype(Archetype&& o) noexcept
    : m_types(std::move(o.m_types)), m_columns(std::move(o.m_columns)),
      m_strides(std::move(o.m_strides)), m_entities(std::move(o.m_entities)),
      m_count(o.m_count), m_capacity(o.m_capacity), m_bytes(o.m_bytes) {
    o.m_columns.clear();
    o.m_count = o.m_capacity = 0;
    o.m_bytes = 0;
}

Archetype& Archetype::operator=(Archetype&& o) noexcept {
    if (this != &o) {
        for (u8* c : m_columns) std::free(c);
        m_types    = std::move(o.m_types);
        m_columns  = std::move(o.m_columns);
        m_strides  = std::move(o.m_strides);
        m_entities = std::move(o.m_entities);
        m_count = o.m_count; m_capacity = o.m_capacity; m_bytes = o.m_bytes;
        o.m_columns.clear();
        o.m_count = o.m_capacity = 0;
        o.m_bytes = 0;
    }
    return *this;
}

u32 Archetype::columnOf(ComponentTypeId t) const {
    // m_types is sorted, so this is a binary search; archetypes hold a handful
    // of components, so the linear scan below is faster in practice.
    for (u32 i = 0; i < (u32)m_types.size(); ++i)
        if (m_types[i] == t) return i;
    return kInvalidIndex;
}

bool Archetype::hasAll(const ComponentTypeId* types, u32 n) const {
    for (u32 i = 0; i < n; ++i)
        if (!has(types[i])) return false;
    return true;
}

void Archetype::ensureCapacity(u32 rows) {
    if (rows <= m_capacity) return;
    u32 cap = m_capacity ? m_capacity : 8;
    while (cap < rows) cap *= 2;

    m_bytes = 0;
    for (size_t i = 0; i < m_columns.size(); ++i) {
        const u64 bytes = (u64)cap * m_strides[i];
        u8* next = static_cast<u8*>(std::aligned_alloc(16, alignUp(bytes, 16)));
        SE_ALWAYS_ASSERT(next != nullptr);
        if (m_columns[i]) {
            std::memcpy(next, m_columns[i], (u64)m_count * m_strides[i]);
            std::free(m_columns[i]);
        }
        m_columns[i] = next;
        m_bytes += bytes;
    }
    m_capacity = cap;
}

u32 Archetype::pushRow(Entity e) {
    ensureCapacity(m_count + 1);
    m_entities.push_back(e);
    return m_count++;
}

Entity Archetype::popRow(u32 row) {
    SE_ASSERT(row < m_count);
    const u32 last = m_count - 1;
    const Entity moved = m_entities[last];
    if (row != last) {
        for (size_t c = 0; c < m_columns.size(); ++c)
            std::memcpy(component((u32)c, row), component((u32)c, last), m_strides[c]);
        m_entities[row] = moved;
    }
    m_entities.pop_back();
    --m_count;
    return row == last ? kNullEntity : moved;
}

void Archetype::copySharedFrom(const Archetype& src, u32 srcRow, u32 row) {
    for (u32 i = 0; i < (u32)m_types.size(); ++i) {
        const u32 sc = src.columnOf(m_types[i]);
        if (sc == kInvalidIndex) continue;
        std::memcpy(component(i, row), src.component(sc, srcRow), m_strides[i]);
    }
}

// ---------------------------------------------------------------------------
// World
// ---------------------------------------------------------------------------
World::World() {
    m_commands = new CommandBuffer(*this);
}

World::~World() {
    delete m_commands;
    for (Archetype* a : m_archetypes) delete a;
}

ComponentTypeId World::componentIdByName(const char* name) const {
    const InternId nid = m_interner.find(name);
    if (nid == kInvalidIntern) return kInvalidComponent;
    auto it = m_metaById.find(nid);
    return it == m_metaById.end() ? kInvalidComponent : it->second;
}

u32 World::archetypeFor(const std::vector<ComponentTypeId>& sorted) {
    const u64 key = archetypeKey(sorted);
    auto it = m_archetypeIndex.find(key);
    if (it != m_archetypeIndex.end()) return it->second;

    m_archetypes.push_back(new Archetype(sorted, m_metas));
    const u32 index = (u32)m_archetypes.size() - 1;
    m_archetypeIndex.emplace(key, index);
    return index;
}

Entity World::create() {
    u32 idx;
    if (!m_freeList.empty()) {
        idx = m_freeList.back();
        m_freeList.pop_back();
    } else {
        idx = (u32)m_records.size();
        m_records.push_back(Record{});
    }
    Record& r = m_records[idx];
    r.alive = true;

    // Entities start in the empty archetype so "no components yet" is still a
    // valid archetype state rather than a special case.
    static const std::vector<ComponentTypeId> kEmpty;
    r.archetype = archetypeFor(kEmpty);
    r.row = m_archetypes[r.archetype]->pushRow(Entity{ idx, r.generation });

    ++m_live;
    return Entity{ idx, r.generation };
}

bool World::alive(Entity e) const {
    return e.valid() && e.index < m_records.size() &&
           m_records[e.index].alive && m_records[e.index].generation == e.generation;
}

void World::destroy(Entity e) {
    if (!alive(e)) return;
    Record& r = m_records[e.index];
    Entity moved = m_archetypes[r.archetype]->popRow(r.row);
    if (moved.valid()) m_records[moved.index].row = r.row;
    r.alive = false;
    r.archetype = kInvalidIndex;
    r.row = kInvalidIndex;
    r.generation++;
    m_freeList.push_back(e.index);
    --m_live;
}

void* World::getRaw(Entity e, ComponentTypeId type) const {
    if (!alive(e)) return nullptr;
    const Record& r = m_records[e.index];
    Archetype* a = m_archetypes[r.archetype];
    const u32 col = a->columnOf(type);
    return col == kInvalidIndex ? nullptr : a->component(col, r.row);
}

void* World::addRaw(Entity e, ComponentTypeId type, u32 size, bool* createdOut) {
    if (createdOut) *createdOut = false;
    if (!alive(e)) return nullptr;
    SE_ASSERT(type < m_metas.size() && m_metas[type].size == size);
    Record& r = m_records[e.index];
    Archetype* src = m_archetypes[r.archetype];
    const u32 existing = src->columnOf(type);
    if (existing != kInvalidIndex) return src->component(existing, r.row);

    std::vector<ComponentTypeId> types = src->types();
    types.push_back(type);
    std::sort(types.begin(), types.end());
    moveTo(e, archetypeFor(types));
    ++m_structuralChanges;
    if (createdOut) *createdOut = true;

    return m_archetypes[m_records[e.index].archetype]
        ->component(m_archetypes[m_records[e.index].archetype]->columnOf(type),
                    m_records[e.index].row);
}

bool World::remove(Entity e, ComponentTypeId type) {
    if (!alive(e)) return false;
    Record& r = m_records[e.index];
    Archetype* src = m_archetypes[r.archetype];
    if (!src->has(type)) return false;

    std::vector<ComponentTypeId> types = src->types();
    types.erase(std::remove(types.begin(), types.end(), type), types.end());
    moveTo(e, archetypeFor(types));
    ++m_structuralChanges;
    return true;
}

void World::moveTo(Entity e, u32 target) {
    Record& r = m_records[e.index];
    if (r.archetype == target) return;

    Archetype* src = m_archetypes[r.archetype];
    Archetype* dst = m_archetypes[target];
    const u32 srcRow = r.row;

    const u32 dstRow = dst->pushRow(e);
    dst->copySharedFrom(*src, srcRow, dstRow);

    // popRow swap-removes, so one other entity changes row; re-point it.
    const Entity moved = src->popRow(srcRow);
    if (moved.valid()) m_records[moved.index].row = srcRow;

    r.archetype = target;
    r.row = dstRow;
}

std::vector<u32> World::archetypesWith(const ComponentTypeId* types, u32 n) const {
    std::vector<u32> out;
    for (u32 i = 0; i < (u32)m_archetypes.size(); ++i) {
        Archetype* a = m_archetypes[i];
        if (a->empty()) continue;
        if (a->hasAll(types, n)) out.push_back(i);
    }
    return out;
}

std::vector<World::Column> World::columnsFor(ComponentTypeId type) {
    std::vector<Column> out;
    for (u32 i = 0; i < (u32)m_archetypes.size(); ++i) {
        Archetype* a = m_archetypes[i];
        if (a->empty()) continue;
        const u32 col = a->columnOf(type);
        if (col != kInvalidIndex) out.push_back(Column{ a, col });
    }
    return out;
}

void World::flushCommands() {
    // Applies queued commands in issue order.  CommandBuffer::flush resolves
    // deferred entity creations before any add/remove that references them, so
    // a system can spawn an entity and attach components to it in one frame.
    m_commands->flush();
}

WorldStats World::stats() const {
    WorldStats s;
    s.entities = m_live;
    s.archetypes = m_archetypes.size();
    s.structuralChanges = m_structuralChanges;
    for (const Archetype* a : m_archetypes) {
        s.componentRows += a->count();
        s.archetypeBytes += a->memoryBytes();
    }
    return s;
}

// ---------------------------------------------------------------------------
// CommandBuffer
// ---------------------------------------------------------------------------
std::vector<Entity> CommandBuffer::flush() {
    std::vector<Entity> created;
    created.reserve(m_creates.size());

    // Placeholder entities encode their create() slot in `generation`; resolve
    // them to real entities before anything else can reference them.
    std::vector<Entity> real(m_creates.size(), kNullEntity);
    for (size_t i = 0; i < m_creates.size(); ++i) real[i] = m_world.create();

    auto resolve = [&](Entity e) -> Entity {
        if (e.index == kInvalidIndex && e.generation < real.size()) return real[e.generation];
        return e;
    };

    for (const Cmd& c : m_cmds) {
        const Entity e = resolve(c.entity);
        switch (c.op) {
        case Op::Create: break;
        case Op::Add: {
            void* p = m_world.addRaw(e, c.type, c.size);
            if (p) std::memcpy(p, m_blob.data() + c.dataOffset, c.size);
            break;
        }
        case Op::Remove: m_world.remove(e, c.type); break;
        case Op::Destroy: m_world.destroy(e); break;
        }
    }

    m_cmds.clear();
    m_blob.clear();
    m_creates.clear();
    created = real;
    return created;
}

} // namespace se
