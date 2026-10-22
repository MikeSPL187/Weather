// A camera-facing disc in the sky for the sun or the moon.
layout(location = 0) in vec2 aCorner;
uniform mat4 uViewProj;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCenter;
uniform float uSize;
out vec2 vUv;
void main() {
    vUv = aCorner;
    vec3 p = uCenter + (uCamRight * aCorner.x + uCamUp * aCorner.y) * uSize;
    gl_Position = uViewProj * vec4(p, 1.0);
}
