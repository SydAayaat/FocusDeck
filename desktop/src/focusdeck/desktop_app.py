"""Qt/Wayland shell for FocusDeck Control Center; all content is bundled locally."""
from __future__ import annotations
import argparse
import base64
import json
import os
import sys
from pathlib import Path
from .control_center import ControlCenter


def main():
    parser = argparse.ArgumentParser(description='FocusDeck Control Center')
    parser.add_argument('--windowed', action='store_true', help='Start unmaximized; Krohnkite may still tile this window')
    parser.add_argument('--decorated', action='store_true', help='Use the compositor’s standard window frame')
    parser.add_argument('--demo', action='store_true', help='Isolated mockup preview; phone writes disabled')
    parser.add_argument('--capture', type=Path, help=argparse.SUPPRESS)
    parser.add_argument('--verify-ui', type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.verify_ui and not args.demo:
        parser.error('--verify-ui requires isolated --demo mode')
    # Respect an explicitly selected platform. Prefer native Wayland in a Wayland session.
    if os.environ.get('WAYLAND_DISPLAY') and 'QT_QPA_PLATFORM' not in os.environ:
        os.environ['QT_QPA_PLATFORM'] = 'wayland'
    try:
        from PySide6.QtCore import QObject, Signal, Slot, QRunnable, QThreadPool, QUrl, Qt, QTimer
        from PySide6.QtGui import QIcon, QShortcut, QKeySequence
        from PySide6.QtWidgets import QApplication, QFileDialog
        from PySide6.QtWebEngineWidgets import QWebEngineView
        from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineUrlRequestInterceptor, QWebEngineSettings
        from PySide6.QtWebChannel import QWebChannel
    except ImportError as exc:
        print(f'Qt could not load: {exc}\nInstall the desktop interface first: bash scripts/setup-control-center.sh', file=sys.stderr)
        return 1
    app = QApplication(sys.argv[:1]); app.setApplicationName('FocusDeck Control Center')
    app.setDesktopFileName('focusdeck-control-center'); app.setOrganizationName('FocusDeck')
    assets = Path(__file__).parent / 'control_ui'
    from .tray_window import TrayWindow
    from .gui_instance import GuiInstance
    app.setQuitOnLastWindowClosed(False)
    backend = ControlCenter()
    instance = None
    if not args.demo:
        instance = GuiInstance(backend.private)
        if not instance.primary:
            instance.activate()
            print('Restoring the running FocusDeck Control Center.')
            return 0
    window = TrayWindow(QIcon(str(assets / 'assets' / 'focusdeck-logo.png')), tray_enabled=not args.demo)
    window.setWindowTitle('FocusDeck Control Center')
    if not args.decorated: window.setWindowFlag(Qt.WindowType.FramelessWindowHint, True)
    window.resize(1440, 900); window.setMinimumSize(420, 360)
    if instance is not None:
        activation_timer = QTimer(window)
        activation_timer.setInterval(750)
        def activate_existing():
            if instance.poll(): window.restore()
        activation_timer.timeout.connect(activate_existing)
        activation_timer.start()
    if args.demo:
        from .control_demo import DemoCenter
        backend = DemoCenter()

    class LocalOnly(QWebEngineUrlRequestInterceptor):
        def interceptRequest(self, info):
            url = info.requestUrl(); scheme = url.scheme()
            if scheme == 'file':
                try: Path(url.toLocalFile()).resolve().relative_to(assets.resolve())
                except ValueError: info.block(True)
            elif scheme not in {'qrc', 'data', 'blob', 'about'}: info.block(True)

    class Page(QWebEnginePage):
        errors = []
        def javaScriptConsoleMessage(self, level, message, line, source):
            if level == QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:
                self.errors.append(dict(message=message, line=line))
            super().javaScriptConsoleMessage(level, message, line, source)
        def acceptNavigationRequest(self, url, navigation_type, is_main_frame):
            return url.scheme() == 'file' and Path(url.toLocalFile()).resolve() == (assets / 'index.html').resolve()

    class TaskSignals(QObject):
        result = Signal(str, str)

    class Task(QRunnable):
        def __init__(self, ident, action, value):
            super().__init__(); self.ident = ident; self.action = action; self.value = value; self.signals = TaskSignals()
        def run(self):
            try: result = dict(ok=True, value=backend.handle(self.action, self.value))
            except Exception as exc: result = dict(ok=False, error=str(exc))
            self.signals.result.emit(self.ident, json.dumps(result, ensure_ascii=False, default=str))

    class Bridge(QObject):
        replied = Signal(str, str)
        def __init__(self):
            super().__init__(); self.tasks = {}; self.pool = QThreadPool(); self.pool.setMaxThreadCount(2)
        @Slot(str, str)
        def call(self, ident, raw):
            try:
                if len(raw) > 24 * 1024 * 1024: raise ValueError('Input exceeds the import limit.')
                request = json.loads(raw); action = request['action']; value = request.get('value') or {}
                if action == 'pick-index':
                    path, _ = QFileDialog.getOpenFileName(window,'Import exact Master Index','','Master Index (*.md *.txt *.json)')
                    if not path: self.replied.emit(ident,json.dumps(dict(ok=True,value=None))); return
                    if Path(path).stat().st_size>512*1024: raise ValueError('Index import is limited to 512 KiB.')
                    action='index-preview';value=dict(source=Path(path).read_text(encoding='utf-8'),id=value.get('id'))
                if action == 'pick-index-export':
                    path, _ = QFileDialog.getSaveFileName(window,'Export actual Index evidence','FocusDeck-index.json','JSON (*.json)')
                    if not path: self.replied.emit(ident,json.dumps(dict(ok=True,value=None))); return
                    from .index_tracker import evidence
                    from .common import atomic_json
                    atomic_json(Path(path),evidence(backend.snapshot()['achievements'],value['id']))
                    self.replied.emit(ident,json.dumps(dict(ok=True,value={'saved':True}))); return
                if action == 'pick-icon':
                    path, _ = QFileDialog.getOpenFileName(window, 'Choose a card icon', '', 'Icons (*.png *.jpg *.jpeg *.webp *.ico)')
                    if not path: self.replied.emit(ident, json.dumps(dict(ok=True, value=None))); return
                    if Path(path).stat().st_size > 16 * 1024 * 1024: raise ValueError('Choose an icon up to 16 MiB.')
                    action = 'icon'; value = dict(data=base64.b64encode(Path(path).read_bytes()).decode())
                if action == 'pick-icons':
                    paths, _ = QFileDialog.getOpenFileNames(window, 'Import icons or a ZIP pack', '', 'Icon packs (*.png *.jpg *.jpeg *.webp *.ico *.zip)')
                    if not paths: self.replied.emit(ident, json.dumps(dict(ok=True, value=None))); return
                    action = 'import-icon-paths'; value = dict(paths=paths)
                if action == 'pick-icons-export':
                    path = QFileDialog.getExistingDirectory(window, 'Export selected icons as a ZIP pack')
                    if not path: self.replied.emit(ident, json.dumps(dict(ok=True, value=None))); return
                    action = 'export-icons'; value = dict(directory=path, ids=value['ids'])
                if action == 'pick-export':
                    path = QFileDialog.getExistingDirectory(window, 'Choose export folder')
                    if not path: self.replied.emit(ident, json.dumps(dict(ok=True, value=None))); return
                    action = 'export'; value = dict(directory=path, key=value.get('key'))
                if action == 'pick-report-export':
                    format = value.get('format', 'text')
                    suffix = 'json' if format == 'json' else 'txt'
                    path, _ = QFileDialog.getSaveFileName(window, 'Save session output', 'FocusDeck-session.' + suffix,
                                                        'Session output (*.' + suffix + ')')
                    if not path: self.replied.emit(ident, json.dumps(dict(ok=True, value=None))); return
                    action = 'save-report'; value = dict(key=value['key'], format=format, path=path)
                if action == 'clipboard':
                    app.clipboard().setText(str(value['text'])); self.replied.emit(ident, json.dumps(dict(ok=True, value={'copied': True}))); return
                task = Task(ident, action, value); self.tasks[ident] = task
                task.signals.result.connect(self.done); self.pool.start(task)
            except Exception as exc: self.replied.emit(ident, json.dumps(dict(ok=False, error=str(exc))))
        @Slot(str, str)
        def done(self, ident, result):
            self.replied.emit(ident, result); self.tasks.pop(ident, None)

    view = QWebEngineView(window); page = Page(view); view.setPage(page)
    interceptor = LocalOnly(page); page.profile().setUrlRequestInterceptor(interceptor)
    page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls, False)
    # Bundled CSS/JS/images are file URLs. The interceptor restricts them to this asset tree.
    page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessFileUrls, True)
    channel = QWebChannel(page); bridge = Bridge(); channel.registerObject('focusdeck', bridge); page.setWebChannel(channel)
    window.setCentralWidget(view)
    # WM shortcuts remain available for moving/resizing/closing the frameless tiled window.
    shortcut = QShortcut(QKeySequence('Ctrl+Q'), window); shortcut.activated.connect(window.quit)
    view.load(QUrl.fromLocalFile(str(assets / 'index.html')))
    if args.windowed: window.show()
    else: window.showMaximized()  # Not exclusive fullscreen: the compositor keeps control of tiling.
    if args.capture:
        def capture():
            args.capture.parent.mkdir(parents=True, exist_ok=True)
            view.grab().save(str(args.capture)); app.quit()
        QTimer.singleShot(6000, capture)
    if args.verify_ui:
        output = args.verify_ui; output.mkdir(parents=True, exist_ok=True)
        checks = []; cases = [(width, name, collapsed) for collapsed in (False, True) for width in (1672, 1440, 1050, 800, 600, 420) for name in ('overview','achievements','cards','sessions','history','devices','settings')]
        cases += [(width, 'history_' + period, False) for width in (1672, 1440, 1050, 800, 600, 420) for period in ('month', 'year', 'all')]
        def next_case():
            if not cases:
                exercise = """window.__workflow = {}; (async () => {
                    navigate('overview');
                    document.querySelector('#gpt-paste').value = exampleCard;
                    await parsePaste();
                    window.__workflow.cardRedirect = page === 'cards' && card.definition.title === 'German vocabulary';
                    const current = document.querySelector('[data-field="card.current"]'); current.value = '25'; current.dispatchEvent(new Event('input', {bubbles:true}));
                    window.__workflow.previewEdit = document.querySelector('#card-preview').textContent.includes('25 / 1250');
                    navigate('overview'); document.querySelector('#gpt-paste').value = exampleSession; await parsePaste();
                    window.__workflow.sessionRedirect = page === 'sessions' && session.blocks.length === 4 && session.blocks[3].timer_mode === 'stopwatch';
                    await actionHandler(document.querySelector('[data-action="block-copy"][data-index="0"]'));
                    window.__workflow.duplicateUnique = session.blocks.length === 5 && new Set(session.blocks.map(b=>b.id)).size === 5;
                    await actionHandler(document.querySelector('[data-action="save-template"]'));
                    window.__workflow.savedTemplate = model.templates.length === 1;
                    navigate('achievements');
                    window.__workflow.sevenTabs = document.querySelectorAll('#nav button').length === 7 && document.querySelector('#nav').textContent.includes('Card Builder') && document.querySelector('#nav').textContent.includes('Analytics');
                    const german = definitions().find(d=>d.id==='demo_german');
                    achievementSelection=german.id; render();
                    window.__workflow.detailEvidence = document.querySelector('#achievement-detail').textContent.includes('200 / 1,250 words') && document.querySelector('#achievement-detail').textContent.includes('54 days');
                    const oldDraft=JSON.stringify(card);
                    await actionHandler(document.querySelector('[data-action="select-achievement"][data-id="demo_gym"]'));
                    window.__workflow.selectionReadOnly = JSON.stringify(card)===oldDraft && achievementSelection==='demo_gym';
                    achievementCategory='health';render();
                    window.__workflow.categoryFilter=document.querySelectorAll('.monitor-card').length===2;
                    achievementSearch='cardio';updateAchievementResults();
                    window.__workflow.searchFilter=document.querySelectorAll('.monitor-card').length===1 && achievementSelection==='demo_cardio';
                    achievementCategory='all';achievementSearch='';achievementView='list';render();
                    await actionHandler(document.querySelector('[data-action="achievement-detail"][data-id="demo_cardio"]'));
                    window.__workflow.fullDetail=Boolean(document.querySelector('#dialog[open].wide-dialog')) && document.querySelector('#dialog-content').textContent.includes('Completed streak history');
                    document.querySelector('#dialog-close').click();
                    window.__workflow.listView=Boolean(document.querySelector('.monitor-grid.list'));
                    await actionHandler(document.querySelector('#sidebar-toggle'));
                    const collapsed=model.ui.sidebar_collapsed;await refresh();
                    window.__workflow.sidebarSaved=model.ui.sidebar_collapsed===collapsed && document.querySelector('#sidebar-toggle').getAttribute('aria-expanded')===String(!collapsed);
                    const colors=['#ffffff','#ff0000'];
                    const files=colors.map((color,index)=>{const canvas=document.createElement('canvas');canvas.width=32;canvas.height=32;const c=canvas.getContext('2d');c.fillStyle=color;c.fillRect(8,8,16,16);return {name:'preview-'+index+'.png',data:canvas.toDataURL('image/png').split(',')[1]};});
                    const imported=await api('import-icons',{files});await receiveIcons(imported);
                    window.__workflow.packImport=imported.entries.length===2 && imported.imported.length===2;
                    newCard();await loadIcon(clone(imported.entries[0].icon));
                    const beforeMask=getComputedStyle(document.querySelector('#card-preview .card-icon')).maskImage;
                    model.settings.palette='blue';applyAppearance();
                    const tinted=document.querySelector('#card-preview .card-icon');
                    window.__workflow.dynamicTint=getComputedStyle(tinted).backgroundColor==='rgb(80, 156, 251)' && getComputedStyle(tinted).maskImage===beforeMask;
                    navigate('settings');window.__workflow.libraryPicker=document.querySelectorAll('[data-library-select]').length===2;
                    const originalLedger=clone(model.achievements.ledger);
                    const d=definitions().find(d=>d.id==='demo_german');
                    model.achievements.ledger.progressHistory.push({achievementId:d.id,unit:'unrelated unit',timestamp:model.achievements.as_of_ms,delta:99999});
                    window.__workflow.unitIsolation=chartValues(d).every(v=>v.value<99999);
                    model.achievements.ledger=originalLedger;
                    navigate('overview');document.querySelector('#gpt-paste').value=exampleRewardSession;await parsePaste();
                    window.__workflow.rewardPaste=session.blocks[0].reward_policy.tiers[0].min_focus_ratio===85 && session.session_reward_policy.eligibility.require_all_focus_completed;
                    const originalPolicy=JSON.stringify(session.blocks[0].reward_policy);
                    advancedSession(0);
                    window.__workflow.policyForm=Boolean(document.querySelector('[data-form-input]')) && !document.querySelector('[data-field="block.0.policy"]');
                    const policyText=[...document.querySelectorAll('[data-form-input]')].find(el=>el.value==='Take a short walk');policyText.value='Enjoy fresh air';policyText.dispatchEvent(new Event('input',{bubbles:true}));
                    await actionHandler(document.querySelector('[data-action="form-save"]'));
                    window.__workflow.policyEditPreserved=session.blocks[0].reward_policy.tiers[0].min_focus_ratio===85 && session.blocks[0].reward_policy.tiers[0].reward.text==='Enjoy fresh air' && session.session_reward_policy.eligibility.require_all_focus_completed;
                    navigate('overview');document.querySelector('#gpt-paste').value='FOCUSDECK BATCH\\n- operation: progress_increment\\n  card: demo_german\\n  amount: 20\\n- operation: event\\n  card: demo_gym\\n  event: completed\\n  amount: 1';await parsePaste();
                    window.__workflow.batchRedirect=page==='cards' && commandBatch.length===2 && document.querySelectorAll('[data-action="edit-operation"]').length===2;
                    await actionHandler(document.querySelector('[data-action="edit-operation"][data-index="0"]'));
                    const amount=[...document.querySelectorAll('[data-form-input]')].find(el=>el.dataset.formPath==='["amount"]');amount.value='25';amount.dispatchEvent(new Event('input',{bubbles:true}));
                    const receipt=commandBatch[0].id;await actionHandler(document.querySelector('[data-action="form-save"]'));
                    window.__workflow.batchEdit=commandBatch[0].amount===25 && commandBatch[0].id===receipt && commandBatch[1].event==='completed';
                    newOperation('icon_set','demo_german');await actionHandler(document.querySelector('[data-action="edit-operation"]'));
                    const variant=document.querySelector('[data-form-variant]');variant.value='1';variant.dispatchEvent(new Event('change',{bubbles:true}));
                    window.__workflow.iconVariant=[...document.querySelectorAll('[data-form-input]')].some(el=>el.dataset.formPath==='["icon","name"]');
                    await actionHandler(document.querySelector('[data-action="form-save"]'));
                    navigate('history');await actionHandler(document.querySelector('[data-action="view-output"]'));
                    window.__workflow.reportVisible=document.querySelector('#session-output').value.includes('Focused timer time:') && document.querySelector('#session-output').value.includes('Report key:');
                    await actionHandler(document.querySelector('[data-action="copy-output"]'));
                    window.__workflow.reportCopy=Boolean(document.querySelector('#toast').textContent.includes('copied'));
                    await actionHandler(document.querySelector('[data-action="output-json"]'));
                    window.__workflow.reportJson=JSON.parse(document.querySelector('#session-output').value).session_id===reportKey.split(':')[0];
                    document.querySelector('#dialog-close').click();
                    const past=model.reports.at(-1);past.started_at_ms-=90*86400000;reportKey=null;historyPeriod='week';navigate('overview');
                    const selected=String(past.session_id+':'+past.started_at_ms);await actionHandler({dataset:{action:'select-report',key:selected}});
                    window.__workflow.exactHistoricalSelection=historyPeriod==='all' && reportKey===selected && document.querySelector('#content').textContent.includes(past.title);
                    model.status.state.phase='stopped';model.status.state.session_id=model.reports[0].session_id;navigate('overview');
                    window.__workflow.completedOutputActions=Boolean(document.querySelector('[data-action="view-output"]')) && document.querySelector('#content').textContent.includes('Session completed');
                    window.__workflow.done = true;
                })().catch(e=>window.__workflow.error=e.message);"""
                window.resize(1440, 941); page.runJavaScript(exercise)
                attempts = [0]
                def finish_check():
                    def result(value):
                        workflow = json.loads(value or '{}'); attempts[0] += 1
                        if not workflow.get('done') and not workflow.get('error') and attempts[0] < 25:
                            QTimer.singleShot(200, finish_check); return
                        (output / 'checks.json').write_text(json.dumps(dict(checks=checks, workflow=workflow, errors=page.errors), indent=2))
                        app.quit()
                    page.runJavaScript('JSON.stringify(window.__workflow)', result)
                QTimer.singleShot(200, finish_check); return
            width, name, collapsed = cases.pop(0); window.resize(width, 941)
            page_name = name.split('_')[0]
            js = "model.ui={sidebar_collapsed:" + str(collapsed).lower() + "};document.querySelector('[data-action=\"navigate\"][data-page=\"" + page_name + "\"]').click();"
            if name.startswith('history_'):
                js += "document.querySelector('[data-action=\"history-period\"][data-period=\"" + name.split('_')[1] + "\"]').click();"
            if name == 'achievements': js += "document.querySelector('[data-action=\"select-achievement\"][data-id=\"demo_german\"]')?.click();"
            if name == 'cards': js += "document.querySelector('[data-action=\"edit-card\"]').click();"
            page.runJavaScript(js)
            def inspect():
                def result(value):
                    checks.append(dict(width=width, page=name, collapsed=collapsed, metrics=json.loads(value)))
                    if width == 1672: view.grab().save(str(output / (name + ('-collapsed' if collapsed else '') + '.png')))
                    QTimer.singleShot(50, next_case)
                page.runJavaScript("JSON.stringify({width:innerWidth,scroll:document.documentElement.scrollWidth,heading:document.querySelector('h1').textContent,controls:document.querySelectorAll('button').length})", result)
            QTimer.singleShot(450, inspect)
        QTimer.singleShot(2500, next_case)
    result = app.exec()
    if instance is not None:
        instance.close()
    return result


if __name__ == '__main__':
    raise SystemExit(main())
