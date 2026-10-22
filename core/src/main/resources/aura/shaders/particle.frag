precision highp float;
in vec2 vUv;
in float vAlpha;
uniform float uKind;
uniform vec3 uTint;
uniform float uAmount;
out vec4 fragColor;
void main() {
    if (vAlpha < 0.05) discard;
    float a = uKind > 0.5 ? smoothstep(1.0, 0.2, length(vUv)) * 0.9 : (1.0 - abs(vUv.x)) * smoothstep(-1.0, 0.6, vUv.y) * 0.55;
    if (uKind > 1.5) a = smoothstep(1.0, 0.0, length(vUv)) * vAlpha * uAmount;
    fragColor = vec4(uTint * a, a);
}
