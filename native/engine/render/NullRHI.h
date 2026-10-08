// SPDX-License-Identifier: MIT
// NullRHI -- a recording backend with no GPU.
//
// Two jobs:
//   1. Host unit tests can exercise the whole render-path contract (resource
//      lifetimes, pass ordering, barrier placement, draw accounting) with no
//      device, no driver and no flakes.
//   2. The editor's headless mode uses it to run play-mode simulation while the
//      user edits something else.
//
// It validates the rules a real backend would fault on, and counts everything
// the profiler reports.
#pragma once

#include "RHI.h"

#include <unordered_map>
#include <vector>

namespace se {

class NullCommandBuffer;

struct NullRHIConfig {
    bool validateStateOrder = true;   // fail on misuse instead of ignoring it
    u64  vramBudgetBytes = 256ull << 20;
};

class NullRHIDevice final : public RHIDevice {
public:
    explicit NullRHIDevice(NullRHIConfig cfg = {});
    ~NullRHIDevice() override;

    Backend backend() const override { return Backend::Null; }
    const char* backendName() const override { return "Null"; }
    const char* deviceName() const override { return "S Engine Null RHI (software)"; }

    RHIHandle createTexture(const TextureDesc&) override;
    RHIHandle createSampler(const SamplerDesc&) override;
    RHIHandle createBuffer(const BufferDesc&) override;
    RHIHandle createPipeline(const PipelineDesc&) override;
    void destroy(RHIHandle) override;

    void updateBuffer(RHIHandle, u64 offset, const void* data, u64 size) override;
    void uploadTexture(RHIHandle, u32 mip, const void* data, u64 size) override;

    RHICommandBuffer* createCommandBuffer() override;
    void submit(RHICommandBuffer*) override;
    void present() override;
    void waitIdle() override {}

    FrameStats frameStats() const override { return m_frame; }
    u64 vramBytes() const override { return m_vramBytes; }

    // ---- test / introspection surface -------------------------------------
    u64 liveResources() const { return m_live.size(); }
    bool owns(RHIHandle h) const { return m_live.count(h) > 0; }
    u64 violations() const { return m_violations; }
    const std::vector<std::string>& violationLog() const { return m_violationLog; }
    void resetFrame() { m_frame = FrameStats{}; }
    bool insideRenderPass() const { return m_inPass; }

    void violate(const char* what);

private:
    struct Resource { u64 bytes; u8 kind; };

    RHIHandle next();

    NullRHIConfig m_cfg;
    std::unordered_map<RHIHandle, Resource> m_live;
    std::vector<NullCommandBuffer*> m_commandBuffers;
    RHIHandle m_nextHandle = 1;
    u64 m_vramBytes = 0;
    FrameStats m_frame;
    u64 m_violations = 0;
    std::vector<std::string> m_violationLog;
    bool m_inPass = false;

    friend class NullCommandBuffer;
};

class NullCommandBuffer final : public RHICommandBuffer {
public:
    explicit NullCommandBuffer(NullRHIDevice* d) : m_device(d) {}

    void beginRenderPass(const RenderPassDesc&) override;
    void endRenderPass() override;
    void setViewport(f32, f32, f32, f32) override;
    void setScissor(i32, i32, u32, u32) override;
    void bindPipeline(RHIHandle) override;
    void bindVertexBuffer(RHIHandle, u64) override;
    void bindIndexBuffer(RHIHandle, u64) override;
    void bindDescriptorSet(u32, RHIHandle) override;
    void pushConstants(const void*, u32) override;
    void draw(u32 vertexCount, u32 firstVertex, u32 instanceCount) override;
    void drawIndexed(u32 indexCount, u32 firstIndex, u32 vertexOffset, u32 instanceCount) override;
    void barrier(RHIHandle) override;

    u64 draws() const { return m_draws; }

private:
    NullRHIDevice* m_device;
    u64 m_draws = 0;
    bool m_inPass = false;
    RHIHandle m_pipeline = kNullRHIHandle;
};

} // namespace se
