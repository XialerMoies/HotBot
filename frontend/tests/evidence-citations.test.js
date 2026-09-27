import {it,expect,vi} from 'vitest';
import {mount,flushPromises} from '@vue/test-utils';
import {ref} from 'vue';
import {createRouter,createMemoryHistory} from 'vue-router';
import QaPage from '../src/pages/QaPage.vue';
import {api} from '../src/services/api.js';
vi.mock('../src/services/api.js',()=>({api:{answer:vi.fn(),message:()=> '失败'}}));
it('结论引用定位证据，标注摘要与片段位置',async()=>{
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/workspace',name:'workspace',component:{template:'<div/>'}}]});await router.push('/workspace?tab=qa&event=e1');await router.isReady();
 api.answer.mockResolvedValue({answer:'下月交付 [ev-1]',claims:[{text:'下月交付',evidenceIds:['ev-1']}],evidence:[{id:'ev-1',quote:'公司计划下月交付',title:'产品报道',kind:'SUMMARY',startOffset:0,endOffset:9,url:'https://example.org/a'}],fallback:false});
 const wrapper=mount(QaPage,{global:{plugins:[router],provide:{app:{events:{data:ref([{id:'e1',name:'产品发布',articleIds:['a']}]),loading:ref(false),error:ref('')},workspace:{data:ref({id:'u1'})},openAuth:vi.fn()}}}});
 await wrapper.find('textarea').setValue('何时交付');await wrapper.find('form').trigger('submit');await flushPromises();
 const citation=wrapper.find('button[data-evidence-id="ev-1"]');expect(citation.exists()).toBe(true);await citation.trigger('click');
 expect(wrapper.find('.evidence-item').classes()).toContain('evidence-selected');
 expect(wrapper.find('.evidence-item').text()).toContain('来源摘要');
 expect(wrapper.find('.evidence-item').text()).toContain('0–9');
 expect(wrapper.find('.evidence-item a').attributes('href')).toBe('https://example.org/a');wrapper.unmount();
});
