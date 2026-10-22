// Rain streaks and snowflakes falling through a box around the island.
layout(location = 0) in vec4 aSeed; // x, y (0..1), z, speed
layout(location = 1) in float aRank;
layout(location = 2) in vec2 aCorner;

uniform mat4 uViewProj;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform float uTime;
uniform float uAmount;
uniform float uKind; // 0 rain, 1 snow
uniform float uSlant;

out vec2 vUv;
out float vAlpha;

void main() {
    float height = 9.0;
    float speed = uKind > 0.5 ? 0.7 : 7.5;
    float fall = mod(aSeed.y * height + uTime * speed * aSeed.w, height);
    vec3 p = vec3(aSeed.x, 6.5 - fall, aSeed.z);
    vec3 dir = normalize(vec3(-uSlant, -1.0, 0.0));
    if (uKind > 1.5) {
        // Stars: fixed points on the sky behind the island, twinkling.
        vec3 s = vec3(aSeed.x * 2.4, 1.0 + aSeed.y * 10.0, -18.0 + aSeed.z);
        float twinkle = 0.6 + 0.4 * sin(uTime * (1.0 + aSeed.w * 2.0) + aSeed.x * 10.0);
        vUv = aCorner;
        vAlpha = step(aRank, 0.45) * twinkle;
        gl_Position = uViewProj * vec4(s + (uCamRight * aCorner.x + uCamUp * aCorner.y) * (0.035 + 0.04 * aSeed.w), 1.0);
        return;
    }
    if (uKind > 0.5) {
        p.x += sin(uTime * 0.9 + aSeed.x * 3.0) * 0.25;
        p += (uCamRight * aCorner.x + uCamUp * aCorner.y) * 0.035;
    } else {
        p.x -= fall * uSlant;
        p += uCamRight * aCorner.x * 0.009 + dir * aCorner.y * 0.16;
    }
    vUv = aCorner;
    vAlpha = step(aRank, uAmount);
    gl_Position = uViewProj * vec4(p, 1.0);
}
