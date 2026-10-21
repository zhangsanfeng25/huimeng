import { useState } from 'react';
import { api } from '../api';
import {Link} from 'react-router-dom';
export function OrderActions({detail,onChanged}:{detail:Record<string,unknown>;onChanged:()=>void}){
  const [note,setNote]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const state=String(detail.logistics_status),action=detail.status==='PENDING_PAYMENT'?'cancel':detail.status==='PAID'?'ship':state==='SHIPPED'?'transit':state==='IN_TRANSIT'?'deliver':state==='OUT_FOR_DELIVERY'?'complete':'';
  const labels:Record<string,string>={cancel:'取消未付款订单',ship:'确认发货',transit:'更新为运输中',deliver:'更新为派送中',complete:'确认完成'};
  const submit=async()=>{if(!note.trim()){setError('请填写处理说明');return;}if(!window.confirm(`确认${labels[action]}？${action==='cancel'?'同次结算的未付款订单将一并取消并释放预留库存。':''}`))return;setBusy(true);try{await api(`/orders/${encodeURIComponent(String(detail.id))}/manage`,{method:'PUT',body:JSON.stringify({action,note,version:Number(detail.version)})});setNote('');setError('');onChanged();}catch(e){setError((e as Error).message);}finally{setBusy(false);}};
  if(detail.after_sale_id){const blocking=!['REJECTED','CANCELLED'].includes(String(detail.after_sale_state));return <section className="order-actions"><h3>关联售后</h3><p>{detail.after_sale_state==='COMPLETED'?`已登记演示退款 ¥${(Number(detail.refunded_cents||0)/100).toFixed(2)}`:blocking?'此订单存在进行中的售后，履约操作暂停。':'售后已驳回或撤销，可核对后继续履约。'}</p><Link to={`/after-sales?record=${encodeURIComponent(String(detail.after_sale_id))}`}>查看售后处理记录</Link>{!blocking&&action?<><label>处理说明<textarea maxLength={500} value={note} onChange={e=>setNote(e.target.value)}/></label><button className="secondary" disabled={busy} onClick={submit}>{labels[action]}</button></>:null}{error?<p role="alert">{error}</p>:null}</section>;}
  return <section className="order-actions"><h3>订单处理</h3>{action?<><label>处理说明<textarea aria-label="订单处理说明" maxLength={500} value={note} onChange={e=>setNote(e.target.value)}/></label><button className="secondary" disabled={busy} onClick={submit}>{busy?'处理中…':labels[action]}</button></>:<p className="muted">此订单已结束，无可执行操作。</p>}{error?<p role="alert" className="page-error">{error}</p>:null}</section>;
}
