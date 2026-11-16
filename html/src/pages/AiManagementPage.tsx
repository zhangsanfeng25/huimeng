import {useEffect, useState} from 'react';
import {LoaderCircle, Plug, Save} from 'lucide-react';
import {api} from '../api';
import {PageHeader, StatusTag} from '../components/Ui';
import './ai-management.css';

type Mode = 'demo' | 'live' | 'unconnected';
type Settings = {endpoint:string; model:string; enabled:boolean; keyConfigured:boolean};
type Data = {config:Settings; mode:Mode};

const modes:Record<Mode,string> = {demo:'本地对话',live:'OpenAI 兼容接口',unconnected:'尚未接入服务商'};

export function AiManagementPage() {
  const [data,setData]=useState<Data>();
  const [settings,setSettings]=useState<Settings>();
  const [key,setKey]=useState('');
  const [configBusy,setConfigBusy]=useState<'save'|'test'|'toggle'>();
  const [feedback,setFeedback]=useState<{text:string; error?:boolean; saved?:boolean}>();

  useEffect(()=>{
    const request=new AbortController();
    api<Data>('/admin/ai/config',{signal:request.signal}).then(result=>{
      setData(result);
      setSettings(result.config);
    }).catch(error=>{
      if(!request.signal.aborted)setFeedback({text:error.message,error:true});
    });
    return ()=>request.abort();
  },[]);

  async function persist(fields:Settings, apiKey:string, action:'save'|'toggle') {
    if(configBusy)return;
    setConfigBusy(action);
    setFeedback(undefined);
    try {
      const {endpoint,model,enabled}=fields;
      const result=await api<Data & {verified:boolean}>('/admin/ai/config',{
        method:'PUT',body:JSON.stringify({endpoint,model,enabled,apiKey}),
      });
      if(action==='save'){
        setSettings(result.config);
        setKey('');
      } else {
        setSettings(current=>current?{...current,enabled:result.config.enabled}:current);
      }
      setData(result);
      window.dispatchEvent(new Event('huimeng:ai-config-saved'));
      setFeedback({text:action==='toggle'?(result.config.enabled?'AI 已启用':'AI 已停用'):result.verified?'已保存并启用':'配置已保存',saved:action==='toggle'});
    } catch(error) {
      setFeedback({text:(error as Error).message,error:true,saved:action==='toggle'});
    } finally {
      setConfigBusy(undefined);
    }
  }

  async function testConnection() {
    if(!settings||configBusy)return;
    setConfigBusy('test');
    setFeedback(undefined);
    try {
      const {endpoint,model}=settings;
      const result=await api<{message:string}>('/admin/ai/connection-test',{
        method:'POST',body:JSON.stringify({endpoint,model,apiKey:key}),
      });
      setFeedback({text:result.message});
    } catch(error) {
      setFeedback({text:(error as Error).message,error:true});
    } finally {
      setConfigBusy(undefined);
    }
  }

  const notice=feedback&&<p className={feedback.error?'page-error':'ai-feedback'} role={feedback.error?'alert':'status'}>{feedback.text}</p>;
  return <>
    <PageHeader title="AI 管理"/>
    {!settings&&notice}
    {settings&&data ? <div className="settings settings-redesign ai-management">
      <section className="panel settings-section">
        <header><span><Plug size={19}/></span><div><h2>API 配置</h2><p>{modes[data.mode]}</p></div></header>
        <form className="ai-config" onSubmit={event=>{
          event.preventDefault();
          void persist(settings,key,'save');
        }}>
          <div className="form-grid">
            <label>HTTPS 接口地址<input type="url" value={settings.endpoint} maxLength={500} placeholder="https://…" disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,endpoint:event.target.value})}/></label>
            <label>模型名称<input value={settings.model} maxLength={120} disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,model:event.target.value})}/></label>
            <label>API Key<input type="password" autoComplete="new-password" value={key} maxLength={4096} disabled={Boolean(configBusy)}
              placeholder={settings.keyConfigured&&settings.endpoint.trim()===data.config.endpoint?'已保存，留空保留当前密钥':'在这里填写服务商密钥'} onChange={event=>setKey(event.target.value)}/></label>
          </div>
          {data.mode==='live'&&<p className="data-note ai-endpoint-help">支持基础地址或完整的 /chat/completions 地址。</p>}
          {data.mode==='demo'&&<p className="data-note ai-endpoint-help">当前未连接外部服务商。</p>}
          <div className="ai-config-footer">
          <label className="ai-switch">
            <input type="checkbox" role="switch" checked={settings.enabled} disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,enabled:event.target.checked})}/>
            <span className="ai-switch-track" aria-hidden="true"/>
            <span>保存后启用</span>
          </label>
          <div className="ai-actions">
            <button className="primary" disabled={Boolean(configBusy)}>{configBusy==='save'?<LoaderCircle className="ai-spin" size={17}/>:<Save size={17}/>}
              {configBusy==='save'?(settings.enabled&&data.mode==='live'?'正在验证连接…':'正在保存…'):'保存配置'}
            </button>
            <button className="secondary" type="button" disabled={Boolean(configBusy)} onClick={()=>void testConnection()}>
              {configBusy==='test'?<LoaderCircle className="ai-spin" size={17}/>:<Plug size={17}/>}
              {configBusy==='test'?'正在测试…':'测试连接'}
            </button>
          </div>
          </div>
          {!feedback?.saved&&notice}
        </form>
      </section>
      <section className="panel settings-section">
        <header><div><h2>已保存配置</h2></div>
          {(data.config.endpoint||data.config.model)&&<StatusTag value={data.config.enabled?'已启用':'已停用'}/>}
        </header>
        {data.config.endpoint||data.config.model ? <>
          <dl className="detail-list">
            <div><dt>接口地址</dt><dd>{data.config.endpoint||'未填写'}</dd></div>
            <div><dt>模型</dt><dd>{data.config.model||'未填写'}</dd></div>
            <div><dt>API Key</dt><dd>{data.config.keyConfigured?'已加密保存':'未保存'}</dd></div>
          </dl>
          <div className="ai-actions ai-saved-actions">
            <button type="button" className="secondary" disabled={Boolean(configBusy)}
              onClick={()=>void persist({...data.config,enabled:!data.config.enabled},'','toggle')}>
              {configBusy==='toggle'?(data.config.enabled?'正在停用…':'正在验证…'):data.config.enabled?'停用 AI':'启用 AI'}
            </button>
          </div>
          {feedback?.saved&&notice}
        </> : <p className="muted">暂无已保存的配置</p>}
      </section>
    </div> : !feedback&&<p className="route-loading" role="status">正在加载…</p>}
  </>;
}
