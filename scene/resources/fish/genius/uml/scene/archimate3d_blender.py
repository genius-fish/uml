"""
archimate3d_blender.py: the ArchiMate 3.2 elements as Blender models.

The same grammar and measures as archimate3d.js on the "ArchiMate 3D Shapes" canvas:

  plinth form   = aspect   square (structure), rounded (behaviour), chamfered (motivation)
  plinth colour = layer    the conventional ArchiMate layer colours, deepened for light
  sculpture     = icon     the element's ArchiMate icon, extruded or turned
  placard       = name     a strip at the front of the plinth top, sloped 35 degrees
  ports                    four Empties, one in the middle of each plinth side

An element is one selectable object, its plinth. Sculpture, placard, name and ports
are children that cannot be selected, so grabbing a plinth moves the whole element.
A connector is a curve whose ends are Hook-ed to ports; its arrowhead follows the
port through constraints. Move an element and its connectors come along.

Every model uses only Blender-native building blocks: a 2D curve extruded with a
bevel, a curve with a round bevel (rods, rings, arcs), UV spheres, cones, cylinders
and a lathe.

Headless:
  blender -b -P archimate3d_blender.py -- [--view view.json | --catalogue]
          [--blend out.blend] [--render out.png] [--samples 96] [--check]
          [--straight] [--labels auto|placard|float]
          [--highlight KEY,NAME,SOURCE>TARGET] [--flyover [FLOW]]
          [--animation out.mp4] [--fps 30] [--frame N]
--labels auto (the default) floats each name above its element when the placards
would render too small to read. --highlight dims the scene and lights what it names;
--flyover flies the camera through one of the view's flows, step by step, into the
timeline, and --animation renders that to a video (EEVEE, H.264).
  Without --view or --catalogue it builds the example view (the canvas's hero).
In the Blender UI: open in the Text Editor and Run Script; it builds the example
view into a new "ArchiMate" collection and leaves the rest of the file alone.

A view file is what genius-uml's SceneLayout writes (fish.genius.uml.scene), or anything
shaped like it:
  {"elements": [{"key": "customer", "type": "business-actor", "name": "Customer",
                 "x": -1.6, "y": 2.6,                      # centre, on the ground plane
                 "w": 2.0, "d": 1.3, "z": 0.0,             # optional: footprint, base height
                 "parent": null, "container": false}, ...], # optional: what it stands on
   "relationships": [{"source": "customer", "target": "handle-order", "type": "assignment",
                      "sourcePort": "east", "targetPort": "west",  # optional: the route
                      "bends": [{"x": 0.0, "y": 2.6}]}, ...],
   "flows": [{"key": "ordering", "name": "Ordering",              # optional: walks
              "steps": [{"source": "customer", "target": "handle-order",
                         "label": "places an order",
                         "relationship": null}, ...]}, ...]}     # optional: the carrier
x and y are in Blender units; +y is away from the viewer. Without ports and bends a
connector runs straight between the nearest ports; --straight forces that.
"""

import argparse
import json
import math
import os
import sys
from collections import namedtuple

import bmesh
import bpy
from mathutils import Matrix, Vector

P_W, P_D, P_H = 2.0, 1.3, 0.2
J_SIZE = 1.24  # a junction's round plinth, as SceneLayout.JUNCTION_SIZE
DEG = math.pi / 180
PI = math.pi

LAYERS = {
    "strategy": ("#F2CF8F", "#B97A1E"),
    "business": ("#F6E27C", "#BF9A0E"),
    "application": ("#93DCEA", "#2690A8"),
    "technology": ("#AEDB95", "#4E9334"),
    "physical": ("#AEDB95", "#4E9334"),
    "motivation": ("#C3BEF2", "#6559C4"),
    "implementation": ("#F6B9C1", "#C9536A"),
    "migration": ("#BCE5BC", "#4F9A55"),
    "other": ("#F7BE84", "#CF6E1F"),
    "neutral": ("#D9D6CF", "#8D8980"),
}

COLL = None  # the collection everything is built into
BOUNDS = {}  # object name -> (lo, hi) of its own geometry, in its local space


# ── colour and materials ──────────────────────────────────────────────────────
def srgb_to_linear(hexc):
    h = hexc.lstrip("#")
    out = []
    for i in (0, 2, 4):
        c = int(h[i:i + 2], 16) / 255
        out.append(((c + 0.055) / 1.055) ** 2.4 if c > 0.04045 else c / 12.92)
    return out


def material(name, rgb_linear, rough=0.55, alpha=1.0):
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat = bpy.data.materials.new(name)
    if bpy.app.version < (5, 0, 0):
        mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    if bsdf is None:
        nodes.clear()
        bsdf = nodes.new("ShaderNodeBsdfPrincipled")
        out = nodes.new("ShaderNodeOutputMaterial")
        mat.node_tree.links.new(bsdf.outputs[0], out.inputs[0])
    bsdf.inputs["Base Color"].default_value = (*rgb_linear, 1.0)
    bsdf.inputs["Roughness"].default_value = rough
    if alpha < 1.0:
        bsdf.inputs["Alpha"].default_value = alpha
        if hasattr(mat, "surface_render_method"):
            mat.surface_render_method = "BLENDED"
        elif hasattr(mat, "blend_method"):
            mat.blend_method = "BLEND"
    mat.diffuse_color = (*rgb_linear, alpha)
    return mat


class Mats:
    _cache = {}

    def __new__(cls, layer):
        if layer in cls._cache:
            return cls._cache[layer]
        self = super().__new__(cls)
        base_hex, deep_hex = LAYERS.get(layer, LAYERS["neutral"])
        base, deep = srgb_to_linear(base_hex), srgb_to_linear(deep_hex)
        light = [b + (1 - b) * 0.5 for b in base]
        pale = [b + (1 - b) * 0.4 for b in base]
        self.base = material(f"am.{layer}.base", base, 0.62)
        self.deep = material(f"am.{layer}.deep", deep, 0.5)
        self.light = material(f"am.{layer}.light", light, 0.55)
        self.glass = material(f"am.{layer}.glass", pale, 0.2, alpha=0.38)
        cls._cache[layer] = self
        return self


def ink():
    return material("am.ink", srgb_to_linear("#2E3138"), 0.45)


def label_ink():
    return material("am.label", srgb_to_linear("#24272D"), 0.75)


def label_light():
    return material("am.label.light", srgb_to_linear("#FBFAF8"), 0.75)


def paper():
    return material("am.paper", srgb_to_linear("#F7F6F2"), 0.5)


def port_mat():
    return material("am.port", srgb_to_linear("#E4572E"), 0.4)


# ── 2D outlines: a shape is a list of loops (outer first, holes after) ─────────
class Path:
    def __init__(self):
        self.pts = []

    def to(self, x, y):
        self.pts.append((x, y))
        return self

    def arc(self, cx, cy, r, a0, a1, clockwise=False):
        # three.js absarc semantics
        d = a1 - a0
        while d < 0:
            d += 2 * PI
        while d > 2 * PI:
            d -= 2 * PI
        if clockwise:
            d = -2 * PI if abs(d - 2 * PI) < 1e-9 else d - 2 * PI
        n = max(4, int(math.ceil(abs(d) / (2 * PI) * 72)))
        for i in range(n + 1):
            a = a0 + d * i / n
            self.pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        return self

    def bezier(self, c1, c2, e, n=28):
        p0 = self.pts[-1]
        for i in range(1, n + 1):
            t = i / n
            u = 1 - t
            self.pts.append(tuple(u ** 3 * p0[k] + 3 * u * u * t * c1[k] + 3 * u * t * t * c2[k] + t ** 3 * e[k] for k in (0, 1)))
        return self

    def loop(self):
        out = []
        for p in self.pts:
            if not out or (abs(p[0] - out[-1][0]) > 1e-6 or abs(p[1] - out[-1][1]) > 1e-6):
                out.append(p)
        if len(out) > 2 and abs(out[0][0] - out[-1][0]) < 1e-6 and abs(out[0][1] - out[-1][1]) < 1e-6:
            out.pop()
        return out


def poly(pts):
    return [list(pts)]


def rrect(w, h, r):
    x, y = -w / 2, -h / 2
    r = min(r, w / 2, h / 2)
    if r < 1e-4:
        return poly([(x, y), (x + w, y), (x + w, y + h), (x, y + h)])
    p = Path().to(x + r, y).to(x + w - r, y)
    p.arc(x + w - r, y + r, r, -PI / 2, 0).to(x + w, y + h - r)
    p.arc(x + w - r, y + h - r, r, 0, PI / 2).to(x + r, y + h)
    p.arc(x + r, y + h - r, r, PI / 2, PI).to(x, y + r)
    p.arc(x + r, y + r, r, PI, PI * 1.5)
    return [p.loop()]


def chamfer(w, h, c):
    x, y = w / 2, h / 2
    return poly([(-x + c, -y), (x - c, -y), (x, -y + c), (x, y - c), (x - c, y), (-x + c, y), (-x, y - c), (-x, -y + c)])


def wave(w, h):
    x0, x1, top, bot = -w / 2, w / 2, h / 2, -h / 2 + 0.07
    p = Path().to(x0, top).to(x1, top).to(x1, bot)
    p.bezier((x1 - w * 0.3, bot - 0.17), (x0 + w * 0.35, bot + 0.15), (x0, bot - 0.03))
    return [p.loop()]


def circle_loop(r, n=64, cx=0.0, cy=0.0):
    return [(cx + r * math.cos(2 * PI * i / n), cy + r * math.sin(2 * PI * i / n)) for i in range(n)]


def gear(R, n, td, hole):
    pts = []
    steps = n * 4
    for i in range(steps):
        a = i / steps * 2 * PI
        r = R if i % 4 in (1, 2) else R - td
        pts.append((r * math.cos(a), r * math.sin(a)))
    loops = [pts]
    if hole:
        loops.append(circle_loop(hole, 48)[::-1])
    return loops


# ── objects ───────────────────────────────────────────────────────────────────
def link(ob, parent=None):
    COLL.objects.link(ob)
    if parent is not None:
        ob.parent = parent
    return ob


def empty(name, parent=None, size=0.05, kind="PLAIN_AXES"):
    e = bpy.data.objects.new(name, None)
    e.empty_display_size = size
    e.empty_display_type = kind
    return link(e, parent)


def smooth_split(ob):
    m = ob.modifiers.new("EdgeSplit", "EDGE_SPLIT")
    m.split_angle = 35 * DEG
    return ob


def bounds(ob, lo, hi):
    # Recorded at build time: an evaluated bound_box read right after creation can
    # still be Blender's placeholder cube.
    BOUNDS[ob.name] = (Vector(lo), Vector(hi))
    return ob


def place_obj(ob, loc=None, rot=None, scale=None):
    if loc is not None:
        ob.location = loc
    if rot is not None:
        ob.rotation_euler = rot
    if scale is not None:
        ob.scale = scale
    return ob


def mesh_from_bm(name, bm, mat, parent, smooth=True):
    if smooth:
        for f in bm.faces:
            f.smooth = True
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    ob = link(bpy.data.objects.new(name, me), parent)
    return smooth_split(ob) if smooth else ob


class B:
    """Builds primitives as children of one parent object, in its local space
    (three.js space for sculptures: y up, +z towards the viewer)."""

    def __init__(self, parent, prefix):
        self.parent = parent
        self.prefix = prefix

    def name(self, kind):
        return f"{self.prefix}.{kind}"

    def sub(self, kind="group", loc=None, rot=None, scale=None):
        e = place_obj(empty(self.name(kind), self.parent, 0.02), loc, rot, scale)
        return B(e, self.prefix)

    def up(self, loops, depth, mat, bevel=0.025, loc=None, rot=None):
        cu = bpy.data.curves.new(self.name("extrude"), "CURVE")
        cu.dimensions = "2D"
        cu.fill_mode = "BOTH"
        b = bevel
        cu.extrude = max(depth / 2 - b, 0.0005)
        cu.bevel_depth = b
        cu.bevel_resolution = 2
        cu.offset = -b if b > 0 else 0.0
        cu.resolution_u = 1
        for lp in loops:
            sp = cu.splines.new("POLY")
            sp.points.add(len(lp) - 1)
            for i, (x, y) in enumerate(lp):
                sp.points[i].co = (x, y, 0.0, 1.0)
            sp.use_cyclic_u = True
            sp.use_smooth = True
        cu.materials.append(mat)
        ob = link(bpy.data.objects.new(cu.name, cu), self.parent)
        xs, ys = [p[0] for p in loops[0]], [p[1] for p in loops[0]]
        bounds(ob, (min(xs), min(ys), -depth / 2), (max(xs), max(ys), depth / 2))
        return place_obj(smooth_split(ob), loc, rot)

    def box(self, w, h, d, mat, bevel=0.02, loc=None, rot=None):
        return self.up(rrect(w, h, bevel * 1.6), d, mat, bevel, loc, rot)

    def tube(self, pts, r, mat, closed=False, loc=None, rot=None):
        cu = bpy.data.curves.new(self.name("tube"), "CURVE")
        cu.dimensions = "3D"
        cu.fill_mode = "FULL"
        cu.bevel_depth = r
        cu.bevel_resolution = 4
        cu.use_fill_caps = not closed
        cu.resolution_u = 1
        sp = cu.splines.new("POLY")
        sp.points.add(len(pts) - 1)
        for i, p in enumerate(pts):
            sp.points[i].co = (p[0], p[1], p[2], 1.0)
        sp.use_cyclic_u = closed
        sp.use_smooth = True
        cu.materials.append(mat)
        ob = link(bpy.data.objects.new(cu.name, cu), self.parent)
        bounds(ob, [min(p[k] for p in pts) - r for k in range(3)], [max(p[k] for p in pts) + r for k in range(3)])
        return place_obj(ob, loc, rot)

    def rod(self, a, b, r, mat):
        if (Vector(a) - Vector(b)).length < 1e-4:
            return None
        return self.tube([a, b], r, mat)

    def ring(self, R, r, mat, arc=2 * PI, loc=None, rot=None):
        closed = arc >= 2 * PI - 1e-6
        n = max(8, int(72 * arc / (2 * PI)))
        pts = [(R * math.cos(arc * i / n), R * math.sin(arc * i / n), 0.0) for i in range(n if closed else n + 1)]
        return self.tube(pts, r, mat, closed, loc, rot)

    def ball(self, p, r, mat, scale=None):
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=40, v_segments=20, radius=r)
        ob = bounds(mesh_from_bm(self.name("ball"), bm, mat, self.parent), (-r, -r, -r), (r, r, r))
        return place_obj(ob, p, None, scale)

    def capsule(self, r, length, mat, loc=None, rot=None):
        # a UV sphere whose upper half is lifted by the cylinder length: one mesh, axis Z
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=40, v_segments=20, radius=r)
        for v in bm.verts:
            v.co.z += length / 2 if v.co.z >= -1e-6 else -length / 2
        ob = bounds(mesh_from_bm(self.name("capsule"), bm, mat, self.parent), (-r, -r, -r - length / 2), (r, r, r + length / 2))
        return place_obj(ob, loc, rot)

    def cylinder(self, r, depth, mat, loc=None, rot=None, segs=56):
        # axis Z, centred
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=segs, radius1=r, radius2=r, depth=depth)
        ob = bounds(mesh_from_bm(self.name("cylinder"), bm, mat, self.parent), (-r, -r, -depth / 2), (r, r, depth / 2))
        return place_obj(ob, loc, rot)

    def disc(self, r, depth, mat, loc=None):
        return self.cylinder(r, depth, mat, loc)

    def cone(self, p, d, r, h, mat):
        # base centre at p, pointing along d
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=28, radius1=r, radius2=0.0, depth=h)
        bmesh.ops.translate(bm, verts=bm.verts, vec=(0, 0, h / 2))
        ob = bounds(mesh_from_bm(self.name("cone"), bm, mat, self.parent), (-r, -r, 0), (r, r, h))
        ob.location = p
        ob.rotation_mode = "QUATERNION"
        ob.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(Vector(d).normalized())
        return ob

    def lathe(self, profile, mat, segs=56):
        # revolve (r, y) points around the local Y axis
        bm = bmesh.new()
        rings = []
        for r, y in profile:
            rings.append([bm.verts.new((r * math.sin(2 * PI * s / segs), y, r * math.cos(2 * PI * s / segs))) for s in range(segs)])
        for a, b in zip(rings, rings[1:]):
            for s in range(segs):
                t = (s + 1) % segs
                try:
                    bm.faces.new((a[s], a[t], b[t], b[s]))
                except ValueError:
                    pass
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        rmax = max(r for r, _ in profile)
        return bounds(mesh_from_bm(self.name("lathe"), bm, mat, self.parent),
                      (-rmax, min(y for _, y in profile), -rmax), (rmax, max(y for _, y in profile), rmax))

    def text(self, body, w, h, mat, loc, rot, font=None, size=0.16):
        cu = bpy.data.curves.new(self.name("name"), "FONT")
        cu.body = body
        cu.align_x = "CENTER"
        cu.align_y = "CENTER"
        cu.size = size
        if font is not None:
            cu.font = font
        tb = cu.text_boxes[0]
        # with align_y CENTER Blender centres the lines on the object origin, not in the box
        tb.width, tb.height, tb.x, tb.y = w, h, -w / 2, 0.0
        cu.overflow = "SCALE"
        cu.materials.append(mat)
        ob = link(bpy.data.objects.new(cu.name, cu), self.parent)
        return place_obj(ob, loc, rot)


def bullseye(b, m, y):
    b.disc(0.38, 0.08, m.deep, (0, y, 0))
    b.disc(0.26, 0.12, m.light, (0, y, 0.01))
    b.disc(0.13, 0.16, m.deep, (0, y, 0.02))


# ── sculptures: one per ArchiMate icon, ported from archimate3d.js ────────────
REQ = [(-0.46, -0.26), (0.3, -0.26), (0.46, 0.26), (-0.3, 0.26)]


def i_resource(b, m):
    b.up(rrect(0.74, 0.42, 0.1), 0.38, m.deep, 0.025)
    b.box(0.07, 0.18, 0.22, m.deep, 0.015, (0.4, 0, 0))
    for x in (-0.2, 0, 0.2):
        b.box(0.07, 0.26, 0.04, m.light, 0.01, (x, 0, 0.2))


def i_capability(b, m):
    s, p = 0.22, 0.245
    for i in range(3):
        for j in range(i + 1):
            b.box(s, s, s, m.deep, 0.025, ((i - 1) * p, s / 2 + j * s, 0))


def i_value_stream(b, m):
    b.up(poly([(-0.5, -0.24), (0.24, -0.24), (0.5, 0), (0.24, 0.24), (-0.5, 0.24), (-0.26, 0)]), 0.3, m.deep)


def i_course_of_action(b, m):
    bullseye(b.sub("target", (0.14, 0, 0)), m, 0.42)
    S, C, E = Vector((-0.52, 0.05, 0.24)), Vector((-0.5, 0.62, 0.24)), Vector((-0.02, 0.44, 0.2))
    pts = [tuple((1 - t) ** 2 * S + 2 * (1 - t) * t * C + t * t * E) for t in (i / 40 for i in range(41))]
    b.tube(pts, 0.032, ink())
    b.ball(tuple(S), 0.032, ink())
    b.cone(tuple(E), tuple(E - C), 0.07, 0.16, ink())


def i_actor(b, m):
    r, d = 0.048, m.deep
    b.ball((0, 0.9, 0), 0.13, d)
    b.rod((0, 0.76, 0), (0, 0.4, 0), r, d)
    b.rod((-0.27, 0.62, 0), (0.27, 0.62, 0), r, d)
    b.rod((0, 0.4, 0), (-0.2, 0.03, 0), r, d)
    b.rod((0, 0.4, 0), (0.2, 0.03, 0), r, d)
    for p in ((0, 0.4, 0), (-0.27, 0.62, 0), (0.27, 0.62, 0), (-0.2, 0.03, 0), (0.2, 0.03, 0)):
        b.ball(p, r, d)


def i_role(b, m):
    b.cylinder(0.22, 0.72, m.deep, (0, 0, 0), (0, PI / 2, 0))
    b.cylinder(0.17, 0.03, m.light, (-0.37, 0, 0), (0, PI / 2, 0))


def i_collaboration(b, m):
    b.ring(0.27, 0.055, m.deep, loc=(-0.16, 0.33, 0.05))
    b.ring(0.27, 0.055, m.light, loc=(0.16, 0.33, -0.05))


def i_interface(b, m):
    b.ring(0.2, 0.055, m.deep, loc=(0.22, 0.26, 0))
    b.rod((-0.5, 0.26, 0), (0.04, 0.26, 0), 0.055, m.deep)


def i_process(b, m):
    b.up(poly([(-0.5, -0.13), (0.1, -0.13), (0.1, -0.3), (0.5, 0), (0.1, 0.3), (0.1, 0.13), (-0.5, 0.13)]), 0.28, m.deep)


def i_function(b, m):
    b.up(poly([(-0.3, -0.42), (0, -0.24), (0.3, -0.42), (0.3, 0.18), (0, 0.42), (-0.3, 0.18)]), 0.26, m.deep)


def i_interaction(b, m):
    r, g = 0.38, 0.05
    left = Path().to(-g, r).arc(-g, 0, r, PI / 2, PI * 1.5).loop()
    right = Path().to(g, -r).arc(g, 0, r, -PI / 2, PI / 2).loop()
    b.up([left], 0.24, m.deep)
    b.up([right], 0.24, m.deep)


def i_event(b, m):
    p = Path().to(-0.5, 0.28).to(0.2, 0.28).arc(0.2, 0, 0.28, PI / 2, -PI / 2, True).to(-0.5, -0.28).to(-0.3, 0)
    b.up([p.loop()], 0.26, m.deep)


def i_service(b, m):
    b.capsule(0.24, 0.56, m.deep, (0, 0, 0), (0, PI / 2, 0))


def i_object(b, m):
    b.box(0.8, 0.6, 0.08, m.deep)
    b.box(0.8, 0.14, 0.05, m.light, 0.012, (0, 0.23, 0.06))


def i_contract(b, m):
    i_object(b, m)
    b.box(0.8, 0.05, 0.04, m.light, 0.01, (0, 0.02, 0.055))


def i_representation(b, m):
    b.up(wave(0.8, 0.64), 0.08, m.deep)
    b.box(0.8, 0.13, 0.05, m.light, 0.012, (0, 0.255, 0.06))


def i_product(b, m):
    b.box(0.84, 0.54, 0.52, m.deep, 0.03)
    b.box(0.38, 0.09, 0.54, m.light, 0.015, (-0.21, 0.31, 0))


def i_component(b, m):
    b.box(0.62, 0.66, 0.46, m.deep, 0.03)
    b.box(0.26, 0.13, 0.32, m.light, 0.015, (-0.33, 0.14, 0))
    b.box(0.26, 0.13, 0.32, m.light, 0.015, (-0.33, -0.14, 0))


def i_node(b, m):
    b.box(0.92, 0.64, 0.72, m.deep, 0.045)


def i_device(b, m):
    s = b.sub("screen", (0, 0.64, -0.05), (-0.12, 0, 0))
    s.box(0.84, 0.52, 0.06, m.deep, 0.02)
    s.box(0.72, 0.4, 0.02, m.light, 0.005, (0, 0.01, 0.035))
    b.box(0.08, 0.3, 0.05, m.deep, 0.015, (0, 0.27, -0.08))
    b.box(0.34, 0.04, 0.22, m.deep, 0.012, (0, 0.02, -0.08))
    b.box(0.8, 0.05, 0.26, m.deep, 0.015, (0, 0.025, 0.3))


def i_system_software(b, m):
    b.disc(0.3, 0.1, m.light, (0.12, 0.44, -0.1))
    b.disc(0.3, 0.12, m.deep, (-0.06, 0.32, 0.04))


def i_path(b, m):
    for x in (-0.2, 0, 0.2):
        b.box(0.13, 0.07, 0.07, m.deep, 0.015, (x, 0.3, 0))
    b.up(poly([(-0.5, 0.3), (-0.31, 0.47), (-0.31, 0.13)]), 0.1, m.deep, 0.012)
    b.up(poly([(0.5, 0.3), (0.31, 0.13), (0.31, 0.47)]), 0.1, m.deep, 0.012)


def i_network(b, m):
    A, Bp, C, D = (-0.42, 0.08, 0), (0.22, 0.08, 0), (0.42, 0.52, 0), (-0.22, 0.52, 0)
    for a, c in ((A, Bp), (Bp, C), (C, D), (D, A)):
        b.rod(a, c, 0.032, m.deep)
    for p in (A, Bp, C, D):
        b.ball(p, 0.08, m.deep)


def i_artifact(b, m):
    b.up(poly([(-0.32, -0.42), (0.32, -0.42), (0.32, 0.2), (0.1, 0.42), (-0.32, 0.42)]), 0.12, m.deep, 0.02)
    b.up(poly([(0.1, 0.2), (0.32, 0.2), (0.1, 0.42)]), 0.04, m.light, 0.008, (0, 0, 0.07))


def i_equipment(b, m):
    b.up(gear(0.3, 10, 0.075, 0.08), 0.18, m.deep, 0.012, (-0.12, 0, 0))
    b.up(gear(0.2, 7, 0.065, 0.06), 0.15, m.light, 0.01, (0.22, 0.29, 0.02), (0, 0, 0.2))


def i_facility(b, m):
    b.up(poly([(-0.5, 0), (0.5, 0), (0.5, 0.55), (0.2, 0.36), (0.2, 0.55), (-0.1, 0.36), (-0.1, 0.55),
               (-0.3, 0.42), (-0.3, 0.9), (-0.46, 0.9), (-0.46, 0.42), (-0.5, 0.42)]), 0.44, m.deep, 0.015)


def i_distribution(b, m):
    b.rod((-0.31, 0.24, 0), (0.31, 0.24, 0), 0.035, m.deep)
    b.rod((-0.31, 0.4, 0), (0.31, 0.4, 0), 0.035, m.deep)
    b.up(poly([(-0.5, 0.32), (-0.3, 0.54), (-0.3, 0.1)]), 0.12, m.deep, 0.012)
    b.up(poly([(0.5, 0.32), (0.3, 0.1), (0.3, 0.54)]), 0.12, m.deep, 0.012)


def i_material(b, m):
    r = 0.38
    v = [(r * math.cos(k * PI / 3), r * math.sin(k * PI / 3)) for k in range(6)]
    b.up(poly(v), 0.24, m.deep, 0.02)
    for i in (0, 2, 4):
        a, c = v[i], v[(i + 1) % 6]
        s, t = 0.68, 0.2
        p, q = (a[0] * s, a[1] * s), (c[0] * s, c[1] * s)
        b.rod((p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, 0.135),
              (q[0] + (p[0] - q[0]) * t, q[1] + (p[1] - q[1]) * t, 0.135), 0.026, m.light)


def i_driver(b, m):
    c = (0, 0.44, 0)
    b.ring(0.28, 0.045, m.deep, loc=c)
    b.ball(c, 0.075, m.light)
    for k in range(8):
        a = k * PI / 4 + PI / 8
        e = (math.cos(a) * 0.42, 0.44 + math.sin(a) * 0.42, 0)
        b.rod(c, e, 0.026, m.deep)
        b.ball(e, 0.045, m.deep)


def i_assessment(b, m):
    cx, cy, R, a = 0.1, 0.52, 0.2, 225 * DEG
    s = (cx + math.cos(a) * (R + 0.04), cy + math.sin(a) * (R + 0.04), 0)
    e = (cx + math.cos(a) * 0.68, cy + math.sin(a) * 0.68, 0)
    b.ring(R, 0.05, m.deep, loc=(cx, cy, 0))
    b.disc(R - 0.02, 0.02, m.glass, (cx, cy, 0))
    b.rod(s, e, 0.055, m.deep)
    b.ball(e, 0.055, m.deep)


def i_goal(b, m):
    bullseye(b, m, 0.4)


def i_outcome(b, m):
    bullseye(b, m, 0.4)
    tip, tail = Vector((0, 0.4, 0.1)), Vector((0.34, 0.74, 0.62))
    u = (tail - tip).normalized()
    base = tip + u * 0.16
    b.rod(tuple(base), tuple(tail), 0.026, ink())
    b.cone(tuple(base), tuple(-u), 0.06, 0.16, ink())
    b.cone(tuple(tail - u * 0.14), tuple(u), 0.07, 0.16, ink())


def i_principle(b, m):
    b.box(0.52, 0.86, 0.06, m.light, 0.02, (0, 0.43, -0.09))
    b.capsule(0.075, 0.36, m.deep, (0, 0.56, 0), (-PI / 2, 0, 0))
    b.ball((0, 0.16, 0), 0.085, m.deep)


def i_requirement(b, m):
    b.up(poly(REQ), 0.24, m.deep)


def i_constraint(b, m):
    b.up(poly(REQ), 0.24, m.deep)
    b.rod((-0.27, -0.2, 0.135), (-0.147, 0.2, 0.135), 0.024, m.light)


def i_meaning(b, m):
    for p, r in (((-0.27, 0.2, 0), 0.2), ((0, 0.3, -0.02), 0.27), ((0.27, 0.2, 0), 0.2),
                 ((0.13, 0.15, 0.13), 0.16), ((-0.13, 0.15, 0.13), 0.16)):
        b.ball(p, r, m.deep)


def i_value(b, m):
    b.ball((0, 0, 0), 0.3, m.deep, scale=(1.45, 0.85, 0.55))


def i_work_package(b, m):
    R, arc, rho = 0.28, 1.55 * PI, 0.3 * PI
    e = arc + rho
    b.ring(R, 0.06, m.deep, arc, (0, 0.4, 0), (0, 0, rho))
    b.ball((R * math.cos(rho), 0.4 + R * math.sin(rho), 0), 0.06, m.deep)
    b.cone((R * math.cos(e), 0.4 + R * math.sin(e), 0), (-math.sin(e), math.cos(e), 0), 0.13, 0.2, m.deep)


def i_deliverable(b, m):
    b.up(wave(0.8, 0.64), 0.1, m.deep)


def i_plateau(b, m):
    b.box(0.72, 0.13, 0.44, m.deep, 0.02, (-0.12, 0.065, 0))
    b.box(0.72, 0.13, 0.44, m.light, 0.02, (0, 0.195, 0))
    b.box(0.72, 0.13, 0.44, m.deep, 0.02, (0.12, 0.325, 0))


def i_gap(b, m):
    b.ring(0.26, 0.05, m.deep, loc=(0, 0.36, 0))
    b.rod((-0.46, 0.45, 0.07), (0.46, 0.45, 0.07), 0.034, m.deep)
    b.rod((-0.46, 0.27, 0.07), (0.46, 0.27, 0.07), 0.034, m.deep)


def i_location(b, m):
    prof = [(0, 0), (0.05, 0.12), (0.14, 0.32), (0.22, 0.5), (0.25, 0.62), (0.23, 0.74), (0.16, 0.83), (0.07, 0.875), (0, 0.885)]
    b.lathe(prof, m.deep)
    b.ball((0, 0.64, 0.2), 0.085, m.light)


def i_and_junction(b, m):
    b.ball((0, 0.26, 0), 0.26, ink())


def i_or_junction(b, m):
    b.ring(0.24, 0.05, ink(), loc=(0, 0.29, 0))
    b.ring(0.24, 0.05, ink(), loc=(0, 0.29, 0), rot=(0, PI / 2, 0))


ICONS = {
    "resource": i_resource, "capability": i_capability, "valueStream": i_value_stream,
    "courseOfAction": i_course_of_action, "actor": i_actor, "role": i_role,
    "collaboration": i_collaboration, "interface": i_interface, "process": i_process,
    "function": i_function, "interaction": i_interaction, "event": i_event, "service": i_service,
    "object": i_object, "contract": i_contract, "representation": i_representation,
    "product": i_product, "component": i_component, "node": i_node, "device": i_device,
    "systemSoftware": i_system_software, "path": i_path, "network": i_network,
    "artifact": i_artifact, "equipment": i_equipment, "facility": i_facility,
    "distribution": i_distribution, "material": i_material, "driver": i_driver,
    "assessment": i_assessment, "goal": i_goal, "outcome": i_outcome, "principle": i_principle,
    "requirement": i_requirement, "constraint": i_constraint, "meaning": i_meaning,
    "value": i_value, "workPackage": i_work_package, "deliverable": i_deliverable,
    "plateau": i_plateau, "gap": i_gap, "location": i_location,
    "andJunction": i_and_junction, "orJunction": i_or_junction,
}

SINK = {"process": 0.1, "interaction": 0.05, "equipment": 0.05, "goal": 0.04, "outcome": 0.04,
        "courseOfAction": 0.04, "gap": 0.04, "workPackage": 0.04, "location": 0.04,
        "collaboration": 0.035, "interface": 0.035, "andJunction": 0.05, "orJunction": 0.05}


# ── the element catalogue ─────────────────────────────────────────────────────
def title_of(key):
    return " ".join(w if w == "of" else w[:1].upper() + w[1:] for w in key.split("-"))


ELEMENTS = {}
LAYER_ORDER = []


def define(layer, form, rows):
    if layer not in LAYER_ORDER:
        LAYER_ORDER.append(layer)
    for key, icon in rows:
        ELEMENTS[key] = {"key": key, "name": title_of(key), "layer": layer, "plinth": form, "icon": icon}


define("strategy", "structure", [("resource", "resource")])
define("strategy", "behaviour", [("capability", "capability"), ("value-stream", "valueStream"), ("course-of-action", "courseOfAction")])
define("business", "structure", [("business-actor", "actor"), ("business-role", "role"), ("business-collaboration", "collaboration"),
                                 ("business-interface", "interface")])
define("business", "behaviour", [("business-process", "process"), ("business-function", "function"), ("business-interaction", "interaction"),
                                 ("business-event", "event"), ("business-service", "service")])
define("business", "structure", [("business-object", "object"), ("contract", "contract"), ("representation", "representation"), ("product", "product")])
define("application", "structure", [("application-component", "component"), ("application-collaboration", "collaboration"),
                                    ("application-interface", "interface")])
define("application", "behaviour", [("application-function", "function"), ("application-interaction", "interaction"),
                                    ("application-process", "process"), ("application-event", "event"), ("application-service", "service")])
define("application", "structure", [("data-object", "object")])
define("technology", "structure", [("node", "node"), ("device", "device"), ("system-software", "systemSoftware"),
                                   ("technology-collaboration", "collaboration"), ("technology-interface", "interface"), ("path", "path"),
                                   ("communication-network", "network")])
define("technology", "behaviour", [("technology-function", "function"), ("technology-process", "process"),
                                   ("technology-interaction", "interaction"), ("technology-event", "event"), ("technology-service", "service")])
define("technology", "structure", [("artifact", "artifact")])
define("physical", "structure", [("equipment", "equipment"), ("facility", "facility"), ("distribution-network", "distribution"), ("material", "material")])
define("motivation", "motivation", [("stakeholder", "role"), ("driver", "driver"), ("assessment", "assessment"), ("goal", "goal"),
                                    ("outcome", "outcome"), ("principle", "principle"), ("requirement", "requirement"),
                                    ("constraint", "constraint"), ("meaning", "meaning"), ("value", "value")])
define("implementation", "behaviour", [("work-package", "workPackage")])
define("implementation", "structure", [("deliverable", "deliverable")])
define("implementation", "behaviour", [("implementation-event", "event")])
define("migration", "structure", [("plateau", "plateau"), ("gap", "gap")])
define("other", "structure", [("location", "location")])
define("neutral", "tray", [("grouping", None)])
define("neutral", "puck", [("and-junction", "andJunction"), ("or-junction", "orJunction")])


# ── plinths, placard, element ─────────────────────────────────────────────────
PLACARD = {"front": 0.6, "back": 0.32, "lip": 0.03, "w": 1.5, "slope": 35 * DEG}


def plinth(key, form, m, w=P_W, d=P_D):
    """The root object of an element, in Blender space. Returns (object, half height)."""
    root = B(None, key)
    if form == "puck":
        ob = root.cylinder(J_SIZE / 2, P_H, m.base, segs=72)
        half = P_H / 2
    elif form == "tray":
        ob = root.up(rrect(w, d, 0.04), 0.05, m.glass, 0.01)
        half = 0.025
    elif form == "behaviour":
        ob, half = root.up(rrect(w, d, 0.36), P_H, m.base, 0.05), P_H / 2
    elif form == "motivation":
        ob, half = root.up(chamfer(w, d, 0.3), P_H, m.base, 0.025), P_H / 2
    else:
        ob, half = root.up(rrect(w, d, 0.04), P_H, m.base, 0.025), P_H / 2
    ob.name = key
    return ob, half


def tray(b, m, w=P_W, d=P_D):
    """A grouping's dashed rim and tab, sized to its footprint."""
    th, hgt, dash, step = 0.045, 0.16, 0.15, 0.25
    ex, ez = d / 2 - th / 2, w / 2 - th / 2
    nx = max(2, round((w - 0.16) / step) + 1)
    nz = max(2, round((d - 0.16) / step) + 1)
    for i in range(nx):
        x = -(w - 0.16) / 2 + (w - 0.16) * i / (nx - 1)
        b.box(dash, hgt, th, m.deep, 0.01, (x, hgt / 2, ex))
        if x > -w / 2 + 0.8:  # the tab stands on the back edge's left end
            b.box(dash, hgt, th, m.deep, 0.01, (x, hgt / 2, -ex))
    for i in range(nz):
        z = -(d - 0.16) / 2 + (d - 0.16) * i / (nz - 1)
        b.box(th, hgt, dash, m.deep, 0.01, (ez, hgt / 2, z))
        b.box(th, hgt, dash, m.deep, 0.01, (-ez, hgt / 2, z))
    b.box(0.72, 0.36, th, m.deep, 0.012, (-w / 2 + 0.36, 0.18, -ex))


def placard(b, text, m, font, front=None, cx=0.0, width=None):
    """The name on a sloped strip; `front` is its front edge, towards the viewer."""
    c = PLACARD
    front = c["front"] if front is None else front
    width = c["w"] if width is None else width
    back = front - (c["front"] - c["back"])
    run = front - back
    rise = run * math.tan(c["slope"])
    tilt = PI / 2 - c["slope"]
    b.up(poly([(front, 0), (front, c["lip"]), (back, c["lip"] + rise), (back, 0)]),
         width, m.light, 0.008, (cx, P_H, 0), (0, -PI / 2, 0))
    b.text(text, width - 0.12, math.hypot(run, rise) - 0.05, label_ink(),
           (cx, P_H + c["lip"] + rise / 2 + math.sin(tilt) * 0.004, back + run / 2 + math.cos(tilt) * 0.004),
           (-tilt, 0, 0), font)


def descendants(ob):
    out = []
    for ch in ob.children:
        out.append(ch)
        out.extend(descendants(ch))
    return out


def place_sculpture(container, group, sink, region):
    """Stand a sculpture on the plinth's top face, centred in `region`:
    (centre x, centre z, max width, max height, max depth) in the element's model space."""
    lo, hi = Vector((1e9, 1e9, 1e9)), Vector((-1e9, -1e9, -1e9))
    for o in descendants(group.parent):
        if o.name not in BOUNDS:
            continue
        m, p = Matrix(), o  # the transform from o's space to the icon group's, from loc/rot/scale alone
        while p is not None and p != group.parent:
            m = p.matrix_basis @ m
            p = p.parent
        blo, bhi = BOUNDS[o.name]
        for c in ((x, y, z) for x in (blo.x, bhi.x) for y in (blo.y, bhi.y) for z in (blo.z, bhi.z)):
            q = m @ Vector(c)
            lo = Vector(map(min, lo, q))
            hi = Vector(map(max, hi, q))
    size, centre = hi - lo, (hi + lo) / 2
    cx, cz, max_w, max_h, max_d = region
    k = min(1.0, max_w / size.x, max_h / size.y, max_d / size.z)
    target = Vector((cx, P_H - sink + size.y * k / 2, cz))
    group.parent.scale = (k, k, k)
    group.parent.location = target - centre * k


def ports_for(w, d):
    """The middle of each plinth side, at half plinth height, in model space (y up, +z front)."""
    return {"east": (w / 2, P_H / 2, 0), "west": (-w / 2, P_H / 2, 0),
            "south": (0, P_H / 2, d / 2), "north": (0, P_H / 2, -d / 2)}


def element(key, etype, name=None, x=0.0, y=0.0, font=None, w=None, d=None, z=0.0, container=False):
    """Build one element; returns (root, {port name: empty}).
    A container is as large as w by d; its own name and sculpture go in the strip at its front."""
    e = ELEMENTS.get(etype)
    if e is None:
        raise ValueError(f"unknown ArchiMate element type: {etype}")
    m = Mats(e["layer"])
    form = e["plinth"]
    name = e["name"] if name is None else name
    if form == "puck":
        w = d = J_SIZE  # its ports sit on the rim of the puck
    else:
        w = P_W if w is None else w
        d = P_D if d is None else d
    root, half = plinth(key, form, m, w, d)
    root.location = (x, y, z + half)
    root["archimate_type"] = etype
    root["archimate_name"] = name
    root["am_w"], root["am_d"] = w, d
    root["am_z"], root["am_container"], root["am_form"] = z, bool(container), form
    model = place_obj(empty(f"{key}.model", root, 0.02), (0, 0, -half), (PI / 2, 0, 0))
    b = B(model, key)
    on_placard = bool(name) and form not in ("puck", "tray")
    if form == "tray":
        tray(b, m, w, d)
        if name:
            b.text(name, 0.62, 0.26, label_light(), (-w / 2 + 0.36, 0.18, -d / 2 + 0.045 + 0.004), (0, 0, 0), font)
    if container and on_placard:
        width = max(1.0, min(1.5, w - 1.4))
        placard(b, name, m, font, d / 2 - 0.08, -w / 2 + 0.15 + width / 2, width)
        region = (w / 2 - 0.45, d / 2 - 0.42, 0.6, 0.55, 0.45)  # small, in the front corner: not a child
    elif on_placard:
        placard(b, name, m, font)
        region = (0.0, -0.15, 1.3, 1.05, 0.8)
    else:
        region = (0.0, 0.0, 1.3, 1.05, 1.0)
    if e["icon"]:
        s = b.sub("icon")
        ICONS[e["icon"]](s, m)
        place_sculpture(model, B(s.parent, key), SINK.get(e["icon"], 0.03), region)
    ports = {}
    for pname, (px, py, pz) in ports_for(w, d).items():
        pe = empty(f"{key}.port.{pname}", root, 0.06, "SPHERE")
        pe.location = (px, -pz, py - half)
        ports[pname] = pe
    for o in descendants(root):
        o.hide_select = True
    return root, ports


# ── connectors ────────────────────────────────────────────────────────────────
REL = {
    "association": (None, None, None),
    "serving": (None, "open", None),
    "realization": ("dash", "hollow", None),
    "assignment": (None, "filled", "dot"),
    "triggering": (None, "filled", None),
    "flow": ("dash", "filled", None),
    "access": ("dot", None, None),
    "read-access": ("dot", None, "open_small"),  # the data flows to the reader, the source
    "write-access": ("dot", "open_small", None),
    "read-write-access": ("dot", "open_small", "open_small"),
    "directed-association": (None, "open_small", None),
    "influence": ("dash", "open", None),
    "specialization": (None, "hollow", None),
    "composition": (None, None, "diamond_filled"),
    "aggregation": (None, None, "diamond_hollow"),
}
LINE_R = 0.028


def decoration(kind, name, at, toward):
    """An arrowhead or tail at port `at`, its local +Z kept pointing at `toward`.
    Returns (object, empty where the line should end)."""
    b = B(None, name)
    if kind in ("filled", "hollow"):
        r, h = (0.085, 0.2) if kind == "filled" else (0.1, 0.2)
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=28, radius1=0.0, radius2=r, depth=h)
        bmesh.ops.translate(bm, verts=bm.verts, vec=(0, 0, h / 2))
        ob = mesh_from_bm(f"{name}.head", bm, ink() if kind == "filled" else paper(), None)
        off = h
    elif kind in ("open", "open_small"):
        a, l = (0.11, 0.17) if kind == "open" else (0.075, 0.12)
        ob = b.tube([(a, 0, l), (0, 0, 0), (-a, 0, l)], LINE_R, ink())
        ob.name = f"{name}.head"
        off = 0.0
    elif kind == "dot":
        ob = b.ball((0, 0, 0), 0.065, ink())
        ob.name = f"{name}.tail"
        off = 0.0
    else:  # diamonds
        bm = bmesh.new()
        v = [bm.verts.new(p) for p in ((0, 0, 0), (0.09, 0, 0.14), (0, 0.055, 0.14), (-0.09, 0, 0.14), (0, -0.055, 0.14), (0, 0, 0.28))]
        for f in ((0, 1, 2), (0, 2, 3), (0, 3, 4), (0, 4, 1), (5, 2, 1), (5, 3, 2), (5, 4, 3), (5, 1, 4)):
            bm.faces.new([v[i] for i in f])
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        ob = mesh_from_bm(f"{name}.tail", bm, ink() if kind == "diamond_filled" else paper(), None, smooth=False)
        off = 0.28
    ob.location = (0, 0, 0)
    c = ob.constraints.new("COPY_LOCATION")
    c.target = at
    t = ob.constraints.new("TRACK_TO")
    t.target = toward
    t.track_axis = "TRACK_Z"
    t.up_axis = "UP_Y"
    ob.hide_select = True
    end = at
    if off > 0:
        end = empty(f"{ob.name}.end", ob, 0.02)
        end.location = (0, 0, off)
        end.hide_select = True
    return ob, end


def hooked_path(name, a, b, bevel, mids=()):
    """A curve from object a through the fixed points `mids` to object b; its two ends are hooked."""
    bpy.context.view_layer.update()
    pts = [a.matrix_world.translation.copy(), *[Vector(p) for p in mids], b.matrix_world.translation.copy()]
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.fill_mode = "FULL"
    cu.bevel_depth = bevel
    cu.bevel_resolution = 3
    cu.use_fill_caps = True
    cu.use_path = True
    sp = cu.splines.new("POLY")
    sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        sp.points[i].co = (*p, 1.0)
    sp.use_smooth = True
    cu.materials.append(ink())
    ob = link(bpy.data.objects.new(name, cu))
    for i, anchor in ((0, a), (len(pts) - 1, b)):
        h = ob.modifiers.new(f"Hook.{i}", "HOOK")
        h.object = anchor
        h.vertex_indices_set([i])
        h.matrix_inverse = anchor.matrix_world.inverted()
    ob.hide_select = True
    return ob


def pattern(name, path, kind):
    """Dashes or dots repeated along a path: Array (fit to curve) + Curve modifier."""
    bm = bmesh.new()
    if kind == "dash":
        step, piece = 0.19, 0.12
        bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=16, radius1=LINE_R, radius2=LINE_R, depth=piece)
        bmesh.ops.rotate(bm, verts=bm.verts, cent=(0, 0, 0), matrix=Matrix.Rotation(PI / 2, 3, "Y"))
        bmesh.ops.translate(bm, verts=bm.verts, vec=(piece / 2, 0, 0))
    else:
        step, piece = 0.1, 0.07
        bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=piece / 2)
        bmesh.ops.translate(bm, verts=bm.verts, vec=(piece / 2, 0, 0))
    ob = mesh_from_bm(name, bm, ink(), None)
    arr = ob.modifiers.new("Array", "ARRAY")
    arr.fit_type = "FIT_CURVE"
    arr.curve = path
    arr.use_relative_offset = True
    arr.relative_offset_displace = (step / piece, 0, 0)
    cm = ob.modifiers.new("Curve", "CURVE")
    cm.object = path
    cm.deform_axis = "POS_X"
    ob.modifiers.move(ob.modifiers.find("EdgeSplit"), len(ob.modifiers) - 1)
    ob.hide_select = True
    return ob


def connect(name, src_ports, tgt_ports, rtype, source_port=None, target_port=None, bends=()):
    """A connector between two elements' ports. With named ports and bends (from
    SceneLayout) it follows that route; without, it runs straight between the
    nearest pair of ports."""
    if rtype not in REL:
        raise ValueError(f"unknown relationship type: {rtype}")
    bpy.context.view_layer.update()
    if source_port in src_ports and target_port in tgt_ports:
        ps, pt = src_ports[source_port], tgt_ports[target_port]
    else:
        ps, pt = min(((s, t) for s in src_ports.values() for t in tgt_ports.values()),
                     key=lambda st: (st[0].matrix_world.translation - st[1].matrix_world.translation).length)
    # the route runs at the height of the higher port, so it clears a container it crosses
    height = max(ps.matrix_world.translation.z, pt.matrix_world.translation.z)
    mids = [(p["x"], p["y"], height) for p in bends]
    line, head, tail = REL[rtype]
    start, end = ps, pt
    if mids:
        # a head or tail looks along its own segment, at the nearest bend
        first = empty(f"{name}.bend.first", None, 0.02)
        first.location = mids[0]
        last = empty(f"{name}.bend.last", None, 0.02)
        last.location = mids[-1]
        first.hide_select = last.hide_select = True
    else:
        first, last = pt, ps
    if tail:
        _, start = decoration(tail, f"{name}.tail", ps, first)
    if head:
        _, end = decoration(head, f"{name}.head", pt, last)
    path = hooked_path(f"{name}.line", start, end, 0.0 if line else LINE_R, mids)
    # the route as drawn, port to port, for a flyover to run along
    path["am_route"] = [c for p in (ps.matrix_world.translation, *map(Vector, mids), pt.matrix_world.translation) for c in p]
    if line:
        pattern(f"{name}.{line}", path, line)
    return path


# ── views ─────────────────────────────────────────────────────────────────────
EXAMPLE_VIEW = {
    "elements": [
        {"key": "customer", "type": "business-actor", "name": "Customer", "x": -1.6, "y": 2.6},
        {"key": "handle-order", "type": "business-process", "name": "Handle Order", "x": 1.6, "y": 2.6},
        {"key": "order-management", "type": "application-component", "name": "Order Management", "x": -1.6, "y": 0},
        {"key": "order-intake", "type": "application-service", "name": "Order Intake", "x": 1.6, "y": 0},
        {"key": "application-server", "type": "node", "name": "Application Server", "x": -1.6, "y": -2.6},
    ],
    "relationships": [
        {"source": "customer", "target": "handle-order", "type": "assignment"},
        {"source": "order-intake", "target": "handle-order", "type": "serving"},
        {"source": "order-management", "target": "order-intake", "type": "realization"},
        {"source": "application-server", "target": "order-management", "type": "serving"},
    ],
}


def rel_name(r):
    """A connector's name: its key, or its ends and type when it has none."""
    return r.get("key") or f"{r['source']}.{r['type']}.{r['target']}"


def build_view(view, font=None, straight=False):
    """Build a view: a scene from SceneLayout, or any hand-written list of elements and relationships."""
    built = {}
    for e in view["elements"]:
        built[e["key"]] = element(e["key"], e["type"], e.get("name"), e.get("x", 0.0), e.get("y", 0.0), font,
                                  e.get("w"), e.get("d"), e.get("z", 0.0), e.get("container", False))
    # an element standing on a container rides along with it
    bpy.context.view_layer.update()
    for e in view["elements"]:
        parent = e.get("parent")
        if parent in built:
            child, holder = built[e["key"]][0], built[parent][0]
            child.parent = holder
            child.matrix_parent_inverse = holder.matrix_world.inverted()
    connectors = []
    for r in view.get("relationships", []):
        name = rel_name(r)
        routed = not straight
        path = connect(name, built[r["source"]][1], built[r["target"]][1], r["type"],
                       r.get("sourcePort") if routed else None, r.get("targetPort") if routed else None,
                       r.get("bends", []) if routed else [])
        path["am_source"], path["am_target"] = r["source"], r["target"]
        connectors.append(path)
    return built, connectors


def catalogue_view():
    rows = [["resource", "capability", "value-stream", "course-of-action"]]
    for layer in ("business", "application", "technology", "physical", "motivation"):
        rows.append([k for k, e in ELEMENTS.items() if e["layer"] == layer])
    rows.append([k for k, e in ELEMENTS.items() if e["layer"] in ("implementation", "migration")])
    rows.append(["location", "grouping", "and-junction", "or-junction"])
    elements = []
    for r, keys in enumerate(rows):
        for c, k in enumerate(keys):
            elements.append({"key": k, "type": k, "x": c * 2.6, "y": (len(rows) - 1 - r) * 2.4})
    return {"elements": elements, "relationships": []}


# ── stage: floor, light, camera, render ───────────────────────────────────────
def frame(scene, cam, corners, azimuth, elevation, margin=0.05):
    """Aim the camera from azimuth/elevation and fit the corners in the frame,
    by the projected extent rather than a bounding sphere."""
    from bpy_extras.object_utils import world_to_camera_view

    az, el = azimuth * DEG, elevation * DEG
    back = Vector((math.cos(el) * math.sin(az), -math.cos(el) * math.cos(az), math.sin(el)))
    target = sum(corners, Vector()) / len(corners)

    def aim(dist):
        cam.location = target + back * dist
        cam.rotation_euler = (-back).to_track_quat("-Z", "Y").to_euler()
        bpy.context.view_layer.update()
        pts = [world_to_camera_view(scene, cam, c) for c in corners]
        return min(p.x for p in pts), max(p.x for p in pts), min(p.y for p in pts), max(p.y for p in pts)

    for _ in range(2):  # fit, then re-centre on the projected box and fit again
        near, far = 0.5, 2000.0
        for _ in range(40):
            mid = (near + far) / 2
            x0, x1, y0, y1 = aim(mid)
            if x0 < margin or x1 > 1 - margin or y0 < margin or y1 > 1 - margin:
                near = mid
            else:
                far = mid
        x0, x1, y0, y1 = aim(far)
        right, up = cam.matrix_world.to_3x3() @ Vector((1, 0, 0)), cam.matrix_world.to_3x3() @ Vector((0, 1, 0))
        w = 2 * far * math.tan(cam.data.angle_x / 2)
        h = w * scene.render.resolution_y / scene.render.resolution_x
        target = target + right * ((x0 + x1) / 2 - 0.5) * w + up * ((y0 + y1) / 2 - 0.5) * h
    aim(far)


def stage(roots, azimuth, elevation, res, samples):
    scene = bpy.context.scene
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    if bpy.app.version < (5, 0, 0):
        world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (*srgb_to_linear("#F3F2EE"), 1.0)
        bg.inputs[1].default_value = 0.85

    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=200)
    mesh_from_bm("floor", bm, material("am.floor", srgb_to_linear("#ECEAE4"), 0.9), None, smooth=False).hide_select = True

    def sun(name, pos, energy, shadow=True, angle=6):
        ld = bpy.data.lights.new(name, "SUN")
        ld.energy = energy
        ld.angle = angle * DEG
        if hasattr(ld, "use_shadow"):
            ld.use_shadow = shadow
        ob = link(bpy.data.objects.new(name, ld))
        ob.rotation_euler = (-Vector(pos)).to_track_quat("-Z", "Y").to_euler()
        return ob

    sun("Key", (-3, -4, 6), 3.2)
    sun("Fill", (4, 3, 2.5), 0.7, shadow=False)

    # frame the plinths themselves, not their bounding box, whose corners can be empty floor
    bpy.context.view_layer.update()
    corners = []
    for r in roots:
        p, hw, hd = r.matrix_world.translation, r.get("am_w", P_W) / 2, r.get("am_d", P_D) / 2
        top = 1.15 if not r.children or r.get("am_w", P_W) <= P_W else 0.6
        corners += [Vector((p.x + sx * hw, p.y + sy * hd, z)) for sx in (-1, 1) for sy in (-1, 1) for z in (0.0, p.z + top)]

    cd = bpy.data.cameras.new("Camera")
    cd.lens = 70
    cd.clip_end = 1000
    cam = link(bpy.data.objects.new("Camera", cd))
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = res
    frame(scene, cam, corners, azimuth, elevation)
    cam["am_azimuth"], cam["am_elevation"] = azimuth, elevation

    scene.render.engine = "CYCLES"
    scene.cycles.samples = samples
    scene.cycles.device = "CPU"
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = "Standard"
    try:
        scene.view_settings.look = "None"
    except TypeError:
        pass


# ── floating labels ───────────────────────────────────────────────────────────
LABEL_MODES = ("auto", "placard", "float")
LABEL_PX = 16              # a floating name's height in the render, in pixels
LEGIBLE_PX = 9             # a placard name below this is not read: float the names
PLACARD_NAME = 0.12        # a placard name's height in scene units, about
LEAF_TOP = 1.6             # a leaf's name floats this high, above its sculpture
CONTAINER_TOP = 0.75       # a container's floats lower, under its children's names
CONTAINER_INSET = 0.35     # and over its front strip, this far in from the edge
RAISES = 8                 # how often a name that covers another may move up
RAISE_STEP = 1.7           # how far it moves each time, in label heights
CHAR_WIDTH = 0.5           # a character's width, in label heights
CARD_MARGIN = 0.7          # the card's width beyond the text, in label heights
CARD_HEIGHT = 1.55         # the card's height, in label heights
CARD_GAP = 0.05            # how far behind the name its card sits, in label heights
CARD_ALPHA = 0.82


def _px_per_unit(scene, cam, point):
    """How many pixels one scene unit spans upright on the screen at `point`."""
    from bpy_extras.object_utils import world_to_camera_view

    up = cam.matrix_world.to_3x3() @ Vector((0, 1, 0))
    here, above = world_to_camera_view(scene, cam, point), world_to_camera_view(scene, cam, point + up)
    return abs(above.y - here.y) * scene.render.resolution_y


def label_size(scene, cam, point):
    """Scene units that render LABEL_PX tall at `point`, at any render size or aspect."""
    return LABEL_PX / _px_per_unit(scene, cam, point)


def placard_px(scene, cam, roots):
    """How tall a placard's name renders, in pixels, at the middle of the view."""
    centre = sum((r.matrix_world.translation for r in roots), Vector()) / len(roots)
    return PLACARD_NAME * _px_per_unit(scene, cam, centre)


def wants_floating(mode, scene, cam, roots):
    """auto floats the names when a placard would render smaller than LEGIBLE_PX."""
    if mode == "auto":
        return placard_px(scene, cam, roots) < LEGIBLE_PX
    return mode == "float"


def _label_extent(name):
    """A floating name with its card: width and height, in label heights."""
    return CHAR_WIDTH * len(name) + CARD_MARGIN, CARD_HEIGHT


def _screen_box(scene, cam, point, name):
    """The name's card on the screen, as fractions of the frame's width and height. The
    names keep the camera's rotation, so the card is upright there."""
    from bpy_extras.object_utils import world_to_camera_view

    centre = world_to_camera_view(scene, cam, point)
    width, height = _label_extent(name)
    half_w = LABEL_PX * width / scene.render.resolution_x / 2
    half_h = LABEL_PX * height / scene.render.resolution_y / 2
    return centre.x - half_w, centre.y - half_h, centre.x + half_w, centre.y + half_h


def _overlap(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def _label_offset(root):
    """Where an element's name floats, relative to its plinth's origin."""
    half = root.matrix_world.translation.z - root.get("am_z", 0.0)
    if root.get("am_container"):
        return Vector((0, -root.get("am_d", P_D) / 2 + CONTAINER_INSET, CONTAINER_TOP - half))
    return Vector((0, 0, LEAF_TOP - half))


def _raise_until_free(scene, cam, root, name, label_height, placed):
    """The name's offset, moved up while it covers a name already placed; and whether it
    came free within RAISES steps."""
    offset = _label_offset(root)
    for _ in range(RAISES + 1):
        box = _screen_box(scene, cam, root.matrix_world.translation + offset, name)
        if not any(_overlap(box, other) for other in placed):
            return offset, box, True
        offset = offset + Vector((0, 0, label_height * RAISE_STEP))
    return offset, box, False


def _label_object(root, name, offset, label_height, card_material, font):
    """The name as text, with its card behind it; text faces its +Z, so behind is -Z."""
    cu = bpy.data.curves.new(f"{root.name}.floating", "FONT")
    cu.body = name
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    cu.size = label_height
    if font is not None:
        cu.font = font
    cu.materials.append(label_ink())
    text = link(bpy.data.objects.new(cu.name, cu), root)
    text.location = offset
    width, height = _label_extent(name)
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=0.5)
    bmesh.ops.scale(bm, vec=(label_height * width, label_height * height, 1), verts=bm.verts)
    card = mesh_from_bm(f"{root.name}.floating.card", bm, card_material, text, smooth=False)
    card.location = (0, 0, -label_height * CARD_GAP)
    for ob in (text, card):
        ob.hide_select = True
        if hasattr(ob, "visible_shadow"):
            ob.visible_shadow = False
    return text


def _face_camera(ob, cam):
    # The camera's own rotation, not a Track To at it: aimed at the camera, a name off the
    # middle of the frame rolls and its card shears.
    keep = ob.constraints.new("COPY_ROTATION")
    keep.target = cam


def float_labels(scene, cam, roots, font=None):
    """Each named element's name above it, on a light card, upright on the screen and
    parented to its element. Taken front to back, a name that covers one already placed
    moves up, at most RAISES times. Returns (names, names not freed, pairs still covering)."""
    bpy.context.view_layer.update()
    named = [r for r in roots if r.get("archimate_name") and r.get("am_form") != "puck"]
    named.sort(key=lambda r: (cam.matrix_world.translation - r.matrix_world.translation).length)
    card_material = material("am.card", srgb_to_linear("#F7F6F2"), 0.6, alpha=CARD_ALPHA)
    placed, stuck = [], 0
    for root in named:
        name = root["archimate_name"]
        label_height = label_size(scene, cam, root.matrix_world.translation + _label_offset(root))
        offset, box, free = _raise_until_free(scene, cam, root, name, label_height, placed)
        stuck += 0 if free else 1
        placed.append(box)
        _face_camera(_label_object(root, name, offset, label_height, card_material, font), cam)
    covering = sum(1 for i, a in enumerate(placed) for b in placed[i + 1:] if _overlap(a, b))
    return len(placed), stuck, covering


# ── highlight: a spot on what matters, the rest dimmed ───────────────────────
ACCENT = "#FFB000"         # the colour of what is pointed at: a lit connector, a pulse
DIM_SHARE = 0.2            # the share of the ambient light left when something is lit
SPOT_HEIGHT = 7.0          # a spot hangs this high above the floor
SPOT_POWER = 1800.0        # watts, for a spot on one element
SPOT_MARGIN = 0.35         # the light pool reaches this far beyond the plinth
SPOT_BLEND = 0.2           # the soft edge of the pool, as Blender's spot_blend
END_SHARE = 0.55           # a lit connector's ends get this share of a spot's power
GLOW = 5.0                 # a lit connector's emission strength
CONNECTOR_PARTS = ("line", "dash", "dot", "head.head", "tail.tail")  # as connect() names them


def accent(strength=GLOW, name="am.accent"):
    """An emissive accent material; `name` makes one an animation can key on its own."""
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat = material(name, srgb_to_linear(ACCENT), 0.35)
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Emission Color"].default_value = (*srgb_to_linear(ACCENT), 1.0)
    bsdf.inputs["Emission Strength"].default_value = strength
    return mat


def ambient(scene):
    """The ambient light a highlight dims, as (owner, property): the world's strength and
    the energy of the two suns."""
    sources = []
    bg = scene.world.node_tree.nodes.get("Background") if scene.world else None
    if bg:
        sources.append((bg.inputs[1], "default_value"))
    for name in ("Key", "Fill"):
        ob = bpy.data.objects.get(name)
        if ob is not None and ob.type == "LIGHT":
            sources.append((ob.data, "energy"))
    return sources


def dim(scene, share=DIM_SHARE):
    """Turn the ambient light down, once."""
    if scene.get("am_dimmed"):
        return
    scene["am_dimmed"], scene["am_ambient"] = True, share
    for owner, prop in ambient(scene):
        setattr(owner, prop, getattr(owner, prop) * share)


def dim_between(scene, frames, share=DIM_SHARE):
    """Key the ambient light: full, dimmed over frames[0]..frames[1], dimmed until
    frames[2], full again by frames[3]."""
    scene["am_ambient"] = share
    for owner, prop in ambient(scene):
        full = getattr(owner, prop)
        for frame_no, value in zip(frames, (full, full * share, full * share, full)):
            setattr(owner, prop, value)
            owner.keyframe_insert(prop, frame=frame_no)
        setattr(owner, prop, full)


def spot_on(root, name, power=SPOT_POWER):
    """A spot straight above an element, its pool just wider than the plinth."""
    radius = max(root.get("am_w", P_W), root.get("am_d", P_D)) / 2 + SPOT_MARGIN
    ld = bpy.data.lights.new(name, "SPOT")
    ld.energy = power
    ld.spot_size = 2 * math.atan(radius / SPOT_HEIGHT) * (1 + SPOT_BLEND)
    ld.spot_blend = SPOT_BLEND
    ld.shadow_soft_size = 0.2
    ob = link(bpy.data.objects.new(name, ld))
    centre = root.matrix_world.translation
    ob.location = (centre.x, centre.y, SPOT_HEIGHT)  # a light points down its -Z already
    ob.hide_select = True
    return ob


def spots():
    return [ob for ob in bpy.data.objects if ob.type == "LIGHT" and ob.data.type == "SPOT"]


def connector_parts(name):
    """Everything drawn for one connector: its line, dashes or dots, head and tail."""
    wanted = {f"{name}.{part}" for part in CONNECTOR_PARTS}
    return [ob for ob in bpy.data.objects if ob.name in wanted]


def light_up(name, mat):
    for ob in connector_parts(name):
        for i in range(len(ob.data.materials)):
            ob.data.materials[i] = mat


def resolve(view, tokens):
    """The element keys and connector names a list of tokens names, each once. A token is
    a key, an element's name (any case), or source>target (names or keys) for the
    connectors between the two, either way round. Returns (element keys, connector names,
    tokens that named nothing)."""
    elements = view["elements"]
    relationships = view.get("relationships", [])
    rel_names = [rel_name(r) for r in relationships]

    def ends(token):
        token = token.strip()
        return [e["key"] for e in elements
                if e["key"] == token or (e.get("name") or "").casefold() == token.casefold()]

    found_elements, found_connectors, unknown = [], [], []

    def add(into, items):
        into.extend(item for item in items if item not in into)

    for token in (t.strip() for t in tokens if t.strip()):
        if token in rel_names:
            add(found_connectors, [token])
        elif ">" in token:
            one, other = (set(ends(side)) for side in token.split(">", 1))
            between = [rel_name(r) for r in relationships
                       if (r["source"] in one and r["target"] in other)
                       or (r["source"] in other and r["target"] in one)]
            add(found_connectors, between)
            if not between:
                unknown.append(token)
        elif ends(token):
            add(found_elements, ends(token))
        else:
            unknown.append(token)
    return found_elements, found_connectors, unknown


def highlight(scene, view, built, tokens):
    """Dim the scene and light what the tokens name: a spot on each element, a glow on
    each connector and a softer spot on its ends. Returns what resolve found."""
    elements, connectors, unknown = resolve(view, tokens)
    if not elements and not connectors:
        return elements, connectors, unknown
    dim(scene)
    lit = {key: spot_on(built[key][0], f"{key}.spot") for key in elements}
    by_name = {rel_name(r): r for r in view.get("relationships", [])}
    for name in connectors:
        light_up(name, accent())
        for end in (by_name[name]["source"], by_name[name]["target"]):
            if end not in lit:
                lit[end] = spot_on(built[end][0], f"{end}.spot", SPOT_POWER * END_SHARE)
    return elements, connectors, unknown


# ── flyover: the camera walks a flow, step by step ───────────────────────────
FPS = 30
OPEN_S, GLIDE_S, RUN_S, HOLD_S, CLOSE_S = 1.2, 1.1, 1.5, 0.7, 1.4   # seconds
FADE_S = 0.4               # a spot, a caption or a finished trail changes over this long
PUSH_IN = 0.6              # the camera creeps this far forward while a step holds
STEP_ELEVATION = 30        # the camera looks down on a step at this angle
STEP_MARGIN = 0.2          # a step's shot keeps this share of the frame free round what it holds
STEP_SPAN = 5.0            # a step's shot spans at least this much floor, so a near pair
                           # or a step to itself is not seen from too close
AZIMUTH_LIMIT = 55         # never further round than this: placards face the front
TRAIL_R = 0.045            # the radius of a step's light trail
TRAIL_DONE = 0.35          # a finished step's trail thins to this share, and stops glowing
TRAIL_GLOW, PULSE_GLOW = 6.0, 12.0
PULSE_R = 0.11
ROUTE_LIFT = 0.06          # a trail along a connector runs this far above it
ARC_RISE, ARC_GROWTH = 0.9, 0.1    # an arc rises this high, plus this share of its span
ARC_POINTS = 24
ARC_END = 0.35             # an arc leaves this far above a plinth
LOOP_R = 0.75              # the radius of the upright ring a step to itself runs round
LOOP_POINTS = 32
CAPTION_SHARE = 34 / 1080  # a step's caption height, as a share of the frame's: 34 px in 1080p
CAPTION_TOP = 0.1          # its distance from the top of the frame, as a share of the height
CAPTION_DEPTH = 2.0        # how far in front of the camera the caption hangs
CAPTION_MARGIN = 1.2       # the caption card's width beyond the text, in caption heights
CAPTION_HEIGHT = 1.8       # the caption card's height, in caption heights
CAPTION_CHARS = 60         # a longer label is cut short, so the card stays in the frame

Flight = namedtuple("Flight", "frames steps along margin clearance gap leaf_share loop")


def find_flow(view, wanted):
    flows = view.get("flows", [])
    if not wanted:
        return flows[0] if flows else None
    return next((f for f in flows if f.get("key") == wanted
                 or (f.get("name") or "").casefold() == wanted.casefold()), None)


def plinth_corners(root):
    p, hw, hd = root.matrix_world.translation, root.get("am_w", P_W) / 2, root.get("am_d", P_D) / 2
    return [Vector((p.x + sx * hw, p.y + sy * hd, z)) for sx in (-1, 1) for sy in (-1, 1) for z in (0.0, p.z + 1.0)]


def step_azimuth(source, target, base):
    """Look at a step from the side, so its pulse crosses the frame; but from the front."""
    v = target.matrix_world.translation - source.matrix_world.translation
    if v.x == 0 and v.y == 0:
        return base
    az = math.degrees(math.atan2(v.y, v.x))
    az = (az + 90) % 180 - 90  # the same line seen from the front half
    return max(-AZIMUTH_LIMIT, min(AZIMUTH_LIMIT, az))


def carrier_of(view, step):
    """The connector a step runs along: the one the layout named, else one joining its
    two ends, either way round."""
    names = {rel_name(r): r for r in view.get("relationships", [])}
    if step.get("relationship") in names:
        return step["relationship"]
    return next((name for name, r in names.items()
                 if {r["source"], r["target"]} == {step["source"], step["target"]}), None)


def arc_end(root):
    """Where an arc leaves or lands: above the plinth, or a container's front strip, where
    its name and sculpture are, rather than on the children standing on it."""
    p = root.matrix_world.translation
    if root.get("am_container"):
        return Vector((p.x, p.y - root.get("am_d", P_D) / 2 + 0.4, p.z + ARC_END))
    return p + Vector((0, 0, ARC_END))


def loop_route(start, azimuth):
    """Once round an upright ring standing on a plinth, turned to face a camera at
    `azimuth`: up off the plinth, over, and back down onto it."""
    across = Vector((math.cos(math.radians(azimuth)), math.sin(math.radians(azimuth)), 0))
    angles = [2 * math.pi * i / LOOP_POINTS for i in range(LOOP_POINTS + 1)]
    return [start + across * (LOOP_R * math.sin(a)) + Vector((0, 0, LOOP_R * (1 - math.cos(a)))) for a in angles]


def arc_route(a, b):
    """An arc from a to b above the scene, the higher the further apart they are."""
    rise = ARC_RISE + ARC_GROWTH * (b - a).length
    fractions = [i / ARC_POINTS for i in range(ARC_POINTS + 1)]
    return [a.lerp(b, u) + Vector((0, 0, rise * 4 * u * (1 - u))) for u in fractions]


def step_route(view, built, step, azimuth):
    """The points a step's pulse runs through, from source to target, and the connector
    they follow, if any: the route a connector joining the two was drawn on (connect()
    records it); for a step to itself, a ring over its plinth facing the camera at
    `azimuth`; otherwise an arc above the scene."""
    carrier = carrier_of(view, step)
    path = bpy.data.objects.get(f"{carrier}.line") if carrier else None
    if path is not None and "am_route" in path:
        flat = list(path["am_route"])
        points = [Vector(flat[i:i + 3]) + Vector((0, 0, ROUTE_LIFT)) for i in range(0, len(flat), 3)]
        return (points if path["am_source"] == step["source"] else points[::-1]), path
    start = arc_end(built[step["source"]][0])
    if step["source"] == step["target"]:
        return loop_route(start, azimuth), None
    return arc_route(start, arc_end(built[step["target"]][0])), None


def route_gap(path, route):
    """How far a route strays from the connector it follows: the largest distance from one
    of its points to the drawn line. A route ends on the ports, a line at its head and tail,
    so an arrowhead's length shows."""
    dg = bpy.context.evaluated_depsgraph_get()
    ev = path.evaluated_get(dg)
    me = ev.to_mesh()
    verts = [path.matrix_world @ v.co for v in me.vertices]
    segments = [(verts[a], verts[b]) for a, b in (e.vertices for e in me.edges)]
    ev.to_mesh_clear()

    def distance(p, a, b):
        ab = b - a
        t = 0.0 if ab.length_squared == 0 else max(0.0, min(1.0, (p - a).dot(ab) / ab.length_squared))
        return (p - (a + ab * t)).length

    return max(min(distance(p, a, b) for a, b in segments) for p in route) if segments else 0.0


def trail(name, points, mat):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.fill_mode = "FULL"
    cu.bevel_depth = TRAIL_R
    cu.bevel_resolution = 3
    cu.use_path = True
    cu.bevel_factor_mapping_end = "SPLINE"
    sp = cu.splines.new("POLY")
    sp.points.add(len(points) - 1)
    for i, p in enumerate(points):
        sp.points[i].co = (*p, 1.0)
    cu.materials.append(mat)
    ob = link(bpy.data.objects.new(name, cu))
    ob.hide_select = True
    return ob


def caption_text(number, step):
    label = (step.get("label") or "").strip()
    if len(label) > CAPTION_CHARS:
        label = label[:CAPTION_CHARS - 3].rstrip() + "..."
    return f"{number}  {label}".rstrip()


def caption(scene, cam, name, text, font):
    """A step's number and label on a card, hung in front of the camera at the top of the
    frame, so it stays put on the screen whatever the camera does."""
    vertical = 2 * math.atan(math.tan(cam.data.angle_x / 2) * scene.render.resolution_y / scene.render.resolution_x)
    frame_h = 2 * CAPTION_DEPTH * math.tan(vertical / 2)
    size = frame_h * CAPTION_SHARE
    cu = bpy.data.curves.new(name, "FONT")
    cu.body = text
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    cu.size = size
    if font is not None:
        cu.font = font
    cu.materials.append(label_ink())
    ob = link(bpy.data.objects.new(name, cu), cam)
    ob.location = (0, frame_h * (0.5 - CAPTION_TOP), -CAPTION_DEPTH)
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=0.5)
    bmesh.ops.scale(bm, vec=(size * (CHAR_WIDTH * len(text) + CAPTION_MARGIN), size * CAPTION_HEIGHT, 1), verts=bm.verts)
    card = mesh_from_bm(f"{name}.card", bm, accent(1.0, "am.caption"), ob, smooth=False)
    card.location = (0, 0, -size * CARD_GAP)
    for o in (ob, card):
        o.hide_select = True
        if hasattr(o, "visible_shadow"):
            o.visible_shadow = False
    return ob


def keyframe(owner, prop, frame_no, value):
    setattr(owner, prop, value)
    owner.keyframe_insert(prop, frame=frame_no)


def show(ob, scales):
    """Key an object's scale: shown at 1, hidden at 0, per (frame, scale)."""
    for frame_no, scale in scales:
        keyframe(ob, "scale", frame_no, (scale,) * 3)


class CameraTrack:
    """The camera's keyframes, in quaternions, each turned the short way round from the last."""

    def __init__(self, cam, start):
        cam.rotation_mode = "QUATERNION"
        self.cam, self.last = cam, start

    def hold(self, frame_no, location, rotation):
        if self.last.dot(rotation) < 0:
            rotation = -rotation
        self.last = rotation
        keyframe(self.cam, "location", frame_no, location)
        keyframe(self.cam, "rotation_quaternion", frame_no, rotation)


def shot_corners(source, target):
    """What a step's shot must hold: both plinths, and a square of floor STEP_SPAN wide
    centred between them, so a near pair or a single leaf is not seen from too close."""
    corners = plinth_corners(source) + plinth_corners(target)
    middle = (source.matrix_world.translation + target.matrix_world.translation) / 2
    half = STEP_SPAN / 2
    return corners + [Vector((middle.x + sx * half, middle.y + sy * half, 0.0)) for sx in (-1, 1) for sy in (-1, 1)]


def flight_poses(scene, cam, built, steps):
    """Where the camera stands for each step and the azimuth it looks from: framed on
    what shot_corners holds, from the side."""
    base = cam.get("am_azimuth", 35.0)
    poses = []
    for st in steps:
        source, target = built[st["source"]][0], built[st["target"]][0]
        azimuth = step_azimuth(source, target, base)
        frame(scene, cam, shot_corners(source, target), azimuth, STEP_ELEVATION, STEP_MARGIN)
        poses.append((cam.location.copy(), cam.rotation_euler.to_quaternion(), azimuth))
    return poses


def step_spots(lights, step, arrive, leave, fade):
    """The step's two elements lit from its arrival until it leaves; the others dark."""
    for k, light in lights.items():
        power = SPOT_POWER if k in (step["source"], step["target"]) else 0.0
        keyframe(light.data, "energy", arrive - fade, light.data.energy)
        keyframe(light.data, "energy", arrive, power)
        keyframe(light.data, "energy", leave, power)


def step_trail(number, route, arrive, run_end, leave, fade):
    """The step's trail, drawn from source to target while the pulse runs; once the step
    is done it thins and stops glowing, a trace of the flow so far."""
    mat = accent(TRAIL_GLOW, f"am.flow.{number}")
    glow = mat.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"]
    path = trail(f"flow.{number}.trail", route, mat)
    keyframe(path.data, "bevel_factor_end", 1, 0.0)
    keyframe(path.data, "bevel_factor_end", arrive, 0.0)
    keyframe(path.data, "bevel_factor_end", run_end, 1.0)
    keyframe(glow, "default_value", leave, TRAIL_GLOW)
    keyframe(glow, "default_value", leave + fade, 0.0)
    keyframe(path.data, "bevel_depth", leave, TRAIL_R)
    keyframe(path.data, "bevel_depth", leave + fade, TRAIL_R * TRAIL_DONE)
    return path


def step_pulse(number, path, arrive, run_end):
    """A bright ball that runs along the trail while it is drawn."""
    pulse = B(None, f"flow.{number}").ball((0, 0, 0), PULSE_R, accent(PULSE_GLOW, "am.pulse"))
    pulse.name = f"flow.{number}.pulse"
    follow = pulse.constraints.new("FOLLOW_PATH")
    follow.target = path
    follow.use_fixed_location = True
    keyframe(follow, "offset_factor", arrive, 0.0)
    keyframe(follow, "offset_factor", run_end, 1.0)
    show(pulse, ((1, 0), (arrive - 1, 0), (arrive, 1), (run_end, 1), (run_end + 1, 0)))


def project(scene, cam, points):
    """Points as the camera frames them: x and y as shares of the frame's width and height."""
    from bpy_extras.object_utils import world_to_camera_view

    return [world_to_camera_view(scene, cam, p) for p in points]


def measure_flight(scene, cam, built, shots):
    """What the flight shows, at each step's arrival, mid-run and departure:
    - margin: how far inside the frame the step's plinths and route stay, at the least;
    - clearance: how far the highest of them stays below the caption bar;
    - leaf_share: the largest share of the frame's width a leaf's plinth takes (a
      container's scaled to a leaf's width), which is how close the camera comes;
    - loop: the narrowest a step to itself opens its ring, as a share of the frame's
      width; None when no step goes to itself."""
    margin, highest, leaf_share, loops = 1.0, 0.0, 0.0, []
    for step, frames, route in shots:
        roots = [built[step[end]][0] for end in ("source", "target")]
        for frame_no in frames:
            scene.frame_set(frame_no)
            plinths = [project(scene, cam, plinth_corners(root)) for root in roots]
            path = project(scene, cam, route)
            for p in [p for points in plinths for p in points] + path:
                margin = min(margin, p.x, 1 - p.x, p.y, 1 - p.y)
                highest = max(highest, p.y)
            for root, points in zip(roots, plinths):
                width = max(p.x for p in points) - min(p.x for p in points)
                leaf_share = max(leaf_share, width * P_W / root.get("am_w", P_W))
            if step["source"] == step["target"]:
                loops.append(max(p.x for p in path) - min(p.x for p in path))
    caption_bottom = 1 - CAPTION_TOP - CAPTION_SHARE * CAPTION_HEIGHT / 2
    return margin, caption_bottom - highest, leaf_share, (min(loops) if loops else None)


def flyover(scene, cam, view, built, flow, font=None, fps=FPS):
    """Animate the camera through a flow's steps: the overview in full light, then per
    step a glide to its two elements, both lit as the rest dims, a pulse and its trail
    from source to target, the step's caption; then back to the overview, the light up
    again. Returns a Flight: its frames, its steps, how many ran along a connector, and
    what measure_flight and route_gap found."""
    scene.render.fps = fps
    s = lambda seconds: max(1, round(seconds * fps))
    steps = [st for st in flow.get("steps", []) if st["source"] in built and st["target"] in built]
    overview = (cam.location.copy(), cam.rotation_euler.to_quaternion())
    poses = flight_poses(scene, cam, built, steps)
    track = CameraTrack(cam, overview[1])
    track.hold(1, *overview)
    f = 1 + s(OPEN_S)
    track.hold(f, *overview)
    opened = f
    lights = {k: spot_on(built[k][0], f"{k}.flyover.spot")
              for k in sorted({e for st in steps for e in (st["source"], st["target"])})}
    for light in lights.values():
        keyframe(light.data, "energy", 1, 0.0)
    shots, along, gap = [], 0, 0.0
    for number, (step, (location, rotation, azimuth)) in enumerate(zip(steps, poses), 1):
        arrive = f + s(GLIDE_S)
        run_end = arrive + s(RUN_S)
        leave = run_end + s(HOLD_S)
        track.hold(arrive, location, rotation)
        track.hold(leave, location + (rotation @ Vector((0, 0, -1))) * PUSH_IN, rotation)
        step_spots(lights, step, arrive, leave, s(FADE_S))
        route, carrier = step_route(view, built, step, azimuth)
        if carrier is not None:
            along += 1
            gap = max(gap, route_gap(carrier, route))
        step_pulse(number, step_trail(number, route, arrive, run_end, leave, s(FADE_S)), arrive, run_end)
        show(caption(scene, cam, f"flow.{number}.caption", caption_text(number, step), font),
             ((1, 0), (arrive - s(FADE_S), 0), (arrive, 1), (leave, 1), (leave + s(FADE_S), 0)))
        shots.append((step, (arrive, (arrive + run_end) // 2, leave), route))
        f = leave
    for light in lights.values():
        keyframe(light.data, "energy", f + s(FADE_S), 0.0)
    if not scene.get("am_dimmed"):  # a --highlight keeps the scene dimmed throughout
        dim_between(scene, (opened, opened + s(GLIDE_S), f, f + s(CLOSE_S)))
    f += s(CLOSE_S)
    track.hold(f, *overview)
    f += s(OPEN_S)
    track.hold(f, *overview)
    scene.frame_start, scene.frame_end = 1, f
    margin, clearance, leaf_share, loop = measure_flight(scene, cam, built, shots)
    scene.frame_set(1)
    return Flight(f, len(steps), along, margin, clearance, gap, leaf_share, loop)


def render_animation(scene, path, samples=32):
    """The timeline to an H.264 video, with EEVEE and Blender's own encoder."""
    engines = {e.identifier for e in scene.render.bl_rna.properties["engine"].enum_items}
    scene.render.engine = "BLENDER_EEVEE" if "BLENDER_EEVEE" in engines else "BLENDER_EEVEE_NEXT"
    if hasattr(scene.eevee, "taa_render_samples"):
        scene.eevee.taa_render_samples = samples
    image = scene.render.image_settings
    if hasattr(image, "media_type"):
        image.media_type = "VIDEO"
    image.file_format = "FFMPEG"
    scene.render.ffmpeg.format = "MPEG4"
    scene.render.ffmpeg.codec = "H264"
    scene.render.ffmpeg.constant_rate_factor = "HIGH"
    scene.render.filepath = os.path.abspath(path)
    bpy.ops.render.render(animation=True)


def point(args, scene, cam, view, built, roots, font):
    """Light, fly or float labels, as the options ask. Returns the flow it flew, or None."""
    if args.highlight:
        elements, connectors, unknown = highlight(scene, view, built, args.highlight.split(","))
        print(f"archimate3d: {len(elements)} elements and {len(connectors)} connectors in the light, "
              f"{len(spots())} spots, ambient light at {scene.get('am_ambient', 1.0):.2f}")
        for token in unknown:
            print(f"archimate3d: nothing called {token!r} in the view")
    flow = find_flow(view, args.flyover) if args.flyover is not None else None
    if args.flyover is not None and flow is None:
        wanted = f"{args.flyover!r} " if args.flyover else ""
        print(f"archimate3d: no flow {wanted}in the view; nothing to fly through")
    if flow:
        flight = flyover(scene, cam, view, built, flow, font, args.fps)
        print(f"archimate3d: flyover of {flow.get('name') or flow.get('key')}: {flight.steps} steps, "
              f"{flight.along} along a connector, {flight.frames} frames at {args.fps} fps")
        print(f"archimate3d: every step's plinths and route stay {flight.margin:.3f} inside the frame and "
              f"{flight.clearance:.3f} below the caption; routes within {flight.gap:.3f} of their connectors")
        print(f"archimate3d: at its closest the camera shows a leaf's plinth at {flight.leaf_share:.3f} "
              f"of the frame's width")
        if flight.loop is not None:
            print(f"archimate3d: a step to itself opens its ring {flight.loop:.3f} of the frame's width")
        return flow
    print(f"archimate3d: a placard's name renders about {placard_px(scene, cam, roots):.1f} px tall")
    if wants_floating(args.labels, scene, cam, roots):
        names, stuck, covering = float_labels(scene, cam, roots, font)
        print(f"archimate3d: {names} floating labels, {stuck} not freed, {covering} covering pairs")
    return None


def resolution(text):
    """WIDTHxHEIGHT, both positive."""
    try:
        w, h = (int(v) for v in text.lower().split("x"))
    except ValueError:
        raise argparse.ArgumentTypeError(f"not WIDTHxHEIGHT: {text}")
    if w <= 0 or h <= 0:
        raise argparse.ArgumentTypeError(f"not WIDTHxHEIGHT: {text}")
    return w, h


def check(built, connectors):
    """Move the first element and report how far the connector ends are from their ports."""
    root = next(r for r, _ in built.values() if r.parent is None)
    root.location.x += 1.5
    root.location.y -= 0.7
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    worst = 0.0
    for path in connectors:
        ev = path.evaluated_get(dg)
        me = ev.to_mesh()
        verts = [path.matrix_world @ v.co for v in me.vertices]
        ev.to_mesh_clear()
        for h in path.modifiers:
            if h.type != "HOOK":
                continue
            anchor = h.object.matrix_world.translation
            near = min((v - anchor).length for v in verts)
            worst = max(worst, near)
    root.location.x -= 1.5
    root.location.y += 0.7
    bpy.context.view_layer.update()
    return worst


def main():
    global COLL
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser(prog="archimate3d_blender")
    ap.add_argument("--view", help="a view as JSON")
    ap.add_argument("--catalogue", action="store_true", help="every element, a row per layer")
    ap.add_argument("--font", help="a .ttf/.otf for the names (default: Blender's own)")
    ap.add_argument("--blend", help="save the .blend here")
    ap.add_argument("--render", help="render a still to this .png")
    ap.add_argument("--samples", type=int, default=96)
    ap.add_argument("--size", type=resolution, help="WIDTHxHEIGHT (default 1920x1200, or 1920x1080 for --animation)")
    ap.add_argument("--azimuth", type=float)
    ap.add_argument("--elevation", type=float)
    ap.add_argument("--check", action="store_true", help="move an element and verify the connectors follow")
    ap.add_argument("--straight", action="store_true", help="ignore the layout's routes: straight lines between the nearest ports")
    ap.add_argument("--labels", choices=LABEL_MODES, default="auto",
                    help="names above the elements too: auto when the placards would be too small to read")
    ap.add_argument("--highlight", help="light these, the rest dimmed: keys, names or source>target, comma-separated")
    ap.add_argument("--flyover", nargs="?", const="", help="fly the camera through a flow (key or name; default the first)")
    ap.add_argument("--animation", help="render the flyover to this .mp4")
    ap.add_argument("--fps", type=int, default=FPS)
    ap.add_argument("--frame", type=int, help="with --render: the flyover's frame to render")
    args = ap.parse_args(argv)

    if bpy.app.background:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        COLL = bpy.context.scene.collection
    else:
        COLL = bpy.data.collections.new("ArchiMate")
        bpy.context.scene.collection.children.link(COLL)
    font = bpy.data.fonts.load(args.font) if args.font else None

    if args.catalogue:
        view = catalogue_view()
    elif args.view:
        with open(args.view) as f:
            view = json.load(f)
    else:
        view = EXAMPLE_VIEW
    built, connectors = build_view(view, font, args.straight)
    print(f"archimate3d: {len(built)} elements, {len(connectors)} connectors")
    if not built:
        # an empty view (a new model) has nothing to frame or render
        print("archimate3d: the view is empty; nothing to build")
        return

    if args.check and connectors:
        print(f"archimate3d: after moving an element, connector ends are within {check(built, connectors):.4f} of their ports")

    if args.animation and args.flyover is None:
        args.flyover = ""
    flow = None
    if bpy.app.background or args.render or args.highlight or args.flyover is not None:
        w, h = args.size or ((1920, 1080) if args.animation else (1920, 1200))
        azimuth = args.azimuth if args.azimuth is not None else (22 if args.catalogue else 35)
        elevation = args.elevation if args.elevation is not None else (50 if args.catalogue else 36)
        roots = [r for r, _ in built.values()]
        stage(roots, azimuth, elevation, (w, h), args.samples)
        flow = point(args, bpy.context.scene, bpy.context.scene.camera, view, built, roots, font)
    # Blender reads a bare relative name against the .blend, not the shell: make them absolute
    if args.blend:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.blend))
    if args.render:
        if args.frame is not None:
            bpy.context.scene.frame_set(args.frame)
        bpy.context.scene.render.filepath = os.path.abspath(args.render)
        bpy.ops.render.render(write_still=True)
    if args.animation and flow:
        render_animation(bpy.context.scene, args.animation)
    elif args.animation:
        print(f"archimate3d: no video written to {args.animation}")


if __name__ == "__main__":
    main()
