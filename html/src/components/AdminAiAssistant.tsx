import {useEffect, useRef, useState, type CSSProperties, type FormEvent, type PointerEvent} from 'react';
import {Bot, LoaderCircle, Send, X} from 'lucide-react';
import {Link} from 'react-router-dom';
import {api} from '../api';
import './admin-ai-assistant.css';

type Product = {id:string; name:string; priceCents:number; coverUrl:string|null; reason:string};
type Turn = {role:'user'|'assistant'; text:string; products?:Product[]; failed?:boolean};
type Availability = {config:{enabled:boolean}; mode:'demo'|'live'|'unconnected'};
type Placement = {edge:'left'|'right'; height:number};
const placementKey='huimeng:admin-ai-position';
const margin=12, buttonSize=52;
const clamp=(value:number,min:number,max:number)=>Math.min(Math.max(value,min),max);

function readPlacement():Placement {
  try {
    const value=JSON.parse(localStorage.getItem(placementKey)??'null');
    if((value?.edge==='left'||value?.edge==='right')&&Number.isFinite(value.height)){
      return {edge:value.edge,height:clamp(value.height,0,1)};
    }
  } catch { /* local storage is optional */ }
  return {edge:'right',height:1};
}

export function AdminAiAssistant() {
  const [open,setOpen]=useState(false);
  const [placement,setPlacement]=useState(readPlacement);
  const [dragPosition,setDragPosition]=useState<{x:number;y:number}>();
  const [availability,setAvailability]=useState<Availability>();
  const [configError,setConfigError]=useState('');
  const [input,setInput]=useState('');
  const [messages,setMessages]=useState<Turn[]>([]);
  const [chatError,setChatError]=useState('');
  const [waiting,setWaiting]=useState(false);
  const conversation=useRef<HTMLDivElement>(null);
  const lastUserMessage=useRef<HTMLDivElement>(null);
  const drag=useRef<{id:number;x:number;y:number;left:number;top:number;point?:{x:number;y:number}}>(null);
  const suppressClick=useRef(false);

  const trigger=useRef<HTMLButtonElement>(null);
  const closeButton=useRef<HTMLButtonElement>(null);
  const available=Boolean(availability?.config.enabled&&availability.mode!=='unconnected');

  useEffect(()=>{
    if(!open)return;
    const controller=new AbortController();
    const refresh=()=>{
      setConfigError('');
      api<Availability>('/admin/ai/config',{signal:controller.signal}).then(setAvailability).catch(error=>{
        if(!controller.signal.aborted){setAvailability(undefined);setConfigError(error.message);}
      });
    };
    refresh();
    window.addEventListener('huimeng:ai-config-saved',refresh);
    closeButton.current?.focus();
    return ()=>{
      controller.abort();
      window.removeEventListener('huimeng:ai-config-saved',refresh);
    };
  },[open]);

  useEffect(()=>{
    const log=conversation.current;
    const latest=lastUserMessage.current;
    if(log&&latest)log.scrollTop+=latest.getBoundingClientRect().top-log.getBoundingClientRect().top-12;
  },[messages,waiting,open]);

  function close(){
    setOpen(false);
    trigger.current?.focus();
  }

  function beginDrag(event:PointerEvent<HTMLButtonElement>){
    if(!event.isPrimary||event.button!==0)return;
    const bounds=event.currentTarget.getBoundingClientRect();
    suppressClick.current=false;
    drag.current={id:event.pointerId,x:event.clientX,y:event.clientY,left:bounds.left,top:bounds.top};
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function dragPoint(event:PointerEvent<HTMLButtonElement>,start:NonNullable<typeof drag.current>){
    return {
      x:clamp(start.left+event.clientX-start.x,margin,Math.max(margin,window.innerWidth-buttonSize-margin)),
      y:clamp(start.top+event.clientY-start.y,margin,Math.max(margin,window.innerHeight-buttonSize-margin)),
    };
  }

  function moveDrag(event:PointerEvent<HTMLButtonElement>){
    const start=drag.current;
    if(!start||start.id!==event.pointerId)return;
    if(!start.point&&Math.hypot(event.clientX-start.x,event.clientY-start.y)<6)return;
    start.point=dragPoint(event,start);
    setDragPosition(start.point);
  }

  function endDrag(event:PointerEvent<HTMLButtonElement>,cancelled=false){
    const start=drag.current;
    if(!start||start.id!==event.pointerId)return;
    drag.current=null;
    setDragPosition(undefined);
    if(!start.point)return;
    suppressClick.current=true;
    if(cancelled)return;
    const point=start.point;
    const next:Placement={
      edge:point.x+buttonSize/2<window.innerWidth/2?'left':'right',
      height:clamp((point.y-margin)/Math.max(1,window.innerHeight-buttonSize-2*margin),0,1),
    };
    setPlacement(next);
    try {localStorage.setItem(placementKey,JSON.stringify(next));} catch { /* local storage is optional */ }
  }

  async function send(event:FormEvent) {
    event.preventDefault();
    const text=input.trim();
    if(!text||waiting||!available)return;
    setInput('');
    setChatError('');
    setWaiting(true);
    setMessages(previous=>[...previous,{role:'user',text}]);
    try {
      const result=await api<{text:string; products:Product[]}>('/ai/chat',{
        method:'POST',
        body:JSON.stringify({message:text,history:messages.filter(turn=>!turn.failed).slice(-12)
          .map(({role,text})=>({role,text:text.slice(0,2000)}))}),
      });
      setMessages(previous=>[...previous,{role:'assistant',text:result.text,products:result.products}]);
    } catch(error) {
      setMessages(previous=>previous.map((turn,index)=>index===previous.length-1?{...turn,failed:true}:turn));
      setInput(text);
      setChatError((error as Error).message);
    } finally {
      setWaiting(false);
    }
  }

  const top=`calc(${placement.height*100}dvh + ${margin-placement.height*(2*margin+buttonSize)}px)`;
  const positionStyle={
    '--ai-ball-top':top,
    ...(dragPosition?{top:dragPosition.y,left:dragPosition.x,right:'auto'}:{}),
  } as CSSProperties;
  const lastUser=messages.length-(messages.at(-1)?.role==='user'?1:2);
  return <div className={`admin-ai admin-ai-${placement.edge} ${placement.height>=.5?'admin-ai-above':'admin-ai-below'}${dragPosition?' is-dragging':''}`} style={positionStyle}>
    <button ref={trigger} type="button" className="primary admin-ai-trigger" aria-label={open?'收起 AI 助手':'打开 AI 助手'}
      aria-expanded={open} aria-controls={open?'admin-ai-dialog':undefined}
      onPointerDown={beginDrag} onPointerMove={moveDrag} onPointerUp={event=>endDrag(event)}
      onPointerCancel={event=>endDrag(event,true)} onLostPointerCapture={event=>endDrag(event,true)}
      onClick={event=>{if(event.detail&&suppressClick.current)return;open?close():setOpen(true);}}>
      <Bot size={24} aria-hidden="true"/>
    </button>
    {open&&<section id="admin-ai-dialog" className="panel admin-ai-dialog" role="dialog" aria-labelledby="admin-ai-title"
      onKeyDown={event=>{if(event.key==='Escape'){event.stopPropagation();close();}}}>
      <header className="admin-ai-header">
        <div><h2 id="admin-ai-title">AI 运营助手</h2>{availability?.mode==='demo'&&<p>模拟联调</p>}</div>
        <button ref={closeButton} type="button" className="icon-button" aria-label="关闭对话" onClick={close}><X size={19}/></button>
      </header>
      {configError?<p className="page-error" role="alert">{configError}</p>:availability&&!available&&
        <p className="data-note">AI 助手当前不可用，请检查<Link to="/ai" onClick={close}>AI 配置</Link>。</p>}
        <div className="ai-conversation" ref={conversation} role="log" aria-label="AI 对话">
          {!messages.length&&<p className="data-note">直接提问，例如「最近 30 天订单有什么变化？」</p>}
          {messages.map((message,index)=><div className={`ai-message ${message.role}`} key={index} ref={index===lastUser?lastUserMessage:undefined}>
            <p>{message.text}</p>
            {message.failed&&<small>发送失败</small>}
            {message.products?.map(product=><div className="ai-product" key={product.id}>
              {product.coverUrl&&<img src={product.coverUrl} alt="" width="38" height="52" loading="lazy"/>}
              <div><strong>{product.name} · ¥{(product.priceCents/100).toFixed(2)}</strong><p>{product.reason}</p></div>
            </div>)}
          </div>)}
          {waiting&&<p className="ai-waiting" role="status"><LoaderCircle className="ai-spin" size={16}/>正在生成答复…</p>}
        </div>
        {chatError&&<p className="page-error" role="alert">{chatError}</p>}
        <form className="ai-compose form-grid" onSubmit={send}>
          <input aria-label="输入消息" maxLength={2000} value={input} readOnly={waiting} onChange={event=>setInput(event.target.value)} placeholder="输入消息"/>
          <button className="primary" disabled={!input.trim()||waiting||!available}><Send size={16}/>发送</button>
        </form>
    </section>}
  </div>;
}
