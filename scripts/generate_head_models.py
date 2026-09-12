"""Build native cuboid item models with Minecraft entity-skin UVs (no copied assets)."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mc_head_function/models/item'
# texture, atlas size, cuboids: (x,y,z,width,height,depth,u,v); face looks toward -Z.
HEADS = {
'enderman': ('enderman/enderman',64,32,[(4,4,4,8,8,8,0,0)]),
'charged_creeper': ('creeper/creeper',64,32,[(4,4,4,8,8,8,0,0)]),
'blaze': ('blaze',64,32,[(4,4,4,8,8,8,0,0)]),
'pig': ('pig/pig',64,32,[(4,4,4,8,8,8,0,0),(6,5,2,4,3,2,16,16)]),
'cow': ('cow/cow',64,32,[(4,4,5,8,8,6,0,0),(3,11,6,1,3,1,22,0),(12,11,6,1,3,1,22,0)]),
'sheep': ('sheep/sheep',64,32,[(5,4,4,6,6,8,0,0)]),
'chicken': ('chicken',64,32,[(6,4,5,4,6,3,0,0),(6,5,3,4,2,2,14,0),(7,3,4,2,2,2,14,4)]),
'wolf': ('wolf/wolf',64,32,[(5,4,5,6,6,4,0,0),(5,10,6,2,2,1,16,14),(9,10,6,2,2,1,16,14),(6.5,4,2,3,3,4,0,10)]),
'fox': ('fox/fox',48,32,[(4,4,5,8,6,6,1,5),(4,10,7,2,2,1,8,1),(10,10,7,2,2,1,15,1),(6,4,2,4,2,3,6,18)]),
'rabbit': ('rabbit/brown',64,32,[(5.5,4,5,5,4,5,32,0),(5.5,8,7,2,5,1,52,0),(8.5,8,7,2,5,1,58,0),(7.5,5,4,1,1,1,32,9)]),
'iron_golem': ('iron_golem/iron_golem',128,128,[(4,3,5,8,10,8,0,0),(7,3,3,2,4,2,24,0)]),
'goat': ('goat/goat',64,64,[(5.5,4,5,5,7,10,34,46),(3.5,8,8,3,2,1,2,61),(9.5,8,8,3,2,1,2,61),(5.5,11,9,2,5,2,12,55),(8.5,11,9,2,5,2,12,55)]),
'llama': ('llama/creamy',128,64,[(6,3,5,4,4,9,0,0),(6,7,10,4,4,4,0,14),(6,11,11,1,3,2,17,0),(9,11,11,1,3,2,17,0)]),
'bee': ('bee/bee',64,64,[(4.5,4.5,3,7,7,10,0,0),(5,11.5,3,1,2,3,2,0),(10,11.5,3,1,2,3,2,3)]),
'bat': ('bat',32,32,[(6,5,7,4,3,2,0,7),(4,7,8,3,5,0,1,15),(9,7,8,3,5,0,8,15)]),
'frog': ('frog/temperate_frog',48,48,[(4.5,4,3.5,7,3,9,0,13),(4.5,7,5,3,2,3,0,0),(8.5,7,5,3,2,3,0,5)]),
'armadillo': ('armadillo',64,64,[(6.5,4,7,3,5,2,43,15),(4.5,8,7.5,2,5,0,43,10),(9.5,8,7.5,2,5,0,47,10)])
}
def element(c,tw,th,tex='skin'):
 x,y,z,w,h,d,u,v=c
 rects={'north':(u+d,v+d,u+d+w,v+d+h),'south':(u+2*d+w,v+d,u+2*d+2*w,v+d+h),'west':(u,v+d,u+d,v+d+h),'east':(u+d+w,v+d,u+2*d+w,v+d+h),'up':(u+d,v,u+d+w,v+d),'down':(u+d+w,v,u+d+2*w,v+d)}
 return {'from':[x,y,z],'to':[x+w,y+h,z+d],'faces':{f:{'uv':[a*16/tw,b*16/th,c*16/tw,e*16/th],'texture':'#'+tex} for f,(a,b,c,e) in rects.items()}}
def head_fit(body):
 # The first cuboid is the solid head core: ears, horns and noses must not count
 # towards coverage. Vanilla's head feature scales by 0.625; our HEAD-only
 # mixin adds 2 so every JSON scale stays below Minecraft's maximum of 4.
 # Cover the 9-pixel skin hat, with 0.2 pixels of clearance on each side.
 origin = body[:3]
 size = body[3:6]
 scale = [max(9.4, length * 1.03) / (0.625 * 2 * length) for length in size]
 translation = [-(start + length / 2 - 8) * factor
                for start, length, factor in zip(origin, size, scale)]
 assert all(0 < factor <= 4 for factor in scale)
 return {'rotation': [0, 0, 0], 'translation': translation, 'scale': scale}

for name,(texture,tw,th,parts) in HEADS.items():
 model={'credit':'Head Function — native cuboids mapped to vanilla entity textures','textures':{'skin':'minecraft:entity/'+texture,'particle':'minecraft:entity/'+texture},'elements':[element(c,tw,th) for c in parts],'display':{
 'gui':{'rotation':[25,145,0],'translation':[0,-1,0],'scale':[0.85]*3},
 'ground':{'translation':[0,3,0],'scale':[0.6]*3},
 'fixed':{'rotation':[0,180,0],'scale':[0.85]*3},
 'head':head_fit(parts[0]),
 'thirdperson_righthand':{'rotation':[75,180,0],'translation':[0,2,1],'scale':[0.65]*3},
 'thirdperson_lefthand':{'rotation':[75,180,0],'translation':[0,2,1],'scale':[0.65]*3},
 'firstperson_righthand':{'rotation':[0,155,0],'translation':[0,2,0],'scale':[0.8]*3},
 'firstperson_lefthand':{'rotation':[0,205,0],'translation':[0,2,0],'scale':[0.8]*3}}}
 if name=='enderman':
  model['textures']['eyes']='minecraft:entity/enderman/enderman_eyes'
  eyes=element((4,4,3.99,8,8,8,0,0),64,32,'eyes')
  eyes['faces']={k:v for k,v in eyes['faces'].items() if k=='north'}
  model['elements'].append(eyes)
 if name=='charged_creeper':
  model['textures']['charge']='minecraft:entity/creeper/creeper_armor'
  model['elements'].append(element((3.65,3.65,3.65,8.7,8.7,8.7,0,0),64,32,'charge'))
 (ROOT/(name+'_head.json')).write_text(json.dumps(model,indent=2)+'\n')
print('Generated',len(HEADS),'3D head models')

# Entity skins are not stitched into the block/item atlas unless explicitly registered.
atlas = ROOT.parents[2] / 'minecraft/atlases/blocks.json'
atlas.parent.mkdir(parents=True, exist_ok=True)
textures = sorted({'minecraft:entity/' + entry[0] for entry in HEADS.values()} | {'minecraft:entity/creeper/creeper_armor', 'minecraft:entity/enderman/enderman_eyes'})
atlas.write_text(json.dumps({'sources': [{'type': 'minecraft:single', 'resource': t, 'sprite': t} for t in textures]}, indent=2) + '\n')
