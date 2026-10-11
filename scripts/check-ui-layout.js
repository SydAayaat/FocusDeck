#!/usr/bin/env node
'use strict';
// Focused source-runtime regressions, no browser/device connection. Not a render test.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.resolve(__dirname,'../desktop/src/focusdeck/control_ui');
const listeners={},nodes=[];
const context=vm.createContext({console,innerWidth:1600,innerHeight:1000,ResizeObserver:class{},requestAnimationFrame:()=>{},
 document:{addEventListener:(type,fn)=>{(listeners[type]||=[]).push(fn)},querySelector:()=>null},
 window:{addEventListener:()=>{}},crypto:{randomUUID:()=> 'test'},
 model:{layouts:{},status:{connected:false},settings:{motion:'off'}},page:'history',historyPeriod:'week',
 $:()=>null,$$:()=>nodes,clone:v=>JSON.parse(JSON.stringify(v)),escape:s=>String(s??''),
 btn:()=>'',icon:n=>`<i data-icon="${n}"></i>`,numberLabel:v=>String(v),duration:v=>String(v),
 api:async(a,v)=>v,toast:()=>{},render:()=>{},age:()=>'',date:()=>'',});
vm.runInContext(fs.readFileSync(path.join(root,'analytics.js'),'utf8'),context);
vm.runInContext(fs.readFileSync(path.join(root,'layout.js'),'utf8'),context);
let checks=0;
function check(expr,message){assert.ok(vm.runInContext(expr,context),message);checks++;}
function card(id,classes,width=1100,defaultSpan=4){
 const parent={clientWidth:width,children:[],classList:{contains:()=>false}},style={removeProperty(k){delete this[k]}},set=new Set(classes);
 const n={dataset:{card:id,defaultSpan:String(defaultSpan)},parentElement:parent,style,
 classList:{contains:k=>set.has(k),add:k=>set.add(k),remove:k=>set.delete(k)},closest:s=>s==='.analytics-grid'&&classes.includes('dashboard-card')?parent:null,
 getBoundingClientRect:()=>({height:180,width:width*Number(n.dataset.effectiveSpan||defaultSpan)/12,left:0,top:0,right:width,bottom:180})};parent.children.push(n);return n;
}
context.card=card('focus',['dashboard-card']);
check("(applyCardLayout(card),card.style.gridColumn==='span 4'&&!card.classList.contains('sized-card'))",'Default focus chart uses one third width, natural height');
context.card=card('focus',['dashboard-card'],430);
check("(applyCardLayout(card),card.style.gridColumn==='span 12')",'Compact focus chart fits viewport');
context.card=card('catalog',['monitor-catalog'],1280,7);
check("(applyCardLayout(card),card.style.gridColumn==='span 7')",'Approved catalog/detail proportions');
context.card=card('detail-fact',['detail-fact'],400,4);
check("(applyCardLayout(card),card.style.gridColumn==='span 4')",'Three detail facts per row');
context.card=card('achievement-demo',['monitor-card'],720,4);
check("(applyCardLayout(card),card.style.gridColumn==='span 4')",'Three achievement cards per row');
check("(pageLayout().cards[card.dataset.card]={span:6,height:312},applyCardLayout(card),card.style.height==='312px')",'Existing explicit heights survive');
check("(pageLayout().cards[card.dataset.card]={span:6,height:312,heightSized:false},applyCardLayout(card),!card.style.height)",'Width-only settings retain natural height');
context.parent={children:[{dataset:{}},{dataset:{card:'hero'}},{dataset:{}},{dataset:{card:'progress'}}]};context.parent.children.forEach(n=>n.parentElement=context.parent);
check("cardLayoutPosition(parent.children[1],[])===1&&cardLayoutPosition(parent.children[3],[])===3",'Fixed header/sections retain slots');
check("cardLayoutPosition(parent.children[1],['progress','hero'])===3&&cardLayoutPosition(parent.children[3],['progress','hero'])===1",'Reorder retains fixed section slots');
context.card=card('kpi-0',['summary-metric'],1200,3);context.card.parentElement.children=Array.from({length:7},(_,i)=>i?{dataset:{card:'kpi-'+i}}:context.card);
check("(applyCardLayout(card),Math.abs(parseFloat(card.style.flexBasis)-(1212/7-12))<.01)",'Seven metrics fit one summary strip');
check("(calendarKpis({focus:0,sessions:0,done:0,breaks:0,tracked:0,punctuality:null,comparisons:0}).match(/class=\"stat\"/g)||[]).length===7",'One panel contains seven real KPI values');
check("lineBars(Array.from({length:24},(_,i)=>({label:String(i),value:null}))).includes('min-width:0px')",'24-hour chart fits its card');
check("lineBars([{label:'Missing',value:null},{label:'Zero',value:0}]).includes('N/A · no record')",'Missing timing remains distinct from zero');
// Real handler path: horizontal resize must not enable a height lock; vertical must.
(async()=>{
 context.card=card('gesture',['dashboard-card'],1200,12);nodes.splice(0,nodes.length,context.card);
 const handle={dataset:{resize:'x'},closest:()=>context.card,setPointerCapture:()=>{}};
 context.handle=handle;context.layoutEditing=true;vm.runInContext('layoutEditing=true;pageLayout().cards={};applyCardLayout(card)',context);
 const event=(x,y)=>({target:{closest:()=>handle},clientX:x,clientY:y,pointerId:42,preventDefault:()=>{},stopPropagation:()=>{}});
 for(const fn of listeners.pointerdown)fn(event(0,0));for(const fn of listeners.pointermove)fn(event(-300,0));
 check("pageLayout().cards.gesture.heightSized===false&&pageLayout().cards.gesture.span===9",'Horizontal gesture changes only width');
 await vm.runInContext('finishLayout()',context);handle.dataset.resize='y';
 for(const fn of listeners.pointerdown)fn(event(0,0));for(const fn of listeners.pointermove)fn(event(0,96));
 check("pageLayout().cards.gesture.heightSized===true&&pageLayout().cards.gesture.span===9",'Vertical gesture locks height independently');
 await vm.runInContext('finishLayout(true)',context);
 check("pageLayout().cards.gesture.heightSized===false",'Cancelled gesture restores previous dimensions');
 console.log(`${checks} focused JavaScript layout checks passed (no renderer).`);
})().catch(e=>{console.error(e);process.exitCode=1});
