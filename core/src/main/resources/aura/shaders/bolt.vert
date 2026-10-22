// A lightning bolt: ribbon triangles built on the CPU for each strike.
layout(location = 0) in vec4 aPos; // xyz, across
uniform mat4 uViewProj;
out float vAcross;
void main() {
    vAcross = aPos.w;
    gl_Position = uViewProj * vec4(aPos.xyz, 1.0);
}
