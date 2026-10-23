package com.hustlecity.game.render.gpu

/**
 * GLSL ES 3.00 shaders. The WebGL2 test harness (tools/glview) runs these exact strings, which the frame-dump test
 * writes next to each dumped frame, so what the harness shows is what phones render.
 */
object Shaders {
    /** World geometry: the camera math mirrors [com.hustlecity.game.render.Projector] so Canvas overlays line up. */
    const val WORLD_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aColor;
layout(location = 3) in float aFlags;
uniform vec3 uCam;
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uFwd;
uniform vec3 uProj;      // focal, centerX, centerY (pixels, y down)
uniform vec2 uViewport;  // width, height
uniform vec2 uNearFar;
uniform mat4 uLight;     // world -> sun shadow map clip space
uniform vec3 uSunDir;
uniform vec4 uShadow;    // strength, normal offset (m), texel size (uv), enabled
out vec3 vPos;
out vec3 vNormal;
out vec4 vColor;
out float vFlags;
out vec4 vLight;
void main() {
    vec3 d = aPos - uCam;
    float x = dot(d, uRight);
    float y = dot(d, uUp);
    float z = dot(d, uFwd);
    float n = uNearFar.x;
    float f = uNearFar.y;
    vec2 hw = uViewport * 0.5;
    gl_Position = vec4((uProj.x * x + (uProj.y - hw.x) * z) / hw.x,
                       (uProj.x * y + (hw.y - uProj.z) * z) / hw.y,
                       z * (f + n) / (f - n) - 2.0 * f * n / (f - n),
                       z);
    vPos = aPos;
    vNormal = aNormal;
    vColor = aColor;
    vFlags = aFlags;
    // look up the shadow a little off the surface, toward the sun, to avoid acne
    vec3 sn = aNormal * (dot(aNormal, uSunDir) >= 0.0 ? 1.0 : -1.0);
    vLight = uLight * vec4(aPos + sn * uShadow.y, 1.0);
}
"""

    const val WORLD_FS = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vPos;
in vec3 vNormal;
in vec4 vColor;
in float vFlags;
in vec4 vLight;
uniform vec3 uCam;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uSkyAmb;
uniform vec3 uGroundAmb;
uniform vec3 uFogColor;
uniform vec3 uFog;       // start, end, amount
uniform vec4 uMisc;      // daylight, wetness, exposure, mode (0 city, 1 interior, 2 map)
uniform vec3 uSkyTop;
uniform vec3 uSkyHorizon;
uniform vec4 uShadow;    // strength, normal offset, texel size, enabled
uniform float uTime;
uniform sampler2DShadow uShadowMap;
out vec4 oColor;

vec3 toLinear(vec3 c) { return c * c; }
vec3 toDisplay(vec3 c) { return sqrt(max(c, vec3(0.0))); }

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p); vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
#ifdef FAST
// one octave keeps the look (same average) at a quarter of the cost
float fbm(vec2 p) { return noise(p) * 0.875; }
float noise3(vec3 p) { return noise(p.xy + p.z * 1.7); }
#else
float fbm(vec2 p) { return noise(p) * 0.5 + noise(p * 2.03 + 7.1) * 0.25 + noise(p * 4.07 + 3.3) * 0.125; }
float noise3(vec3 p) { return noise(p.xy + p.z * 1.7) * 0.6 + noise(p.yz * 1.3 - p.x * 0.9) * 0.4; }
#endif

// surface coordinates: horizontal faces use world xy, walls use (along the wall, height)
vec2 planar(vec3 p, vec3 n) {
    if (abs(n.z) > 0.7) return p.xy;
    vec2 t = normalize(vec2(-n.y, n.x) + vec2(1e-5, 0.0));
    return vec2(dot(p.xy, t), p.z);
}

// pattern contrast fades out where the pattern gets smaller than a pixel (no shimmering far away)
float fadeFor(vec2 uv) { vec2 w = fwidth(uv); return 1.0 - smoothstep(0.18, 0.55, max(w.x, w.y)); }

float shadowAt() {
    if (uShadow.w < 0.5) return 1.0;
    vec3 c = vLight.xyz / vLight.w * 0.5 + 0.5;
    if (c.x <= 0.001 || c.x >= 0.999 || c.y <= 0.001 || c.y >= 0.999 || c.z >= 1.0) return 1.0;
#ifdef FAST
    // a single tap: linear filtering on the depth compare already blends 2x2 texels
    return mix(1.0, texture(uShadowMap, c), uShadow.x);
#else
    float t = uShadow.z;
    float s = 0.0;
    s += texture(uShadowMap, vec3(c.xy + vec2(-t, -t), c.z));
    s += texture(uShadowMap, vec3(c.xy + vec2(t, -t), c.z));
    s += texture(uShadowMap, vec3(c.xy + vec2(-t, t), c.z));
    s += texture(uShadowMap, vec3(c.xy + vec2(t, t), c.z));
    s += texture(uShadowMap, vec3(c.xy, c.z)) * 2.0;
    s += texture(uShadowMap, vec3(c.xy + vec2(0.0, 1.6 * t), c.z));
    s += texture(uShadowMap, vec3(c.xy + vec2(0.0, -1.6 * t), c.z));
    s /= 8.0;
    return mix(1.0, s, uShadow.x);
#endif
}

void main() {
    vec3 albedo = toLinear(vColor.rgb);
    vec3 V = normalize(uCam - vPos);
    float flags = vFlags + 0.01;
    bool emissive = mod(flags, 2.0) >= 1.0;
    int mat = int(floor(flags / 2.0));
    vec3 N = normalize(vNormal);
    if (dot(N, V) < 0.0) N = -N;
    float spec = 0.06;
    float gloss = 28.0;
    float reflectAmt = 0.0;
    vec2 uv = planar(vPos, N);
    bool wall = abs(N.z) < 0.5;
    if (mat == 1 && wall) {            // brick: staggered courses with mortar joints
        vec2 b = uv * vec2(1.0 / 0.46, 1.0 / 0.16);
        float row = floor(b.y);
        b.x += mod(row, 2.0) * 0.5;
        vec2 f = fract(b);
        float mortar = 1.0 - step(0.07, f.x) * step(0.13, f.y);
        float v = 0.86 + 0.24 * hash(floor(b));
        float k = fadeFor(b);
        albedo *= mix(1.0, mix(v, 1.45, mortar), k) * (0.94 + 0.12 * fbm(uv * 0.7));
    } else if (mat == 2 && wall) {     // dressed stone blocks
        vec2 b = uv * vec2(1.0 / 1.2, 1.0 / 0.55);
        b.x += mod(floor(b.y), 2.0) * 0.5;
        vec2 f = fract(b);
        float joint = 1.0 - step(0.025, f.x) * step(0.05, f.y);
        albedo *= mix(1.0, mix(0.93 + 0.12 * hash(floor(b)), 0.78, joint), fadeFor(b)) * (0.95 + 0.1 * fbm(uv * 2.0));
    } else if (mat == 3) {             // glass: reflects the sky, more at grazing angles
        reflectAmt = 0.18 + 0.6 * pow(1.0 - max(dot(N, V), 0.0), 4.0);
        spec = 0.9; gloss = 140.0;
        if (wall) albedo *= 0.85 + 0.25 * noise(vec2(floor(uv.x / 1.5), floor(uv.y / 3.3)) * 3.1);
    } else if (mat == 4) {             // asphalt: grain and worn patches
        albedo *= 0.86 + 0.22 * fbm(vPos.xy * 0.18) + 0.1 * (noise(vPos.xy * 9.0) - 0.5) * fadeFor(vPos.xy * 9.0);
        spec = 0.04 + 0.3 * uMisc.y; gloss = 40.0 + 80.0 * uMisc.y;
    } else if (mat == 5) {             // sidewalk slabs
        vec2 b = vPos.xy / 1.5;
        vec2 f = fract(b);
        float line = 1.0 - step(0.03, f.x) * step(0.03, f.y);
        albedo *= mix(1.0, mix(0.95 + 0.08 * hash(floor(b)), 0.78, line), fadeFor(b)) * (0.96 + 0.08 * fbm(vPos.xy * 0.5));
    } else if (mat == 6) {             // grass
        float g = fbm(vPos.xy * 0.6);
        albedo *= 0.78 + 0.42 * g;
        albedo.r *= 0.92 + 0.2 * noise(vPos.xy * 0.13);
        albedo *= 0.94 + 0.12 * (noise(vPos.xy * 6.0) - 0.5) * fadeFor(vPos.xy * 6.0) + 0.06;
    } else if (mat == 7) {             // wood planks
        vec2 b = vec2(uv.x * 6.0, uv.y * 0.8);
        float plank = floor(b.x);
        b.y += hash(vec2(plank, 3.0)) * 7.0;
        float seam = 1.0 - step(0.04, fract(b.x));
        float grain = noise(vec2(b.x * 3.0, b.y * 22.0));
        albedo *= mix(1.0, (0.86 + 0.2 * hash(vec2(plank, floor(b.y)))) * (0.9 + 0.2 * grain) * (1.0 - 0.35 * seam), fadeFor(b));
        spec = 0.12; gloss = 50.0;
    } else if (mat == 8) {             // floor tiles: polished with speckles
        albedo *= 0.95 + 0.1 * noise(vPos.xy * 14.0) * fadeFor(vPos.xy * 14.0);
        spec = 0.25; gloss = 70.0;
    } else if (mat == 9 && wall) {     // corrugated metal
        float r = sin(uv.x * 6.2832 / 0.35);
        albedo *= mix(1.0, 0.86 + 0.14 * r, fadeFor(uv / 0.35));
        spec = 0.35; gloss = 60.0;
    } else if (mat == 10) {            // roof gravel and membrane
        albedo *= 0.88 + 0.2 * fbm(vPos.xy * 0.9) + 0.08 * (hash(floor(vPos.xy * 6.0)) - 0.5) * fadeFor(vPos.xy * 6.0);
    } else if (mat == 11) {            // water: moving ripples that reflect the sky
        vec2 q = vPos.xy * 0.08 + vec2(uTime * 0.03, uTime * 0.02);
        float e = 0.5;
        vec2 grad = vec2(fbm(q + vec2(e, 0.0)) - fbm(q - vec2(e, 0.0)), fbm(q + vec2(0.0, e)) - fbm(q - vec2(0.0, e)));
        N = normalize(vec3(-grad * 0.9, 1.0));
        reflectAmt = 0.25 + 0.55 * pow(1.0 - max(dot(N, V), 0.0), 3.0);
        spec = 1.2; gloss = 220.0;
    } else if (mat == 12 && wall) {    // painted plaster
        albedo *= 0.96 + 0.07 * fbm(uv * 3.0);
    } else if (mat == 13) {            // leafy canopy clumps
        float l = noise3(vPos * 1.6);
        albedo *= 0.72 + 0.55 * l;
        spec = 0.03;
    } else if (mat == 16) {            // glossy car paint
        spec = 0.7; gloss = 90.0;
        reflectAmt = 0.08 + 0.3 * pow(1.0 - max(dot(N, V), 0.0), 4.0);
    } else if (mat == 15) {            // cloth: a fine weave, no shine
        albedo *= 0.94 + 0.08 * noise(vPos.xy * 90.0 + vPos.z * 70.0) * fadeFor(vPos.xy * 90.0);
        spec = 0.02; gloss = 8.0;
    } else if (mat == 17) {            // skin: a soft sheen
        spec = 0.09; gloss = 18.0;
    } else if (mat == 18) {            // hair: strands catch the light
        albedo *= 0.88 + 0.22 * noise(vec2((vPos.x + vPos.y) * 160.0, vPos.z * 12.0));
        spec = 0.22; gloss = 36.0;
    } else if (mat == 14) {            // concrete panels
        vec2 b = uv / vec2(3.0, 3.3);
        vec2 f = fract(b);
        float joint = 1.0 - step(0.012, f.x) * step(0.015, f.y);
        albedo *= mix(1.0, mix(0.96 + 0.06 * hash(floor(b)), 0.8, joint), fadeFor(b)) * (0.94 + 0.12 * fbm(uv * 1.3));
    } else {
        albedo *= 0.97 + 0.06 * fbm(uv * 0.9);
    }

    vec3 col;
    if (emissive) {
        // light sources keep their color and glow a little more at night
        col = albedo * (1.15 + 0.75 * (1.0 - uMisc.x));
    } else {
        float sh = shadowAt();
        float ndl = max(dot(N, uSunDir), 0.0);
        float wrap = max((dot(N, uSunDir) + 0.3) / 1.3, 0.0);
        vec3 amb = mix(uGroundAmb, uSkyAmb, N.z * 0.5 + 0.5);
        // ambient occlusion: walls darken toward the ground
        float vertical = 1.0 - abs(N.z);
        // (people are lit as they are: the darkening is for walls)
        float ao = (mat == 15 || mat >= 17) ? 1.0 : mix(1.0, mix(0.55, 1.0, smoothstep(0.0, 3.5, vPos.z)), vertical);
        vec3 H = normalize(uSunDir + V);
        float wetSpec = uMisc.y * step(0.9, N.z);
        float s = pow(max(dot(N, H), 0.0), gloss + 120.0 * wetSpec) * (spec + 0.6 * wetSpec);
        // skin and cloth wrap the light further round, like soft materials do
        vec3 direct = uSunColor * mix(ndl, wrap, (mat == 17 || mat == 15) ? 0.55 : 0.25) * sh;
        col = albedo * (amb * ao + direct) + uSunColor * s * ndl * sh;
        if (reflectAmt > 0.0) {
            vec3 R = reflect(-V, N);
            vec3 sky = toLinear(mix(uSkyHorizon, uSkyTop, smoothstep(-0.05, 0.6, R.z)));
            col = mix(col, sky * (0.55 + 0.45 * uMisc.x) + uSunColor * s, reflectAmt);
        }
        // wet streets mirror a little sky
        col += wetSpec * 0.1 * toLinear(uSkyHorizon) * (1.0 - abs(dot(N, V)));
    }
    col *= uMisc.z;
    // soft filmic shoulder so bright surfaces don't clip
    col = col * (1.0 + col * 0.08) / (1.0 + col * 0.5);
    if (uMisc.w < 0.5) {
        float dist = length(uCam - vPos);
        float t = clamp((dist - uFog.x) / max(uFog.y - uFog.x, 1.0), 0.0, 1.0) * uFog.z;
        col = mix(col, toLinear(uFogColor), t);
    }
    oColor = vec4(toDisplay(col), vColor.a);
}
"""

    /** The world fragment shader, optionally in its cheaper variant for mid-range GPUs. */
    fun worldFs(fast: Boolean) = if (fast) WORLD_FS.replaceFirst("precision highp float;", "precision highp float;\n#define FAST 1") else WORLD_FS

    /** Depth-only pass from the sun for shadow mapping. */
    const val SHADOW_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uLight;
void main() { gl_Position = uLight * vec4(aPos, 1.0); }
"""

    const val SHADOW_FS = """#version 300 es
precision mediump float;
void main() { }
"""

    /** Fullscreen sky gradient with a sun glow. */
    const val SKY_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
void main() { gl_Position = vec4(aPos, 0.999, 1.0); }
"""

    const val SKY_FS = """#version 300 es
precision highp float;
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uFwd;
uniform vec3 uProj;
uniform vec2 uViewport;
uniform vec3 uSkyTop;
uniform vec3 uSkyHorizon;
uniform vec3 uFogColor;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec4 uMisc;
out vec4 oColor;
void main() {
    float sx = gl_FragCoord.x;
    float sy = uViewport.y - gl_FragCoord.y;
    float a = (sx - uProj.y) / uProj.x;
    float b = -(sy - uProj.z) / uProj.x;
    vec3 dir = normalize(uFwd + a * uRight + b * uUp);
    float el = dir.z;
    vec3 sky = mix(uSkyHorizon, uSkyTop, smoothstep(-0.02, 0.45, el));
    sky = mix(uFogColor, sky, smoothstep(-0.08, 0.02, el));
    float s = max(dot(dir, uSunDir), 0.0);
    sky += uSunColor * (pow(s, 64.0) * 0.6 + pow(s, 8.0) * 0.12) * uMisc.x;
    oColor = vec4(sky, 1.0);
}
"""
}
