import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import EventDetail from '../src/components/events/EventDetail.vue';
import EventWorkbenchDetail from '../src/components/events/EventWorkbenchDetail.vue';

describe('事件主体与多源时间线', () => {
  it('点击时间节点定位来源，未登录时触发证据登录入口', async () => {
    const event={id:'e1',name:'发布',status:'ONGOING',timeline:[{articleId:'a',summary:'发布',stage:'FIRST_REPORT'}]};
    const wrapper=mount(EventWorkbenchDetail,{props:{event,articles:[{id:'a',title:'原始报道',source:'官方'}],authenticated:true,evidenceRequested:true}});
    await wrapper.get('.timeline-source').trigger('click');
    expect(wrapper.get('.evidence-card').classes()).toContain('evidence-selected');
    await wrapper.setProps({authenticated:false,evidenceRequested:false});
    await wrapper.get('.timeline-source').trigger('click');
    expect(wrapper.emitted('evidence')).toHaveLength(1);
    wrapper.unmount();
  });
  it('实际工作台显示真实来源数量，不把规则权重显示成准确率', () => {
    const event={name:'发布',status:'ONGOING',articleIds:['a','b'],subjects:[{name:'NVIDIA',type:'COMPANY',role:'PRIMARY',confidence:.9}],timeline:[{articleId:'a',summary:'发布',stage:'FIRST_REPORT'}],confidence:.7};
    const wrapper=mount(EventWorkbenchDetail,{props:{event,articles:[{id:'a',source:'甲科技'},{id:'b',source:'乙科技'}],authenticated:true,evidenceRequested:true}});
    expect(wrapper.find('.event-facts').text()).toContain('2 个来源');
    expect(wrapper.find('.workbench-timeline').text()).toContain('甲科技');
    expect(wrapper.text()).not.toContain('置信度');
    expect(wrapper.find('.workbench-subjects').text()).not.toContain('90%');
    wrapper.unmount();
  });
  it('主体类型使用中文，展示来源数并将时间节点关联到报道来源', () => {
    const event = {name:'芯片发布',status:'ONGOING',articleIds:['a','b','c'],entities:['NVIDIA'],
      subjects:[{name:'NVIDIA',type:'COMPANY',role:'PRIMARY',confidence:.9},{name:'Jensen Huang',type:'PERSON',role:'RELATED'}, {name:'B200',type:'PRODUCT',role:'RELATED'}],
      timeline:[{articleId:'a',occurredAt:'2026-09-20T00:00:00Z',stage:'FIRST_REPORT',summary:'发布'}, {articleId:'b',occurredAt:'2026-09-21T00:00:00Z',stage:'UPDATE',summary:'交付'}]};
    const articles = [{id:'a',title:'发布报道',source:'甲科技',url:'https://a.example.org/1'}, {id:'b',title:'交付报道',source:'乙科技',url:'https://b.example.org/2'}, {id:'c',title:'后续',source:'乙科技',url:'https://b.example.org/3'}];
    const wrapper = mount(EventDetail,{props:{event,articles}});
    expect(wrapper.find('.event-subject-hero').text()).toContain('公司');
    expect(wrapper.find('.subject-list').text()).toContain('人物');
    expect(wrapper.find('.subject-list').text()).toContain('产品');
    expect(wrapper.find('.event-metadata').text()).toContain('2 个来源');
    expect(wrapper.find('.event-timeline').text()).toContain('甲科技');
    expect(wrapper.find('.event-timeline').text()).toContain('乙科技');
    wrapper.unmount();
  });
});
