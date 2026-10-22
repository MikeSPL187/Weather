in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in vec4 vParams;
in vec4 vShadow;

uniform float uWet;
uniform float uSnow;
uniform float uWindowGlow;
uniform float uRain;
uniform float uDay;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    float mat = vParams.x;
    vec3 albedo = vColor;
    vec3 V = normalize(uCamPos - vWorld);
    vec3 L = normalize(uLightDir);
    float ndl = max(dot(n, L), 0.0);
    float sh = ndl > 0.0 ? shadowAt(vShadow, ndl) : 0.0;

    // Snow settles on faces that look up; rain darkens and glosses what it soaks.
    float snowMask = uSnow * vParams.y * smoothstep(0.35, 0.8, n.y);
    bool porous = mat < 0.5 || abs(mat - 3.0) < 0.5 || abs(mat - 8.0) < 0.5;
    if (porous) albedo *= 1.0 - 0.32 * uWet * (1.0 - snowMask);
    albedo = mix(albedo, vec3(0.86, 0.9, 0.97), snowMask);

    vec3 hemi = mix(uGroundAmb, uSkyAmb, n.y * 0.5 + 0.5);
    float ao = vParams.z;
    vec3 color = albedo * (hemi * ao + uLightColor * ndl * sh + lampLight(vWorld, n));

    // Sky-coloured rim keeps silhouettes crisp against the background.
    float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
    color += skyColor(vec3(0.0, 0.3, 0.0)) * rim * 0.14 * ao;

    vec3 H = normalize(L + V);
    vec3 R = reflect(-V, n);
    float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
    if (abs(mat - 5.0) < 0.5) {
        // Gilded and enamelled: tinted reflections of the sky and a sharp highlight.
        color = albedo * (hemi * ao * 0.45 + uLightColor * ndl * sh * 0.7);
        color += albedo * skyColor(R) * (0.35 + 0.5 * fresnel);
        color += uLightColor * pow(max(dot(n, H), 0.0), 60.0) * sh * 1.6;
    } else if (abs(mat - 6.0) < 0.5) {
        // Glass towers mirror the sky.
        color = albedo * hemi * ao * 0.35 + skyColor(R) * (0.45 + 0.5 * fresnel) + uLightColor * pow(max(dot(n, H), 0.0), 120.0) * sh * 2.0;
        color = mix(color, color + vec3(1.0, 0.78, 0.45) * 0.5, uWindowGlow * step(0.5, fract(vWorld.y * 6.0 + vWorld.x * 3.0)) * 0.6);
    } else if (abs(mat - 1.0) < 0.5) {
        // Windows: dark glass reflecting the sky by day, warm light at night.
        vec3 glass = albedo * 0.35 + skyColor(R) * (0.25 + 0.5 * fresnel);
        float lit = step(0.32, vParams.w);
        vec3 warm = mix(vec3(1.0, 0.55, 0.22), vec3(1.0, 0.74, 0.42), fract(vParams.w * 7.0)) * 1.15;
        color = mix(glass, warm, uWindowGlow * lit);
    } else if (abs(mat - 4.0) < 0.5) {
        color = mix(color, vec3(1.0, 0.7, 0.36) * 2.2, uLampGlow);
    } else if (abs(mat - 9.0) < 0.5) {
        float blink = step(0.0, sin(uTime * 3.0));
        color = mix(color, vec3(1.0, 0.15, 0.1) * 3.0 * blink, 1.0 - uDay * 0.6);
    } else if (abs(mat - 10.0) < 0.5) {
        color = albedo * (0.6 + 0.6 * ndl * sh) + albedo * 0.4 * (1.0 - uDay);
    } else if (porous) {
        // Wet ground picks up a soft sheen, and raindrops splash on flat ground.
        color += uLightColor * pow(max(dot(n, H), 0.0), 24.0) * sh * 0.35 * uWet * (1.0 - snowMask);
        if (uRain > 0.01 && n.y > 0.9) {
            float splash = length(ripples(vWorld.xz * 1.6));
            color += (uSkyAmb + uLightColor * 0.3) * smoothstep(0.02, 0.06, splash) * 0.45 * uRain * (1.0 - snowMask);
        }
    }

    color += albedo * uFlash * 0.5;
    color = applyFog(color, vWorld);
    fragColor = vec4(toScreen(color), 1.0);
}
