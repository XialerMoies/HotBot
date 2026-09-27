import { afterEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import QaPage from '../src/pages/QaPage.vue';
import EventsPage from '../src/pages/EventsPage.vue';
import WorkspaceHubPage from '../src/pages/WorkspaceHubPage.vue';
import { api } from '../src/services/api.js';
vi.mock('../src/services/api.js',()=>({api:{answer:vi.fn(),eventDetail:vi.fn(),evidenceSources:vi.fn(),message:()=> '请求失败'}}));
const wrappers=[];
const resource=data=>({data:ref(data),loading:ref(false),error:ref(''),updatedAt:ref(null),load:vi.fn(),reset:vi.fn()});
const events=Array.from({length:28},(_,i)=>({id:'e'+i,name:'事件 '+i,subjects:[],timeline:[],articleIds:[]}));
async function setup(component,logged=false,path='/workspace?tab=events&event=e0',stubs={}){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/workspace',name:'workspace',component:{template:'<div />'}}]});
 await router.push(path);await router.isReady();
 const app={events:resource(events),workspace:resource(logged?{id:'u1'}:null),openAuth:vi.fn()};
 const wrapper=mount(component,{global:{plugins:[router],provide:{app},stubs}});wrappers.push(wrapper);await flushPromises();return {wrapper,app,router};
}
const click=async(w,text)=>{const b=w.findAll('button').find(b=>b.text().includes(text));expect(b,`button ${text}`).toBeTruthy();await b.trigger('click');await flushPromises();};
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.clearAllMocks();});
describe('前端收尾回归',()=>{
 it('直达非第一页事件时保留目标并定位所在页',async()=>{const {wrapper,router}=await setup(EventsPage,false,'/workspace?tab=events&event=e20');expect(router.currentRoute.value.query.event).toBe('e20');expect(wrapper.find('.rail-event.selected').text()).toContain('事件 20');expect(wrapper.find('.pagination').text()).toContain('第 2 / 2 页');});
 it('游客直接打开问答仍需登录',async()=>{const {wrapper,app}=await setup(QaPage,false,'/workspace?tab=qa&event=e0');expect(wrapper.find('textarea').exists()).toBe(false);await click(wrapper,'登录');expect(app.openAuth).toHaveBeenCalled();expect(api.answer).not.toHaveBeenCalled();});
 it('退出登录时取消问答并丢弃迟到回答',async()=>{let resolve;api.answer.mockReturnValue(new Promise(r=>resolve=r));const {wrapper,app}=await setup(QaPage,true,'/workspace?tab=qa&event=e0');await wrapper.find('textarea').setValue('发生了什么');await wrapper.find('form').trigger('submit');const signal=api.answer.mock.calls[0][2];app.workspace.data.value=null;await flushPromises();expect(signal.aborted).toBe(true);resolve({answer:'旧账户回答',evidence:[]});await flushPromises();expect(wrapper.text()).not.toContain('旧账户回答');expect(wrapper.find('textarea').exists()).toBe(false);});
 it('游客工作台不请求详情，证据入口打开登录框',async()=>{api.eventDetail.mockResolvedValue({event:events[0],articles:[{id:'a',title:'私有入口证据'}]});const {wrapper,app}=await setup(EventsPage);expect(api.eventDetail).not.toHaveBeenCalled();expect(wrapper.text()).not.toContain('私有入口证据');await click(wrapper,'登录查看证据');expect(app.openAuth).toHaveBeenCalled();});
 it('工作台分页按14条计算并切换到新页事件；无结果不残留详情',async()=>{const {wrapper,router}=await setup(EventsPage);expect(wrapper.find('.pagination').text()).toContain('第 1 / 2 页');await click(wrapper,'下一页');expect(wrapper.find('.pagination').text()).toContain('第 2 / 2 页');expect(router.currentRoute.value.query.event).toBe('e14');await wrapper.find('input[type=search]').setValue('找不到的事件');await flushPromises();expect(wrapper.find('.event-focus').exists()).toBe(false);});
 it('已登录详情失败可重试；退出清除原文',async()=>{api.evidenceSources.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([{id:'a',title:'已获取的证据'}]);const {wrapper,app}=await setup(EventsPage,true);await click(wrapper,'查看依据');expect(wrapper.text()).toContain('请求失败');await click(wrapper,'重试');expect(wrapper.text()).toContain('已获取的证据');app.workspace.data.value=null;await flushPromises();expect(wrapper.text()).not.toContain('已获取的证据');});
 it('工作台标签与浏览器返回保持一致',async()=>{const {wrapper,router}=await setup(WorkspaceHubPage,false,'/workspace?tab=qa',{DashboardPage:true,EventsPage:true,QaPage:true,ArticlesPage:true});await router.push('/workspace?tab=events');await flushPromises();expect(wrapper.find('[role=tab][data-state=active]').text()).toBe('事件追踪');await router.push('/workspace');await flushPromises();expect(wrapper.find('[role=tab][data-state=active]').text()).toBe('事件追踪');});
});
