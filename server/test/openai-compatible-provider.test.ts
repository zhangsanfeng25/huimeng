import test from 'node:test';
import assert from 'node:assert/strict';
import {createOpenAiCompatibleProvider, resolvePublicDestination} from '../src/services/openai-compatible-provider.js';

const credentials={endpoint:'https://discovery-api.intern-ai.org.cn/v1',model:'Agents-A1',apiKey:'fixture-only'};
const signal=new AbortController().signal;

test('proxy DNS placeholders use verified public answers and still reject private targets',async()=>{
  const local=[{address:'198.18.0.225',family:4}];
  assert.deepEqual(await resolvePublicDestination('discovery-api.intern-ai.org.cn',local,signal,async()=>[
    {address:'47.100.69.224',family:4},
  ]),{address:'47.100.69.224',family:4});
  assert.deepEqual(await resolvePublicDestination('other-provider.example',local,signal,async()=>[
    {address:'93.184.216.34',family:4},
  ]),{address:'93.184.216.34',family:4});
  assert.deepEqual(await resolvePublicDestination('discovery-api.intern-ai.org.cn',[
    ...local,{address:'2001:2::89',family:6},
  ],signal,async()=>[
    {address:'47.100.69.224',family:4},
  ]),{address:'47.100.69.224',family:4});
  await assert.rejects(resolvePublicDestination('198.18.0.225',local,signal,async()=>{
    assert.fail('IP literals must not trigger a fallback');
  }),/可公开访问/);
  await assert.rejects(resolvePublicDestination('discovery-api.intern-ai.org.cn',local,signal,async()=>[
    {address:'127.0.0.1',family:4},
  ]),/可公开访问/);
  await assert.rejects(resolvePublicDestination('discovery-api.intern-ai.org.cn',[{address:'10.1.2.3',family:4}],signal,async()=>{
    assert.fail('private DNS result must not trigger a fallback');
  }),/可公开访问/);
});

test('the selected model and key are sent to each configured Chat Completions endpoint',async()=>{
  const calls:unknown[][]=[];
  const provider=createOpenAiCompatibleProvider(async(url,options)=>{
    calls.push([url,options]);
    return Response.json({choices:[{message:{content:'连接成功'}}]});
  });
  await provider.test(credentials,signal);
  assert.equal(await provider.reply(credentials,{role:'ADMIN',feature:'trends',message:'最近订单',history:[],products:[]},signal),'连接成功');
  assert.equal(calls[0][0],'https://discovery-api.intern-ai.org.cn/v1/chat/completions');
  const options=calls[0][1] as RequestInit;
  assert.equal(options.redirect,'error');
  assert.equal(options.signal,signal);
  assert.equal(new Headers(options.headers).get('Authorization'),'Bearer fixture-only');
  assert.equal(JSON.parse(String(options.body)).model,'Agents-A1');
  assert.deepEqual(JSON.parse(String(options.body)).thinking,{type:'disabled'});
  const second={endpoint:'https://second.invalid/gateway/v1/chat/completions/',model:'second-model',apiKey:'second-key'};
  await provider.test(second,signal);
  assert.equal(calls[2][0],'https://second.invalid/gateway/v1/chat/completions');
  const secondOptions=calls[2][1] as RequestInit;
  assert.equal(new Headers(secondOptions.headers).get('Authorization'),'Bearer second-key');
  assert.equal(JSON.parse(String(secondOptions.body)).model,'second-model');
  assert.equal(JSON.parse(String(secondOptions.body)).thinking,undefined);
});

test('private destinations are rejected and provider errors are redacted',async()=>{
  for (const endpoint of ['https://localhost/v1','https://127.0.0.1/v1','https://[::ffff:127.0.0.1]/v1','https://169.254.169.254/v1']){
    await assert.rejects(createOpenAiCompatibleProvider().test({...credentials,endpoint},signal),/可公开访问/);
  }
  const provider=createOpenAiCompatibleProvider(async()=>Response.json({message:'fixture-only'}, {status:401}));
  await assert.rejects(provider.test(credentials,signal),{message:'服务商拒绝了 API Key，请核对密钥及所属平台'});
  const redirect=createOpenAiCompatibleProvider(async()=>Response.redirect('https://second.invalid',307));
  await assert.rejects(redirect.test(credentials,signal),/重定向/);
});
