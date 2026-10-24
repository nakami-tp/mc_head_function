"""Synthesize script lines with the user's local Mambo model; cache keyed by all inputs."""
import hashlib,json,re,urllib.request,wave
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'video/intro/eight-heads'
rows=[]
chapter=''
for line in (ROOT/'video/intro/脚本.md').read_text().splitlines():
    if line.startswith('### '):chapter=line[4:]
    if line.startswith('> '):
        text=line[2:]
        speech=text.replace('MC','我的世界')
        rows.append({'id':f'{len(rows)+1:02}', 'chapter':chapter,'text':text,'speech':speech,'speed':1.08,'seed':42})
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
for row in rows:
    key=hashlib.sha256(json.dumps(row,ensure_ascii=False,sort_keys=True).encode()).hexdigest()[:12]
    p=OUT/'audio'/f"{row['id']}-{key}.wav"
    if not p.exists():
        req=urllib.request.Request('http://127.0.0.1:9881/tts',data=json.dumps({'text':row['speech'],'speed':row['speed'],'seed':row['seed']}).encode(),headers={'Content-Type':'application/json'})
        with opener.open(req,timeout=300) as response:
            p.write_bytes(response.read())
            row['service_audio_id']=response.headers.get('X-Audio-Id')
    with wave.open(str(p)) as wav:row['duration']=wav.getnframes()/wav.getframerate()
    row['file']=str(p.relative_to(OUT))
    print(row['id'],row['chapter'],round(row['duration'],3),row['text'],flush=True)
    (OUT/'audio/manifest.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
print('TOTAL VOICE SECONDS',sum(r['duration'] for r in rows))
