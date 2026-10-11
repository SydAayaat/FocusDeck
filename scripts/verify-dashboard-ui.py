#!/usr/bin/env python3
"""Focused, isolated Qt WebEngine verification. Requires optional desktop GUI dependencies.

Uses only DemoCenter fixtures and in-memory bridge writes; never connects to a phone.
Run: PYTHONPATH=desktop/src QTWEBENGINE_CHROMIUM_FLAGS=--no-sandbox python3 scripts/verify-dashboard-ui.py /tmp/dashboard-qa
"""
import json
import sys
import time
from pathlib import Path
from PySide6.QtCore import QUrl
from PySide6.QtWidgets import QApplication
from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineScript
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtTest import QTest
from focusdeck.control_demo import DemoCenter

out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
app=QApplication([])
class Page(QWebEnginePage):
    errors=[]
    def javaScriptConsoleMessage(self,level,message,line,source):
        if level==QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:self.errors.append({'message':message,'line':line})
page=Page();view=QWebEngineView();view.setPage(page);view.resize(1440,1100)
fixture=DemoCenter().data
script=QWebEngineScript();script.setInjectionPoint(QWebEngineScript.InjectionPoint.DocumentCreation);script.setWorldId(QWebEngineScript.ScriptWorldId.MainWorld)
script.setSourceCode('window.__fixture='+json.dumps(fixture)+''';window.focusdeckTestBridge=async (action,value={})=>{
 const copy=v=>JSON.parse(JSON.stringify(v));if(action==='snapshot')return copy(window.__fixture);if(action==='names')return {};
 if(action==='save-layout'){window.__fixture.layouts[value.template]=copy(value.layout);return copy(window.__fixture.layouts);}
 if(action==='achievement-order'){window.__fixture.achievements.ledger.cardOrder=value.order;return {state:'applied'};}
 if(action==='ui'){window.__fixture.ui=value;return value;}throw Error('Isolated QA: '+action);
};''')
page.scripts().insert(script)
assets=Path(__file__).resolve().parents[1]/'desktop/src/focusdeck/control_ui/index.html'
view.show();page.load(QUrl.fromLocalFile(str(assets)))
def js(source):
    result=[];page.runJavaScript(source,lambda value:result.append(value))
    until=time.monotonic()+10
    while not result and time.monotonic()<until:app.processEvents();QTest.qWait(10)
    if not result:raise RuntimeError('Renderer callback timed out')
    return result[0]
def async_js(source):
    js('window.__qaResult=null;(async()=>{try{window.__qaResult=JSON.stringify(await (async()=>{'+source+'})());}catch(e){window.__qaResult=JSON.stringify({error:e.stack});}})();')
    until=time.monotonic()+10
    while time.monotonic()<until:
        value=js('window.__qaResult');
        if value:return json.loads(value)
        QTest.qWait(20)
    raise RuntimeError('Gesture test timed out')
until=time.monotonic()+10
while time.monotonic()<until:
    if js('typeof initialized!=="undefined"&&initialized'):break
    QTest.qWait(50)
checks={};layouts=[]
metrics='''(()=>{const cards=layoutNodes().filter(n=>n.getBoundingClientRect().width>0);let overlaps=[];
 for(let i=0;i<cards.length;i++)for(let j=i+1;j<cards.length;j++){const a=cards[i],b=cards[j];if(a.parentElement!==b.parentElement)continue;const x=a.getBoundingClientRect(),y=b.getBoundingClientRect();if(Math.min(x.right,y.right)-Math.max(x.left,y.left)>1&&Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top)>1)overlaps.push([a.dataset.card,b.dataset.card]);}
 return {cards:cards.length,handles:document.querySelectorAll('[data-resize]').length,overflow:document.documentElement.scrollWidth>innerWidth+1,overlaps,bad:cards.filter(n=>!Number.isFinite(n.getBoundingClientRect().height)||n.getBoundingClientRect().width<1).map(n=>n.dataset.card)};})()'''
for width,target,scope in [(1440,'overview','week'),(1440,'achievements','week'),(1440,'history','day'),(1440,'history','week'),(1440,'history','month'),(1440,'history','year'),(1440,'history','all'),(420,'overview','week'),(420,'achievements','week'),(420,'history','day'),(420,'history','week')]:
    view.resize(width,1100);js(f"layoutEditing=false;historyPeriod='{scope}';analyticsAnchor=dayKey(Date.now());navigate('{target}');")
    QTest.qWait(550);data=json.loads(js("JSON.stringify("+metrics+")"));data.update(width=width,page=target,scope=scope);layouts.append(data)
    checks[f'{width}-{target}-{scope}']=data['cards']>0 and not data['handles'] and not data['overflow'] and not data['overlaps'] and not data['bad']
    if width==1440 and (target!='history' or scope in ('day','week')):view.grab().save(str(out/f'{target}-{scope}.png'))
view.resize(1440,1100);QTest.qWait(100)
result=async_js('''
 const frame=()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const wait=ms=>new Promise(r=>setTimeout(r,ms));
 async function drag(node,axis,dx,dy,cancel=false){const h=node.querySelector('[data-resize="'+axis+'"]');h.setPointerCapture=()=>{};const b=h.getBoundingClientRect();const x=b.left+8,y=b.top+8;h.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:42,clientX:x,clientY:y}));document.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,pointerId:42,clientX:x+dx,clientY:y+dy}));document.dispatchEvent(new PointerEvent(cancel?'pointercancel':'pointerup',{bubbles:true,pointerId:42,clientX:x+dx,clientY:y+dy}));await wait(360);}
 const assertions={};
 for(const [target,scope,id] of [['overview','week','timeline'],['achievements','week','achievement-demo_german'],['history','day','timeline'],['history','week','focus']]) {
  layoutEditing=true;historyPeriod=scope;navigate(target);await wait(200);const cards=layoutNodes();assertions[target+'-'+scope+'-handles']=cards.every(c=>c.querySelectorAll(':scope>[data-resize]').length===3);
  const c=cards.find(c=>c.dataset.card===id)||cards[0],old=c.getBoundingClientRect();await drag(c,'xy',-old.width*.45,96);const saved=pageLayout().cards[c.dataset.card];assertions[target+'-'+scope+'-resize']=saved.span<Number(c.dataset.defaultSpan||12)&&saved.height>old.height;
  assertions[target+'-'+scope+'-saved']=JSON.stringify(window.__fixture.layouts[layoutTemplate()])===JSON.stringify(pageLayout());
  const resizedMetrics=JSON.parse(JSON.stringify((()=>{const cards=layoutNodes().filter(n=>n.getBoundingClientRect().width>0);return cards.every((a,i)=>cards.slice(i+1).every(b=>{if(a.parentElement!==b.parentElement)return true;const x=a.getBoundingClientRect(),y=b.getBoundingClientRect();return Math.min(x.right,y.right)-Math.max(x.left,y.left)<=1||Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top)<=1;}));})()));
  assertions[target+'-'+scope+'-resize-reflow']=resizedMetrics;
  const dimensions=JSON.stringify(saved);layoutEditing=false;render();await frame();const applied=layoutNodes().find(c=>c.dataset.card===id)||layoutNodes()[0];assertions[target+'-'+scope+'-retained']=JSON.stringify(pageLayout().cards[applied.dataset.card])===dimensions&&!document.querySelector('[data-resize]');
  layoutEditing=true;render();await wait(100);const next=layoutNodes().find(c=>c.dataset.card===id)||layoutNodes()[0];await drag(next,'xy',120,240,true);assertions[target+'-'+scope+'-cancel']=JSON.stringify(pageLayout().cards[next.dataset.card])===dimensions;
  await layoutAction(document.querySelector('[data-action="reset-layout"]'));assertions[target+'-'+scope+'-reset']=Object.keys(pageLayout().cards).length===0;
 }
 layoutEditing=true;navigate('overview');await wait(100);const kpi=layoutNodes().find(c=>c.dataset.card.startsWith('kpi'))||layoutNodes()[0];await drag(kpi,'x',-100,0);const horizontal=clone(pageLayout().cards[kpi.dataset.card]);assertions.horizontalMinimum=horizontal.height>=120&&horizontal.height<=2400;await drag(kpi,'y',0,96);assertions.verticalIndependent=pageLayout().cards[kpi.dataset.card].span===horizontal.span&&pageLayout().cards[kpi.dataset.card].height>horizontal.height;
 const dimensionsBefore=JSON.stringify(pageLayout().cards);await layoutAction(kpi.querySelector('[data-action="layout-later"]'));assertions.genericOrderIndependent=JSON.stringify(pageLayout().cards)===dimensionsBefore&&pageLayout().order.length>0;const ordered=clone(pageLayout().order);render();await wait(100);assertions.genericOrderRetained=JSON.stringify(pageLayout().order)===JSON.stringify(ordered);
 const template=layoutTemplate();pageLayout().cards.queueProbe={span:6,height:120};const first=saveDashboardLayout();pageLayout().cards.queueProbe={span:12,height:240};const second=saveDashboardLayout();await Promise.all([first,second]);assertions.latestQueuedSave=window.__fixture.layouts[template].cards.queueProbe.height===240&&pageLayout().cards.queueProbe.height===240;await layoutAction(document.querySelector('[data-action="reset-layout"]'));
 layoutEditing=true;navigate('achievements');const achievementDimensions=JSON.stringify(pageLayout().cards);const beforeOrder=JSON.stringify(ledger().cardOrder||[]);await layoutAction(document.querySelector('[data-action="order-later"]'));assertions.achievementOrderIndependent=JSON.stringify(pageLayout().cards)===achievementDimensions&&JSON.stringify(ledger().cardOrder||[])!==beforeOrder;achievementSearch='german';updateAchievementResults();await frame();assertions.filteredHandles=document.querySelector('.monitor-card').querySelectorAll('[data-resize]').length===3;achievementSearch='';render();
 await actionHandler(document.querySelector('[data-action="achievement-detail"]'));await frame();assertions.detailHandles=document.querySelector('#dialog-content [data-resize]')!=null;document.querySelector('#dialog-close').click();
 navigate('history');historyPeriod='all';render();const bar=document.querySelector('[data-chart="focus"][data-next]');await analyticsAction(bar);await wait(550);assertions.drill=historyPeriod==='year'&&analyticsStack.length===1;await analyticsAction(document.querySelector('[data-action="analytics-back"]'));assertions.back=historyPeriod==='all'&&analyticsStack.length===0;
 for(const motion of ['full','reduced','off']){model.settings.motion=motion;applyAppearance();render();await frame();assertions['motion-'+motion]=document.body.dataset.motion===motion;}
 return assertions;
''')
checks.update(result)
# Actual mouse events verify handle capture and live updates, independently of synthetic cases.
js("layoutEditing=true;model.settings.motion='off';historyPeriod='week';navigate('overview');document.querySelector('[data-card=timeline]').scrollIntoView({block:'center'});")
QTest.qWait(150)
position=json.loads(js("JSON.stringify((()=>{const r=document.querySelector('[data-card=timeline] [data-resize=xy]').getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]})())"))
from PySide6.QtCore import QPoint, Qt
point=QPoint(round(position[0]),round(position[1]));QTest.mousePress(view.focusProxy(),Qt.MouseButton.LeftButton,Qt.KeyboardModifier.NoModifier,point)
QTest.mouseMove(view.focusProxy(),point+QPoint(-250,96),50);QTest.qWait(60)
checks['native-pointer-live']=js('layoutGesture!==null&&pageLayout().cards.timeline!=null')
QTest.mouseRelease(view.focusProxy(),Qt.MouseButton.LeftButton,Qt.KeyboardModifier.NoModifier,point+QPoint(-250,96));QTest.qWait(300)
checks['native-pointer-release']=js('layoutGesture===null&&window.__fixture.layouts[layoutTemplate()].cards.timeline!=null')
checks['javascript-errors']=not page.errors
result={'checks':checks,'layouts':layouts,'errors':page.errors};(out/'result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2));view.close();sys.exit(0 if all(checks.values()) else 1)
