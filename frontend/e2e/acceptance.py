"""Local browser acceptance against the running Vite/API services.
Uses only a disposable test account, real APIs, and explicitly labelled fault injection.
Run: python frontend/e2e/acceptance.py
"""
from pathlib import Path
import os, json, uuid, time, traceback
from playwright.sync_api import sync_playwright, expect
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'logs' / 'frontend-acceptance'
OUT.mkdir(parents=True, exist_ok=True)
BASE = 'http://127.0.0.1:5173'
results=[]; errors=[]
def record(name, action):
    try:
        action(); results.append({'name':name,'status':'PASS'}); print('PASS',name,flush=True)
    except Exception as exc:
        results.append({'name':name,'status':'FAIL','error':str(exc)[:1200]}); print('FAIL',name,str(exc)[:700],flush=True)
        page.screenshot(path=str(OUT/f'failure-{len(results)}.png'))
def go(route):
    page.goto(BASE+'/#/'+route); page.wait_for_load_state('networkidle'); page.locator('.content').wait_for()
def snapshot(name): page.screenshot(path=str(OUT/(name+'.png')),full_page=True)
def btn(name,scope=None): return (scope or page).get_by_role('button',name=name,exact=True)
def tab(name): page.get_by_role('tab',name=name,exact=True).click(); page.wait_for_timeout(120)
def assert_no_overflow():
    m=page.evaluate('''() => ({w:innerWidth, doc:document.documentElement.scrollWidth, bad:[...document.querySelectorAll('button,input,textarea,select')].filter(e=>e.getBoundingClientRect().width && (e.getBoundingClientRect().right>innerWidth+1 || e.getBoundingClientRect().left < -1) && !e.closest('.table-scroll,[role=tablist]')).map(e=>e.textContent.slice(0,60))})''')
    assert m['doc']<=m['w']+1,m
    assert not m['bad'],m
    for button in page.locator('.dock button').all():
        box=button.bounding_box();assert box and box['width']>=38 and box['height']>=38
        hit=button.evaluate("e => {const b=e.getBoundingClientRect(); return e.contains(document.elementFromPoint(b.x+b.width/2,b.y+b.height/2));}")
        assert hit, 'Dock button occluded: '+str(button.get_attribute('aria-label'))
    for button in page.locator('.industrial-tabs [data-state=active]').all():
        colors=button.evaluate("e=>({fg:getComputedStyle(e).color,bg:getComputedStyle(e).backgroundColor})")
        assert colors['fg']=='rgb(245, 246, 247)', colors
with sync_playwright() as p:
    chrome=Path(os.environ['LOCALAPPDATA'])/'ms-playwright/chromium-1117/chrome-win/chrome.exe'
    browser=p.chromium.launch(executable_path=str(chrome),headless=True)
    context=browser.new_context(viewport={'width':1440,'height':900},locale='zh-CN',reduced_motion='reduce')
    page=context.new_page(); page.set_default_timeout(12000)
    page.on('pageerror',lambda e:errors.append(str(e)))
    event_response=context.request.get(BASE+'/api/events'); assert event_response.ok
    events=event_response.json(); assert events,'Real events needed for acceptance'
    eid=events[0]['id']; term=events[0]['name'][:8]
    routes=['home','workspace?tab=overview',f'workspace?tab=events&event={eid}','workspace?tab=articles','workspace?tab=qa','settings','settings?tab=operations']
    def guest():
        go('home'); expect(page.locator('.home-event').first).to_be_visible()
        btn('查看依据').first.click();expect(page.get_by_role('dialog')).to_be_visible();btn('关闭登录窗口').click()
        h=page.locator('.he-head').first;h.click();expect(h).to_have_attribute('aria-expanded','false');h.click()
        page.get_by_role('searchbox',name='搜索主体或事件').fill('___NO_EVENT___');expect(page.get_by_text('暂无匹配事件',exact=True)).to_be_visible()
        page.get_by_role('searchbox',name='搜索主体或事件').fill('')
        go(f'workspace?tab=events&event={eid}');btn('登录查看证据').click();expect(page.get_by_role('dialog')).to_be_visible();btn('关闭登录窗口').click()
        tab('证据问答');expect(page.get_by_text('登录后基于证据提问')).to_be_visible();assert page.locator('textarea').count()==0
    record('guest guards, collapse and empty search',guest)
    for width,height in [(1920,1080),(1440,900),(1024,768),(768,1024),(390,844)]:
        page.set_viewport_size({'width':width,'height':height})
        for i,r in enumerate(routes):
            def layout(r=r,width=width,i=i):
                go(r);assert_no_overflow();snapshot(f'guest-{width}-{i}')
            record(f'layout {width} {r.split("&")[0]}',layout)
    page.set_viewport_size({'width':1440,'height':900})
    username='qa_'+uuid.uuid4().hex[:10];password=uuid.uuid4().hex+'Aa9!'
    (OUT/'test-account.json').write_text(json.dumps({'username':username,'password':password}),encoding='utf-8')
    def register():
        go('settings');btn('登录 / 注册').click();dialog=page.get_by_role('dialog');btn('注册',dialog).click()
        dialog.get_by_label('用户名',exact=True).fill(username);dialog.get_by_label('密码',exact=True).fill(password);btn('注册并创建工作区',dialog).click()
        expect(dialog).not_to_be_visible();expect(page.get_by_role('status').filter(has_text='登录成功')).to_be_visible();expect(page.get_by_role('tab',name='关注对象 (0)',exact=True)).to_be_visible()
    record('real registration creates personal workspace',register)
    def follow():
        page.get_by_placeholder('例如：OpenAI、机器人').fill(term);btn('添加关注').click();expect(page.get_by_text('已添加关注。',exact=True)).to_be_visible()
        expect(page.locator('.management-list strong').filter(has_text=term)).to_be_visible()
        page.get_by_placeholder('例如：OpenAI、机器人').fill(term);btn('添加关注').click();expect(page.get_by_text('已关注该对象。',exact=True)).to_be_visible()
        go('home');btn('我的关注').click();expect(page.locator('.home-event').first).to_be_visible()
    record('real follow save, duplicate feedback and home filter',follow)
    def subscription():
        go('settings');page.get_by_role('tab',name='订阅规则 (0)',exact=True).click();page.get_by_placeholder('例如：生成式人工智能').fill(term);btn('添加订阅').click();expect(btn('暂停')).to_be_visible()
        btn('暂停').click();expect(btn('恢复')).to_be_visible();btn('恢复').click();expect(btn('暂停')).to_be_visible()
        page.reload();page.wait_for_load_state('networkidle');page.get_by_role('tab',name='订阅规则 (1)',exact=True).click();expect(btn('暂停')).to_be_visible()
    record('real subscription pause resume and reload persistence',subscription)
    def failures():
        page.route('**/api/workspace/subscriptions/*',lambda route:route.fulfill(status=503,json={'error':'验收模拟：服务暂不可用'}) if route.request.method=='PATCH' else route.continue_())
        try:
            btn('暂停').click();expect(page.get_by_role('alert').filter(has_text='验收模拟')).to_be_visible();expect(btn('暂停')).to_be_visible()
        finally:page.unroute('**/api/workspace/subscriptions/*')
        btn('暂停').click();expect(btn('恢复')).to_be_visible();btn('恢复').click();expect(btn('暂停')).to_be_visible()
    record('injected subscription failure preserves state and retries',failures)
    def digest():
        tab('每日摘要');btn('生成今日摘要').click();expect(page.get_by_text('今日摘要已更新。',exact=True)).to_be_visible();expect(page.locator('.digest-entry')).to_have_count(1)
        page.get_by_role('tab',name='提醒',exact=False).click();expect(btn('标记已读').first).to_be_visible();btn('标记已读').first.click();expect(page.get_by_text('已标记为已读。',exact=True)).to_be_visible()
        page.get_by_label('仅看未读').check();assert page.locator('.management-list li').count()>=0
    record('real daily digest and notification read',digest)
    def evidence():
        go(f'workspace?tab=events&event={eid}');btn('查看依据').click();expect(page.locator('.evidence-card').first).to_be_visible()
        snapshot('member-evidence-desktop');btn('围绕事件提问').click();expect(page.locator('textarea')).to_be_visible()
    record('real evidence retrieval and QA navigation',evidence)
    def logout_login():
        page.get_by_role('button',name='点击退出登录',exact=False).click();expect(page.get_by_role('button',name='登录或注册')).to_be_visible();assert page.evaluate("localStorage.getItem('hotbot-token')") is None
        go('workspace?tab=qa');expect(page.get_by_text('登录后基于证据提问')).to_be_visible();btn('登录 / 注册').click();dialog=page.get_by_role('dialog');dialog.get_by_label('用户名',exact=True).fill(username);dialog.get_by_label('密码',exact=True).fill('wrongpassword');btn('登录',dialog).last.click();expect(dialog.get_by_role('alert')).to_be_visible()
        dialog.get_by_label('密码',exact=True).fill(password);btn('登录',dialog).last.click();expect(dialog).not_to_be_visible();go('settings');expect(page.get_by_role('tab',name='关注对象 (1)',exact=True)).to_be_visible();expect(page.get_by_role('tab',name='订阅规则 (1)',exact=True)).to_be_visible()
    record('real logout, wrong-password feedback and relogin persistence',logout_login)
    def delete_items():
        btn('移除').click();expect(page.get_by_role('alertdialog')).to_be_visible();btn('取消',page.get_by_role('alertdialog')).click();expect(page.locator('.management-list li')).to_have_count(1)
        btn('移除').click();btn('确认删除',page.get_by_role('alertdialog')).click();expect(page.get_by_role('tab',name='关注对象 (0)',exact=True)).to_be_visible()
        page.get_by_role('tab',name='订阅规则 (1)',exact=True).click();btn('删除').click();btn('确认删除',page.get_by_role('alertdialog')).click();expect(page.get_by_role('tab',name='订阅规则 (0)',exact=True)).to_be_visible()
    record('test-owned follow/subscription delete cancel and confirm',delete_items)
    def expired():
        page.route('**/api/workspace',lambda route:route.fulfill(status=400,json={'error':'invalid token'}))
        try:
            page.reload();expect(page.get_by_role('dialog')).to_be_visible();expect(page.get_by_text('登录已失效，请重新登录。',exact=True)).to_be_visible();assert page.evaluate("localStorage.getItem('hotbot-token')") is None
        finally:page.unroute('**/api/workspace')
    record('injected expired session clears auth and prompts login',expired)
    results.append({'name':'uncaught browser errors','status':'PASS' if not errors else 'FAIL','errors':errors})
    (OUT/'results.json').write_text(json.dumps({'results':results,'username':username,'browser':browser.version},ensure_ascii=False,indent=2),encoding='utf-8')
    browser.close()
print('RESULTS',sum(r['status']=='PASS' for r in results),'/',len(results),'output:',OUT,flush=True)

raise SystemExit(0 if all(r['status']=='PASS' for r in results) else 1)
