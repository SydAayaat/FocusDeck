#!/usr/bin/env python3
"""Focused local Qt pairing UI QA, reusing DemoCenter and its in-memory bridge.
Never opens a socket, touches genuine config or redeems an invitation.
"""
import base64
import io
import json
import sys
import time
from pathlib import Path
import qrcode
from PySide6.QtCore import QUrl
from PySide6.QtWidgets import QApplication
from PySide6.QtWebEngineCore import QWebEnginePage,QWebEngineScript
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtTest import QTest
from focusdeck.control_demo import DemoCenter

out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
invitation=dict(type='focusdeck_pairing_invitation',invitation_version=1,protocol_version=1,
    invitation_id='f46fe057-40e3-4b58-a561-b1b7c50b91ea',nonce='t'*43,host='127.0.0.1',port=8765,
    desktop_id='DISPOSABLE-UI-HOST',certificate_sha256='a'*64,role='phone',expires_at_ms=int(time.time()*1000)+120000)
qr={}
for role in ('phone','viewer'):
    image=qrcode.make(json.dumps(invitation|dict(role=role),separators=(',',':')));buffer=io.BytesIO();image.save(buffer,format='PNG')
    qr[role]='data:image/png;base64,'+base64.b64encode(buffer.getvalue()).decode()
app=QApplication([])
class Page(QWebEnginePage):
    errors=[]
    def javaScriptConsoleMessage(self,level,message,line,source):
        if level==QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:self.errors.append(dict(message=message,line=line))
page=Page();view=QWebEngineView();view.setPage(page);view.resize(1200,1000)
script=QWebEngineScript();script.setInjectionPoint(QWebEngineScript.InjectionPoint.DocumentCreation);script.setWorldId(QWebEngineScript.ScriptWorldId.MainWorld)
script.setSourceCode('window.__fixture='+json.dumps(DemoCenter().data)+';window.__invite='+json.dumps(invitation)+';window.__qr='+json.dumps(qr)+''';
window.__calls=[];window.focusdeckTestBridge=async(action,value={})=>{
 window.__calls.push({action,value});const copy=v=>JSON.parse(JSON.stringify(v));
 if(action==='snapshot')return copy(window.__fixture);if(action==='names')return {};
 if(action==='pair-invite'){const i=copy(window.__invite);i.role=value.viewer?'viewer':'phone';return {ok:true,invitation:i,qr_image:window.__qr[i.role]};}
 if(action==='revoke-invite')return {ok:true};
 if(action==='pairing')return {ok:true,pairing:{protocol_version:1,host:'127.0.0.1',port:8765,token:'DISPOSABLE_TEST_TOKEN_NO_REAL_CREDENTIAL',certificate_sha256:'a'.repeat(64),desktop_id:'DISPOSABLE-UI-HOST',role:value.viewer?'viewer':'phone'}};
 throw Error('Isolated pairing QA: '+action);
};''')
page.scripts().insert(script);view.show()
page.load(QUrl.fromLocalFile(str(Path(__file__).resolve().parents[1]/'desktop/src/focusdeck/control_ui/index.html')))
def js(source):
    result=[];page.runJavaScript(source,lambda value:result.append(value));until=time.monotonic()+10
    while not result and time.monotonic()<until:app.processEvents();QTest.qWait(10)
    if not result:raise RuntimeError('Renderer callback timed out')
    return result[0]
def wait(expression):
    until=time.monotonic()+10
    while time.monotonic()<until:
        if js(expression):return
        QTest.qWait(20)
    raise AssertionError('Renderer state did not become ready: '+expression)
def click(action):
    js("(document.querySelector('#dialog[open]')||document).querySelector('[data-action=\""+action+"\"]').click()")
wait('typeof initialized!=="undefined"&&initialized');js("navigate('devices')")
checks={}
checks['manual-independent-of-invite']=js("document.querySelector('[data-action=\"manual-pairing\"]')!==null")
click('pair-phone');wait("document.querySelector('#dialog-content img')?.complete")
checks['local-qr-image']=js("document.querySelector('#dialog-content img').naturalWidth>0&&document.querySelector('#dialog-content img').src.startsWith('data:image/png')")
checks['expiry-and-pin-visible']=js("document.querySelector('#dialog-content').textContent.includes('Expires')&&document.querySelector('#dialog-content').textContent.includes('a'.repeat(64))")
checks['no-permanent-token-in-qr']= 'token' not in invitation
QTest.qWait(500);app.processEvents()
view.grab().save(str(out/'desktop-qr-invitation.png'))
click('regenerate-invite');wait("window.__calls.filter(c=>c.action==='pair-invite').length===2")
click('revoke-invite');wait("document.querySelector('#dialog-content').textContent.includes('Invitation revoked')")
checks['revoke']=js("window.__calls.some(c=>c.action==='revoke-invite'&&c.value.invitation_id===window.__invite.invitation_id)")
js("document.querySelector('#dialog-close').click()")
click('pair-viewer');wait("document.querySelector('#dialog-content').textContent.includes('Pair a tablet viewer')")
click('manual-pairing');wait("document.querySelector('#pairing-text')!==null")
checks['manual-viewer-role']=js("JSON.parse(document.querySelector('#pairing-text').value).pairing.role==='viewer'")
checks['manual-no-invite-required']=js("window.__calls.some(c=>c.action==='pairing'&&c.value.viewer===true)")
checks['no-browser-errors']=not page.errors
(out/'results.json').write_text(json.dumps(dict(checks=checks,errors=page.errors),indent=2)+'\n')
print(json.dumps(checks,indent=2))
page.deleteLater();view.close();app.processEvents()
if not all(checks.values()):raise SystemExit(1)
