// SPDX-License-Identifier: MIT
#include "Memory.h"

#include <cstdlib>
#include <cstdio>
#include <cstring>

namespace se {

namespace detail {
[[noreturn]] void assertFail(const char* expr, const char* file, int line) {
    std::fprintf(stderr, "[S Engine] ASSERT FAILED: %s\n  at %s:%d\n", expr, file, line);
    std::fflush(stderr);
    std::abort();
}
} // namespace detail

// ---------------------------------------------------------------------------
namespace {
inline u64 alignUp(u64 v, u64 a) { return (v + (a - 1)) & ~(a - 1); }
}

// ---------------------------------------------------------------------------
// LinearAllocator
// ---------------------------------------------------------------------------
LinearAllocator::LinearAllocator(u64 reserveBytes)
    : m_capacity(alignUp(reserveBytes, 64)) {
    m_base = static_cast<u8*>(std::aligned_alloc(64, m_capacity));
    SE_ALWAYS_ASSERT(m_base != nullptr);
    m_stats.bytesTotal = m_capacity;
}

LinearAllocator::~LinearAllocator() {
    std::free(m_base);
}

void* LinearAllocator::alloc(u64 size, u32 alignment) {
    const u64 aligned = alignUp(m_offset, alignment ? alignment : 1);
    if (aligned + size > m_capacity) {
        SE_ASSERT(false && "LinearAllocator exhausted: raise the reserve or rewind sooner");
        return nullptr;
    }
    m_offset = aligned + size;
    m_stats.allocations++;
    m_stats.bytesUsed = m_offset;
    if (m_offset > m_stats.bytesPeak) m_stats.bytesPeak = m_offset;
    return m_base + aligned;
}

void LinearAllocator::rewind(u64 mark) {
    SE_ASSERT(mark <= m_offset);
    m_offset = mark;
    m_stats.bytesUsed = m_offset;
}

// ---------------------------------------------------------------------------
// PoolAllocator
// ---------------------------------------------------------------------------
PoolAllocator::PoolAllocator(u64 blockSize, u64 blocksPerChunk)
    : m_blockSize(blockSize < sizeof(void*) ? sizeof(void*) : blockSize),
      m_blocksPerChunk(blocksPerChunk) {}

PoolAllocator::~PoolAllocator() {
    Chunk* c = m_chunks;
    while (c) { Chunk* next = c->next; std::free(c); c = next; }
}

void PoolAllocator::grow() {
    // Block 0 of a chunk holds the back-pointer; blocks 1..N-1 are payload.
    const u64 payload = m_blocksPerChunk;
    const u64 bytes = sizeof(Chunk*) + payload * m_blockSize;
    u8* raw = static_cast<u8*>(std::aligned_alloc(16, alignUp(bytes, 16)));
    SE_ALWAYS_ASSERT(raw != nullptr);
    Chunk* chunk = reinterpret_cast<Chunk*>(raw);
    chunk->next = m_chunks;
    m_chunks = chunk;

    u8* first = raw + sizeof(Chunk*);
    // Push every block onto the free list, tail first so the head is block 0.
    for (u64 i = payload; i > 0; --i) {
        void** slot = reinterpret_cast<void**>(first + (i - 1) * m_blockSize);
        *slot = m_freeList;
        m_freeList = slot;
    }
    m_allocated += payload;
    m_stats.bytesTotal += bytes;
    if (m_allocated > m_stats.highWaterBlocks) m_stats.highWaterBlocks = m_allocated;
}

void* PoolAllocator::alloc() {
    if (!m_freeList) grow();
    void* p = m_freeList;
    m_freeList = *reinterpret_cast<void**>(p);
    ++m_live;
    m_stats.allocations++;
    m_stats.bytesUsed = m_live * m_blockSize;
    if (m_stats.bytesUsed > m_stats.bytesPeak) m_stats.bytesPeak = m_stats.bytesUsed;
    return p;
}

void PoolAllocator::free(void* p) {
    if (!p) return;
    SE_ASSERT(m_live > 0);
    *reinterpret_cast<void**>(p) = m_freeList;
    m_freeList = p;
    --m_live;
    m_stats.frees++;
    m_stats.bytesUsed = m_live * m_blockSize;
}

// ---------------------------------------------------------------------------
// BucketAllocator
// ---------------------------------------------------------------------------
BucketAllocator::BucketAllocator(u64 blocksPerChunk) {
    for (u32 i = 0; i < kClassCount; ++i)
        m_pools[i] = new PoolAllocator(kClassSizes[i], blocksPerChunk);
}

BucketAllocator::~BucketAllocator() {
    for (u32 i = 0; i < kClassCount; ++i) delete m_pools[i];
}

u32 BucketAllocator::classIndex(u64 size) {
    for (u32 i = 0; i < kClassCount; ++i)
        if (size <= kClassSizes[i]) return i;
    return kInvalidIndex;
}

void* BucketAllocator::alloc(u64 size) {
    const u32 idx = classIndex(size);
    m_stats.allocations++;
    if (idx == kInvalidIndex) {
        const u64 rounded = alignUp(size, 16);
        m_stats.bytesUsed += rounded;
        if (m_stats.bytesUsed > m_stats.bytesPeak) m_stats.bytesPeak = m_stats.bytesUsed;
        return std::aligned_alloc(16, rounded);
    }
    void* p = m_pools[idx]->alloc();
    m_stats.bytesUsed += kClassSizes[idx];
    m_stats.bytesTotal = 0;
    for (u32 i = 0; i < kClassCount; ++i) m_stats.bytesTotal += m_pools[i]->stats().bytesTotal;
    if (m_stats.bytesUsed > m_stats.bytesPeak) m_stats.bytesPeak = m_stats.bytesUsed;
    return p;
}

void BucketAllocator::free(void* p, u64 size) {
    if (!p) return;
    m_stats.frees++;
    const u32 idx = classIndex(size);
    if (idx == kInvalidIndex) {
        m_stats.bytesUsed -= alignUp(size, 16);
        std::free(p);
        return;
    }
    m_pools[idx]->free(p);
    m_stats.bytesUsed -= kClassSizes[idx];
}

// ---------------------------------------------------------------------------
// FrameStack
// ---------------------------------------------------------------------------
FrameStack::FrameStack(u32 frames, u64 reservePerFrame)
    : m_count(frames ? frames : 1) {
    m_arenas = new LinearAllocator*[m_count];
    for (u32 i = 0; i < m_count; ++i) m_arenas[i] = new LinearAllocator(reservePerFrame);
}

FrameStack::~FrameStack() {
    for (u32 i = 0; i < m_count; ++i) delete m_arenas[i];
    delete[] m_arenas;
}

void FrameStack::beginFrame() {
    m_index = (m_index + 1) % m_count;
    m_arenas[m_index]->reset();
}

LinearAllocator* FrameStack::scratch() { return m_arenas[m_index]; }

} // namespace se
