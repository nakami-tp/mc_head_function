"""Frame-aligned edit, readable captions, original background score and separate editable stems."""
import json,math,re,subprocess,hashlib
from pathlib import Path
import numpy as np
import soundfile as sf
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'video/intro/eight-heads'
TAKE=Path((R/'latest-take.txt').read_text().strip());F=R/'final';F.mkdir(exist_ok=True)
SOURCES=json.loads((R/'source-overrides.json').read_text()) if (R/'source-overrides.json').exists() else {}
def source(name):return Path(SOURCES.get(name,str(TAKE/(name+'.mp4'))))
CONFIG=json.loads((R/'narration-timeline.json').read_text());C={x['name']:x for x in CONFIG}
# The burp gets a short gap before the last joke.
for row in C['pig']['lines']:
 if row['id']=='44':row['start']+=.9
C['pig']['duration']=19.6
C['outro']['duration']=9.2
order=['hook','acquire','zombie','ender','tree01','creeper','charged','blaze','tree02','goat','golem','tree03','pig','outro','mv-placeholder']
inputs=[source(n) for n in order if n!='mv-placeholder']+[source('showcase')]
source_state=json.dumps([(str(p),p.stat().st_size,p.stat().st_mtime_ns) for p in inputs]).encode()
code_hash=hashlib.sha256(Path(__file__).read_bytes()+(R/'narration-timeline.json').read_bytes()+source_state).hexdigest()[:10]
E=R/'edit'/code_hash;E.mkdir(parents=True,exist_ok=True)
FF='/opt/homebrew/bin/ffmpeg';FONT='/System/Library/Fonts/Hiragino Sans GB.ttc';SR=48000
M=[];cursor=0
for name in order:
 dur=2 if name.startswith('tree') else 2 if name=='mv-placeholder' else C[name]['duration']
 frames=round(dur*30);dur=frames/30
 m={'name':name,'frames':frames,'duration':dur,'start_frame':cursor,'start':cursor/30,'lines':C.get(name,{}).get('lines',[])};M.append(m);cursor+=frames
TOTAL=cursor/30
(R/'edit-manifest.json').write_text(json.dumps({'take':str(TAKE),'duration':TOTAL,'frames':cursor,'segments':M,'cache':str(E)},ensure_ascii=False,indent=2))
def run(cmd):subprocess.run(cmd,check=True)
def png(text,size=60,style='subtitle'):
 font=ImageFont.truetype(FONT,size);lines=text.split('\n');width=math.ceil(max(font.getlength(x) for x in lines))+48;height=(size+17)*len(lines)+24
 im=Image.new('RGBA',(width,height));d=ImageDraw.Draw(im)
 if style=='tag':
  d.rounded_rectangle((0,0,width-1,height-1),radius=12,fill=(12,18,24,210));d.rectangle((0,0,7,height-1),fill=(172,242,103,255))
 for i,line in enumerate(lines):
  d.text(((width-font.getlength(line))/2,7+i*(size+17)),line,font=font,fill=(255,222,104,255) if style=='punch' else (255,255,255,255),stroke_width=4,stroke_fill=(8,12,16,255))
 return im

def clauses(text):
 raw=[x for x in re.split(r'(?<=[，。；？！])',text) if x];out=[];current=''
 for part in raw:
  if len(current)+len(part)>23 and current:out.append(current);current=''
  current+=part
 if current:out.append(current)
 return out

def parts_for(m):
 n=m['name'];d=m['duration']
 if n=='hook':return [('hook',.7,4.2),('showcase',1.5,d-4.2)]
 if n=='goat':return [('goat',1.5,9.9),('goat',7.5,2.7),('goat',14.1,d-12.6)]
 if n.startswith('tree'):return [(n,1.5,d)]
 if n=='mv-placeholder':return []
 return [(n,1.5,d)]

for m in M:
 n=m['name'];d=m['duration'];base=E/(n+'-base.mkv');picture=E/(n+'-captioned.mp4')
 if not base.exists():
  pieces=parts_for(m)
  if pieces:
   cmd=[FF,'-y','-v','error'];filters=[]
   for i,(src,at,length) in enumerate(pieces):
    cmd+=['-ss',str(at),'-i',str(source(src))]
    nf=round(length*30)
    filters+=[f'[{i}:v]setpts=PTS-STARTPTS,fps=30,tpad=stop_mode=clone:stop_duration=0.12,trim=end_frame={nf},setpts=N/(30*TB)[v{i}]',f'[{i}:a]asetpts=PTS-STARTPTS,aresample=48000,apad,atrim=duration={nf/30}[a{i}]']
   filters+=[''.join(f'[v{i}][a{i}]' for i in range(len(pieces)))+f'concat=n={len(pieces)}:v=1:a=1[v][a]']
   cmd+=['-filter_complex',';'.join(filters),'-map','[v]','-map','[a]','-frames:v',str(m['frames']),'-c:v','libx264','-preset','fast','-crf','17','-pix_fmt','yuv420p','-c:a','pcm_s24le',str(base)];run(cmd)
  else:
   run([FF,'-y','-v','error','-f','lavfi','-i',f'color=c=0x11171b:s=1920x1080:r=30:d={d}','-f','lavfi','-i',f'anullsrc=r=48000:cl=stereo:d={d}','-c:v','libx264','-crf','17','-c:a','pcm_s24le','-t',str(d),str(base)])
 if picture.exists():continue
 overlays=[]
 def add(text,a,b,size=60,style='subtitle',placement='bottom'):
  im=png(text,size,style);path=E/f'{n}-caption-{len(overlays)}.png';im.save(path)
  if placement=='top':x,y=60,50
  elif placement=='right':x,y=1920-im.width-60,52
  elif placement=='center':x,y=(1920-im.width)//2,(1080-im.height)//2
  else:x,y=(1920-im.width)//2,1000-im.height
  overlays.append((path,max(0,a),min(d,b),x,y))
 if n not in ('tree01','tree02','tree03','mv-placeholder'):
  title='头部功能  /  八颗头' if n=='hook' else C[n]['chapter']
  if n=='acquire':title='先把头拿到手'
  if n=='outro':title='头部功能  ·  Minecraft 1.21.1'
  add(title,.12,min(d,3.2),42,'tag','top')
 for row in m['lines']:
  cs=clauses(row['text']);total=sum(len(x) for x in cs);t=row['start'];style='punch' if row['id'] in ('09','20','24','29','44') else 'subtitle'
  for clause in cs:
   length=row['duration']*len(clause)/total
   if len(clause)>25:clause=clause[:24]+'\n'+clause[24:]
   add(clause,t,t+length,62,style);t+=length
 if n=='acquire':add('仅指定成年生物\n玩家 / Boss 不适用',5.1,9.8,34,'tag','right')
 if n=='acquire':add('猪头左键是吞',12,16.2,34,'tag','right')
 if n=='charged':add('需要视线无遮挡',5.9,9.8,36,'tag','right')
 if n=='goat':add('碰撞回放',9.9,12.6,36,'tag','right')
 if n=='mv-placeholder':add('片尾 MV 预留',0,d,48,'tag','center')
 cmd=[FF,'-y','-v','error','-i',str(base)];filters=[];prev='0:v'
 for i,(path,a,b,x,y) in enumerate(overlays,1):
  cmd+=['-i',str(path)];filters.append(f"[{prev}][{i}:v]overlay={x}:{y}:enable='gte(t,{a:.4f})*lt(t,{b:.4f})'[v{i}]");prev=f'v{i}'
 if n=='outro':filters.append(f'[{prev}]fade=t=out:st={d-.45}:d=0.45[vfade]');prev='vfade'
 if filters:cmd+=['-filter_complex',';'.join(filters),'-map',f'[{prev}]']
 else:cmd+=['-map','0:v']
 cmd+=['-an','-frames:v',str(m['frames']),'-c:v','libx264','-preset','fast','-crf','18','-pix_fmt','yuv420p','-r','30',str(picture)];run(cmd);print('picture',n,flush=True)

(E/'concat.txt').write_text(''.join(f"file '{E/(m['name']+'-captioned.mp4')}'\n" for m in M))
run([FF,'-y','-v','error','-f','concat','-safe','0','-i',str(E/'concat.txt'),'-c','copy','-movflags','+faststart',str(F/'画面_1080p30.mp4')])
parts=[]
for m in M:
 wav=E/(m['name']+'-game.wav');run([FF,'-y','-v','error','-i',str(E/(m['name']+'-base.mkv')),'-vn','-ar',str(SR),'-ac','2',str(wav)])
 data,sr=sf.read(wav,dtype='float32',always_2d=True);nf=round(m['duration']*SR);parts.append(np.pad(data,((0,max(0,nf-len(data))),(0,0)))[:nf])
game=np.concatenate(parts);voice=np.zeros_like(game);duck=np.ones(len(game),np.float32)*.72
voice_rows=[]
for m in M:
 for row in m['lines']:
  norm=R/'audio'/('norm-'+Path(row['file']).name)
  if not norm.exists():run([FF,'-y','-v','error','-i',str(R/row['file']),'-af','loudnorm=I=-18:TP=-2:LRA=9','-ar',str(SR),'-ac','2',str(norm)])
  data,sr=sf.read(norm,dtype='float32',always_2d=True);a=round((m['start']+row['start'])*SR);b=min(len(game),a+len(data));voice[a:b]+=data[:b-a]
  lo=max(0,a-4800);hi=min(len(game),b+9600);env=np.full(hi-lo,.17,dtype=np.float32)
  if a>lo:env[:a-lo]=np.linspace(.72,.17,a-lo)
  if hi>b:env[b-lo:]=np.linspace(.17,.72,hi-b)
  duck[lo:hi]=np.minimum(duck[lo:hi],env)
  voice_rows.append({**row,'at':a/SR})
# Quiet original score: plucked triads, bass and a sparse percussion pulse, generated locally.
rng=np.random.default_rng(42);bgm=np.zeros((len(game),2),np.float32);beat=60/104
chords=[(48,55,60,63),(44,51,56,60),(46,53,58,62),(43,50,55,58)]
def tone(start,midi,length,amp,pan=0):
 a=int(start*SR);n=min(int(length*SR),len(game)-a)
 if a<0 or n<=0:return
 t=np.arange(n)/SR;hz=440*2**((midi-69)/12);env=(1-np.exp(-t*100))*np.exp(-t*4.6/length)
 x=(np.sin(2*np.pi*hz*t)+.22*np.sin(2*np.pi*hz*2*t)+.1*np.sin(2*np.pi*hz*3*t))*env*amp
 bgm[a:a+n,0]+=x*(1-pan*.35);bgm[a:a+n,1]+=x*(1+pan*.35)
for k in range(int(TOTAL/beat)):
 sec=k*beat;ch=chords[(k//8)%4]
 if sec>M[-2]['start']:break
 if k%2==0:tone(sec,ch[0]-12,.55,.06)
 if k%2==1:tone(sec,ch[1+k%3]+12,.36,.022,(-1)**k)
 if k%4==0:
  a=int(sec*SR);tt=np.arange(int(.12*SR))/SR;hit=np.sin(2*np.pi*(52*tt+12*(1-np.exp(-tt*26))))*np.exp(-tt*35)*.032;bgm[a:a+len(hit)]+=hit[:,None]
 if k%2==1:
  a=int(sec*SR);tt=np.arange(int(.045*SR))/SR;hat=rng.standard_normal(len(tt))*np.exp(-tt*100)*.004;bgm[a:a+len(hat)]+=hat[:,None]
# Additional score avoidance at game-sound inserts and final haunting.
for m in M:
 a=round(m['start']*SR);b=round((m['start']+m['duration'])*SR)
 if m['name'].startswith('tree') or m['name'] in ('outro','mv-placeholder'):bgm[a:b]=0
for i in range(2):bgm[:,i]*=np.minimum(1,np.arange(len(game))/(SR*.5))*np.minimum(1,(len(game)-np.arange(len(game)))/(SR*.5))
bgm*=.35+.65*(duck/.72)[:,None]
sf.write(F/'解说_曼波.wav',voice,SR,subtype='PCM_24');sf.write(F/'游戏原声.wav',game,SR,subtype='PCM_24');sf.write(F/'游戏原声_解说避让.wav',game*duck[:,None],SR,subtype='PCM_24');sf.write(F/'原创背景音乐.wav',bgm,SR,subtype='PCM_24')
sf.write(E/'premix.wav',voice+game*duck[:,None]+bgm,SR,subtype='FLOAT')
run([FF,'-y','-v','error','-i',str(E/'premix.wav'),'-af','alimiter=limit=0.84:level=false,loudnorm=I=-16:TP=-1.5:LRA=10','-ar',str(SR),'-c:a','pcm_s24le',str(F/'混音母带.wav')])
master=F/'头部功能_八颗头_曼波_1080p30.mp4'
run([FF,'-y','-v','error','-i',str(F/'画面_1080p30.mp4'),'-i',str(F/'混音母带.wav'),'-map','0:v','-map','1:a','-c:v','copy','-c:a','aac','-b:a','192k','-t',str(TOTAL),'-movflags','+faststart',str(master)])
run([FF,'-y','-v','error','-i',str(master),'-vf','scale=1280:720','-c:v','libx264','-crf','26','-preset','fast','-c:a','aac','-b:a','128k','-movflags','+faststart',str(F/'头部功能_八颗头_全长试看_720p.mp4')])
def timestamp(t):
 ms=round(t*1000);return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'
srt=[]
for i,r in enumerate(voice_rows,1):srt.append(f"{i}\n{timestamp(r['at'])} --> {timestamp(r['at']+r['duration'])}\n{r['text']}\n")
(F/'解说字幕.srt').write_text('\n'.join(srt));(F/'逐句配音时间表.json').write_text(json.dumps(voice_rows,ensure_ascii=False,indent=2))
print('FINAL',master,'duration',TOTAL,'frames',cursor,flush=True)
