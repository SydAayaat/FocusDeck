#!/usr/bin/env python3
"""Isolated native Qt/WebChannel performance interactions; no user archive/clipboard/network writes."""
import json,sys,time,copy,datetime as dt
from pathlib import Path
from PySide6.QtCore import QUrl,QObject,Signal,Slot
from PySide6.QtWebChannel import QWebChannel
from PySide6.QtWidgets import QApplication
from PySide6.QtWebEngineCore import QWebEnginePage
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtTest import QTest
from focusdeck.control_demo import DemoCenter
from focusdeck.analytics import dataset
from focusdeck.performance import performance_report

ROOT=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
app=QApplication([]);center=DemoCenter();copied=[];calls=[]
# Vary only disposable fixture measurements to exercise both sorting directions.
for i,r in enumerate(center.data['reports']):
    b=r['blocks'][0];start=b['started_at_ms'];active=(i+1)*600000;planned=1200000;b.update(active_ms=active,planned_ms=planned,budget_ms=planned,overtime_ms=max(0,active-planned),finished_at_ms=start+active)
    br=r['blocks'][1];br.update(started_at_ms=start+active,finished_at_ms=start+active+br['active_ms'])
    r.update(focused_ms=active,active_ms=active+br['active_ms'],finished_at_ms=br['finished_at_ms'],planned_ms=planned+br['planned_ms'],wall_ms=active+br['active_ms'])
    for m in r['schedule_snapshot']['activities']:
        block=next(x for x in r['blocks'] if x['id']==m['id']);m['planned_ms']=block['planned_ms'];m['timer_mode']='countdown'
    r['events']=[]
    for n,block in enumerate(r['blocks']):
        for kind,stamp,amount in [('block_started',block['started_at_ms'],0),('block_done' if block['outcome']=='done' else 'block_skipped',block['finished_at_ms'],block['active_ms'])]:
            r['events'].append(dict(seq=len(r['events'])+1,kind=kind,at_ms=stamp,mono_ms=stamp-start,boot=1,block_index=n,phase='active',active_ms=amount,paused_ms=0,budget_ms=block['budget_ms']))
    event=r['events'][-1];r['events'].append(dict(event,seq=event['seq']+1,kind='session_complete' if r['status']=='complete' else 'session_stopped',phase=r['status']))
center.data['analytics']=dataset(center.data['reports'])
class Page(QWebEnginePage):
    errors=[]
    def javaScriptConsoleMessage(self,level,message,line,source):
        if level==QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:self.errors.append(dict(message=message,line=line))
class Bridge(QObject):
    replied=Signal(str,str)
    @Slot(str,str)
    def call(self,ident,raw):
        try:
            r=json.loads(raw);action=r['action'];value=r.get('value') or {};calls.append(action)
            if action=='clipboard':copied.append(value['text']);result={'copied':True}
            elif action=='names':result={}
            else:result=center.handle(action,value)
            self.replied.emit(ident,json.dumps(dict(ok=True,value=result)))
        except Exception as e:self.replied.emit(ident,json.dumps(dict(ok=False,error=str(e))))
page=Page();view=QWebEngineView();view.setPage(page);view.resize(1440,1100)
channel=QWebChannel(page);bridge=Bridge();channel.registerObject('focusdeck',bridge);page.setWebChannel(channel)
view.show();page.load(QUrl.fromLocalFile(str(ROOT/'desktop/src/focusdeck/control_ui/index.html')))
def js(code):
    result=[];page.runJavaScript(code,result.append);until=time.monotonic()+10
    while not result and time.monotonic()<until:app.processEvents();QTest.qWait(10)
    if not result:raise RuntimeError('JavaScript callback timed out')
    return result[0]
def wait_for(code):
    until=time.monotonic()+10
    while time.monotonic()<until:
        if js(code):return
        QTest.qWait(20)
    raise RuntimeError('UI state did not arrive: '+code)
def async_js(code):
    js("window.__result=null;(async()=>{try{window.__result=JSON.stringify(await (async()=>{"+code+"})());}catch(e){window.__result=JSON.stringify({error:e.stack});}})();")
    wait_for('window.__result!==null');return json.loads(js('window.__result'))
wait_for('typeof initialized!=="undefined"&&initialized');js("historyPeriod='week';analyticsAnchor=dayKey(Date.now());navigate('history');")
wait_for("periodPerformance(calendarRange())?.execution!=null")
checks=async_js('''
 const checks={};const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const rows=()=>[...document.querySelectorAll('.analytics-top tbody tr')].map(r=>r.textContent);
 const descending=rows();await analyticsAction(document.querySelector('[data-action=analytics-sort][data-key=time]'));const ascending=rows();checks.sortTimeReversed=ascending.length>1&&ascending.join('|')===descending.reverse().join('|');
 await analyticsAction(document.querySelector('[data-action=analytics-sort][data-key=time]'));checks.timeDescendingAgain=analyticsSort.direction===-1;
 await analyticsAction(document.querySelector('[data-action=analytics-sort][data-key=total]'));checks.totalActive=document.querySelector('[data-key=total]').closest('th').getAttribute('aria-sort')==='descending';
 await analyticsAction(document.querySelector('[data-action=analytics-sort][data-key=total]'));checks.totalAscending=analyticsSort.direction===1;
 const x=periodPerformance(calendarRange()).execution;checks.scoreSeparatesBeatScore=x.score!=null&&x.score<=100;
 await analyticsAction(document.querySelector('[data-action=performance-detail]'));checks.componentDetails=document.querySelector('.analytics-popup').textContent.includes('Eligible')&&document.querySelector('.analytics-popup').textContent.includes('15 minutes');dismissAnalyticsPopup();
 const hour=document.querySelector('[data-chart=hourly][data-index="12"]');await analyticsAction(hour);checks.hourMetadata=['Total focus','Break','Pause','sessions','blocks','Average focus'].every(t=>document.querySelector('.analytics-popup').textContent.includes(t));dismissAnalyticsPopup();
 await analyticsAction(document.querySelector('[data-chart=punctuality]'));checks.startDetails=document.querySelector('.analytics-popup').textContent.includes('positive late');dismissAnalyticsPopup();
 await analyticsAction(document.querySelector('[data-action=analytics-comparison]'));checks.comparisonBoundaries=document.querySelector('.analytics-popup').textContent.includes('previous_bounds');dismissAnalyticsPopup();
 await analyticsAction(document.querySelector('[data-action=copy-performance]'));checks.copyRequested=true;
 changeAnalytics('month',analyticsAnchor);await wait(250);const selector=document.querySelector('#calendar-metric');selector.value='hours';selector.dispatchEvent(new Event('change',{bubbles:true}));checks.metricChanged=calendarMetric==='hours';
 checks.monthDailyBuckets=calendarBuckets().length===new Date(calendarRange().end+'T12:00:00Z').getUTCDate();
 const selected=document.querySelector('[data-action=analytics-calendar-day]');const chosen=selected.dataset.date;await analyticsAction(selected);await wait(250);checks.calendarOpensDay=historyPeriod==='day'&&analyticsAnchor===chosen;
 const timeline=document.querySelector('.timeline-segment');if(timeline){await analyticsAction(timeline);checks.timelineDetails=document.querySelector('[data-card=activity]').textContent.includes('Signed duration deviation');}else{const id=Object.keys(analyticsData().activities)[0];const a=analyticsData().activities[id];changeAnalytics('day',dayKey(a.actual_start));await wait(250);await analyticsAction(document.querySelector('.timeline-segment'));checks.timelineDetails=document.querySelector('[data-card=activity]').textContent.includes('Signed duration deviation');}
 checks.sortSurvivesPeriod=analyticsSort.key==='total'&&analyticsSort.direction===1;
 const weekTemplate='analytics-week@wide';const monthTemplate='analytics-aggregate@wide';checks.layoutTemplatesSeparate=weekTemplate!==monthTemplate;
 for(const p of ['day','week','month','year','all']){changeAnalytics(p,dayKey(Date.now()));await wait(250);checks['scope-'+p]=document.querySelector('.analytics-grid').dataset.chartScope===p;}
 for(const motion of ['full','reduced','off']){model.settings.motion=motion;applyAppearance();render();checks['motion-'+motion]=document.body.dataset.motion===motion;}
 changeAnalytics('month','1990-01-01');await wait(250);checks.emptyBiasUnavailable=document.querySelector('[data-card=summary]').textContent.includes('signed bias N/A');checks.emptyFocusUnavailable=bucketDetails('1990-01-01','1990-01-31').includes('Active focus N/A');
 return checks;
''')
if 'error' in checks:raise RuntimeError(checks['error'])
checks['structured-copy']=bool(copied) and all(k in json.loads(copied[-1]) for k in ('execution','estimation_by_project','consistency','comparison','contributions'))
checks['real-performance-handler']='performance-report' in calls
layouts=[]
for width in (1440,960,420):
    view.resize(width,1100)
    for scope in ('day','week','month','year','all'):
        js(f"layoutEditing=false;historyPeriod='{scope}';analyticsAnchor=dayKey(Date.now());navigate('history');")
        wait_for('periodPerformance(calendarRange())?.execution!=null');QTest.qWait(500)
        check=js('document.documentElement.scrollWidth<=innerWidth+1');checks[f'width-{width}-{scope}']=check;layouts.append(dict(width=width,scope=scope,overflow=not check))
        if width in (1440,420) and scope in ('day','week','month'):view.grab().save(str(out/f'analytics-{scope}-{width}.png'))
checks['javascript-errors']=not page.errors
result=dict(checks=checks,layouts=layouts,errors=page.errors);(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2));view.close()
sys.exit(0 if all(checks.values()) else 1)
