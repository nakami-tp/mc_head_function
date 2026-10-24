"""Export chronological frame comparisons around every editorial/camera cut."""
import json, subprocess
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[2]
R=ROOT/'video/intro/eight-heads'
manifest=json.loads((R/'edit-manifest.json').read_text())
take=Path(manifest['take'])
overrides=json.loads((R/'source-overrides.json').read_text())
video=R/'final/头部功能_八颗头_曼波_1080p30.mp4'
out=R/'qa/boundaries';out.mkdir(exist_ok=True)
cuts=[]
for segment in manifest['segments']:
 name=segment['name'];base=segment['start'];duration=segment['duration']
 if base:cuts.append((base,name+' / start'))
 if name=='hook':local=[4.2]
 elif name=='goat':local=[4.6,7.9,9.9,11.8,12.6]
 elif name=='mv-placeholder':local=[]
 else:
  plan=Path(overrides.get(name,str(take/(name+'.mp4')))).with_suffix('.json')
  shots=json.loads(plan.read_text())['shots'];local=[];time=0
  for shot in shots:
   time+=(shot['end']-shot['start'])/20/shot.get('speed',1)
   if 0<time-1.5<duration-.1:local.append(time-1.5)
 cuts.extend((base+t,name+' / camera') for t in local if 0<t<duration-.1)
cuts.sort()
pages=[]
for index,(at,label) in enumerate(cuts):
 row=index%6
 if row==0:sheet=Image.new('RGB',(1440,6*298),(12,15,18))
 for column,delta in enumerate([-1/30,0,1/30]):
  frame=out/f'{index:02}-{column}.jpg'
  subprocess.run(['/opt/homebrew/bin/ffmpeg','-y','-v','error','-ss',str(max(0,at+delta)),'-i',str(video),'-frames:v','1','-vf','scale=480:270',str(frame)],check=True)
  sheet.paste(Image.open(frame),(column*480,row*298))
 ImageDraw.Draw(sheet).text((8,row*298+273),f'{at:.3f}s {label}    previous / cut / next',fill='white')
 if row==5 or index==len(cuts)-1:
  path=out/f'page-{index//6+1}.jpg';sheet.save(path);pages.append(str(path))
(out/'index.json').write_text(json.dumps({'cuts':cuts,'pages':pages},indent=2))
print(f'{len(cuts)} cut points across {len(pages)} pages')
