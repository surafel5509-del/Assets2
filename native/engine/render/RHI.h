// SPDX-License-Identifier: MIT
// S Engine 2.0 -- Rendering Hardware Interface.
//
// One abstraction over Vulkan (primary) and OpenGL ES 3.0+ (fallback for
// devices without a usable Vulkan 1.1 driver).  The interface deliberately
// mirrors Vulkan's object model -- device, queue, command buffer, pipeline,
// descriptor set -- because that is the stricter of the two; GLES is expressed
// as a state-machine emulation on top of it rather than the other way round.
//
// Everything here is pure interface plus POD descriptions.  Backends live in
// render/vulkan/ and render/gles/; NullRHI (render/NullRHI.*) is a recording
// backend used by the host test suite and by the editor's headless mode.
#pragma once

#include "../core/Types.h"
#include "../math/Math.h"

#include <string>
#include <vector>

namespace se {

using RHIHandle = u64;
inline constexpr RHIHandle kNullRHIHandle = 0;

// ---------------------------------------------------------------------------
enum class Format : u8 {
    Unknown,
    R8G8B8A8_UNorm, R8G8B8A8_SRGB,
    B5G6R5_UNorm,
    R16G16B16A16_Float,
    R32G32B32A32_Float,
    D24_UNorm_S8_UInt,
    D32_Float,
};

enum class TextureType : u8 { Tex2D, TexCube, Tex2DArray };
enum class Filter : u8 { Nearest, Linear };
enum class Wrap : u8 { Repeat, Clamp, MirroredRepeat };
enum class LoadOp : u8 { Load, Clear, DontCare };
enum class StoreOp : u8 { Store, DontCare };
enum class PrimitiveTopology : u8 { TriangleList, TriangleStrip, LineList, PointList };
enum class CullMode : u8 { None, Back, Front };
enum class BlendFactor : u8 { Zero, One, SrcAlpha, OneMinusSrcAlpha, DstAlpha, OneMinusDstAlpha };
enum class CompareOp : u8 { Never, Less, Equal, LessOrEqual, Greater, GreaterOrEqual, Always };
enum class Backend : u8 { Null, Vulkan, Gles3 };

// ---------------------------------------------------------------------------
struct TextureDesc {
    u32 width = 1, height = 1, depth = 1;
    u32 mipLevels = 1;
    u32 arrayLayers = 1;
    u32 samples = 1;
    Format format = Format::R8G8B8A8_UNorm;
    TextureType type = TextureType::Tex2D;
    bool renderTarget = false;
    bool depthStencil = false;
    bool sampled = true;
    bool generateMips = false;
};

struct SamplerDesc {
    Filter magFilter = Filter::Linear;
    Filter minFilter = Filter::Linear;
    Filter mipFilter = Filter::Linear;
    Wrap wrapU = Wrap::Repeat, wrapV = Wrap::Repeat, wrapW = Wrap::Repeat;
    f32 maxAnisotropy = 1.0f;
    CompareOp compareOp = CompareOp::Always;
    bool compareEnable = false;
};

struct BufferDesc {
    u64 size = 0;
    bool vertex = false;
    bool index = false;
    bool uniform = false;
    bool storage = false;
    bool indirect = false;
};

struct VertexAttribute {
    u32 location = 0;
    u32 binding = 0;
    u32 offset = 0;
    Format format = Format::R32G32B32A32_Float;
};

struct BlendState {
    bool enabled = false;
    BlendFactor srcColor = BlendFactor::SrcAlpha;
    BlendFactor dstColor = BlendFactor::OneMinusSrcAlpha;
    BlendFactor srcAlpha = BlendFactor::One;
    BlendFactor dstAlpha = BlendFactor::OneMinusSrcAlpha;
};

struct DepthStencilState {
    bool depthTest = true;
    bool depthWrite = true;
    CompareOp depthOp = CompareOp::Less;
    bool stencilTest = false;
};

struct RasterState {
    CullMode cull = CullMode::Back;
    bool frontFaceCounterClockwise = true;
    bool wireframe = false;
    f32 lineWidth = 1.0f;
    f32 depthBias = 0.0f;
};

struct AttachmentDesc {
    RHIHandle texture = kNullRHIHandle;
    u32 mipLevel = 0;
    u32 layer = 0;
    LoadOp loadOp = LoadOp::Clear;
    StoreOp storeOp = StoreOp::Store;
    Vec4 clearValue{ 0, 0, 0, 1 };
};

struct RenderPassDesc {
    std::vector<AttachmentDesc> colors;
    AttachmentDesc depth;
    bool hasDepth = false;
    u32 width = 0, height = 0;
};

struct PipelineDesc {
    std::string vertexSource;      // SPIR-V path (Vulkan) or GLSL path (GLES)
    std::string fragmentSource;
    std::vector<VertexAttribute> attributes;
    u32 vertexStride = 0;
    PrimitiveTopology topology = PrimitiveTopology::TriangleList;
    BlendState blend;
    DepthStencilState depth;
    RasterState raster;
    u32 sampleCount = 1;
};

// ---------------------------------------------------------------------------
// Material constants for the PBR forward+ pass.  Laid out to match the std140
// uniform block in shaders/pbr.frag exactly -- do not reorder.
// ---------------------------------------------------------------------------
struct PBRMaterial {
    Vec4  baseColor{ 1, 1, 1, 1 };
    Vec4  emissive{ 0, 0, 0, 1 };
    f32   metallic   = 0.0f;
    f32   roughness  = 0.5f;
    f32   ao         = 1.0f;
    f32   alphaCutoff = 0.5f;
    u32   flags      = 0;          // bit0 unlit, bit1 alphaTest, bit2 doubleSided
    u32   pad[3]     = { 0, 0, 0 };
};

enum class LightType : u8 { Directional, Point, Spot };

struct LightData {
    LightType type = LightType::Directional;
    Vec3  position{ 0, 0, 0 };
    Vec3  direction{ 0, -1, 0 };
    Vec4  color{ 1, 1, 1, 1 };
    f32   intensity = 1.0f;
    f32   range = 10.0f;
    f32   innerConeCos = 0.9f;
    f32   outerConeCos = 0.8f;
    bool  castsShadow = false;
    u32   shadowMapIndex = kInvalidIndex;
};

// ---------------------------------------------------------------------------
// Cascaded shadow maps.  Four splits is the usual mobile sweet spot: enough to
// keep a directional light crisp from arm's length to the horizon without
// blowing the fragment budget on extra passes.
// ---------------------------------------------------------------------------
struct CascadeShadowParams {
    u32 cascadeCount = 4;
    f32 splitLambda = 0.92f;        // 0 = uniform, 1 = pure logarithmic
    f32 maxDistance = 120.0f;
    u32 mapResolution = 1024;
    f32 depthBias = 0.0025f;
    f32 normalOffsetBias = 0.03f;
    f32 pcfRadius = 1.5f;           // texels
};

struct CascadeSplit {
    Mat4 viewProj;
    f32  farDepth = 0.0f;           // view-space split boundary
};

// ---------------------------------------------------------------------------
struct DrawCall {
    RHIHandle pipeline = kNullRHIHandle;
    RHIHandle vertexBuffer = kNullRHIHandle;
    RHIHandle indexBuffer = kNullRHIHandle;
    u64 indexOffset = 0;
    u32 indexCount = 0;
    u32 vertexOffset = 0;
    u32 instanceCount = 1;
    Vec4 sortKey{ 0, 0, 0, 0 };     // (pass, depth, material, mesh) for batching
};

struct FrameStats {
    u64 drawCalls = 0;
    u64 triangles = 0;
    u64 vertices = 0;
    u64 pipelineBinds = 0;
    u64 bufferUploadBytes = 0;
    u64 renderPasses = 0;
    u64 barriers = 0;
};

// ---------------------------------------------------------------------------
class RHICommandBuffer {
public:
    virtual ~RHICommandBuffer() = default;
    virtual void beginRenderPass(const RenderPassDesc&) = 0;
    virtual void endRenderPass() = 0;
    virtual void setViewport(f32 x, f32 y, f32 w, f32 h) = 0;
    virtual void setScissor(i32 x, i32 y, u32 w, u32 h) = 0;
    virtual void bindPipeline(RHIHandle) = 0;
    virtual void bindVertexBuffer(RHIHandle, u64 offset = 0) = 0;
    virtual void bindIndexBuffer(RHIHandle, u64 offset = 0) = 0;
    virtual void bindDescriptorSet(u32 setIndex, RHIHandle) = 0;
    virtual void pushConstants(const void* data, u32 size) = 0;
    virtual void draw(u32 vertexCount, u32 firstVertex = 0, u32 instanceCount = 1) = 0;
    virtual void drawIndexed(u32 indexCount, u32 firstIndex = 0,
                             u32 vertexOffset = 0, u32 instanceCount = 1) = 0;
    virtual void barrier(RHIHandle resource) = 0;
};

class RHIDevice {
public:
    virtual ~RHIDevice() = default;

    virtual Backend backend() const = 0;
    virtual const char* backendName() const = 0;
    virtual const char* deviceName() const = 0;

    virtual RHIHandle createTexture(const TextureDesc&) = 0;
    virtual RHIHandle createSampler(const SamplerDesc&) = 0;
    virtual RHIHandle createBuffer(const BufferDesc&) = 0;
    virtual RHIHandle createPipeline(const PipelineDesc&) = 0;
    virtual void      destroy(RHIHandle) = 0;

    virtual void updateBuffer(RHIHandle, u64 offset, const void* data, u64 size) = 0;
    virtual void uploadTexture(RHIHandle, u32 mip, const void* data, u64 size) = 0;

    virtual RHICommandBuffer* createCommandBuffer() = 0;
    virtual void submit(RHICommandBuffer*) = 0;
    virtual void present() = 0;
    virtual void waitIdle() = 0;

    virtual FrameStats frameStats() const = 0;
    virtual u64 vramBytes() const = 0;
};

/// Factory implemented per backend; selected once at startup from the device's
/// reported Vulkan version, falling back to GLES 3.0.
RHIDevice* createRHIDevice(Backend preferred, void* nativeWindow, void* eglDisplay, void* eglSurface);

} // namespace se
