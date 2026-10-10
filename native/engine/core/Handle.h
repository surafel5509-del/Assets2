// SPDX-License-Identifier: MIT
// S Engine 2.0 -- generational handles.
//
// A handle is a 64-bit value: a dense index into a slot array plus a
// generation counter.  When a slot is recycled its generation increments, so a
// handle to a destroyed object never aliases a live one -- the classic
// use-after-free that raw indices cause.  Validity is one comparison.
#pragma once

#include "Types.h"

#include <vector>


namespace se {

struct Handle {
    u32 index      = kInvalidIndex;
    u32 generation = 0;

    bool valid() const { return index != kInvalidIndex; }
    bool operator==(const Handle& o) const { return index == o.index && generation == o.generation; }
    bool operator!=(const Handle& o) const { return !(*this == o); }
};

static_assert(sizeof(Handle) == 8, "Handle must stay 64 bits to be cheap to pass around");

inline constexpr Handle kNullHandle{};

/// Owning side of a handle table: maps Handle <-> dense slot.
/// T is the record stored per slot; it must be trivially copyable.
template <typename T>
class HandleTable {
public:
    HandleTable() = default;

    /// Reserves a slot and returns a handle that resolves to it.
    Handle create(const T& value) {
        u32 idx;
        if (!m_freeList.empty()) {
            idx = m_freeList.back();
            m_freeList.pop_back();
        } else {
            idx = (u32)m_slots.size();
            m_slots.push_back(Slot{});
        }
        m_slots[idx].value = value;
        m_slots[idx].alive = true;
        ++m_live;
        return Handle{ idx, m_slots[idx].generation };
    }

    bool alive(Handle h) const {
        return h.valid() && h.index < m_slots.size() &&
               m_slots[h.index].alive && m_slots[h.index].generation == h.generation;
    }

    T* get(Handle h) {
        return alive(h) ? &m_slots[h.index].value : nullptr;
    }
    const T* get(Handle h) const {
        return alive(h) ? &m_slots[h.index].value : nullptr;
    }

    /// Returns false for a stale handle; the slot's generation is bumped so any
    /// outstanding handle to it becomes permanently invalid.
    bool destroy(Handle h) {
        if (!alive(h)) return false;
        Slot& s = m_slots[h.index];
        s.alive = false;
        s.generation++;
        m_freeList.push_back(h.index);
        --m_live;
        return true;
    }

    u64 live() const { return m_live; }
    u64 capacity() const { return m_slots.size(); }

    /// Dense iteration over live slots (generation is exposed for re-handling).
    template <typename Fn>
    void forEach(Fn&& fn) {
        for (u32 i = 0; i < (u32)m_slots.size(); ++i)
            if (m_slots[i].alive) fn(Handle{ i, m_slots[i].generation }, m_slots[i].value);
    }

    void clear() {
        m_slots.clear();
        m_freeList.clear();
        m_live = 0;
    }

private:
    struct Slot { T value{}; u32 generation = 0; bool alive = false; };
    std::vector<Slot> m_slots;
    std::vector<u32>  m_freeList;
    u64 m_live = 0;
};

} // namespace se
