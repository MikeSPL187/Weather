// WebGL2 twin of app/src/main/kotlin/app/aura/ui/scene3d/SceneRenderer.kt: the same shaders,
// meshes and pass order, so the 3D scene can be reviewed in a browser without a phone.
export async function createRenderer(canvas, base) {
  const gl = canvas.getContext('webgl2', { premultipliedAlpha: true, alpha: true, antialias: true });
  if (!gl) throw new Error('WebGL2 unavailable');
  const text = async (n) => (await fetch(base + n)).text();
  const bin = async (n, T) => new T(await (await fetch(base + n)).arrayBuffer());

  function compile(type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) + '\n' + src);
    return s;
  }
  const programs = {};
  for (const name of ['world', 'shadow', 'water', 'cloud', 'sky', 'particle', 'bolt']) {
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl.VERTEX_SHADER, await text(name + '.vert')));
    gl.attachShader(p, compile(gl.FRAGMENT_SHADER, await text(name + '.frag')));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(name + ': ' + gl.getProgramInfoLog(p));
    programs[name] = p;
  }

  function mesh(vertices, indices, layout) {
    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    const vb = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, vb);
    gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
    const stride = layout.reduce((a, b) => a + b, 0) * 4;
    let offset = 0;
    layout.forEach((size, loc) => {
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride, offset);
      offset += size * 4;
    });
    const ib = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint32Array(indices.buffer), gl.STATIC_DRAW);
    gl.bindVertexArray(null);
    return { vao, count: indices.length };
  }

  const models = {};
  async function model(landmark) {
    if (models[landmark]) return models[landmark];
    const m = {};
    for (const part of ['world', 'water']) {
      m[part] = mesh(await bin(`${landmark}.${part}.f32`, Float32Array), await bin(`${landmark}.${part}.i32`, Int32Array), [3, 3, 3, 4]);
    }
    m.lamps = await bin(`${landmark}.lamps.f32`, Float32Array);
    m.cloud = mesh(await bin(`${landmark}.cloud.f32`, Float32Array), await bin(`${landmark}.cloud.i32`, Int32Array), [4, 4, 2]);
    models[landmark] = m;
    return m;
  }
  const particles = mesh(await bin('particles.f32', Float32Array), await bin('particles.i32', Int32Array), [4, 1, 2]);
  const quad = mesh(new Float32Array([-1, -1, 1, -1, 1, 1, -1, 1]), new Int32Array([0, 1, 2, 0, 2, 3]), [2]);

  // Shadow map: depth texture with hardware comparison.
  const SHADOW = 2048;
  const shadowTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, shadowTex);
  gl.texStorage2D(gl.TEXTURE_2D, 1, gl.DEPTH_COMPONENT24, SHADOW, SHADOW);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_MODE, gl.COMPARE_REF_TO_TEXTURE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_FUNC, gl.LEQUAL);
  const shadowFbo = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFbo);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, shadowTex, 0);
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);

  function bind(name, uniforms, extra = {}) {
    const p = programs[name];
    gl.useProgram(p);
    const all = Object.assign({}, uniforms, extra);
    for (const key in all) {
      const loc = gl.getUniformLocation(p, key);
      if (loc === null) continue;
      const v = all[key];
      if (key.endsWith('[0]')) gl.uniform3fv(loc, v);
      else if (v.length === 16) gl.uniformMatrix4fv(loc, false, v);
      else if (v.length === 3) gl.uniform3fv(loc, v);
      else if (v.length === 2) gl.uniform2fv(loc, v);
      else gl.uniform1f(loc, v[0]);
    }
    const sm = gl.getUniformLocation(p, 'uShadowMap');
    if (sm !== null) gl.uniform1i(sm, 0);
    const st = gl.getUniformLocation(p, 'uShadowTexel');
    if (st !== null) gl.uniform1f(st, 1 / SHADOW);
  }

  function draw(m) {
    gl.bindVertexArray(m.vao);
    gl.drawElements(gl.TRIANGLES, m.count, gl.UNSIGNED_INT, 0);
  }

  async function render(frame) {
    const m = await model(frame.landmark);
    const u = Object.assign({}, frame.uniforms, { 'uLamps[0]': m.lamps });
    // 1. Shadow depth from the key light.
    gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFbo);
    gl.viewport(0, 0, SHADOW, SHADOW);
    gl.clear(gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);
    gl.depthMask(true);
    gl.disable(gl.BLEND);
    gl.disable(gl.CULL_FACE);
    gl.enable(gl.POLYGON_OFFSET_FILL);
    gl.polygonOffset(1.5, 3.0);
    bind('shadow', u);
    draw(m.world);
    gl.disable(gl.POLYGON_OFFSET_FILL);

    // 2. Opaque world over a transparent background: the app's sky gradient shows through.
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.CULL_FACE);
    gl.cullFace(gl.BACK);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, shadowTex);
    bind('world', u);
    draw(m.world);

    // 3. Stars, sun and moon, behind everything that is in front of them.
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.depthMask(false);
    gl.disable(gl.CULL_FACE);
    if (u.uStars[0] > 0.01) {
      bind('particle', u, { uAmount: u.uStars, uKind: [2], uTint: [1, 1, 1] });
      draw(particles);
    }
    if (frame.sun) {
      bind('sky', u, { uCenter: u.uSunPos, uSize: [4.2], uColor: u.uSunColor, uAlpha: u.uSunAlpha, uMoon: [0] });
      draw(quad);
    }
    if (frame.moon) {
      bind('sky', u, { uCenter: u.uMoonPos, uSize: [3.0], uColor: [1, 1, 1], uAlpha: u.uMoonAlpha, uMoon: [1] });
      draw(quad);
    }
    // 4. Water and puddles.
    gl.enable(gl.POLYGON_OFFSET_FILL);
    gl.polygonOffset(-1, -2);
    bind('water', u);
    draw(m.water);
    gl.disable(gl.POLYGON_OFFSET_FILL);
    // 5. Clouds.
    bind('cloud', u);
    draw(m.cloud);
    // 6. Rain or snow.
    if (u.uRain[0] > 0.01) {
      bind('particle', u, { uAmount: u.uRain, uKind: [0], uTint: [0.75, 0.83, 1.0] });
      draw(particles);
    }
    if (u.uSnowFall[0] > 0.01) {
      bind('particle', u, { uAmount: u.uSnowFall, uKind: [1], uTint: [1, 1, 1] });
      draw(particles);
    }
    // 7. Lightning, added on top.
    if (frame.bolt && frame.bolt.length) {
      const vao = gl.createVertexArray();
      gl.bindVertexArray(vao);
      const vb = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, vb);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(frame.bolt), gl.DYNAMIC_DRAW);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 4, gl.FLOAT, false, 16, 0);
      gl.blendFunc(gl.ONE, gl.ONE);
      bind('bolt', u);
      gl.drawArrays(gl.TRIANGLES, 0, frame.bolt.length / 4);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    }
    gl.depthMask(true);
    gl.finish();
  }
  return { render };
}
