"""Render the current ORGANISM sculpts to transparent PNGs (Blender headless)."""
import bpy, sys, math, mathutils
from pathlib import Path

argv = sys.argv[sys.argv.index('--') + 1:]
stl_path, out_path, r, g, b, size = argv[0], argv[1], *map(float, argv[2:5]), int(argv[5])

# clean slate
bpy.ops.wm.read_factory_settings(use_empty=True)

try:
    bpy.ops.wm.stl_import(filepath=stl_path)
except AttributeError:
    bpy.ops.import_mesh.stl(filepath=stl_path)
obj = bpy.context.selected_objects[0]

# centre on origin, sit on z=0
bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
obj.location = (0, 0, 0)
bpy.ops.object.shade_smooth()

dims = obj.dimensions
reach = max(dims)

mat = bpy.data.materials.new('piece')
mat.use_nodes = True
bsdf = mat.node_tree.nodes['Principled BSDF']
bsdf.inputs['Base Color'].default_value = (r, g, b, 1)
for name, value in (('Roughness', 0.42), ('Metallic', 0.0)):
    if name in bsdf.inputs:
        bsdf.inputs[name].default_value = value
obj.data.materials.append(mat)

# three-quarter view, looking slightly down
cam_data = bpy.data.cameras.new('cam')
cam = bpy.data.objects.new('cam', cam_data)
bpy.context.collection.objects.link(cam)
angle = math.radians(38)
dist = reach * 2.9
cam.location = (dist * math.cos(angle) * 0.72, -dist * math.cos(angle), dist * math.sin(angle) * 1.05)
direction = mathutils.Vector((0, 0, 0)) - cam.location
cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
cam_data.lens = 85
bpy.context.scene.camera = cam

# Sun strength is irradiance and the world is uniform, so neither depends on
# how big the mesh happens to be — these meshes are in millimetres, and
# area-light wattage at that scale renders as a silhouette.
world = bpy.data.worlds.new('w')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.55, 0.58, 0.62, 1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.30
bpy.context.scene.world = world

def add_sun(rot, strength):
    d = bpy.data.lights.new('sun', 'SUN')
    d.energy = strength
    d.angle = math.radians(25)          # soft shadow edges
    lamp = bpy.data.objects.new('sun', d)
    lamp.rotation_euler = [math.radians(a) for a in rot]
    bpy.context.collection.objects.link(lamp)

add_sun((52, 0, -46), 1.5)      # key, over the camera's left shoulder
add_sun((66, 0,  128), 0.45)     # fill from the other side
add_sun((-24, 0, 180), 0.6)     # rim from behind

scene = bpy.context.scene
scene.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in \
    scene.render.bl_rna.properties['engine'].enum_items.keys() else 'BLENDER_EEVEE'
scene.render.film_transparent = True
# the headless config has no OCIO roles, so pin the transform rather than
# letting it fall back to something that blows the highlights out
try:
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
except Exception:
    pass
scene.render.resolution_x = scene.render.resolution_y = size
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.filepath = out_path
bpy.ops.render.render(write_still=True)
print(f'RENDERED {out_path}')
