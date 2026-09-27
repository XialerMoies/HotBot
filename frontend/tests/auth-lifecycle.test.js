import { describe,it,expect,vi,afterEach } from 'vitest';
import {mount,flushPromises} from '@vue/test-utils';
import AuthForm from '../src/components/workspace/AuthForm.vue';
import {api} from '../src/services/api.js';
vi.mock('../src/services/api.js',()=>({api:{login:vi.fn(),register:vi.fn(),setToken:vi.fn(),message:()=> '登录失败'}}));
let wrapper;afterEach(()=>{wrapper?.unmount();vi.clearAllMocks();});
describe('登录弹窗生命周期',()=>{
 it('关闭表单后迟到的登录不能保存会话',async()=>{let resolve;api.login.mockReturnValue(new Promise(r=>resolve=r));wrapper=mount(AuthForm);await wrapper.find('input[autocomplete=username]').setValue('tester');await wrapper.find('input[type=password]').setValue('password123');await wrapper.find('form').trigger('submit');wrapper.unmount();resolve({token:'late',workspace:{id:'u1'}});await flushPromises();expect(api.setToken).not.toHaveBeenCalled();});
 it('提交中不能切换登录注册或重复提交',async()=>{let resolve;api.login.mockReturnValue(new Promise(r=>resolve=r));wrapper=mount(AuthForm);await wrapper.find('input[autocomplete=username]').setValue('tester');await wrapper.find('input[type=password]').setValue('password123');await wrapper.find('form').trigger('submit');expect(wrapper.findAll('.auth-mode button').every(b=>b.attributes('disabled')!==undefined)).toBe(true);await wrapper.find('form').trigger('submit');expect(api.login).toHaveBeenCalledTimes(1);resolve({token:'ok',workspace:{id:'u1'}});await flushPromises();expect(wrapper.emitted('authenticated')[0]).toEqual([{id:'u1'}]);});
});
