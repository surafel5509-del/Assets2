// SPDX-License-Identifier: MIT
// S Engine 2.0 -- custom allocators.
//
// The engine never calls new/delete on the hot path.  Four allocators cover
// every lifetime pattern a game frame produces:
//
//   LinearAllocator  bump allocator, freed in O(1).  Per-frame scratch:
//                    cull results, command buffers, transient strings.
//   PoolAllocator    fixed-size blocks from a free list.  ECS component rows,
//                    particles, nodes -- anything allocated and freed singly.
//   BucketAllocator  size-classed pools (16/32/64/128/256/512 B) for the
//                    general case, so small allocations never fragment.
//   FrameStack       a ring of LinearAllocators, one per buffered frame, so
//                    frame N's scratch survives while frame N+1 records.
//
// Every allocator is thread-affine by design (no locks); the ECS hands each
// worker its own scratch arena.
#pragma once

#include "../core/Types.h"

namespace se {

// ---------------------------------------------------------------------------
struct AllocatorStats {
    u64 allocations = 0;
    u64 frees       = 0;
    u64 bytesUsed   = 0;
    u64 bytesPeak   = 0;
    u64 bytesTotal  = 0;   // committed from the system
    u64 highWaterBlocks = 0;

    void merge(const AllocatorStats& o) {
        allocations += o.allocations; frees += o.frees;
        bytesUsed += o.bytesUsed; bytesPeak += o.bytesPeak;
        bytesTotal += o.bytesTotal; highWaterBlocks += o.highWaterBlocks;
    }
};

// ---------------------------------------------------------------------------
// LinearAllocator -- bump pointer.  Alloc is a single add; free is a reset.
// ---------------------------------------------------------------------------
class LinearAllocator : NonCopyable {
public:
    explicit LinearAllocator(u64 reserveBytes = 1u << 16);
    ~LinearAllocator();

    void* alloc(u64 size, u32 alignment = 16);

    template <typename T, typename... Args>
    T* create(Args&&... args) {
        void* p = alloc(sizeof(T), alignof(T) > 16 ? (u32)alignof(T) : 16);
        return new (p) T(std::forward<Args>(args)...);
    }

    /// Rewinds to a previously taken mark.  Everything after it is reclaimed.
    u64  mark() const { return m_offset; }
    void rewind(u64 mark);
    void reset() { rewind(0); }

    u64  used()     const { return m_offset; }
    u64  capacity() const { return m_capacity; }
    const AllocatorStats& stats() const { return m_stats; }

private:
    u8*  m_base     = nullptr;
    u64  m_capacity = 0;
    u64  m_offset   = 0;
    AllocatorStats m_stats;
};

// ---------------------------------------------------------------------------
// PoolAllocator -- fixed-size blocks, intrusive free list, O(1) both ways.
// ---------------------------------------------------------------------------
class PoolAllocator : NonCopyable {
public:
    PoolAllocator(u64 blockSize, u64 blocksPerChunk = 256);
    ~PoolAllocator();

    void* alloc();
    void  free(void* p);

    u64 blockSize()        const { return m_blockSize; }
    u64 liveBlocks()       const { return m_live; }
    u64 allocatedBlocks()  const { return m_allocated; }
    const AllocatorStats& stats() const { return m_stats; }

private:
    void grow();

    u64  m_blockSize;
    u64  m_blocksPerChunk;
    void* m_freeList    = nullptr;
    u64  m_live         = 0;
    u64  m_allocated    = 0;
    AllocatorStats m_stats;
    struct Chunk { Chunk* next; };
    Chunk* m_chunks = nullptr;
};

// ---------------------------------------------------------------------------
// ScopedPoolLeakGuard is unnecessary; instead a tiny RAII helper for the
// common "allocate a block for the duration of this scope" case.
// ---------------------------------------------------------------------------
class BucketAllocator : NonCopyable {
public:
    static constexpr u32  kClassCount = 6;
    static constexpr u64  kClassSizes[kClassCount] = { 16, 32, 64, 128, 256, 512 };

    explicit BucketAllocator(u64 blocksPerChunk = 256);
    ~BucketAllocator();

    void* alloc(u64 size);
    void  free(void* p, u64 size);

    /// Sizes above the largest class fall back to the system allocator.
    static u32 classIndex(u64 size);
    const AllocatorStats& stats() const { return m_stats; }

private:
    PoolAllocator* m_pools[kClassCount];
    AllocatorStats m_stats;
};

// ---------------------------------------------------------------------------
// FrameStack -- ring of linear arenas, one per in-flight frame.
// ---------------------------------------------------------------------------
class FrameStack : NonCopyable {
public:
    explicit FrameStack(u32 frames = 3, u64 reservePerFrame = 1u << 18);
    ~FrameStack();

    void              beginFrame();
    LinearAllocator*  scratch();
    u32               frameIndex() const { return m_index; }

private:
    LinearAllocator** m_arenas;
    u32 m_count;
    u32 m_index = 0;
};

} // namespace se
