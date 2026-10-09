import { useEffect, useState } from 'react';
import { api } from '../api';
import { StatusTag } from './Ui';

type Application={id:string;shopName:string;contactName:string;contactPhone:string;businessAddress:string;description:string;status:string;businessStatus:string;rejectionReason:string;appliedAt:string;reviewedAt:string|null;updatedAt:string};
export function MerchantApplicationReview({detail,onChanged,onBusyChange}:{detail:Record<string,unknown>;onChanged:()=>void;onBusyChange:(busy:boolean)=>void}){
  const application=detail.application as Application|undefined;
  const [note,setNote]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  useEffect(()=>{setNote(application?.rejectionReason||'');setError('');},[application?.updatedAt]);
  if(!application)return null;
  const review=async(approve:boolean)=>{
    if(busy)return;if(!approve&&!note.trim()){setError('请填写驳回原因，申请人可根据原因修改后重新提交。');return;}
    setBusy(true);onBusyChange(true);setError('');
    try{await api(`/merchants/${encodeURIComponent(application.id)}`,{method:'PUT',body:JSON.stringify({shop:application.shopName,approval:approve?'已通过':'已驳回',status:approve?'营业中':'待审核',note:note.trim(),expectedUpdatedAt:application.updatedAt})});onChanged();}
    catch(reason){setError((reason as Error).message);}finally{setBusy(false);onBusyChange(false);}
  };
  const pending=application.status==='PENDING';
  return <section className="product-details merchant-application"><h3>商家申请资料</h3><StatusTag value={{PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回'}[application.status]||application.status}/><dl className="detail-list">
    {Object.entries({'店铺名称':application.shopName,'联系人':application.contactName,'联系电话':application.contactPhone,'经营地址':application.businessAddress,'店铺介绍':application.description,'申请时间':application.appliedAt?new Date(application.appliedAt).toLocaleString('zh-CN'):'','审核时间':application.reviewedAt?new Date(application.reviewedAt).toLocaleString('zh-CN'):'','驳回原因':application.rejectionReason}).map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value||'—'}</dd></div>)}
  </dl>{pending?<div className="review-actions"><p className="muted">通过后立即为当前用户开放商家身份，App 无需重新登录。</p><label>审核说明 / 驳回原因<textarea maxLength={1000} value={note} onChange={e=>setNote(e.target.value)} disabled={busy} placeholder="驳回时必填，请说明需要补充或修改的资料"/></label>{error?<p role="alert" className="page-error">{error}</p>:null}<div className="row-actions"><button className="primary" disabled={busy} onClick={()=>review(true)}>{busy?'处理中…':'同意申请并开通商家'}</button><button className="danger-button" disabled={busy} onClick={()=>review(false)}>驳回申请</button></div></div>:<p className="muted">{application.status==='APPROVED'?'申请已通过，经营状态可在商家管理中维护。':'申请人可以根据驳回原因修改并重新提交。'}</p>}</section>;
}
