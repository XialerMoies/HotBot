import { describe,it,expect,vi,afterEach } from 'vitest';
import { ref } from 'vue';
import { mount,flushPromises } from '@vue/test-utils';
import { createRouter,createMemoryHistory } from 'vue-router';
import EventHomeCard from '../src/components/events/EventHomeCard.vue';
import {api} from '../src/services/api.js';
vi.mock('../src/services/api.js',()=>({api:{eventDetail:vi.fn(),follow:vi.fn(),message:()=> '请求失败'}}));
const event={id:'e1',name:'模型测试开放',status:'ONGOING',subjects:[{name:'测试公司',type:'COMPANY',role:'PRIMARY'}],articleIds:['a1'],timeline:[{articleId:'a1',summary:'开放测试',occurredAt:'2026-09-27T00:00:00Z'}]};
let wrapper;
afterEach(()=>{wrapper?.unmount();vi.clearAllMocks();});
async function setup(value=null){const workspace={data:ref(value)},openAuth=vi.fn();const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div/>'}},{path:'/workspace',name:'workspace',component:{template:'<div/>'}}]});await router.push('/');await router.isReady();wrapper=mount(EventHomeCard,{props:{event},global:{plugins:[router],provide:{app:{workspace,openAuth}}}});return {workspace,openAuth,router};}
function button(label){return wrapper.findAll('button').find(b=>b.text().includes(label));}
describe('事件首页真实操作',()=>{
 it('游客的证据、时间节点、关注和问答入口均请求登录，不调用接口',async()=>{const {openAuth}=await setup();for(const label of ['查看依据','关注主体','基于证据提问','开放测试']){await button(label).trigger('click');}expect(openAuth).toHaveBeenCalledTimes(4);expect(api.eventDetail).not.toHaveBeenCalled();expect(api.follow).not.toHaveBeenCalled();});
 it('登录后关注保存到工作区，重复关注禁用',async()=>{const {workspace}=await setup({id:'u1',follows:[]});api.follow.mockResolvedValue({id:'u1',follows:[{value:'测试公司',type:'company'}]});await button('关注主体').trigger('click');await flushPromises();expect(api.follow).toHaveBeenCalledWith({value:'测试公司',type:'company'});expect(workspace.data.value.follows).toHaveLength(1);expect(button('已关注').attributes('disabled')).toBeDefined();});
 it('退出后丢弃尚未返回的证据请求',async()=>{const {workspace}=await setup({id:'u1',follows:[]});let resolve;api.eventDetail.mockReturnValue(new Promise(r=>resolve=r));await button('查看依据').trigger('click');workspace.data.value=null;await flushPromises();resolve({articles:[{id:'a1',content:'不应显示的原文',url:'https://example.com'}]});await flushPromises();expect(wrapper.find('.he-evidence').exists()).toBe(false);expect(wrapper.text()).not.toContain('不应显示的原文');});
 it('收起仍保留事件标题并更新展开状态',async()=>{await setup();await wrapper.find('.he-head').trigger('click');expect(wrapper.find('.he-head').attributes('aria-expanded')).toBe('false');expect(wrapper.find('.he-title').text()).toBe(event.name);});
});
