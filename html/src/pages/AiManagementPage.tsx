import {useEffect, useState, type FormEvent} from 'react';
import {LoaderCircle, Plug, Save} from 'lucide-react';
import {api} from '../api';
import {PageHeader, StatusTag} from '../components/Ui';
import './ai-management.css';

type Mode = 'demo' | 'live' | 'unconnected';
type Settings = {endpoint:string; model:string; enabled:boolean; keyConfigured:boolean};
type Data = {config:Settings; mode:Mode};

const modes:Record<Mode,string> = {demo:'模拟联调',live:'OpenAI 兼容接口',unconnected:'尚未接入服务商'};

export function AiManagementPage() {
  const [data,setData]=useState<Data>();
  const [settings,setSettings]=useState<Settings>();
  const [key,setKey]=useState('');
  const [configBusy,setConfigBusy]=useState<'save'|'test'>();
  const [feedback,setFeedback]=useState<{text:string; error?:boolean}>();

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

  async function save(event:FormEvent) {
    event.preventDefault();
    if(!settings||configBusy)return;
    setConfigBusy('save');
    setFeedback(undefined);
    try {
      const {endpoint,model,enabled}=settings;
      const result=await api<Data & {verified:boolean}>('/admin/ai/config',{
        method:'PUT',body:JSON.stringify({endpoint,model,enabled,apiKey:key}),
      });
      setSettings(result.config);
      setData(result);
      window.dispatchEvent(new Event('huimeng:ai-config-saved'));
      setKey('');
      setFeedback({text:result.verified?'连接测试通过，AI 配置已保存并启用':'AI 配置已保存'});
    } catch(error) {
      setFeedback({text:(error as Error).message,error:true});
    } finally {
      setConfigBusy(undefined);
    }
  }

  async function testConnection() {
    setConfigBusy('test');
    setFeedback(undefined);
    try {
      const result=await api<{message:string}>('/admin/ai/connection-test',{method:'POST'});
      setFeedback({text:result.message});
    } catch(error) {
      setFeedback({text:(error as Error).message,error:true});
    } finally {
      setConfigBusy(undefined);
    }
  }

  return <>
    <PageHeader title="AI 管理" description="配置模型接口、密钥和 AI 助手启用状态。"/>
    {feedback&&<p className={feedback.error?'page-error':'ai-feedback'} role={feedback.error?'alert':'status'}>{feedback.text}</p>}
    {settings&&data ? <div className="settings settings-redesign ai-management">
      <section className="panel settings-section">
        <header><span><Plug size={19}/></span><div><h2>API 配置</h2><p>{modes[data.mode]}</p></div><div className="ai-config-status"><StatusTag value={data.config.enabled?'已启用':'已停用'}/></div></header>
        <form className="ai-config" onSubmit={save}>
          <div className="form-grid">
            <label>HTTPS 接口地址<input type="url" value={settings.endpoint} maxLength={500} placeholder="https://…" disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,endpoint:event.target.value})}/></label>
            <label>模型名称<input value={settings.model} maxLength={120} disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,model:event.target.value})}/></label>
            <label>API Key<input type="password" autoComplete="new-password" value={key} maxLength={4096} disabled={Boolean(configBusy)}
              placeholder={settings.keyConfigured&&settings.endpoint.trim()===data.config.endpoint?'已保存，留空保留当前密钥':'在这里填写服务商密钥'} onChange={event=>setKey(event.target.value)}/></label>
          </div>
          <label className="ai-switch">
            <input type="checkbox" role="switch" checked={settings.enabled} disabled={Boolean(configBusy)}
              onChange={event=>setSettings({...settings,enabled:event.target.checked})}/>
            <span className="ai-switch-track" aria-hidden="true"/>
            <span>启用 AI 助手</span>
          </label>
          <div className="ai-actions">
            <button className="primary" disabled={Boolean(configBusy)}>{configBusy==='save'?<LoaderCircle className="ai-spin" size={17}/>:<Save size={17}/>}
              {configBusy==='save'?(settings.enabled&&data.mode==='live'?'正在验证连接…':'正在保存…'):'保存配置'}
            </button>
            <button className="secondary" type="button" disabled={Boolean(configBusy)} onClick={()=>void testConnection()}>
              {configBusy==='test'?<LoaderCircle className="ai-spin" size={17}/>:<Plug size={17}/>}测试已保存配置
            </button>
          </div>
          {data.mode==='live'&&<p className="data-note">填写服务商的 OpenAI 兼容基础地址或完整的 /chat/completions 地址。启用后点击保存，连接测试通过即生效。</p>}
          {data.mode==='demo'&&<p className="data-note">模拟模式可体验对话。退出模拟模式后，才能验证真实服务商连接。</p>}
        </form>
      </section>
    </div> : !feedback&&<p className="route-loading" role="status">正在加载…</p>}
  </>;
}
