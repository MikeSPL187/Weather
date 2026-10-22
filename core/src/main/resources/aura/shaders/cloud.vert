// Soft cloud puffs orbiting the island; banks appear one by one as cover grows.
layout(location = 0) in vec4 aCenter; // xyz, size
layout(location = 1) in vec4 aInfo;   // rank, shade, speed, seed
layout(location = 2) in vec2 aCorner;

uniform mat4 uViewProj;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform float uTime;
uniform float uCover;
uniform float uCloudDrop;
uniform float uCloudScale;
uniform float uMist;
uniform vec3 uCamPos;

out vec2 vUv;
out float vShade;
out float vAlpha;
out float vSeed;
out vec3 vWorld;

void main() {
    float a = uTime * aInfo.z;
    // The orbit is an ellipse: undo its squash, turn, squash again.
    vec2 xz = vec2(aCenter.x, aCenter.z / 0.7);
    xz = vec2(xz.x * cos(a) - xz.y * sin(a), xz.x * sin(a) + xz.y * cos(a));
    bool mist = aInfo.x > 1.5;
    vec3 c = vec3(xz.x, aCenter.y - (mist ? 0.0 : uCloudDrop), xz.y * 0.7);
    float size = aCenter.w * (mist ? 1.0 : uCloudScale);
    vec2 stretch = mist ? vec2(2.0, 0.55) : vec2(1.2, 0.85);
    vec3 p = c + (uCamRight * aCorner.x * stretch.x + uCamUp * aCorner.y * stretch.y) * size;
    vUv = aCorner;
    vShade = aInfo.y;
    vSeed = aInfo.w;
    vAlpha = mist ? uMist * 0.55 : smoothstep(aInfo.x - 0.06, aInfo.x + 0.04, uCover * 1.04);
    // Clouds between the camera and the island thin out so the landmark stays in view.
    vec3 axis = normalize(-uCamPos);
    vec3 rel = c - uCamPos;
    float along = dot(rel, axis);
    float lateral = length(rel - axis * along);
    float inFront = smoothstep(0.5, 3.0, length(uCamPos) - along);
    float overIsland = 1.0 - smoothstep(2.2, 4.2, lateral);
    vAlpha *= 1.0 - (mist ? 0.5 : 0.85) * inFront * overIsland;
    vWorld = p;
    gl_Position = uViewProj * vec4(p, 1.0);
}
