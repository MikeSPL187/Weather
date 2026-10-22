precision highp float;
in vec2 vUv;
uniform vec3 uColor;
uniform float uAlpha;
uniform float uMoon;
out vec4 fragColor;
void main() {
    float r = length(vUv);
    // The disc fills the inner quarter; the rest of the quad is its glow.
    float disc = smoothstep(0.27, 0.25, r);
    if (uMoon > 0.5) {
        float bite = smoothstep(0.25, 0.27, length(vUv - vec2(0.1, 0.06)));
        disc *= mix(1.0, bite, 0.92);
        vec3 c = mix(vec3(0.72, 0.76, 0.9), vec3(1.0), smoothstep(0.25, -0.1, vUv.x + vUv.y));
        float glow = exp(-r * 4.5) * 0.35;
        float a = (disc + glow) * uAlpha;
        fragColor = vec4((c * disc + vec3(0.65, 0.72, 1.0) * glow) * uAlpha, a);
        return;
    }
    // A bright core warming towards the limb, inside a wide soft glow.
    vec3 core = mix(uColor, vec3(1.0, 0.98, 0.9), smoothstep(0.26, 0.0, r) * 0.85);
    float glow = exp(-r * 3.0) * 0.55 + exp(-r * 8.0) * 0.45;
    vec3 c = core * disc + uColor * glow * (1.0 - disc);
    float a = clamp(disc + glow * 0.8, 0.0, 1.0) * uAlpha;
    fragColor = vec4(c * uAlpha, a);
}
