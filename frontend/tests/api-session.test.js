import {afterEach,describe,expect,it,vi} from 'vitest';
const handlers=vi.hoisted(()=>({}));
vi.mock('axios',()=>({default:{create:()=>({interceptors:{response:{use:(_ok,error)=>{handlers.error=error;}}}})}}));
import '../src/services/api.js';
afterEach(()=>{localStorage.clear();vi.restoreAllMocks();});
describe('会话失效处理',()=>{
 it('当前认证请求失效清除令牌并发出事件',async()=>{localStorage.setItem('hotbot-token','current');const dispatch=vi.spyOn(window,'dispatchEvent');const error={config:{headers:{'X-Workspace-Token':'current'}},response:{status:400,data:{error:'invalid token'}}};await expect(handlers.error(error)).rejects.toBe(error);expect(localStorage.getItem('hotbot-token')).toBeNull();expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({type:'hotbot:session-expired'}));});
 it('旧会话迟到的401不能注销新账户',async()=>{localStorage.setItem('hotbot-token','new');const dispatch=vi.spyOn(window,'dispatchEvent');const error={config:{headers:{'X-Workspace-Token':'old'}},response:{status:401}};await expect(handlers.error(error)).rejects.toBe(error);expect(localStorage.getItem('hotbot-token')).toBe('new');expect(dispatch).not.toHaveBeenCalled();});
 it('登录失败不能误清除已有会话',async()=>{localStorage.setItem('hotbot-token','current');const error={config:{headers:{}},response:{status:401,data:{error:'invalid credentials'}}};await expect(handlers.error(error)).rejects.toBe(error);expect(localStorage.getItem('hotbot-token')).toBe('current');});
});
