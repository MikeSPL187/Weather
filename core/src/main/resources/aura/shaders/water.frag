in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in vec4 vParams;
in vec4 vShadow;

uniform float uPuddle;
uniform float uRain;
uniform float uSnow;

out vec4 fragColor;

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

void main() {
    bool puddle = abs(vParams.x - 7.0) < 0.5;
    vec3 n = vec3(0.0, 1.0, 0.0);
    vec2 wave = vec2(sin(vWorld.x * 9.0 + uTime * 1.3), cos(vWorld.z * 11.0 - uTime * 1.1)) * 0.025;
    n = normalize(n + vec3(wave.x, 0.0, wave.y) + vec3(ripples(vWorld.xz).x, 0.0, ripples(vWorld.xz).y) * uRain);
    vec3 V = normalize(uCamPos - vWorld);
    vec3 L = normalize(uLightDir);
    vec3 R = reflect(-V, n);
    float sh = shadowAt(vShadow, 1.0);
    float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
    vec3 hemi = mix(uGroundAmb, uSkyAmb, 0.8);
    vec3 body = vColor * (hemi + uLightColor * 0.4 * sh + lampLight(vWorld, vec3(0.0, 1.0, 0.0)));
    vec3 reflection = skyColor(R) * 1.1;
    vec3 color = mix(body, reflection, 0.35 + 0.6 * fresnel);
    color += uLightColor * pow(max(dot(R, L), 0.0), 220.0) * sh * 4.0;
    // Ponds and canals freeze over in snow.
    color = mix(color, vec3(0.78, 0.84, 0.92) * (hemi + uLightColor * 0.5 * sh), uSnow * 0.85);
    color += vec3(uFlash * 0.6);
    color = applyFog(color, vWorld);
    float alpha = puddle ? uPuddle * (1.0 - uSnow) * 0.9 : 0.92;
    if (alpha < 0.01) discard;
    fragColor = vec4(toScreen(color) * alpha, alpha);
}
