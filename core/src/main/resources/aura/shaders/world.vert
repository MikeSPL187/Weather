// Island, buildings and water. Foliage sways in the wind, floating rocks bob.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec4 aParams; // material, snow, ambient occlusion, phase

uniform mat4 uViewProj;
uniform mat4 uLightViewProj;
uniform float uTime;
uniform float uWind;

out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out vec4 vParams;
out vec4 vShadow;

void main() {
    vec3 p = aPos;
    float mat = aParams.x;
    if (abs(mat - 3.0) < 0.5) {
        float h = max(p.y - 0.2, 0.0);
        p.x += sin(uTime * 1.7 + aParams.w) * 0.03 * h * uWind;
        p.z += cos(uTime * 1.3 + aParams.w) * 0.02 * h * uWind;
    }
    if (abs(mat - 8.0) < 0.5) {
        p.y += sin(uTime * 0.8 + aParams.w) * 0.14;
    }
    vWorld = p;
    vNormal = aNormal;
    vColor = aColor;
    vParams = aParams;
    vShadow = uLightViewProj * vec4(p, 1.0);
#ifdef SHADOW
    gl_Position = uLightViewProj * vec4(p, 1.0);
#else
    gl_Position = uViewProj * vec4(p, 1.0);
#endif
}
