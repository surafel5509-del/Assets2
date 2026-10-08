// SPDX-License-Identifier: MIT
// S Engine 2.0 -- core primitive types and platform-agnostic aliases.
#pragma once

#include <cstddef>
#include <cstdint>
#include <cassert>
#include <type_traits>
#include <utility>

namespace se {

using u8  = uint8_t;
using u16 = uint16_t;
using u32 = uint32_t;
using u64 = uint64_t;
using i8  = int8_t;
using i16 = int16_t;
using i32 = int32_t;
using i64 = int64_t;
using f32 = float;
using f64 = double;

// ---------------------------------------------------------------------------
// Debug checks.  SE_ASSERT compiles away in release; SE_ALWAYS_ASSERT does not.
// ---------------------------------------------------------------------------
#if defined(SE_DEBUG) || !defined(NDEBUG)
#define SE_DEBUG_BUILD 1
#endif

#ifdef SE_DEBUG_BUILD
#define SE_ASSERT(cond)  do { if (!(cond)) ::se::detail::assertFail(#cond, __FILE__, __LINE__); } while (0)
#else
#define SE_ASSERT(cond)  ((void)0)
#endif
#define SE_ALWAYS_ASSERT(cond) do { if (!(cond)) ::se::detail::assertFail(#cond, __FILE__, __LINE__); } while (0)

namespace detail {
[[noreturn]] void assertFail(const char* expr, const char* file, int line);
}

// ---------------------------------------------------------------------------
// Non-copyable / non-movable base for subsystems with unique ownership.
// ---------------------------------------------------------------------------
struct NonCopyable {
    NonCopyable() = default;
    NonCopyable(const NonCopyable&) = delete;
    NonCopyable& operator=(const NonCopyable&) = delete;
};

// ---------------------------------------------------------------------------
// Components stored in the ECS must be trivially relocatable: the archetype
// storage moves them with memmove and never runs a destructor on relocation.
// ---------------------------------------------------------------------------
template <typename T>
inline constexpr bool isComponentV =
    std::is_trivially_destructible_v<T> && std::is_trivially_copyable_v<T>;

static_assert(sizeof(void*) == 8 || sizeof(void*) == 4, "unsupported pointer width");

inline constexpr u32 kInvalidIndex = 0xFFFFFFFFu;

} // namespace se
