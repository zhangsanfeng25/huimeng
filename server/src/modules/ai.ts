import {Router} from 'express';
import {z} from 'zod';
import {config} from '../config.js';
import {asyncRoute, audit, requireAuth} from '../core/http.js';
import {aiUsage, publicAiConfig, readAiCredentials, recordAiCall, saveAiConfig} from '../services/ai-config.js';
import {assistantContext, demoAdminResponse, demoAiProvider, features, inferFeature, withAiDeadline, type AiProvider, type AiRole} from '../services/ai-assistant.js';
import {createOpenAiCompatibleProvider,AiProviderError} from '../services/openai-compatible-provider.js';
import {completionUrl,sameAiEndpoint} from '../services/ai-endpoint.js';

const endpointSchema=z.string().trim().max(500).refine(value=>{
  if (!value) return true;
  try {
    completionUrl(value);
    return true;
  } catch {
    return false;
  }
});
const settingsSchema=z.object({
  endpoint:endpointSchema,
  model:z.string().trim().max(120),
  apiKey:z.string().trim().max(4096).optional(),
  enabled:z.boolean(),
}).strict();
const chatSchema=z.object({
  feature:z.string().trim().min(1).max(30).optional(),
  message:z.string().trim().min(1).max(2000),
  history:z.array(z.object({role:z.enum(['user','assistant']),text:z.string().trim().min(1).max(2000)}).strict()).max(12).default([]),
}).strict();

function connectionError(error: unknown) {
  if (error instanceof AiProviderError) return error.message;
  if (error instanceof Error && error.message==='AI_TIMEOUT') return '服务商响应超时，请稍后重试';
  return '无法连接服务商，请核对 HTTPS 接口地址和服务端网络';
}

export function createAiRouter(provider?:AiProvider) {
  const router=Router();
  const demo=config.demoMode && process.env.HUIMENG_AI_DEMO==='1';
  const model=demo ? demoAiProvider : provider;
  const mode=demo ? 'demo' : model ? 'live' : 'unconnected';
  const admin=requireAuth('ADMIN');
  let saving=false;

  router.get('/admin/ai/config',admin,(_req,res)=>{
    res.json({config:publicAiConfig(),usage:aiUsage(),mode});
  });

  router.put('/admin/ai/config',admin,asyncRoute(async(req,res)=>{
    const parsed=settingsSchema.safeParse(req.body);
    if(!parsed.success)return res.status(400).json({message:'请输入有效的 HTTPS 接口地址、模型和启用状态'});
    const {apiKey='',...settings}=parsed.data;
    if(saving)return res.status(409).json({message:'正在验证另一份 AI 配置，请稍后重试'});
    saving=true;
    try {
      let verified=false;
      if(settings.enabled&&!demo){
        if(!settings.endpoint||!settings.model)return res.status(400).json({message:'启用前请填写 HTTPS 接口地址和模型名称'});
        const current=publicAiConfig();
        const sameEndpoint=sameAiEndpoint(current.endpoint,settings.endpoint);
        if(!apiKey&&current.keyConfigured&&!sameEndpoint){
          return res.status(400).json({message:'更换接口地址时，请输入新接口对应的 API Key'});
        }
        const candidateKey=apiKey||(sameEndpoint?readAiCredentials().apiKey:'');
        if(!candidateKey)return res.status(400).json({message:'启用前请填写服务商的 API Key'});
        if(!provider)return res.status(503).json({message:'当前尚未接入 AI 服务商'});
        try {
          await withAiDeadline(signal=>provider.test({...settings,apiKey:candidateKey},signal));
          verified=true;
        } catch(error) {
          return res.status(502).json({message:connectionError(error)+'。本次未保存，原配置保持不变。'});
        }
      }
      const saved=saveAiConfig(settings,apiKey);
      audit(req.actor!.id,req.actor!.name,'AI 管理','保存 AI 配置','ai_config');
      return res.json({config:saved,mode,verified});
    } catch {
      return res.status(503).json({message:'AI 配置无法保存，请检查服务端数据库与加密主密钥'});
    } finally {
      saving=false;
    }
  }));

  router.post('/admin/ai/connection-test',admin,asyncRoute(async(_req,res)=>{
    const saved=publicAiConfig();
    if(!saved.endpoint||!saved.model||!saved.keyConfigured){
      return res.status(400).json({message:'请先填写并保存 HTTPS 接口地址、模型名称和 API Key'});
    }
    if(demo)return res.status(503).json({message:'当前为模拟模式，未向真实服务商发起连接测试'});
    if(!provider)return res.status(503).json({message:'当前尚未接入 AI 服务商'});
    try {
      const credentials=readAiCredentials();
      await withAiDeadline(signal=>provider.test(credentials,signal));
      return res.json({mode,message:'服务商连接测试通过'});
    } catch(error) {
      return res.status(502).json({message:connectionError(error)});
    }
  }));

  router.post('/ai/chat',requireAuth('USER','MERCHANT','ADMIN'),asyncRoute(async(req,res)=>{
    const parsed=chatSchema.safeParse(req.body);
    if(!parsed.success)return res.status(400).json({message:'请输入有效的消息，单条最多 2000 字'});
    const role=req.actor!.activeRole as AiRole;
    const {feature:requestedFeature,message,history}=parsed.data;
    if(requestedFeature&&!(features[role] as readonly string[]).includes(requestedFeature)){
      return res.status(403).json({message:'当前身份无法使用该功能'});
    }
    const feature=requestedFeature??inferFeature(role,message,history);
    const saved=publicAiConfig();
    if(!saved.enabled||!model||(!demo&&(!saved.endpoint||!saved.model||!saved.keyConfigured))){
      return res.status(503).json({message:'AI 助手暂未启用，请联系管理员'});
    }
    const started=Date.now();
    try {
      const response=await (async()=>{
        if(demo&&role==='ADMIN')return demoAdminResponse(message);
        const credentials=demo?{endpoint:'',model:'',apiKey:''}:readAiCredentials();
        const context=assistantContext(role,feature,message,history,req.actor!.merchantId);
        const text=context.clarification??await withAiDeadline(signal=>model.reply(credentials,context,signal));
        return {text,products:context.products};
      })();
      const {text,products}=response;
      if(typeof text!=='string'||!text.trim()||text.length>4000)throw new Error('AI_INVALID_RESPONSE');
      recordAiCall(role,true,Date.now()-started);
      return res.json({text,products,mode});
    } catch(error) {
      recordAiCall(role,false,Date.now()-started);
      const timedOut=error instanceof Error && error.message==='AI_TIMEOUT';
      return res.status(timedOut?504:502).json({message:timedOut?'AI 响应超时，请重试':'AI 暂时无法回答，请稍后重试或联系管理员'});
    }
  }));
  return router;
}

export const aiRouter=createAiRouter(createOpenAiCompatibleProvider());
