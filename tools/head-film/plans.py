"""Camera plans in world space; all shots derive from the same recorded performance."""
import json,sys
from pathlib import Path
out=Path(sys.argv[1]);scenes=json.loads((out/'scenes.json').read_text());plans=[]
def plan(name,s,shots):
 data={'version':1,'name':name,'capture':'capture.json','shots':shots,'output':{'file':name+'.mp4','width':1920,'height':1080,'fps':30,'bitrate':10000000,'audio':True,'hidePlayers':False}}
 p=out/(name+'.json');p.write_text(json.dumps(data,indent=2));plans.append(str(p))
def shot(s,a,b,target,cam,to=None,entity=None,fov=60,speed=1):
 q={'start':s['start']+round(a*20),'end':s['start']+round(b*20),'from':cam,'to':to or cam,'fov':fov,'speed':speed}
 if entity:q.update(entity=entity,targetOffset=target)
 else:q['target']=[s['x']+target[0],s['y']+target[1],s['z']+target[2]]
 return q
for s in scenes:
 n=s['name'];end=(s['end']-s['start'])/20;P=s['player'];seq=[]
 def add(a,b,t,c,d=None,entity=None,fov=60,speed=1):seq.append(shot(s,a,b,t,c,d,entity,fov,speed))
 if n=='showcase':add(0,end,[0,1.4,5.5],[8,4,9],[7,3,8])
 elif n=='acquire':
  add(0,5.5,[.5,1.4,2],[4,1.2,-1],[3.5,1.2,-.5]);add(5.5,11.4,[0,1,0],[3,1.7,4],[2.5,1.4,3.5],s['actor']);add(11.4,end,[0,1.1,0],[2,1,3.5],[2.2,.8,3],P)
 elif n=='zombie':
  add(0,6.7,[.5,.5,5],[4,2,5],[3,1.2,4]);add(6.7,13.3,[0,1.2,0],[2,.5,3.2],[1.8,.4,3],P);add(13.3,end,[.5,1.2,5.2],[4,1.3,2],[3,1.2,2])
 elif n=='ender':
  add(0,9.5,[.5,1.4,6],[5,2,-5],[4,1.7,-4]);add(9.5,12.1,[.5,1,6],[5,3,-5],[4,2,-4]);add(12.1,end,[.5,1,.5],[12,11,12],[11,10,11],fov=65)
 elif n=='creeper':
  add(0,3.8,[.5,1.2,6],[7,3,-7],[6,2.5,-6]);add(3.8,7.3,[.5,.7,1],[3,1,4],[2.6,.8,3.8]);add(7.3,end,[.5,1,1],[5,2,5],[4,1.5,5])
 elif n=='charged':
  add(0,5.9,[.5,1.2,4.5],[7,3,-5],[6,2.5,-4]);add(5.9,10.7,[.5,2,7],[9,4,-7],[8,4,-6]);add(10.7,end,[.5,2,7],[7,5,-6],[9,4,-5])
 elif n=='blaze':
  add(0,5.7,[.5,.4,7],[8,6,-7],[7,5,-6]);add(5.7,13.2,[.5,1,5],[7,3,-6],[6,2.5,-5]);add(13.2,16.4,[.5,.5,8],[9,3,4],[8,2.5,3]);add(16.4,end,[.5,1,2],[5,2,4],[4,1.5,3])
 elif n=='goat':
  add(0,4.6,[.5,1,4],[5,2,4],[4,1.5,3]);add(4.6,7.9,[0,1,0],[5,2.5,5],[4,2,5],P,fov=70);add(7.9,12,[.5,1,17],[9,4,6],[7,3,5]);add(12,end,[0,1.1,0],[3,1,4],[2.5,.8,3.5],P)
 elif n=='golem':
  add(0,6.2,[.5,13,.5],[3,1,4],[2.5,.8,3.5]);add(6.2,11.5,[.5,11,18],[16,9,10],[14,7,10],fov=65);add(11.5,18.6,[.5,2,34],[14,7,11],[12,6,9],fov=65);add(18.6,end,[.5,13,.5],[3,1,4],[2.5,.8,3.5])
 elif n=='pig':
  add(0,4.5,[0,1.1,0],[2,.7,3.5],[1.7,.6,3.2],P);add(4.5,7.6,[0,1.1,0],[2,.5,3],[1.6,.4,2.8],P);add(7.6,15.5,[0,1.1,0],[3,1,3.5],[-2,1,3.5],P);add(15.5,end,[0,1.2,0],[2.5,1.5,4],[3,1.3,4.5],P)
 elif n.startswith('tree') or n=='outro':add(0,end,[-18.5,2.3,-1.5],[9,3,7],[8,2.5,6])
 else:raise ValueError(n)
 # Thirty source ticks warm up the replay renderer before the editorial in-point.
 warm=dict(seq[0]);warm['start']=s['start']-30;warm['end']=s['start'];warm['to']=warm['from'];seq.insert(0,warm)
 plan(n,s,seq)
 if n=='golem':
  roller=s.get('entities',{}).get('roller')
  if roller:plan('hook',s,[shot(s,7,12.5,[0,2.2,0],[8,4,8],[7,3.5,9],roller,65)])
  else:plan('hook',s,[shot(s,7,12.5,[.5,11,15],[13,6,10],[11,5,8],None,65)])
(out/'plans.json').write_text(json.dumps(plans,indent=2));print(f'{len(plans)} camera plans written')
