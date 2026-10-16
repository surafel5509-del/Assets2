package com.sengine.engine.render

import android.opengl.GLES20
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Scene
import com.sengine.engine.math.Mat4
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** Forward Blinn-Phong mesh renderer with gradient sky, fog, 1 directional + 4 point lights. */
class Renderer3D {
    lateinit var shaders: ShaderLibrary
    private var skyProg = 0
    private var skyPos = 0; private var skyInv = 0; private var skyTop = 0; private var skyHorizon = 0; private var skyGround = 0
    private var skyTexLoc = 0; private var skyUseTex = 0; private var skySunDir = 0; private var skySunColor = 0
    private var skySunParams = 0; private var skyExposure = 0
    private lateinit var fullscreen: FloatBuffer

    // water
    private var waterProg = 0
    private var wPos = 0; private var wNormal = 0; private var wUV = 0
    private var wMVP = 0; private var wModel = 0; private var wTime = 0; private var wColors = 0
    private var wDeep = 0; private var wParams = 0; private var wCam = 0; private var wSunDir = 0; private var wSunColor = 0

    private val mvp = FloatArray(16)
    private val inv = FloatArray(16)
    private val normalMat = FloatArray(16)

    // per-frame lighting
    private val ambient = FloatArray(3)
    private val dirDir = floatArrayOf(-0.4f, -1f, -0.3f)
    private val dirColor = FloatArray(3)
    private val pointPos = FloatArray(16)
    private val pointColor = FloatArray(12)
    private val fogColor = FloatArray(3)
    private val fog = FloatArray(3)
    lateinit var view: View3D

    var drawCalls = 0
    var time = 0f
    var resW = 1f
    var resH = 1f

    fun init(shaders: ShaderLibrary) {
        this.shaders = shaders
        skyProg = GL.compile(SKY_VS, SKY_FS)
        skyPos = GLES20.glGetAttribLocation(skyProg, "aPos")
        skyInv = GLES20.glGetUniformLocation(skyProg, "uInvVP")
        skyTop = GLES20.glGetUniformLocation(skyProg, "uTop")
        skyHorizon = GLES20.glGetUniformLocation(skyProg, "uHorizon")
        skyGround = GLES20.glGetUniformLocation(skyProg, "uGround")
        skyTexLoc = GLES20.glGetUniformLocation(skyProg, "uSkyTex")
        skyUseTex = GLES20.glGetUniformLocation(skyProg, "uUseTex")
        skySunDir = GLES20.glGetUniformLocation(skyProg, "uSunDir")
        skySunColor = GLES20.glGetUniformLocation(skyProg, "uSunColor")
        skySunParams = GLES20.glGetUniformLocation(skyProg, "uSunParams")
        skyExposure = GLES20.glGetUniformLocation(skyProg, "uExposure")
        waterProg = GL.compile(WATER_VS, WATER_FS)
        wPos = GLES20.glGetAttribLocation(waterProg, "aPos"); wNormal = GLES20.glGetAttribLocation(waterProg, "aNormal"); wUV = GLES20.glGetAttribLocation(waterProg, "aUV")
        wMVP = GLES20.glGetUniformLocation(waterProg, "uMVP"); wModel = GLES20.glGetUniformLocation(waterProg, "uModel")
        wTime = GLES20.glGetUniformLocation(waterProg, "uTime"); wColors = GLES20.glGetUniformLocation(waterProg, "uColor")
        wDeep = GLES20.glGetUniformLocation(waterProg, "uDeep"); wParams = GLES20.glGetUniformLocation(waterProg, "uParams")
        wCam = GLES20.glGetUniformLocation(waterProg, "uCamPos"); wSunDir = GLES20.glGetUniformLocation(waterProg, "uSunDir"); wSunColor = GLES20.glGetUniformLocation(waterProg, "uSunColor")
        fullscreen = GL.floatBuffer(8)
        fullscreen.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
    }

    /**
     * Draws the sky: gradient or a real 360° sky texture, plus the visible sun
     * disc (size / halo / shader style come from the scene's [com.sengine.engine.core.Sun]).
     */
    fun drawSky(v: View3D, top: Int, horizon: Int, ground: Int = 0xFF5B6B52.toInt(),
                skyTexture: Tex? = null, sun: com.sengine.engine.core.Sun? = null, exposure: Float = 1f) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(skyProg)
        GLES20.glUniformMatrix4fv(skyInv, 1, false, v.invViewProj, 0)
        GLES20.glUniform3f(skyTop, GL.r(top), GL.g(top), GL.b(top))
        GLES20.glUniform3f(skyHorizon, GL.r(horizon), GL.g(horizon), GL.b(horizon))
        GLES20.glUniform3f(skyGround, GL.r(ground), GL.g(ground), GL.b(ground))
        GLES20.glUniform1f(skyExposure, exposure)
        if (skyTexture != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, skyTexture.id)
            GLES20.glUniform1i(skyTexLoc, 0)
            GLES20.glUniform1f(skyUseTex, 1f)
        } else GLES20.glUniform1f(skyUseTex, 0f)
        if (sun != null) {
            val d = sun.sunDirection()
            val c = sun.effectiveColor()
            val glow = sun.sunshine * (0.25f + sun.dayFactor())
            GLES20.glUniform3f(skySunDir, d[0], d[1], d[2])
            GLES20.glUniform3f(skySunColor, GL.r(c) * glow, GL.g(c) * glow, GL.b(c) * glow)
            // x: cos(sun angular size), y: halo strength, z: shader style, w: sunshine
            GLES20.glUniform4f(skySunParams, kotlin.math.cos(sun.sunSize.toDouble()).toFloat(), sun.halo, sun.shader.toFloat(), sun.sunshine)
        } else {
            GLES20.glUniform3f(skySunDir, 0.3f, 0.8f, 0.4f)
            GLES20.glUniform3f(skySunColor, 0f, 0f, 0f)
            GLES20.glUniform4f(skySunParams, 0.999f, 0f, 0f, 0f)
        }
        fullscreen.position(0)
        GLES20.glEnableVertexAttribArray(skyPos)
        GLES20.glVertexAttribPointer(skyPos, 2, GLES20.GL_FLOAT, false, 0, fullscreen)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(skyPos)
        GLES20.glDepthMask(true)
        drawCalls++
    }

    /** Animated water surface for a [com.sengine.engine.core.Water] component. */
    fun drawWater(mesh: Mesh, model: FloatArray, w: com.sengine.engine.core.Water) {
        GLES20.glUseProgram(waterProg)
        Mat4.mul(mvp, view.viewProj, model)
        GLES20.glUniformMatrix4fv(wMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(wModel, 1, false, model, 0)
        GLES20.glUniform1f(wTime, time)
        GLES20.glUniform4f(wColors, GL.r(w.color), GL.g(w.color), GL.b(w.color), w.opacity)
        GLES20.glUniform3f(wDeep, GL.r(w.deepColor), GL.g(w.deepColor), GL.b(w.deepColor))
        // x waveHeight, y waveSpeed, z waveScale, w specular (+ foam packed in sun color alpha path)
        GLES20.glUniform4f(wParams, w.waveHeight, w.waveSpeed, w.waveScale, w.specular)
        GLES20.glUniform3f(wCam, view.eye[0], view.eye[1], view.eye[2])
        GLES20.glUniform3f(wSunDir, -dirDir[0], -dirDir[1], -dirDir[2])
        GLES20.glUniform3f(wSunColor, dirColor[0], dirColor[1], dirColor[2])
        val b = mesh.buffer
        b.position(0)
        GLES20.glEnableVertexAttribArray(wPos)
        GLES20.glVertexAttribPointer(wPos, 3, GLES20.GL_FLOAT, false, 32, b)
        if (wNormal >= 0) { b.position(3); GLES20.glEnableVertexAttribArray(wNormal); GLES20.glVertexAttribPointer(wNormal, 3, GLES20.GL_FLOAT, false, 32, b) }
        if (wUV >= 0) { b.position(6); GLES20.glEnableVertexAttribArray(wUV); GLES20.glVertexAttribPointer(wUV, 2, GLES20.GL_FLOAT, false, 32, b) }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
        GLES20.glDisableVertexAttribArray(wPos)
        if (wNormal >= 0) GLES20.glDisableVertexAttribArray(wNormal)
        if (wUV >= 0) GLES20.glDisableVertexAttribArray(wUV)
        drawCalls++
    }

    /** Collects lights from the scene. With no lights, a default sun is used. */
    fun setupLights(scene: Scene, v: View3D) {
        view = v
        val amb = scene.ambient
        ambient[0] = GL.r(amb); ambient[1] = GL.g(amb); ambient[2] = GL.b(amb)
        fogColor[0] = GL.r(scene.fogColor); fogColor[1] = GL.g(scene.fogColor); fogColor[2] = GL.b(scene.fogColor)
        fog[0] = scene.fogStart; fog[1] = scene.fogEnd; fog[2] = if (scene.fog) 1f else 0f
        // A Sun component (colour / sunshine / temperature editor) wins over plain lights.
        val sun = scene.objects.firstOrNull { o -> o.isActiveInHierarchy() && o.getAny<com.sengine.engine.core.Sun>() != null }
            ?.getAny<com.sengine.engine.core.Sun>()
        if (sun != null) {
            val d = sun.lightDirection()
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6f)
            dirDir[0] = d[0] / len; dirDir[1] = d[1] / len; dirDir[2] = d[2] / len
            val c = sun.effectiveColor()
            val i = sun.sunshine * (0.12f + 0.88f * sun.dayFactor())
            dirColor[0] = GL.r(c) * i; dirColor[1] = GL.g(c) * i; dirColor[2] = GL.b(c) * i
        }
        var hasDir = sun != null
        if (sun == null) dirColor.fill(0f)
        pointPos.fill(0f); pointColor.fill(0f)
        // default: sun from above (only when there is no Sun component)
        if (sun == null) { dirDir[0] = -0.4f; dirDir[1] = -1f; dirDir[2] = -0.3f }
        var points = 0
        // point lights: nearest 4 to camera
        val pts = ArrayList<Pair<Float, Pair<FloatArray, Light>>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val l = go.get<Light>() ?: continue
            val w = go.world3
            if (l.kind == 0) {
                if (!hasDir) {
                    val d = Mat4.dir(w, 0f, 0f, -1f)
                    val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6f)
                    dirDir[0] = d[0] / len; dirDir[1] = d[1] / len; dirDir[2] = d[2] / len
                    dirColor[0] = GL.r(l.color) * l.intensity; dirColor[1] = GL.g(l.color) * l.intensity; dirColor[2] = GL.b(l.color) * l.intensity
                    hasDir = true
                }
            } else {
                pts.add(v.distanceTo(w[12], w[13], w[14]) to (floatArrayOf(w[12], w[13], w[14]) to l))
            }
        }
        val anyLight = hasDir || pts.isNotEmpty()
        if (!anyLight) { dirColor[0] = 0.85f; dirColor[1] = 0.83f; dirColor[2] = 0.8f }
        for ((_, pl) in pts.sortedBy { it.first }) {
            if (points >= 4) break
            val (p, l) = pl
            pointPos[points * 4] = p[0]; pointPos[points * 4 + 1] = p[1]; pointPos[points * 4 + 2] = p[2]; pointPos[points * 4 + 3] = l.range
            pointColor[points * 3] = GL.r(l.color) * l.intensity; pointColor[points * 3 + 1] = GL.g(l.color) * l.intensity; pointColor[points * 3 + 2] = GL.b(l.color) * l.intensity
            points++
        }
    }

    fun drawMesh(mesh: Mesh, model: FloatArray, mr: MeshRenderer, tex: Tex?, program: MeshProgram?,
                 vertexStart: Int = 0, vertexCount: Int = -1,
                 rampTopColor: Int? = null, windStrength: Float = 0f, windSpeed: Float = 0f) {
        val p = program ?: shaders.defaultMesh
        GLES20.glUseProgram(p.id)
        Mat4.mul(mvp, view.viewProj, model)
        Mat4.invert(inv, model)
        Mat4.transpose(normalMat, inv)
        GLES20.glUniformMatrix4fv(p.uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(p.uModel, 1, false, model, 0)
        GLES20.glUniformMatrix4fv(p.uNormalMat, 1, false, normalMat, 0)
        GLES20.glUniform4f(p.uColor, GL.r(mr.color), GL.g(mr.color), GL.b(mr.color), GL.a(mr.color))
        if (p.uColorB >= 0 && rampTopColor != null) {
            GLES20.glUniform4f(p.uColorB, GL.r(rampTopColor), GL.g(rampTopColor), GL.b(rampTopColor), GL.a(rampTopColor))
            GLES20.glUniform1f(p.uRamp, 1f)
        } else if (p.uRamp >= 0) GLES20.glUniform1f(p.uRamp, 0f)
        if (p.uUVOffset >= 0) GLES20.glUniform2f(p.uUVOffset, mr.offsetX, mr.offsetY)
        if (p.uUVRot >= 0) GLES20.glUniform1f(p.uUVRot, Math.toRadians(mr.uvRotation.toDouble()).toFloat())
        if (p.uWind >= 0) GLES20.glUniform3f(p.uWind, windStrength, windSpeed, 0f)
        if (p.uTime >= 0) GLES20.glUniform1f(p.uTime, time)
        GLES20.glUniform1f(p.uTiling, mr.tiling)
        GLES20.glUniform3f(p.uCamPos, view.eye[0], view.eye[1], view.eye[2])
        GLES20.glUniform3fv(p.uAmbient, 1, ambient, 0)
        GLES20.glUniform3fv(p.uDirDir, 1, dirDir, 0)
        GLES20.glUniform3fv(p.uDirColor, 1, dirColor, 0)
        GLES20.glUniform4fv(p.uPointPos, 4, pointPos, 0)
        GLES20.glUniform3fv(p.uPointColor, 4, pointColor, 0)
        GLES20.glUniform1f(p.uSpec, mr.specular)
        GLES20.glUniform1f(p.uShine, mr.shininess)
        GLES20.glUniform1f(p.uEmission, mr.emission)
        GLES20.glUniform1f(p.uUnlit, if (mr.unlit) 1f else 0f)
        GLES20.glUniform3fv(p.uFogColor, 1, fogColor, 0)
        GLES20.glUniform3fv(p.uFog, 1, fog, 0)
        if (p.uTime >= 0) GLES20.glUniform1f(p.uTime, time)
        if (p.uParam >= 0) GLES20.glUniform1f(p.uParam, mr.shaderParam)
        if (p.uResolution >= 0) GLES20.glUniform2f(p.uResolution, resW, resH)
        if (tex != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex.id)
            // per-material texture system: wrap mode + pixel filtering, per object
            if (isPot(tex.w) && isPot(tex.h)) {
                val wrap = when (mr.wrapMode) { 1 -> GLES20.GL_CLAMP_TO_EDGE; 2 -> 0x8370 /* GL_MIRRORED_REPEAT */; else -> GLES20.GL_REPEAT }
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
            }
            if (mr.pixelFilter) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            } else {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            }
            GLES20.glUniform1i(p.uTex, 0)
            GLES20.glUniform1f(p.uUseTex, 1f)
        } else GLES20.glUniform1f(p.uUseTex, 0f)
        val b = mesh.buffer
        // NOTE: FloatBuffer positions are in float units; vertex ranges are converted by callers via byte-free offsets below.
        b.position(vertexStart * 8)
        GLES20.glEnableVertexAttribArray(p.aPos)
        GLES20.glVertexAttribPointer(p.aPos, 3, GLES20.GL_FLOAT, false, 32, b)
        if (p.aNormal >= 0) {
            b.position(vertexStart * 8 + 3)
            GLES20.glEnableVertexAttribArray(p.aNormal)
            GLES20.glVertexAttribPointer(p.aNormal, 3, GLES20.GL_FLOAT, false, 32, b)
        }
        if (p.aUV >= 0) {
            b.position(vertexStart * 8 + 6)
            GLES20.glEnableVertexAttribArray(p.aUV)
            GLES20.glVertexAttribPointer(p.aUV, 2, GLES20.GL_FLOAT, false, 32, b)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, if (vertexCount > 0) vertexCount else mesh.vertexCount)
        GLES20.glDisableVertexAttribArray(p.aPos)
        if (p.aNormal >= 0) GLES20.glDisableVertexAttribArray(p.aNormal)
        if (p.aUV >= 0) GLES20.glDisableVertexAttribArray(p.aUV)
        drawCalls++
    }

    private fun isPot(n: Int) = n > 0 && (n and (n - 1)) == 0

    companion object {
        const val SKY_VS = """
attribute vec2 aPos;
varying vec2 vNdc;
void main() { vNdc = aPos; gl_Position = vec4(aPos, 0.9999, 1.0); }
"""
        const val SKY_FS = """
precision mediump float;
varying vec2 vNdc;
uniform mat4 uInvVP;
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uGround;
uniform sampler2D uSkyTex;
uniform float uUseTex;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec4 uSunParams;
uniform float uExposure;
void main() {
  vec4 a = uInvVP * vec4(vNdc, -1.0, 1.0);
  vec4 b = uInvVP * vec4(vNdc, 1.0, 1.0);
  vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
  vec3 c;
  if (uUseTex > 0.5) {
    // equirectangular real sky texture
    vec2 uv = vec2(atan(d.z, d.x) / 6.2831853 + 0.5, acos(clamp(d.y, -1.0, 1.0)) / 3.14159265);
    c = texture2D(uSkyTex, uv).rgb;
    if (d.y < 0.0) c = mix(c, uGround, clamp(-d.y * 3.0, 0.0, 0.85));
  } else if (d.y >= 0.0) c = mix(uHorizon, uTop, pow(d.y, 0.6));
  else c = mix(uHorizon, uGround, clamp(-d.y * 4.0, 0.0, 1.0));
  // ---- visible sun (small disc in the sky, editor-driven) ----
  float cosang = dot(d, normalize(uSunDir));
  float discCos = uSunParams.x;
  float style = uSunParams.z;
  float disc = smoothstep(discCos, discCos + 0.0015, cosang);
  if (style > 3.5) disc = step(discCos, cosang);                    // cartoon: hard edge
  float halo = pow(max(cosang, 0.0), mix(90.0, 700.0, clamp(uSunParams.y, 0.0, 1.0))) * uSunParams.y;
  if (style > 0.5 && style < 1.5) halo *= 1.8;                      // soft glow
  if (style > 1.5 && style < 2.5) {                                // golden flare
    halo += pow(max(cosang, 0.0), 18.0) * 0.35 * uSunParams.y;
    disc *= 1.2;
  }
  if (style > 2.5 && style < 3.5) { halo *= 0.25; disc *= 0.9; }    // moon
  float sunUp = clamp(normalize(uSunDir).y * 4.0 + 0.35, 0.0, 1.0);
  c += uSunColor * (disc * 1.6 + halo) * sunUp;
  gl_FragColor = vec4(c * uExposure, 1.0);
}
"""

        const val WATER_VS = """
uniform mat4 uMVP;
uniform mat4 uModel;
uniform float uTime;
uniform vec4 uParams;
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUV;
varying vec3 vWorld;
varying vec2 vUV;
varying float vWave;
void main() {
  vec3 p = aPos;
  vec4 wp = uModel * vec4(aPos, 1.0);
  float w1 = sin(wp.x * uParams.z + uTime * uParams.y);
  float w2 = cos(wp.z * uParams.z * 1.3 + uTime * uParams.y * 0.8);
  float w3 = sin((wp.x + wp.z) * uParams.z * 0.55 - uTime * uParams.y * 0.6);
  vWave = (w1 + w2 + w3) / 3.0;
  p.y += vWave * uParams.x;
  vWorld = (uModel * vec4(p, 1.0)).xyz;
  vUV = aUV;
  gl_Position = uMVP * vec4(p, 1.0);
}
"""

        const val WATER_FS = """
precision mediump float;
varying vec3 vWorld;
varying vec2 vUV;
varying float vWave;
uniform vec4 uColor;
uniform vec3 uDeep;
uniform vec4 uParams;
uniform float uTime;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
void main() {
  vec3 viewDir = normalize(uCamPos - vWorld);
  float fres = pow(1.0 - max(viewDir.y, 0.0), 2.0);
  vec3 col = mix(uDeep, uColor.rgb, 0.35 + 0.4 * fres + 0.25 * (vWave * 0.5 + 0.5));
  // moving sparkle / foam crests
  float sparkle = sin(vWorld.x * 4.0 + uTime * 2.0) * sin(vWorld.z * 5.0 - uTime * 1.7);
  float foamBand = smoothstep(0.72, 0.98, vWave) * 0.35;
  col += vec3(foamBand);
  col += uSunColor * pow(max(dot(reflect(-normalize(uSunDir), vec3(0.0, 1.0, 0.0)), viewDir), 0.0), 64.0) * uParams.w;
  col += vec3(0.04) * smoothstep(0.75, 1.0, sparkle);
  gl_FragColor = vec4(col, uColor.a);
}
"""
    }
}
