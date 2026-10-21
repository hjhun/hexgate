"""Synthetic raw-PTY input observer. Copy alone into a fresh private temp directory."""
import os,sys,time,tty,termios,select,json,base64
from pathlib import Path
root=Path(__file__).parent
counts={name:0 for name in ['pasteRect','pasteLine','pasteUnsolicited','ctrlC','ctrlV','tab','enter','up','ime','unknown']}
patterns=[('pasteRect',b'\x1b[200~ALPHA\nDELTA\x1b[201~'),('pasteLine',b'\x1b[200~ALPHA\x1b[201~'),('pasteUnsolicited',b'\x1b[200~UNSOLICITED_FIXTURE\x1b[201~'),('ime',b'pwd\r'),('ctrlC',b'\x03'),('ctrlV',b'\x16'),('tab',b'\x09'),('enter',b'\r'),('up',b'\x1b[A')]
pending=bytearray();previous=termios.tcgetattr(0);sent=False;input_bytes=0

def save():
 data=dict(counts);data.update(pid=os.getpid(),inputBytes=input_bytes,unsolicitedSent=sent)
 tmp=root/'state.pending';tmp.write_text(json.dumps(data));tmp.replace(root/'state.json')
try:
 tty.setraw(0)
 os.write(1,b'\x1b[?1049h\x1b[?1000l\x1b[?1002l\x1b[?1006l\x1b[?2004h\x1b[2J\x1b[HALPHA BRAVO CHARLIE\r\nDELTA ECHO FOXTROT\r\nINPUT_FIXTURE_READY\r\n')
 save();deadline=time.monotonic()+900
 while time.monotonic()<deadline and not (root/'release').exists():
  if not sent and (root/'emit-unsolicited').exists():
   os.write(1,b'\x1b]52;c;'+base64.b64encode(b'UNSOLICITED_FIXTURE')+b'\x07');sent=True;save()
  ready,_,_=select.select([0],[],[],.03)
  if not ready:continue
  data=os.read(0,256)
  if not data:break
  input_bytes+=len(data);pending.extend(data)
  while pending:
   found=False
   for name,token in patterns:
    if pending.startswith(token):
     counts[name]+=1;del pending[:len(token)];found=True;break
   if found:continue
   if any(token.startswith(pending) for _,token in patterns):break
   counts['unknown']+=1;del pending[0]
  save()
finally:
 termios.tcsetattr(0,termios.TCSADRAIN,previous)
 os.write(1,b'\x1b[?2004l\x1b[?1049l')
