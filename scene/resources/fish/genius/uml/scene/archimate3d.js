/*
 * archimate3d.js: the ArchiMate 3.2 elements as 3D models, on three.js r149.
 *
 * Grammar
 *   plinth form   = aspect   square (structure), rounded (behaviour), chamfered (motivation)
 *   plinth colour = layer    the conventional ArchiMate layer colours, deepened for light
 *   sculpture     = icon     the element's ArchiMate icon, extruded or turned
 *   ports                    the middle of each plinth side, at half plinth height
 *   placard                  a sloped strip at the front of the plinth top, carrying the name
 *
 * A plinth is 2.0 x 1.3 x 0.2 units. Every model is built only from primitives
 * Blender has natively (an extruded outline with a bevel, cylinder, sphere,
 * torus, cone, lathe, tube along a curve), so the same spec ports to bpy.
 *
 * Use: <canvas data-am="business-actor"> inside a root, then AM3D.mount(root, opts)
 * with opts { azimuth, elevation, spin }. The placard shows the element's ArchiMate
 * name unless the canvas sets data-label (data-label="" leaves it blank); the hero
 * scene takes its five names from data-labels="a|b|c|d|e".
 */
(function () {
  'use strict';
  var T = window.THREE;
  if (!T) { if (window.console) console.error('archimate3d: three.js is not loaded'); return; }
  if (T.ColorManagement) {
    if ('legacyMode' in T.ColorManagement) T.ColorManagement.legacyMode = false;
    if ('enabled' in T.ColorManagement) T.ColorManagement.enabled = true;
  }

  var DEG = Math.PI / 180;
  var P = { w: 2.0, d: 1.3, h: 0.2 };

  var LAYERS = {
    strategy:       { name: 'Strategy',       base: '#F2CF8F', deep: '#B97A1E' },
    business:       { name: 'Business',       base: '#F6E27C', deep: '#BF9A0E' },
    application:    { name: 'Application',    base: '#93DCEA', deep: '#2690A8' },
    technology:     { name: 'Technology',     base: '#AEDB95', deep: '#4E9334' },
    physical:       { name: 'Physical',       base: '#AEDB95', deep: '#4E9334' },
    motivation:     { name: 'Motivation',     base: '#C3BEF2', deep: '#6559C4' },
    implementation: { name: 'Implementation', base: '#F6B9C1', deep: '#C9536A' },
    migration:      { name: 'Migration',      base: '#BCE5BC', deep: '#4F9A55' },
    other:          { name: 'Location',       base: '#F7BE84', deep: '#CF6E1F' },
    neutral:        { name: 'Clay',           base: '#D9D6CF', deep: '#8D8980' }
  };

  // ── materials ──────────────────────────────────────────────────────────────
  function std(color, rough) {
    return new T.MeshStandardMaterial({ color: color, roughness: rough, metalness: 0 });
  }
  var matCache = {}, shared = {};
  function mats(layer) {
    if (matCache[layer]) return matCache[layer];
    var L = LAYERS[layer] || LAYERS.neutral;
    var base = new T.Color(L.base), deep = new T.Color(L.deep), white = new T.Color('#FFFFFF');
    return (matCache[layer] = {
      base: std(base, 0.62),
      deep: std(deep, 0.5),
      light: std(base.clone().lerp(white, 0.5), 0.55),
      glass: new T.MeshStandardMaterial({
        color: base.clone().lerp(white, 0.4), roughness: 0.2, metalness: 0,
        transparent: true, opacity: 0.38, depthWrite: false
      })
    });
  }
  function ink() { return shared.ink || (shared.ink = std(new T.Color('#2E3138'), 0.45)); }
  function paper() { return shared.paper || (shared.paper = std(new T.Color('#F7F6F2'), 0.5)); }
  function portMat() { return shared.port || (shared.port = std(new T.Color('#E4572E'), 0.4)); }

  // ── geometry helpers ───────────────────────────────────────────────────────
  function poly(pts) {
    var s = new T.Shape();
    s.moveTo(pts[0][0], pts[0][1]);
    for (var i = 1; i < pts.length; i++) s.lineTo(pts[i][0], pts[i][1]);
    s.closePath();
    return s;
  }
  function rrect(w, h, r) {
    var x = -w / 2, y = -h / 2;
    r = Math.min(r, w / 2, h / 2);
    if (r < 1e-4) return poly([[x, y], [x + w, y], [x + w, y + h], [x, y + h]]);
    var s = new T.Shape();
    s.moveTo(x + r, y);
    s.lineTo(x + w - r, y);
    s.absarc(x + w - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + w, y + h - r);
    s.absarc(x + w - r, y + h - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + h);
    s.absarc(x + r, y + h - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r);
    s.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false);
    return s;
  }
  function chamfer(w, h, c) {
    var x = w / 2, y = h / 2;
    return poly([[-x + c, -y], [x - c, -y], [x, -y + c], [x, y - c], [x - c, y], [-x + c, y], [-x, y - c], [-x, -y + c]]);
  }
  function wave(w, h) {
    var s = new T.Shape(), x0 = -w / 2, x1 = w / 2, top = h / 2, bot = -h / 2 + 0.07;
    s.moveTo(x0, top);
    s.lineTo(x1, top);
    s.lineTo(x1, bot);
    s.bezierCurveTo(x1 - w * 0.3, bot - 0.17, x0 + w * 0.35, bot + 0.15, x0, bot - 0.03);
    s.closePath();
    return s;
  }
  function gear(R, n, td, hole) {
    var pts = [], steps = n * 4;
    for (var i = 0; i < steps; i++) {
      var a = (i / steps) * Math.PI * 2, ph = i % 4, r = ph === 1 || ph === 2 ? R : R - td;
      pts.push([r * Math.cos(a), r * Math.sin(a)]);
    }
    var s = poly(pts);
    if (hole) {
      var h = new T.Path();
      h.absarc(0, 0, hole, 0, Math.PI * 2, true);
      s.holes.push(h);
    }
    return s;
  }

  // Extrude an XY outline along z, centred on z = 0, with a bevel that keeps the outline's size.
  function extrude(shape, depth, bevel) {
    var b = bevel == null ? 0.025 : bevel, core = Math.max(depth - 2 * b, 0.002);
    var g = new T.ExtrudeGeometry(shape, {
      depth: core, bevelEnabled: b > 0, bevelThickness: b, bevelSize: b,
      bevelOffset: -b, bevelSegments: 3, curveSegments: 36
    });
    g.translate(0, 0, -core / 2);
    return g;
  }
  function mesh(g, m) {
    var o = new T.Mesh(g, m);
    o.castShadow = !m.transparent;
    o.receiveShadow = true;
    return o;
  }
  function grp() {
    var g = new T.Group();
    for (var i = 0; i < arguments.length; i++) g.add(arguments[i]);
    return g;
  }
  function at(o, x, y, z) { o.position.set(x, y, z); return o; }
  function up(shape, depth, m, bevel) { return mesh(extrude(shape, depth, bevel), m); }
  function flat(shape, h, m, bevel) {
    var g = extrude(shape, h, bevel);
    g.rotateX(-Math.PI / 2);
    g.translate(0, h / 2, 0);
    return mesh(g, m);
  }
  function box(w, h, d, m, bevel) {
    var b = bevel == null ? 0.02 : bevel;
    return mesh(extrude(rrect(w, h, b * 1.6), d, b), m);
  }
  function rod(a, b, r, m) {
    var A = new T.Vector3().fromArray(a), B = new T.Vector3().fromArray(b), dir = B.clone().sub(A), len = dir.length();
    if (len < 1e-4) return new T.Group();
    var o = mesh(new T.CylinderGeometry(r, r, len, 20), m);
    o.position.copy(A).add(B).multiplyScalar(0.5);
    o.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), dir.normalize());
    return o;
  }
  function ball(p, r, m) { var o = mesh(new T.SphereGeometry(r, 32, 20), m); o.position.fromArray(p); return o; }
  // A cone whose base centre is at p, pointing along dir.
  function cone(p, dir, r, h, m) {
    var g = new T.ConeGeometry(r, h, 28);
    g.translate(0, h / 2, 0);
    var o = mesh(g, m);
    o.position.fromArray(p);
    o.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), new T.Vector3().fromArray(dir).normalize());
    return o;
  }
  function ring(R, r, m, arc) { return mesh(new T.TorusGeometry(R, r, 20, 72, arc == null ? Math.PI * 2 : arc), m); }
  function disc(r, depth, m) {
    var g = new T.CylinderGeometry(r, r, depth, 56);
    g.rotateX(Math.PI / 2);
    return mesh(g, m);
  }
  function bullseye(m, y) {
    return grp(
      at(disc(0.38, 0.08, m.deep), 0, y, 0),
      at(disc(0.26, 0.12, m.light), 0, y, 0.01),
      at(disc(0.13, 0.16, m.deep), 0, y, 0.02)
    );
  }

  // ── sculptures: one per ArchiMate icon ─────────────────────────────────────
  var REQ = [[-0.46, -0.26], [0.3, -0.26], [0.46, 0.26], [-0.3, 0.26]];
  var I = {
    // strategy
    resource: function (m) {
      var g = grp(up(rrect(0.74, 0.42, 0.1), 0.38, m.deep, 0.025), at(box(0.07, 0.18, 0.22, m.deep, 0.015), 0.4, 0, 0));
      [-0.2, 0, 0.2].forEach(function (x) { g.add(at(box(0.07, 0.26, 0.04, m.light, 0.01), x, 0, 0.2)); });
      return g;
    },
    capability: function (m) {
      var g = grp(), s = 0.22, p = 0.245;
      for (var i = 0; i < 3; i++) for (var j = 0; j <= i; j++) g.add(at(box(s, s, s, m.deep, 0.025), (i - 1) * p, s / 2 + j * s, 0));
      return g;
    },
    valueStream: function (m) {
      return grp(up(poly([[-0.5, -0.24], [0.24, -0.24], [0.5, 0], [0.24, 0.24], [-0.5, 0.24], [-0.26, 0]]), 0.3, m.deep));
    },
    courseOfAction: function (m) {
      var target = bullseye(m, 0.42);
      target.position.x = 0.14;
      var S = new T.Vector3(-0.52, 0.05, 0.24), C = new T.Vector3(-0.5, 0.62, 0.24), E = new T.Vector3(-0.02, 0.44, 0.2);
      var curve = new T.QuadraticBezierCurve3(S, C, E);
      return grp(target, mesh(new T.TubeGeometry(curve, 40, 0.032, 12, false), ink()), ball(S.toArray(), 0.032, ink()),
        cone(E.toArray(), curve.getTangent(1).toArray(), 0.07, 0.16, ink()));
    },

    // structure and behaviour, shared by the business, application and technology layers
    actor: function (m) {
      var r = 0.048, d = m.deep;
      return grp(
        ball([0, 0.9, 0], 0.13, d),
        rod([0, 0.76, 0], [0, 0.4, 0], r, d), rod([-0.27, 0.62, 0], [0.27, 0.62, 0], r, d),
        rod([0, 0.4, 0], [-0.2, 0.03, 0], r, d), rod([0, 0.4, 0], [0.2, 0.03, 0], r, d),
        ball([0, 0.4, 0], r, d), ball([-0.27, 0.62, 0], r, d), ball([0.27, 0.62, 0], r, d),
        ball([-0.2, 0.03, 0], r, d), ball([0.2, 0.03, 0], r, d)
      );
    },
    role: function (m) {
      var g = new T.CylinderGeometry(0.22, 0.22, 0.72, 48), cap = new T.CylinderGeometry(0.17, 0.17, 0.03, 48);
      g.rotateZ(Math.PI / 2);
      cap.rotateZ(Math.PI / 2);
      return grp(mesh(g, m.deep), at(mesh(cap, m.light), -0.37, 0, 0));
    },
    collaboration: function (m) {
      return grp(at(ring(0.27, 0.055, m.deep), -0.16, 0.33, 0.05), at(ring(0.27, 0.055, m.light), 0.16, 0.33, -0.05));
    },
    'interface': function (m) {
      return grp(at(ring(0.2, 0.055, m.deep), 0.22, 0.26, 0), rod([-0.5, 0.26, 0], [0.04, 0.26, 0], 0.055, m.deep));
    },
    process: function (m) {
      return grp(up(poly([[-0.5, -0.13], [0.1, -0.13], [0.1, -0.3], [0.5, 0], [0.1, 0.3], [0.1, 0.13], [-0.5, 0.13]]), 0.28, m.deep));
    },
    'function': function (m) {
      return grp(up(poly([[-0.3, -0.42], [0, -0.24], [0.3, -0.42], [0.3, 0.18], [0, 0.42], [-0.3, 0.18]]), 0.26, m.deep));
    },
    interaction: function (m) {
      var r = 0.38, g = 0.05, L = new T.Shape(), R = new T.Shape();
      L.moveTo(-g, r);
      L.absarc(-g, 0, r, Math.PI / 2, Math.PI * 1.5, false);
      L.closePath();
      R.moveTo(g, -r);
      R.absarc(g, 0, r, -Math.PI / 2, Math.PI / 2, false);
      R.closePath();
      return grp(up(L, 0.24, m.deep), up(R, 0.24, m.deep));
    },
    event: function (m) {
      var s = new T.Shape();
      s.moveTo(-0.5, 0.28);
      s.lineTo(0.2, 0.28);
      s.absarc(0.2, 0, 0.28, Math.PI / 2, -Math.PI / 2, true);
      s.lineTo(-0.5, -0.28);
      s.lineTo(-0.3, 0);
      s.closePath();
      return grp(up(s, 0.26, m.deep));
    },
    service: function (m) {
      var g = new T.CapsuleGeometry(0.24, 0.56, 12, 40);
      g.rotateZ(Math.PI / 2);
      return grp(mesh(g, m.deep));
    },
    object: function (m) {
      return grp(box(0.8, 0.6, 0.08, m.deep), at(box(0.8, 0.14, 0.05, m.light, 0.012), 0, 0.23, 0.06));
    },
    contract: function (m) {
      return grp(box(0.8, 0.6, 0.08, m.deep), at(box(0.8, 0.14, 0.05, m.light, 0.012), 0, 0.23, 0.06),
        at(box(0.8, 0.05, 0.04, m.light, 0.01), 0, 0.02, 0.055));
    },
    representation: function (m) {
      return grp(up(wave(0.8, 0.64), 0.08, m.deep), at(box(0.8, 0.13, 0.05, m.light, 0.012), 0, 0.255, 0.06));
    },
    product: function (m) {
      return grp(box(0.84, 0.54, 0.52, m.deep, 0.03), at(box(0.38, 0.09, 0.54, m.light, 0.015), -0.21, 0.31, 0));
    },
    component: function (m) {
      return grp(box(0.62, 0.66, 0.46, m.deep, 0.03),
        at(box(0.26, 0.13, 0.32, m.light, 0.015), -0.33, 0.14, 0),
        at(box(0.26, 0.13, 0.32, m.light, 0.015), -0.33, -0.14, 0));
    },

    // technology
    node: function (m) { return grp(box(0.92, 0.64, 0.72, m.deep, 0.045)); },
    device: function (m) {
      var screen = grp(box(0.84, 0.52, 0.06, m.deep, 0.02), at(box(0.72, 0.4, 0.02, m.light, 0.005), 0, 0.01, 0.035));
      screen.position.set(0, 0.64, -0.05);
      screen.rotation.x = -0.12;
      return grp(screen,
        at(box(0.08, 0.3, 0.05, m.deep, 0.015), 0, 0.27, -0.08),
        at(box(0.34, 0.04, 0.22, m.deep, 0.012), 0, 0.02, -0.08),
        at(box(0.8, 0.05, 0.26, m.deep, 0.015), 0, 0.025, 0.3));
    },
    systemSoftware: function (m) {
      return grp(at(disc(0.3, 0.1, m.light), 0.12, 0.44, -0.1), at(disc(0.3, 0.12, m.deep), -0.06, 0.32, 0.04));
    },
    path: function (m) {
      var d = m.deep, g = grp();
      [-0.2, 0, 0.2].forEach(function (x) { g.add(at(box(0.13, 0.07, 0.07, d, 0.015), x, 0.3, 0)); });
      g.add(up(poly([[-0.5, 0.3], [-0.31, 0.47], [-0.31, 0.13]]), 0.1, d, 0.012));
      g.add(up(poly([[0.5, 0.3], [0.31, 0.13], [0.31, 0.47]]), 0.1, d, 0.012));
      return g;
    },
    network: function (m) {
      var d = m.deep, A = [-0.42, 0.08, 0], B = [0.22, 0.08, 0], C = [0.42, 0.52, 0], D = [-0.22, 0.52, 0];
      return grp(rod(A, B, 0.032, d), rod(B, C, 0.032, d), rod(C, D, 0.032, d), rod(D, A, 0.032, d),
        ball(A, 0.08, d), ball(B, 0.08, d), ball(C, 0.08, d), ball(D, 0.08, d));
    },
    artifact: function (m) {
      return grp(up(poly([[-0.32, -0.42], [0.32, -0.42], [0.32, 0.2], [0.1, 0.42], [-0.32, 0.42]]), 0.12, m.deep, 0.02),
        at(up(poly([[0.1, 0.2], [0.32, 0.2], [0.1, 0.42]]), 0.04, m.light, 0.008), 0, 0, 0.07));
    },

    // physical
    equipment: function (m) {
      var big = up(gear(0.3, 10, 0.075, 0.08), 0.18, m.deep, 0.012), small = up(gear(0.2, 7, 0.065, 0.06), 0.15, m.light, 0.01);
      big.position.set(-0.12, 0, 0);
      small.position.set(0.22, 0.29, 0.02);
      small.rotation.z = 0.2;
      return grp(big, small);
    },
    facility: function (m) {
      return grp(up(poly([[-0.5, 0], [0.5, 0], [0.5, 0.55], [0.2, 0.36], [0.2, 0.55], [-0.1, 0.36], [-0.1, 0.55],
        [-0.3, 0.42], [-0.3, 0.9], [-0.46, 0.9], [-0.46, 0.42], [-0.5, 0.42]]), 0.44, m.deep, 0.015));
    },
    distribution: function (m) {
      var d = m.deep;
      return grp(rod([-0.31, 0.24, 0], [0.31, 0.24, 0], 0.035, d), rod([-0.31, 0.4, 0], [0.31, 0.4, 0], 0.035, d),
        up(poly([[-0.5, 0.32], [-0.3, 0.54], [-0.3, 0.1]]), 0.12, d, 0.012),
        up(poly([[0.5, 0.32], [0.3, 0.1], [0.3, 0.54]]), 0.12, d, 0.012));
    },
    material: function (m) {
      var r = 0.38, v = [], k;
      for (k = 0; k < 6; k++) v.push([r * Math.cos((k * Math.PI) / 3), r * Math.sin((k * Math.PI) / 3)]);
      var g = grp(up(poly(v), 0.24, m.deep, 0.02));
      [0, 2, 4].forEach(function (i) {
        var a = v[i], b = v[(i + 1) % 6], s = 0.68, t = 0.2, p = [a[0] * s, a[1] * s], q = [b[0] * s, b[1] * s];
        g.add(rod([p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, 0.135],
          [q[0] + (p[0] - q[0]) * t, q[1] + (p[1] - q[1]) * t, 0.135], 0.026, m.light));
      });
      return g;
    },

    // motivation
    driver: function (m) {
      var d = m.deep, c = [0, 0.44, 0], g = grp(at(ring(0.28, 0.045, d), 0, 0.44, 0), ball(c, 0.075, m.light));
      for (var k = 0; k < 8; k++) {
        var a = (k * Math.PI) / 4 + Math.PI / 8, e = [Math.cos(a) * 0.42, 0.44 + Math.sin(a) * 0.42, 0];
        g.add(rod(c, e, 0.026, d));
        g.add(ball(e, 0.045, d));
      }
      return g;
    },
    assessment: function (m) {
      var cx = 0.1, cy = 0.52, R = 0.2, a = 225 * DEG;
      var s = [cx + Math.cos(a) * (R + 0.04), cy + Math.sin(a) * (R + 0.04), 0], e = [cx + Math.cos(a) * 0.68, cy + Math.sin(a) * 0.68, 0];
      return grp(at(ring(R, 0.05, m.deep), cx, cy, 0), at(disc(R - 0.02, 0.02, m.glass), cx, cy, 0),
        rod(s, e, 0.055, m.deep), ball(e, 0.055, m.deep));
    },
    goal: function (m) { return bullseye(m, 0.4); },
    outcome: function (m) {
      var tip = new T.Vector3(0, 0.4, 0.1), tail = new T.Vector3(0.34, 0.74, 0.62), u = tail.clone().sub(tip).normalize();
      var base = tip.clone().addScaledVector(u, 0.16);
      return grp(bullseye(m, 0.4), rod(base.toArray(), tail.toArray(), 0.026, ink()),
        cone(base.toArray(), u.clone().negate().toArray(), 0.06, 0.16, ink()),
        cone(tail.clone().addScaledVector(u, -0.14).toArray(), u.toArray(), 0.07, 0.16, ink()));
    },
    principle: function (m) {
      var bar = new T.CapsuleGeometry(0.075, 0.36, 10, 28);
      return grp(at(box(0.52, 0.86, 0.06, m.light, 0.02), 0, 0.43, -0.09), at(mesh(bar, m.deep), 0, 0.56, 0),
        ball([0, 0.16, 0], 0.085, m.deep));
    },
    requirement: function (m) { return grp(up(poly(REQ), 0.24, m.deep)); },
    constraint: function (m) {
      return grp(up(poly(REQ), 0.24, m.deep), rod([-0.27, -0.2, 0.135], [-0.147, 0.2, 0.135], 0.024, m.light));
    },
    meaning: function (m) {
      var d = m.deep;
      return grp(ball([-0.27, 0.2, 0], 0.2, d), ball([0, 0.3, -0.02], 0.27, d), ball([0.27, 0.2, 0], 0.2, d),
        ball([0.13, 0.15, 0.13], 0.16, d), ball([-0.13, 0.15, 0.13], 0.16, d));
    },
    value: function (m) {
      var b = ball([0, 0, 0], 0.3, m.deep);
      b.scale.set(1.45, 0.85, 0.55);
      return grp(b);
    },

    // implementation and migration
    workPackage: function (m) {
      var R = 0.28, arc = 1.55 * Math.PI, rho = 0.3 * Math.PI, e = arc + rho;
      var t = at(ring(R, 0.06, m.deep, arc), 0, 0.4, 0);
      t.rotation.z = rho;
      return grp(t, ball([R * Math.cos(rho), 0.4 + R * Math.sin(rho), 0], 0.06, m.deep),
        cone([R * Math.cos(e), 0.4 + R * Math.sin(e), 0], [-Math.sin(e), Math.cos(e), 0], 0.13, 0.2, m.deep));
    },
    deliverable: function (m) { return grp(up(wave(0.8, 0.64), 0.1, m.deep)); },
    plateau: function (m) {
      return grp(at(box(0.72, 0.13, 0.44, m.deep, 0.02), -0.12, 0.065, 0), at(box(0.72, 0.13, 0.44, m.light, 0.02), 0, 0.195, 0),
        at(box(0.72, 0.13, 0.44, m.deep, 0.02), 0.12, 0.325, 0));
    },
    gap: function (m) {
      return grp(at(ring(0.26, 0.05, m.deep), 0, 0.36, 0), rod([-0.46, 0.45, 0.07], [0.46, 0.45, 0.07], 0.034, m.deep),
        rod([-0.46, 0.27, 0.07], [0.46, 0.27, 0.07], 0.034, m.deep));
    },

    // other
    location: function (m) {
      var pts = [[0, 0], [0.05, 0.12], [0.14, 0.32], [0.22, 0.5], [0.25, 0.62], [0.23, 0.74], [0.16, 0.83], [0.07, 0.875], [0, 0.885]]
        .map(function (p) { return new T.Vector2(p[0], p[1]); });
      return grp(mesh(new T.LatheGeometry(pts, 56), m.deep), ball([0, 0.64, 0.2], 0.085, m.light));
    },
    andJunction: function () { return grp(ball([0, 0.26, 0], 0.26, ink())); },
    orJunction: function () {
      var b = at(ring(0.24, 0.05, ink()), 0, 0.29, 0);
      b.rotation.y = Math.PI / 2;
      return grp(at(ring(0.24, 0.05, ink()), 0, 0.29, 0), b);
    }
  };

  // How far a sculpture sinks into its plinth, so round or pointed bottoms look planted.
  var SINK = { process: 0.1, interaction: 0.05, equipment: 0.05, goal: 0.04, outcome: 0.04, courseOfAction: 0.04, gap: 0.04,
    workPackage: 0.04, location: 0.04, collaboration: 0.035, 'interface': 0.035, andJunction: 0.05, orJunction: 0.05 };

  // ── the element catalogue ──────────────────────────────────────────────────
  var E = {};
  function titleOf(id) {
    return id.split('-').map(function (w) { return w === 'of' ? w : w.charAt(0).toUpperCase() + w.slice(1); }).join(' ');
  }
  function def(layer, plinthForm, rows) {
    rows.forEach(function (r) { E[r[0]] = { id: r[0], name: titleOf(r[0]), layer: layer, plinth: plinthForm, icon: r[1] }; });
  }
  def('strategy', 'structure', [['resource', 'resource']]);
  def('strategy', 'behaviour', [['capability', 'capability'], ['value-stream', 'valueStream'], ['course-of-action', 'courseOfAction']]);
  def('business', 'structure', [['business-actor', 'actor'], ['business-role', 'role'], ['business-collaboration', 'collaboration'],
    ['business-interface', 'interface'], ['business-object', 'object'], ['contract', 'contract'], ['representation', 'representation'],
    ['product', 'product']]);
  def('business', 'behaviour', [['business-process', 'process'], ['business-function', 'function'], ['business-interaction', 'interaction'],
    ['business-event', 'event'], ['business-service', 'service']]);
  def('application', 'structure', [['application-component', 'component'], ['application-collaboration', 'collaboration'],
    ['application-interface', 'interface'], ['data-object', 'object']]);
  def('application', 'behaviour', [['application-function', 'function'], ['application-interaction', 'interaction'],
    ['application-process', 'process'], ['application-event', 'event'], ['application-service', 'service']]);
  def('technology', 'structure', [['node', 'node'], ['device', 'device'], ['system-software', 'systemSoftware'],
    ['technology-collaboration', 'collaboration'], ['technology-interface', 'interface'], ['path', 'path'],
    ['communication-network', 'network'], ['artifact', 'artifact']]);
  def('technology', 'behaviour', [['technology-function', 'function'], ['technology-process', 'process'],
    ['technology-interaction', 'interaction'], ['technology-event', 'event'], ['technology-service', 'service']]);
  def('physical', 'structure', [['equipment', 'equipment'], ['facility', 'facility'], ['distribution-network', 'distribution'],
    ['material', 'material']]);
  def('motivation', 'motivation', [['stakeholder', 'role'], ['driver', 'driver'], ['assessment', 'assessment'], ['goal', 'goal'],
    ['outcome', 'outcome'], ['principle', 'principle'], ['requirement', 'requirement'], ['constraint', 'constraint'],
    ['meaning', 'meaning'], ['value', 'value']]);
  def('implementation', 'behaviour', [['work-package', 'workPackage'], ['implementation-event', 'event']]);
  def('implementation', 'structure', [['deliverable', 'deliverable']]);
  def('migration', 'structure', [['plateau', 'plateau'], ['gap', 'gap']]);
  def('other', 'structure', [['location', 'location']]);
  def('neutral', 'tray', [['grouping', null]]);
  def('neutral', 'puck', [['and-junction', 'andJunction'], ['or-junction', 'orJunction']]);

  // ── plinths ────────────────────────────────────────────────────────────────
  function spread(len, n) {
    var out = [];
    for (var i = 0; i < n; i++) out.push(-len / 2 + (len * i) / (n - 1));
    return out;
  }
  function tray(m) {
    var g = grp(flat(rrect(P.w, P.d, 0.04), 0.05, m.glass, 0.01)), th = 0.045, hgt = 0.16, ex = P.d / 2 - th / 2, ez = P.w / 2 - th / 2;
    spread(P.w - 0.16, 8).forEach(function (x) {
      g.add(at(box(0.15, hgt, th, m.deep, 0.01), x, hgt / 2, ex));
      g.add(at(box(0.15, hgt, th, m.deep, 0.01), x, hgt / 2, -ex));
    });
    spread(P.d - 0.16, 5).forEach(function (z) {
      g.add(at(box(th, hgt, 0.15, m.deep, 0.01), ez, hgt / 2, z));
      g.add(at(box(th, hgt, 0.15, m.deep, 0.01), -ez, hgt / 2, z));
    });
    g.add(at(box(0.72, 0.36, th, m.deep, 0.012), -P.w / 2 + 0.36, 0.18, -ex));
    return g;
  }
  function plinth(form, m) {
    if (form === 'tray') return tray(m);
    if (form === 'puck') {
      var g = new T.CylinderGeometry(0.62, 0.62, P.h, 72);
      g.translate(0, P.h / 2, 0);
      return grp(mesh(g, m.base));
    }
    if (form === 'behaviour') return grp(flat(rrect(P.w, P.d, 0.36), P.h, m.base, 0.05));
    if (form === 'motivation') return grp(flat(chamfer(P.w, P.d, 0.3), P.h, m.base, 0.025));
    return grp(flat(rrect(P.w, P.d, 0.04), P.h, m.base, 0.025));
  }

  // ── names ──────────────────────────────────────────────────────────────────
  // A placard: a wedge at the front of the plinth top whose face slopes 35° towards
  // the viewer, so the name reads from the usual camera height. It sits on the top,
  // not on a side, so the ports stay free. Long names wrap to two lines and shrink.
  var PLACARD = { front: 0.6, back: 0.32, lip: 0.03, w: 1.5, slope: 35 * DEG };
  var FONT = "'Archivo', 'Helvetica Neue', Arial, sans-serif", labels = [];

  function layoutLabel(ctx, text, W, H) {
    var maxW = W * 0.92, words = text.split(/\s+/).filter(Boolean);
    function width(s, px) { ctx.font = '600 ' + px + 'px ' + FONT; return ctx.measureText(s).width || 1; }
    var one = H * 0.56, best = { px: Math.min(one, (one * maxW) / width(text, one)), lines: [text] };
    var two = H * 0.4;
    for (var i = 1; i < words.length; i++) {
      var a = words.slice(0, i).join(' '), b = words.slice(i).join(' ');
      var px = Math.min(two, (two * maxW) / Math.max(width(a, two), width(b, two)));
      if (px > best.px * 1.15) best = { px: px, lines: [a, b] };
    }
    return best;
  }
  function paintLabel(l) {
    var ctx = l.canvas.getContext('2d');
    if (!ctx) return;
    var W = l.canvas.width, H = l.canvas.height, lay = layoutLabel(ctx, l.text, W, H), lh = lay.px * 1.12;
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = l.color;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '600 ' + lay.px + 'px ' + FONT;
    lay.lines.forEach(function (s, i) { ctx.fillText(s, W / 2, H / 2 - (lh * (lay.lines.length - 1)) / 2 + i * lh); });
    l.texture.needsUpdate = true;
  }
  function labelMesh(text, w, h, color) {
    var canvas = document.createElement('canvas');
    canvas.width = 1024;
    canvas.height = Math.max(64, Math.round((1024 * h) / w));
    var texture = new T.CanvasTexture(canvas), l = { text: text, canvas: canvas, texture: texture, color: color || '#24272D' };
    texture.encoding = T.sRGBEncoding;
    texture.anisotropy = 8;
    paintLabel(l);
    labels.push(l);
    var o = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshStandardMaterial({
      map: texture, transparent: true, roughness: 0.75, metalness: 0,
      depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2
    }));
    o.receiveShadow = true;
    return o;
  }
  function placard(text, m) {
    var c = PLACARD, run = c.front - c.back, rise = run * Math.tan(c.slope), tilt = Math.PI / 2 - c.slope;
    var g = extrude(poly([[c.front, 0], [c.front, c.lip], [c.back, c.lip + rise], [c.back, 0]]), c.w, 0.008);
    g.rotateY(-Math.PI / 2);
    g.translate(0, P.h, 0);
    var face = labelMesh(text, c.w - 0.12, Math.hypot(run, rise) - 0.05);
    face.rotation.x = -tilt;
    face.position.set(0, P.h + c.lip + rise / 2 + Math.sin(tilt) * 0.004, c.back + run / 2 + Math.cos(tilt) * 0.004);
    return grp(mesh(g, m.light), face);
  }
  // A grouping carries its name on the tab, as in the 2D notation.
  function tabLabel(text) {
    var face = labelMesh(text, 0.62, 0.26, '#FBFAF8');
    face.position.set(-P.w / 2 + 0.36, 0.18, -P.d / 2 + 0.045 + 0.004);
    return face;
  }

  // Centre a sculpture on the plinth and stand it on the top face; behind the placard when there is one.
  function place(icon, sink, back) {
    icon.updateMatrixWorld(true);
    var b = new T.Box3().setFromObject(icon), size = b.getSize(new T.Vector3()), c = b.getCenter(new T.Vector3());
    var k = Math.min(1, 1.3 / size.x, 1.05 / size.y, (back ? 0.8 : 1.0) / size.z), holder = new T.Group();
    icon.position.sub(c);
    holder.add(icon);
    holder.scale.setScalar(k);
    holder.position.set(0, P.h - sink + (size.y * k) / 2, back ? -0.15 : 0);
    return holder;
  }

  // label: null or undefined shows the ArchiMate name, '' shows none.
  function element(id, label) {
    var e = E[id], m = mats(e ? e.layer : 'neutral'), form = e ? e.plinth : 'structure', g = new T.Group();
    if (label == null) label = e ? e.name : '';
    var onPlacard = !!label && form !== 'puck' && form !== 'tray';
    g.add(plinth(form, m));
    if (onPlacard) g.add(placard(label, m));
    if (label && form === 'tray') g.add(tabLabel(label));
    if (e && e.icon && I[e.icon]) g.add(place(I[e.icon](m), SINK[e.icon] == null ? 0.03 : SINK[e.icon], onPlacard));
    g.userData = { id: id, label: label, ports: ports() };
    return g;
  }
  function ports() {
    var y = P.h / 2;
    return { east: [P.w / 2, y, 0], west: [-P.w / 2, y, 0], south: [0, y, P.d / 2], north: [0, y, -P.d / 2] };
  }

  // ── connectors ─────────────────────────────────────────────────────────────
  function connector(a, b, type) {
    var g = new T.Group(), k = ink(), d = b.clone().sub(a).normalize(), r = 0.028;
    var head = type === 'serving' ? 0 : 0.2, end = b.clone().addScaledVector(d, -head);
    if (type === 'realization') {
      var seg = 0.12, gap = 0.07, L = end.distanceTo(a), s = 0;
      while (s < L) {
        var e2 = Math.min(s + seg, L);
        g.add(rod(a.clone().addScaledVector(d, s).toArray(), a.clone().addScaledVector(d, e2).toArray(), r, k));
        s = e2 + gap;
      }
      g.add(cone(end.toArray(), d.toArray(), 0.1, head, paper()));
      return g;
    }
    g.add(rod(a.toArray(), end.toArray(), r, k));
    if (type === 'serving') {
      var p = new T.Vector3(-d.z, 0, d.x);
      g.add(rod(b.toArray(), b.clone().addScaledVector(d, -0.17).addScaledVector(p, 0.11).toArray(), r, k));
      g.add(rod(b.toArray(), b.clone().addScaledVector(d, -0.17).addScaledVector(p, -0.11).toArray(), r, k));
      g.add(ball(b.toArray(), r, k));
    } else {
      g.add(cone(end.toArray(), d.toArray(), 0.085, head, k));
    }
    if (type === 'assignment') g.add(ball(a.toArray(), 0.065, k));
    return g;
  }

  // ── demo scenes ────────────────────────────────────────────────────────────
  function portsDemo(label) {
    var m = mats('neutral'), g = new T.Group(), y = P.h / 2, pm = portMat();
    g.add(plinth('structure', m));
    if (label) g.add(placard(label, m));
    g.add(place(I.actor(m), 0.03, !!label));
    var pp = ports();
    Object.keys(pp).forEach(function (k) { g.add(ball(pp[k], 0.07, pm)); });
    g.add(connector(new T.Vector3(P.w / 2 + 0.07, y, 0), new T.Vector3(P.w / 2 + 0.8, y, 0), 'serving'));
    g.add(connector(new T.Vector3(0, y, P.d / 2 + 0.8), new T.Vector3(0, y, P.d / 2 + 0.07), 'triggering'));
    return g;
  }
  var HERO_NAMES = ['Customer', 'Handle Order', 'Order Management', 'Order Intake', 'Application Server'];
  function hero(names) {
    var g = new T.Group(), X = 1.6, Z = 2.6, y = P.h / 2, hw = P.w / 2, hd = P.d / 2, n = 0;
    names = names || HERO_NAMES;
    function put(id, x, z) {
      var label = names[n] != null ? names[n] : HERO_NAMES[n], e = element(id, label);
      n++;
      e.position.set(x, 0, z);
      g.add(e);
    }
    function V(x, z) { return new T.Vector3(x, y, z); }
    put('business-actor', -X, -Z);
    put('business-process', X, -Z);
    put('application-component', -X, 0);
    put('application-service', X, 0);
    put('node', -X, Z);
    g.add(connector(V(-X + hw, -Z), V(X - hw, -Z), 'assignment'));
    g.add(connector(V(X, -hd), V(X, -Z + hd), 'serving'));
    g.add(connector(V(-X + hw, 0), V(X - hw, 0), 'realization'));
    g.add(connector(V(-X, Z - hd), V(-X, hd), 'serving'));
    return g;
  }

  // ── scenes ─────────────────────────────────────────────────────────────────
  function lights(scene, span, mapSize) {
    scene.add(new T.HemisphereLight(0xffffff, 0xb8b2a6, 0.55));
    var key = new T.DirectionalLight(0xffffff, 0.95), c = key.shadow.camera;
    key.position.set(-3, 6, 4).multiplyScalar(span / 2.4);
    key.castShadow = true;
    c.left = c.bottom = -span;
    c.right = c.top = span;
    c.near = 0.1;
    c.far = span * 8;
    key.shadow.mapSize.set(mapSize, mapSize);
    key.shadow.bias = -0.0004;
    key.shadow.normalBias = 0.02;
    key.shadow.radius = 3;
    scene.add(key);
    var fill = new T.DirectionalLight(0xffffff, 0.28);
    fill.position.set(4, 2.5, -3);
    scene.add(fill);
  }
  function ground() {
    var g = new T.Mesh(new T.PlaneGeometry(60, 60), new T.ShadowMaterial({ opacity: 0.13 }));
    g.rotation.x = -Math.PI / 2;
    g.receiveShadow = true;
    return g;
  }
  function cellFrame() { return { target: new T.Vector3(0, 0.56, 0), radius: 1.42 }; }
  // names: { label, list } from the canvas's data-label / data-labels; null means default.
  function buildScene(id, names) {
    var scene = new T.Scene(), content, frame, label = names ? names.label : null;
    if (id === 'hero') {
      content = hero(names ? names.list : null);
      content.updateMatrixWorld(true);
      var s = new T.Box3().setFromObject(content).getBoundingSphere(new T.Sphere());
      frame = { target: s.center, radius: s.radius * 0.9, lift: 14 };
      lights(scene, 6.5, 2048);
    } else if (id === 'ports') {
      content = portsDemo(label == null ? 'Element name' : label);
      frame = { target: new T.Vector3(0.3, 0.42, 0.3), radius: 1.85 };
      lights(scene, 2.6, 1024);
    } else if (id.indexOf('plinth-') === 0) {
      var form = id.slice(7), m = mats('neutral');
      if (label == null) label = titleOf(form);
      content = grp(plinth(form, m));
      if (label) content.add(placard(label, m));
      frame = { target: new T.Vector3(0, 0.12, 0), radius: 1.3 };
      lights(scene, 2.4, 1024);
    } else {
      content = element(id, label);
      frame = cellFrame();
      lights(scene, 2.4, 1024);
    }
    scene.add(content);
    scene.add(ground());
    return { scene: scene, frame: frame, content: content };
  }

  // ── rendering into <canvas data-am> elements ───────────────────────────────
  var renderers = {}, views = [], store = new WeakMap(), pending = false, looping = false, ro = null;

  function rendererFor(W, H) {
    var key = W + 'x' + H;
    if (renderers[key]) return renderers[key];
    var keys = Object.keys(renderers);
    if (keys.length >= 3) { renderers[keys[0]].dispose(); delete renderers[keys[0]]; }
    var r = new T.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
    r.setPixelRatio(1);
    r.setSize(W, H, false);
    r.outputEncoding = T.sRGBEncoding;
    r.shadowMap.enabled = true;
    r.shadowMap.type = T.PCFSoftShadowMap;
    r.setClearColor(0x000000, 0);
    return (renderers[key] = r);
  }
  function num(v, d) { v = Number(v); return isFinite(v) ? v : d; }

  function draw(v, t) {
    var c = v.canvas, w = c.clientWidth, h = c.clientHeight;
    if (!w || !h) return;
    var dpr = Math.min(window.devicePixelRatio || 1, 2), W = Math.round(w * dpr), H = Math.round(h * dpr);
    if (c.width !== W) c.width = W;
    if (c.height !== H) c.height = H;
    var o = v.opts, cam = v.camera, f = v.frame;
    var az = (num(o.azimuth, 35) + (o.spin ? t * 24 : 0)) * DEG, el = (num(o.elevation, 24) + (f.lift || 0)) * DEG;
    cam.aspect = w / h;
    cam.updateProjectionMatrix();
    var vf = cam.fov * DEG, hf = 2 * Math.atan(Math.tan(vf / 2) * cam.aspect), dist = f.radius / Math.sin(Math.min(vf, hf) / 2);
    cam.position.set(f.target.x + dist * Math.cos(el) * Math.sin(az), f.target.y + dist * Math.sin(el),
      f.target.z + dist * Math.cos(el) * Math.cos(az));
    cam.lookAt(f.target);
    var ctx = c.getContext('2d');
    try {
      var r = rendererFor(W, H);
      r.render(v.scene, cam);
      ctx.clearRect(0, 0, W, H);
      ctx.drawImage(r.domElement, 0, 0);
    } catch (err) {
      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = '#5B5E66';
      ctx.font = 13 * dpr + 'px sans-serif';
      ctx.fillText('3D preview needs WebGL', 16 * dpr, 28 * dpr);
    }
  }

  function frameAll(now) {
    views = views.filter(function (v) { return v.canvas.isConnected; });
    var spin = false;
    for (var i = 0; i < views.length; i++) {
      draw(views[i], now / 1000);
      if (views[i].opts.spin) spin = true;
    }
    looping = spin;
    if (spin) requestAnimationFrame(frameAll);
  }
  function schedule() {
    if (looping || pending) return;
    pending = true;
    requestAnimationFrame(function (now) { pending = false; frameAll(now); });
  }
  function namesOf(canvas) {
    return {
      label: canvas.hasAttribute('data-label') ? canvas.getAttribute('data-label') : null,
      list: canvas.hasAttribute('data-labels') ? canvas.getAttribute('data-labels').split('|') : null
    };
  }
  // A canvas whose element or names changed since it was built is rebuilt.
  function viewFor(canvas) {
    var id = canvas.getAttribute('data-am') || '', names = namesOf(canvas), sig = JSON.stringify([id, names]);
    var v = store.get(canvas);
    if (v && v.sig === sig) return v;
    var built = buildScene(id, names);
    if (v) {
      v.scene = built.scene;
      v.frame = built.frame;
      v.sig = sig;
      return v;
    }
    v = { canvas: canvas, sig: sig, scene: built.scene, frame: built.frame, camera: new T.PerspectiveCamera(22, 1, 0.1, 400), opts: {} };
    store.set(canvas, v);
    views.push(v);
    if (window.ResizeObserver) {
      ro = ro || new ResizeObserver(schedule);
      ro.observe(canvas);
    }
    return v;
  }
  // Names are painted with the page's font; repaint them once it has loaded.
  function refreshLabels() {
    labels.forEach(paintLabel);
    schedule();
  }
  if (document.fonts) {
    if (document.fonts.ready) document.fonts.ready.then(refreshLabels);
    if (document.fonts.addEventListener) document.fonts.addEventListener('loadingdone', refreshLabels);
  }
  function mount(root, opts) {
    if (!root || !root.querySelectorAll) return;
    var list = root.querySelectorAll('canvas[data-am]');
    for (var i = 0; i < list.length; i++) viewFor(list[i]).opts = opts || {};
    schedule();
  }

  window.AM3D = { mount: mount, element: element, scene: buildScene, ELEMENTS: E, LAYERS: LAYERS, PLINTH: P, PLACARD: PLACARD, version: '0.2.0' };
})();
