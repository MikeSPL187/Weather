precision highp float;
in float vAcross;
uniform float uFlash;
out vec4 fragColor;
void main() {
    float core = exp(-vAcross * vAcross * 6.0);
    vec3 c = mix(vec3(0.55, 0.65, 1.0), vec3(1.0), core * core);
    float a = core * clamp(uFlash * 1.6 + 0.35, 0.0, 1.0);
    fragColor = vec4(c * a, a);
}
