"""Render the ORGANISM box as a three-quarter hero shot.

Matches Sol's box_hero_solo_@2x.png. The defaults were solved against that
image rather than guessed: measuring its silhouette gives a front face whose
far edge is 0.619x the height of its near edge and whose projected width is
0.720x that near edge. turn=38 / dist=1.5 / lens=42 reproduces those to
0.599 and 0.716. The side panel reads wider than Sol's because the ORGANISM
carton really is deeper — 336x336x75 mm, the 13-inch box the trailer uses.

    blender -b -noaudio --python script/render_box.py -- FACE SIDE OUT SIZE
"""
import bpy, bmesh, sys, math, mathutils

argv = sys.argv[sys.argv.index('--') + 1:]
face_img, side_img, out_path, size = argv[0], argv[1], argv[2], int(argv[3])
# turn (deg), camera distance (box widths), camera height (box heights), lens (mm)
turn = float(argv[4]) if len(argv) > 4 else 38.0   # matches Sol's 3/4 turn
dist = float(argv[5]) if len(argv) > 5 else 1.5
elev = float(argv[6]) if len(argv) > 6 else 0.06
lens = float(argv[7]) if len(argv) > 7 else 42.0
# where the cover ends and the side begins, as a fraction of the wrap sheet
FOLD = float(argv[8]) if len(argv) > 8 else 0.18

bpy.ops.wm.read_factory_settings(use_empty=True)

# 395 x 395 x 65 mm, standing on edge like Sol's
# Straight from ../organism/pieces/build_anim.py, which renders the trailer:
#   IW, ID, BH = 330, 330, 95   (real 13x13 in floor) and the lid top / fold
#   line at world z = 116  ->  a closed box 330 x 330 x 116 mm.
# Units here are 100 mm. build_box.py's 220x58 is the older, shallower
# product-shot stand-in; this is the carton the videos actually show.
W, D, H = 3.30, 1.16, 3.30
HALF_W, HALF_H = W / 2, H / 2

# build_anim.py: WRAPS = 0.56/330 — image-fraction per mm, the cover panel
# being ~0.56 of box_wrap.png. So the fold sits at (1-0.56)/2 = 0.22 of the
# sheet, and the walls continue outward from it at the SAME scale, which is
# what keeps the art unstretched as it turns the corner.
WRAP_SCALE = 0.56 / 3.30
bpy.ops.mesh.primitive_cube_add(size=1)          # spans -0.5..0.5
box = bpy.context.active_object
box.scale = (W, D, H)                            # -> vertices at +/-W/2 etc.
bpy.ops.object.transform_apply(scale=True)
bpy.ops.object.shade_flat()

# a whisper of a bevel so the edges catch light instead of reading as a render
bevel = box.modifiers.new('bevel', 'BEVEL')
bevel.width, bevel.segments = 0.012, 3


def image_material(name, path, roughness=0.42):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes['Principled BSDF']
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(path)
    links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    if 'Roughness' in bsdf.inputs:
        bsdf.inputs['Roughness'].default_value = roughness
    return mat


# ONE material from the whole wrap. The art runs continuously across every
# fold, so the faces cannot be textured separately — they are UV'd as the net.
box.data.materials.append(image_material('cover', face_img, roughness=0.6))
box.data.materials.append(image_material('wrap', side_img, roughness=0.6))

mesh = box.data
bm = bmesh.new(); bm.from_mesh(mesh)
uv = bm.loops.layers.uv.verify()

# A port of build_anim.py's wrap_walls(): the cover fills the front face, and
# every wall drapes box_wrap.png outward from the fold at uniform scale. No
# per-face crops, no hinge direction to get backwards — the art simply
# continues over the edge.
S = WRAP_SCALE
for f in bm.faces:
    n = f.normal
    front = n.y < -0.5
    f.material_index = 0 if front else 1
    for loop in f.loops:
        co = loop.vert.co
        if front:
            u, v = co.x / W + 0.5, co.z / H + 0.5
        else:
            depth = co.y + D / 2                 # 0 at the cover, D at the back
            if abs(n.x) > 0.5:                   # left / right walls
                v = 0.5 + S * co.z
                u = ((0.5 - S * HALF_W) - S * depth if n.x < 0
                     else (0.5 + S * HALF_W) + S * depth)
            elif abs(n.z) > 0.5:                 # top / bottom walls
                u = 0.5 + S * co.x
                v = ((0.5 + S * HALF_H) + S * depth if n.z > 0
                     else (0.5 - S * HALF_H) - S * depth)
            else:                                # back
                u, v = 0.5 + S * co.x, 0.5 + S * co.z
        loop[uv].uv = (u, v)
bm.to_mesh(mesh); bm.free()

# Sol's box is turned so the viewer's left side of the box shows
box.rotation_euler = (0, 0, math.radians(turn))

world = bpy.data.worlds.new('w')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.6, 0.62, 0.66, 1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.34
bpy.context.scene.world = world


def add_sun(rot, strength):
    d = bpy.data.lights.new('sun', 'SUN')
    d.energy, d.angle = strength, math.radians(20)
    lamp = bpy.data.objects.new('sun', d)
    lamp.rotation_euler = [math.radians(a) for a in rot]
    bpy.context.collection.objects.link(lamp)


add_sun((62, 0, -34), 2.0)     # key, front left and above
add_sun((74, 0,  120), 0.55)   # fill
add_sun((-14, 0, 200), 0.7)    # rim

cam_data = bpy.data.cameras.new('cam')
cam_data.lens = lens
cam = bpy.data.objects.new('cam', cam_data)
bpy.context.collection.objects.link(cam)
cam.location = (0, -dist * W, elev * H)
cam.rotation_euler = (mathutils.Vector((0, 0, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
bpy.context.scene.camera = cam

scene = bpy.context.scene
engines = scene.render.bl_rna.properties['engine'].enum_items.keys()
scene.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
scene.render.film_transparent = True
scene.render.resolution_x = scene.render.resolution_y = size
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
try:
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
except Exception:
    pass
scene.render.filepath = out_path
bpy.ops.render.render(write_still=True)
print(f'RENDERED {out_path}')
