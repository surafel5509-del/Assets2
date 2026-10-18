package com.sengine.engine.render

import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Multi-pass bloom: extract bright pixels, Gaussian blur (horizontal + vertical, ping-pong),
 * then additively combine with the source. Gives a soft HDR-style glow around bright areas.
 */
class BloomPass {
    private var fboA = 0; private var texA = 0
    private var fboB = 0; private var texB = 0
    private var fboExtract = 0; private var texExtract = 0
    private var w = 0; private var h = 0
    private lateinit var quad: FloatBuffer
    private var extractProg = 0
    private var blurHProg = 0; private var blurVProg = 0
    private var combineProg = 0
    // uniform locations
    private var eTex = 0; private var eThreshold = 0
    private var bTex = 0; private var bDir = 0; private var bResolution = 0
    private var cSource = 0; private var cBloom = 0; private var cIntensity = 0

    fun init() {
        release()
        quad = GL.floatBuffer(8)
        quad.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
        extractProg = GL.compile(BLOOM_EXTRACT_VS, BLOOM_EXTRACT_FS)
        blurHProg = GL.compile(BLOOM_BLUR_VS, BLOOM_BLUR_FS)
        blurVProg = GL.compile(BLOOM_BLUR_VS, BLOOM_BLUR_FS)
        combineProg = GL.compile(BLOOM_COMBINE_VS, BLOOM_COMBINE_FS)
        eTex = GLES20.glGetUniformLocation(extractProg, "uTex")
        eThreshold = GLES20.glGetUniformLocation(extractProg, "uThreshold")
        bTex = GLES20.glGetUniformLocation(blurHProg, "uTex")
        bDir = GLES20.glGetUniformLocation(blurHProg, "uDir")
        bResolution = GLES20.glGetUniformLocation(blurHProg, "uResolution")
        cSource = GLES20.glGetUniformLocation(combineProg, "uSource")
        cBloom = GLES20.glGetUniformLocation(combineProg, "uBloom")
        cIntensity = GLES20.glGetUniformLocation(combineProg, "uIntensity")
    }

    fun release() {
        if (fboA != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboA), 0)
        if (texA != 0) GLES20.glDeleteTextures(1, intArrayOf(texA), 0)
        if (fboB != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboB), 0)
        if (texB != 0) GLES20.glDeleteTextures(1, intArrayOf(texB), 0)
        if (fboExtract != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboExtract), 0)
        if (texExtract != 0) GLES20.glDeleteTextures(1, intArrayOf(texExtract), 0)
        fboA = 0; texA = 0; fboB = 0; texB = 0; fboExtract = 0; texExtract = 0; w = 0; h = 0
    }

    private fun ensure(width: Int, height: Int) {
        if (w == width && h == height && fboA != 0) return
        release()
        w = width; h = height
        val ids = IntArray(1)
        fun tex(): Int { GLES20.glGenTextures(1, ids, 0); val t = ids[0]; GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return t }
        fun fbo(ct: Int): Int { GLES20.glGenFramebuffers(1, ids, 0); val f = ids[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, f)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ct, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            return f }
        texA = tex(); fboA = fbo(texA)
        texB = tex(); fboB = fbo(texB)
        texExtract = tex(); fboExtract = fbo(texExtract)
    }

    /** Render [sourceTex] (the scene) into [destFbo] with bloom added. Call with default framebuffer bound. */
    fun apply(sourceTex: Int, destWidth: Int, destHeight: Int, threshold: Float, intensity: Float, passes: Int) {
        ensure(destWidth, destHeight)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        // 1) Extract bright pixels
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboExtract)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(extractProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTex)
        GLES20.glUniform1i(eTex, 0)
        GLES20.glUniform1f(eThreshold, threshold)
        quad.position(0)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)

        // 2) Ping-pong blur (horizontal then vertical) `passes` times
        var readTex = texExtract
        var curFbo = fboA
        var curTex = texA
        for (i in 0 until passes) {
            val dir = if (i % 2 == 0) 1f else 0f // 1 = horizontal, 0 = vertical
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, curFbo)
            GLES20.glViewport(0, 0, w, h)
            val prog = if (dir == 1f) blurHProg else blurVProg
            GLES20.glUseProgram(prog)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, readTex)
            GLES20.glUniform1i(bTex, 0)
            GLES20.glUniform1f(bDir, dir)
            GLES20.glUniform2f(bResolution, w.toFloat(), h.toFloat())
            quad.position(0)
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(0)
            // swap: what we just wrote becomes the next read; alternate FBO/texture
            readTex = curTex
            curTex = if (curTex == texA) texB else texA
            curFbo = if (curFbo == fboA) fboB else fboA
        }
        val bloomTex = readTex

        // 3) Combine source + bloom (additive)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, destWidth, destHeight)
        GLES20.glUseProgram(combineProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTex)
        GLES20.glUniform1i(cSource, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTex)
        GLES20.glUniform1i(cBloom, 1)
        GLES20.glUniform1f(cIntensity, intensity)
        quad.position(0)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
        GLES20.glEnable(GLES20.GL_BLEND)
    }

    companion object {
        private const val BLOOM_EXTRACT_VS = """
attribute vec2 aPos;
varying vec2 vUV;
void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
"""
        private const val BLOOM_EXTRACT_FS = """
precision mediump float;
varying vec2 vUV;
uniform sampler2D uTex;
uniform float uThreshold;
void main() {
  vec4 c = texture2D(uTex, vUV);
  float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
  float bright = smoothstep(uThreshold, 1.0, lum);
  gl_FragColor = vec4(c.rgb * bright, c.a * bright);
}
"""
        private const val BLOOM_BLUR_VS = """
attribute vec2 aPos;
varying vec2 vUV;
void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
"""
        private const val BLOOM_BLUR_FS = """
precision mediump float;
varying vec2 vUV;
uniform sampler2D uTex;
uniform float uDir; // 1 = horizontal, 0 = vertical
uniform vec2 uResolution;
void main() {
  vec4 col = vec4(0.0);
  float weights[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
  vec2 offset = uDir == 1.0 ? vec2(1.0 / uResolution.x, 0.0) : vec2(0.0, 1.0 / uResolution.y);
  for (int i = -2; i <= 2; i++) {
    col += texture2D(uTex, vUV + offset * float(i)) * weights[i + 2];
  }
  gl_FragColor = col;
}
"""
        private const val BLOOM_COMBINE_VS = """
attribute vec2 aPos;
varying vec2 vUV;
void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
"""
        private const val BLOOM_COMBINE_FS = """
precision mediump float;
varying vec2 vUV;
uniform sampler2D uSource;
uniform sampler2D uBloom;
uniform float uIntensity;
void main() {
  vec4 src = texture2D(uSource, vUV);
  vec4 bloom = texture2D(uBloom, vUV);
  gl_FragColor = vec4(src.rgb + bloom.rgb * uIntensity, src.a);
}
"""
    }
}
