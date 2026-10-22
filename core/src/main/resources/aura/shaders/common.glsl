// Shared lighting helpers; prepended to every lit fragment shader.
precision highp float;
precision highp sampler2DShadow;

uniform sampler2DShadow uShadowMap;
uniform float uShadowTexel;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uSkyAmb;
uniform vec3 uGroundAmb;
uniform vec3 uSkyTop;
uniform vec3 uSkyBottom;
uniform vec3 uFogColor;
uniform vec3 uCamPos;
uniform float uFog;
uniform float uTime;
uniform float uFlash;
uniform vec3 uLamps[8];
uniform float uLampGlow;

/** Warm pools of light from the street lamps after dusk. */
vec3 lampLight(vec3 world, vec3 n) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        vec3 d = uLamps[i] - world;
        float dist = length(d);
        if (dist < 0.001 || uLamps[i] == vec3(0.0)) continue;
        float fall = 1.0 / (1.0 + dist * dist * 22.0);
        sum += vec3(1.0, 0.66, 0.34) * fall * (0.35 + 0.65 * max(dot(n, d / dist), 0.0));
    }
    return sum * 2.2 * uLampGlow;
}

const vec2 POISSON[8] = vec2[8](
    vec2(-0.613, 0.617), vec2(0.170, -0.040), vec2(-0.299, -0.792), vec2(0.645, 0.493),
    vec2(-0.651, -0.172), vec2(0.421, -0.711), vec2(-0.040, 0.991), vec2(0.910, -0.211)
);

float shadowAt(vec4 lightPos, float ndl) {
    vec3 s = lightPos.xyz / lightPos.w * 0.5 + 0.5;
    if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0 || s.z > 1.0) return 1.0;
    float bias = mix(0.0025, 0.0008, ndl);
    float sum = 0.0;
    for (int i = 0; i < 8; i++) {
        sum += texture(uShadowMap, vec3(s.xy + POISSON[i] * uShadowTexel * 2.2, s.z - bias));
    }
    return sum / 8.0;
}

vec3 skyColor(vec3 dir) {
    return mix(uSkyBottom, uSkyTop, smoothstep(-0.1, 0.75, dir.y));
}

/** Fog thickens low down and far away; in clear weather only a faint haze remains. */
vec3 applyFog(vec3 color, vec3 world) {
    float d = length(world - uCamPos);
    float haze = 1.0 - exp(-d * 0.012);
    float low = exp(-max(world.y + 0.6, 0.0) * 0.9);
    float amount = clamp(haze * 0.18 + uFog * (0.12 + 0.38 * low) * (1.0 - exp(-d * 0.09)), 0.0, 0.8);
    return mix(color, uFogColor, amount);
}

/** Filmic curve, then linear to sRGB for the screen. */
vec3 toScreen(vec3 c) {
    c = c * (2.51 * c + 0.03) / (c * (2.43 * c + 0.59) + 0.14);
    return pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));
}

/** Expanding rings where raindrops land. */
vec2 ripples(vec2 p) {
    vec2 g = vec2(0.0);
    for (int i = 0; i < 3; i++) {
        vec2 cell = floor(p * 7.0 + float(i) * 0.37);
        vec2 f = fract(p * 7.0 + float(i) * 0.37) - 0.5;
        float h = fract(sin(dot(cell, vec2(12.9898, 78.233)) + float(i)) * 43758.5453);
        float t = fract(uTime * 0.9 + h);
        float d = length(f) - t * 0.5;
        float ring = sin(d * 40.0) * exp(-abs(d) * 18.0) * (1.0 - t);
        g += normalize(f + 0.0001) * ring;
    }
    return g * 0.12;
}
