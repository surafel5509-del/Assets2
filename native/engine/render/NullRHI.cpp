// SPDX-License-Identifier: MIT
#include "NullRHI.h"

namespace se {

namespace {
u64 textureBytes(const TextureDesc& d) {
    u32 bpp = 4;
    switch (d.format) {
    case Format::R8G8B8A8_UNorm: case Format::R8G8B8A8_SRGB: case Format::D24_UNorm_S8_UInt: bpp = 4; break;
    case Format::B5G6R5_UNorm: bpp = 2; break;
    case Format::R16G16B16A16_Float: bpp = 8; break;
    case Format::R32G32B32A32_Float: bpp = 16; break;
    case Format::D32_Float: bpp = 4; break;
    default: bpp = 4; break;
    }
    u64 bytes = 0;
    u32 w = d.width ? d.width : 1, h = d.height ? d.height : 1;
    for (u32 mip = 0; mip < (d.mipLevels ? d.mipLevels : 1); ++mip) {
        bytes += (u64)w * h * bpp * (d.arrayLayers ? d.arrayLayers : 1);
        w = w > 1 ? w / 2 : 1;
        h = h > 1 ? h / 2 : 1;
    }
    return bytes;
}
} // namespace

// ---------------------------------------------------------------------------
NullRHIDevice::NullRHIDevice(NullRHIConfig cfg) : m_cfg(cfg) {}

NullRHIDevice::~NullRHIDevice() {
    for (auto* cb : m_commandBuffers) delete cb;
}

RHIHandle NullRHIDevice::next() { return m_nextHandle++; }

void NullRHIDevice::violate(const char* what) {
    ++m_violations;
    m_violationLog.emplace_back(what);
    SE_ASSERT(!m_cfg.validateStateOrder && "NullRHI contract violation");
}

RHIHandle NullRHIDevice::createTexture(const TextureDesc& d) {
    const RHIHandle h = next();
    m_live[h] = Resource{ textureBytes(d), 0 };
    m_vramBytes += m_live[h].bytes;
    return h;
}

RHIHandle NullRHIDevice::createSampler(const SamplerDesc&) {
    const RHIHandle h = next();
    m_live[h] = Resource{ 64, 1 };
    return h;
}

RHIHandle NullRHIDevice::createBuffer(const BufferDesc& d) {
    const RHIHandle h = next();
    m_live[h] = Resource{ d.size, 2 };
    m_vramBytes += d.size;
    return h;
}

RHIHandle NullRHIDevice::createPipeline(const PipelineDesc& p) {
    if (p.vertexSource.empty() || p.fragmentSource.empty())
        violate("createPipeline: shader stage with no source");
    if (p.vertexStride == 0)
        violate("createPipeline: vertexStride is 0");
    const RHIHandle h = next();
    m_live[h] = Resource{ 4096, 3 };
    return h;
}

void NullRHIDevice::destroy(RHIHandle h) {
    auto it = m_live.find(h);
    if (it == m_live.end()) { violate("destroy: handle is not live (double free?)"); return; }
    m_vramBytes -= it->second.bytes;
    m_live.erase(it);
}

void NullRHIDevice::updateBuffer(RHIHandle h, u64 offset, const void* data, u64 size) {
    auto it = m_live.find(h);
    if (it == m_live.end() || it->second.kind != 2) { violate("updateBuffer: not a buffer"); return; }
    if (offset + size > it->second.bytes) { violate("updateBuffer: write past end of buffer"); return; }
    if (!data && size) { violate("updateBuffer: null data with non-zero size"); return; }
    m_frame.bufferUploadBytes += size;
}

void NullRHIDevice::uploadTexture(RHIHandle h, u32, const void* data, u64 size) {
    auto it = m_live.find(h);
    if (it == m_live.end() || it->second.kind != 0) { violate("uploadTexture: not a texture"); return; }
    if (!data && size) { violate("uploadTexture: null data with non-zero size"); return; }
    if (size > it->second.bytes) { violate("uploadTexture: payload larger than the texture"); return; }
    m_frame.bufferUploadBytes += size;
}

RHICommandBuffer* NullRHIDevice::createCommandBuffer() {
    auto* cb = new NullCommandBuffer(this);
    m_commandBuffers.push_back(cb);
    return cb;
}

void NullRHIDevice::submit(RHICommandBuffer* cb) {
    if (!cb) { violate("submit: null command buffer"); return; }
    auto* ncb = static_cast<NullCommandBuffer*>(cb);
    m_frame.drawCalls += ncb->draws();
}

void NullRHIDevice::present() {
    if (m_inPass) violate("present: render pass still open");
}

// ---------------------------------------------------------------------------
void NullCommandBuffer::beginRenderPass(const RenderPassDesc& d) {
    if (m_inPass) m_device->violate("beginRenderPass: already inside a pass");
    if (d.width == 0 || d.height == 0) m_device->violate("beginRenderPass: zero-sized framebuffer");
    for (const auto& c : d.colors)
        if (!m_device->owns(c.texture)) m_device->violate("beginRenderPass: color attachment not live");
    if (d.hasDepth && !m_device->owns(d.depth.texture))
        m_device->violate("beginRenderPass: depth attachment not live");
    m_inPass = true;
    m_device->m_inPass = true;
    m_device->m_frame.renderPasses++;
}

void NullCommandBuffer::endRenderPass() {
    if (!m_inPass) m_device->violate("endRenderPass: no pass open");
    m_inPass = false;
    m_device->m_inPass = false;
}

void NullCommandBuffer::setViewport(f32, f32, f32 w, f32 h) {
    if (w <= 0 || h <= 0) m_device->violate("setViewport: non-positive extent");
}

void NullCommandBuffer::setScissor(i32, i32, u32, u32) {}

void NullCommandBuffer::bindPipeline(RHIHandle h) {
    if (!m_device->owns(h)) m_device->violate("bindPipeline: handle not live");
    m_pipeline = h;
    m_device->m_frame.pipelineBinds++;
}

void NullCommandBuffer::bindVertexBuffer(RHIHandle h, u64) {
    if (h != kNullRHIHandle && !m_device->owns(h)) m_device->violate("bindVertexBuffer: handle not live");
}

void NullCommandBuffer::bindIndexBuffer(RHIHandle h, u64) {
    if (h != kNullRHIHandle && !m_device->owns(h)) m_device->violate("bindIndexBuffer: handle not live");
}

void NullCommandBuffer::bindDescriptorSet(u32, RHIHandle h) {
    if (h != kNullRHIHandle && !m_device->owns(h)) m_device->violate("bindDescriptorSet: handle not live");
}

void NullCommandBuffer::pushConstants(const void* data, u32 size) {
    if (!data && size) m_device->violate("pushConstants: null data");
    if (size > 128) m_device->violate("pushConstants: exceeds the 128-byte mobile-safe limit");
}

void NullCommandBuffer::draw(u32 vertexCount, u32, u32 instanceCount) {
    if (!m_inPass) m_device->violate("draw: outside a render pass");
    if (m_pipeline == kNullRHIHandle) m_device->violate("draw: no pipeline bound");
    if (instanceCount == 0) m_device->violate("draw: instanceCount is 0");
    // An invalid draw is reported but never counted: frameStats must reflect
    // what a real backend would submit, or the profiler would report GPU work
    // that never happened.
    if (!m_inPass || m_pipeline == kNullRHIHandle) return;
    ++m_draws;
    m_device->m_frame.vertices += (u64)vertexCount * instanceCount;
    m_device->m_frame.triangles += ((u64)vertexCount / 3) * instanceCount;
}

void NullCommandBuffer::drawIndexed(u32 indexCount, u32, u32, u32 instanceCount) {
    if (!m_inPass) m_device->violate("drawIndexed: outside a render pass");
    if (m_pipeline == kNullRHIHandle) m_device->violate("drawIndexed: no pipeline bound");
    if (indexCount % 3 != 0) m_device->violate("drawIndexed: indexCount not a multiple of 3");
    if (!m_inPass || m_pipeline == kNullRHIHandle) return;
    ++m_draws;
    m_device->m_frame.triangles += ((u64)indexCount / 3) * instanceCount;
}

void NullCommandBuffer::barrier(RHIHandle h) {
    if (h != kNullRHIHandle && !m_device->owns(h)) m_device->violate("barrier: handle not live");
    m_device->m_frame.barriers++;
}

} // namespace se
