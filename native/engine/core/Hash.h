// SPDX-License-Identifier: MIT
// S Engine 2.0 -- hashing and string interning.
//
// Interned strings turn asset names, component names and script property keys
// into u32 ids, so ECS lookups, JSON scene loading and the visual-scripting
// VM all compare integers instead of strcmp'ing.  The interner is append-only
// for the life of the process; ids are stable within a run but NOT persisted,
// so serialised data stores the literal string, never the id.
#pragma once

#include "../core/Types.h"

#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

namespace se {

/// FNV-1a, 64-bit.  Chosen because it is branch-free, needs no seed table and
/// hashes the short identifiers this engine uses without clustering.
inline u64 fnv1a(const void* data, u64 len, u64 h = 1469598103934665603ull) {
    const u8* p = static_cast<const u8*>(data);
    for (u64 i = 0; i < len; ++i) {
        h ^= p[i];
        h *= 1099511628211ull;
    }
    return h;
}

inline u64 hashString(const char* s) { return fnv1a(s, std::strlen(s)); }
inline u64 hashCombine(u64 a, u64 b) { return a ^ (b + 0x9e3779b97f4a7c15ull + (a << 6) + (a >> 2)); }

using InternId = u32;
inline constexpr InternId kInvalidIntern = 0;   // id 0 is reserved for ""

class StringInterner {
public:
    /// Returns a stable id for `s`.  Ids start at 1.
    InternId intern(const char* s) {
        if (!s || !*s) return kInvalidIntern;
        const u64 h = hashString(s);
        auto it = m_map.find(h);
        if (it != m_map.end()) return it->second;
        m_strings.emplace_back(s);
        const InternId id = (InternId)m_strings.size();
        m_map.emplace(h, id);
        return id;
    }

    InternId intern(const std::string& s) { return intern(s.c_str()); }

    /// nullptr for an unknown id -- callers must handle it, ids are not
    /// validated on the hot path.
    const char* resolve(InternId id) const {
        if (id == kInvalidIntern) return "";
        if (id > m_strings.size()) return nullptr;
        return m_strings[id - 1].c_str();
    }

    /// Hash-only lookup; returns kInvalidIntern when the string is unknown.
    InternId find(const char* s) const {
        if (!s || !*s) return kInvalidIntern;
        auto it = m_map.find(hashString(s));
        return it == m_map.end() ? kInvalidIntern : it->second;
    }

    u64 size() const { return m_strings.size(); }

private:
    std::vector<std::string>             m_strings;
    std::unordered_map<u64, InternId>    m_map;
};

} // namespace se
