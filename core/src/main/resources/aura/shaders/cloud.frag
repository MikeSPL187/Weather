precision highp float;
in vec2 vUv;
in float vShade;
in float vAlpha;
in float vSeed;
in vec3 vWorld;

uniform vec3 uCloudLit;
uniform vec3 uCloudShade;
uniform vec3 uLightDir;
uniform float uFlash;

out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
    return v;
}

vec3 toScreen(vec3 c) {
    c = c * (2.51 * c + 0.03) / (c * (2.43 * c + 0.59) + 0.14);
    return pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));
}

void main() {
    if (vAlpha < 0.01) discard;
    vec2 uv = vUv;
    float r = length(uv * vec2(1.0, 1.15));
    float n = fbm(uv * 2.6 + vSeed * 17.0);
    float d = r + (n - 0.5) * 0.7;
    float a = smoothstep(0.92, 0.62, d);
    if (a < 0.003) discard;
    // A fake sphere normal: lit on top and towards the light, grey underneath.
    vec3 normal = normalize(vec3(uv.x, uv.y + 0.25, sqrt(max(0.0, 1.0 - min(r, 1.0)))));
    float light = clamp(dot(normal, normalize(vec3(uLightDir.x, uLightDir.y + 0.4, 0.6))) * 0.5 + 0.5, 0.0, 1.0);
    float t = clamp(light * 0.7 + vShade * 0.35 + (uv.y * 0.5 + 0.5) * 0.25 - 0.15 + n * 0.2, 0.0, 1.0);
    vec3 color = mix(uCloudShade, uCloudLit, t) + vec3(uFlash * 0.8);
    float alpha = a * vAlpha * 0.9;
    fragColor = vec4(toScreen(color) * alpha, alpha);
}
