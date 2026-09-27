from pathlib import Path
import os,json,tempfile
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'logs/frontend-acceptance';BASE='http://127.0.0.1:5173';results=[]
with sync_playwright() as p:
 ext=OUT/'zoom-extension';ext.mkdir(parents=True,exist_ok=True)
 (ext/'manifest.json').write_text(json.dumps({'manifest_version':3,'name':'Local Acceptance Zoom','version':'1.0','permissions':['tabs'],'background':{'service_worker':'background.js'}}))
 (ext/'background.js').write_text('chrome.runtime.onInstalled.addListener(()=>{});')
 ctx=p.chromium.launch_persistent_context(user_data_dir=str(OUT/'isolated-zoom-profile'),executable_path=str(Path(os.environ['LOCALAPPDATA'])/'ms-playwright/chromium-1117/chrome-win/chrome.exe'),headless=False,args=['--headless=new',f'--disable-extensions-except={ext}',f'--load-extension={ext}'],viewport={'width':1440,'height':900},locale='zh-CN',reduced_motion='reduce')
 page=ctx.new_page();page.set_default_timeout(12000)
 worker=ctx.service_workers[0] if ctx.service_workers else ctx.wait_for_event('serviceworker')
 def go(route):page.goto(BASE+'/#/'+route);page.wait_for_load_state('networkidle')
 def check(name,func):
  try:func();results.append({'name':name,'status':'PASS'});print('PASS',name,flush=True)
  except Exception as e:results.append({'name':name,'status':'FAIL','error':str(e)[:1000]});print('FAIL',name,str(e)[:500],flush=True);page.screenshot(path=str(OUT/f'extended-fail-{len(results)}.png'))
 def button(name):return page.get_by_role('button',name=name,exact=True)
 def zoom(scale):
  go('home')
  actual=worker.evaluate('''async scale=>{const tabs=await chrome.tabs.query({});const tab=tabs.find(t=>t.url.startsWith('http://127.0.0.1:5173'));await chrome.tabs.setZoom(tab.id,scale);return await chrome.tabs.getZoom(tab.id);}''',scale)
  assert abs(actual-scale)<.01
 def layout(check_dock=True):
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
  for b in (page.locator('.dock button').all() if check_dock else []):assert b.evaluate('e=>{const r=e.getBoundingClientRect();return e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))}')
 events=ctx.request.get(BASE+'/api/events').json();eid=events[0]['id']
 routes=['home','workspace?tab=overview',f'workspace?tab=events&event={eid}','workspace?tab=articles','workspace?tab=qa','settings','settings?tab=operations']
 for scale in [.8,1.25,1.5]:
  zoom(scale)
  for i,route in enumerate(routes):
   def test(route=route,i=i,scale=scale):
    go(route);layout();page.screenshot(path=str(OUT/f'zoom-{scale}-{i}.png'),full_page=False)
   check(f'actual browser zoom {scale} {route.split("&")[0]}',test)
 zoom(1)
 creds=json.loads((OUT/'test-account.json').read_text())
 def login():
  go('settings');button('登录 / 注册').click();d=page.get_by_role('dialog');d.get_by_label('用户名',exact=True).fill(creds['username']);d.get_by_label('密码',exact=True).fill(creds['password']);d.locator('form').get_by_role('button',name='登录',exact=True).click();expect(d).not_to_be_visible()
 check('existing test account login',login)
 for width,height in [(1920,1080),(768,1024),(390,844)]:
  ctx.set_default_timeout(12000);page.set_viewport_size({'width':width,'height':height})
  for tabname in ['关注对象 (0)','订阅规则 (0)','提醒','每日摘要']:
   def test(tabname=tabname,width=width):
    go('settings');page.get_by_role('tab',name=tabname,exact=tabname!='提醒').click();layout();page.screenshot(path=str(OUT/f'member-{width}-{tabname[:2]}.png'),full_page=True)
   check(f'member layout {width} {tabname}',test)
  def qa_layout():
   go(f'workspace?tab=qa&event={eid}');expect(page.locator('textarea')).to_be_visible();layout();page.screenshot(path=str(OUT/f'member-{width}-qa.png'),full_page=True)
  check(f'member QA layout {width}',qa_layout)
 page.set_viewport_size({'width':1440,'height':900})
 def pager():
  go('workspace?tab=events');button('下一页').click();expect(page.locator('.pagination')).to_contain_text('第 2 /');assert page.locator('.rail-event.selected').count()==1
  page.get_by_role('searchbox',name='搜索事件').fill('___NONE___');expect(page.locator('.event-focus')).to_have_count(0)
 check('real event pagination and empty selection',pager)
 def history():
  go('workspace?tab=events');page.get_by_role('tab',name='来源文章',exact=True).click();page.get_by_role('tab',name='证据问答',exact=True).click();page.go_back();expect(page.get_by_role('tab',name='来源文章',exact=True)).to_have_attribute('aria-selected','true');page.go_forward();expect(page.get_by_role('tab',name='证据问答',exact=True)).to_have_attribute('aria-selected','true')
 check('real browser back and forward tab navigation',history)
 def qa():
  go(f'workspace?tab=qa&event={eid}');page.locator('textarea').fill('发生了什么？');button('提交问题').click();expect(page.locator('.answer-text')).to_be_visible(timeout=75000);assert page.locator('.answer-text').inner_text().strip();page.screenshot(path=str(OUT/'real-qa-result.png'),full_page=True)
 check('real QA response or retrieval fallback',qa)
 def qa_fail():
  go(f'workspace?tab=qa&event={eid}');page.route('**/api/evidence/**',lambda r:r.fulfill(status=503,json={'error':'验收模拟问答故障'}));page.locator('textarea').fill('发生了什么？');button('提交问题').click();expect(page.get_by_role('alert')).to_contain_text('验收模拟问答故障');page.unroute('**/api/evidence/**');button('重试').click();expect(page.locator('.answer-text')).to_be_visible(timeout=75000)
 check('injected QA failure and real retry',qa_fail)
 def evidence_failure():
  go(f'workspace?tab=events&event={eid}');assert 'event='+eid in page.url;page.route('**/api/events/'+eid,lambda r:r.fulfill(status=503,json={'error':'验收模拟证据故障'}));button('查看依据').click();expect(page.get_by_role('alert')).to_contain_text('验收模拟证据故障');page.unroute('**/api/events/'+eid);button('重试').click();expect(page.locator('.evidence-card').first).to_be_visible()
 check('injected evidence failure and real retry',evidence_failure)
 def refresh_failure():
  go('home');count=page.locator('.home-event').count();page.route('**/api/events',lambda r:r.fulfill(status=503,json={'error':'验收模拟列表故障'}));button('刷新情报').click();expect(page.get_by_role('alert')).to_contain_text('验收模拟列表故障');assert page.locator('.home-event').count()==count;page.unroute('**/api/events');button('重试').click();expect(page.get_by_role('alert')).not_to_be_visible()
 check('injected list failure preserves content and retries',refresh_failure)
 def modal_mobile():
  page.get_by_role('button',name='点击退出登录',exact=False).click();page.set_viewport_size({'width':390,'height':844});page.get_by_role('button',name='登录或注册').click();expect(page.get_by_role('dialog')).to_be_visible();layout(False);page.screenshot(path=str(OUT/'mobile-auth.png'));button('关闭登录窗口').click();expect(page.get_by_role('dialog')).not_to_be_visible()
 check('mobile login modal close and bounds',modal_mobile)
 (OUT/'extended-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');ctx.close()
print('EXTENDED',sum(x['status']=='PASS' for x in results),'/',len(results))

raise SystemExit(0 if all(r['status']=='PASS' for r in results) else 1)
