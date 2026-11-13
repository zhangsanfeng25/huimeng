import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtempSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test, {after} from 'node:test';
import express from 'express';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-ai-'));
process.env.DATABASE_PATH = join(fixture, 'test.db');
process.env.UPLOAD_DIR = join(fixture, 'uploads');
process.env.HUIMENG_AI_MASTER_KEY = randomBytes(32).toString('base64');
process.env.HUIMENG_AI_DEMO = '1';
const {db} = await import('../src/core/db.js');
await import('../scripts/migrate.js');
await import('../scripts/seed.js');
const {authRouter} = await import('../src/modules/auth.js');
const {aiRouter,createAiRouter} = await import('../src/modules/ai.js');
import type {AiContext} from '../src/services/ai-assistant.js';
const {recommendationIntent} = await import('../src/services/ai-catalog.js');
const app = express();
app.use(express.json());
app.use('/auth', authRouter);
app.use(aiRouter);
const providerInputs:AiContext[]=[];
const replyModels:string[]=[];
const testedModels:string[]=[];
process.env.HUIMENG_AI_DEMO = '0';
app.use('/live',createAiRouter({
  async test(credentials,signal){
    testedModels.push(credentials.model);
    if(credentials.model==='offline-model')throw new Error('fixture provider failure');
    assert.equal(credentials.apiKey,credentials.model==='fixture-second'?'fixture-second-key':'fixture-live-key');
    assert.equal(signal.aborted,false);
  },
  async reply(credentials,context){
    replyModels.push(credentials.model);
    providerInputs.push(context);
    return `已处理 ${context.feature}`;
  },
}));
process.env.HUIMENG_AI_DEMO = '1';
const server = app.listen(0, '127.0.0.1');
await new Promise<void>(resolve => server.once('listening', resolve));
const origin = `http://127.0.0.1:${(server.address() as import('node:net').AddressInfo).port}`;
after(() => { server.closeAllConnections(); server.close(); db.close(); });

function call(path: string, token = '', method = 'GET', body?: object) {
  return fetch(origin + path, {method, headers: {'Content-Type':'application/json', Authorization:`Bearer ${token}`},
    body: body && JSON.stringify(body)});
}

async function login(username: string, role: string) {
  const result = await call('/auth/login', '', 'POST', {username, role, password: role === 'ADMIN' ? 'huimeng123' : 'demo12345'});
  assert.equal(result.status, 200);
  return (await result.json()).token as string;
}

test('administrator config is encrypted, redacted and preserved on blank update', async () => {
  const admin = await login('admin', 'ADMIN');
  const buyer = await login('xingxing', 'USER');
  const path = '/admin/ai/config';
  assert.equal((await call(path, buyer)).status, 403);
  assert.equal((await call('/admin/ai/connection-test', admin, 'POST', {})).status, 400);
  const fields = {endpoint: 'https://example.invalid/v1', model: 'fixture', enabled: true};
  const key = 'fixture-key-for-tests';
  const saved = await call(path, admin, 'PUT', {...fields, apiKey: key});
  assert.equal(saved.status, 200);
  assert.equal((await saved.text()).includes(key), false);
  const encrypted = (db.prepare('SELECT encrypted_key FROM ai_config').get() as {encrypted_key:string}).encrypted_key;
  assert.ok(encrypted && !encrypted.includes(key));
  assert.equal((await call(path, admin, 'PUT', {...fields, apiKey: ''})).status, 200);
  assert.equal((await (await call(path, admin)).json()).config.keyConfigured, true);
  const connection = await call('/admin/ai/connection-test', admin, 'POST', {});
  assert.equal(connection.status, 503);
  assert.match((await connection.json()).message,/模拟模式/);
});

test('chat honors session role, uses real products, and reports calls without transcripts', async () => {
  const buyer = await login('xingxing', 'USER');
  const merchant = await login('merchant_a', 'MERCHANT');
  const admin = await login('admin', 'ADMIN');
  const path = '/ai/chat';
  assert.equal((await call(path, '', 'POST', {feature:'reading', message:'阅读建议'})).status, 401);
  assert.equal((await call(path, buyer, 'POST', {feature:'operations', message:'运营建议'})).status, 403);
  const ask = await call(path, buyer, 'POST', {feature:'recommend', message:'帮我推荐绘本'});
  assert.equal(ask.status, 200);
  assert.equal((await ask.json()).products.length, 0);
  const result = await call(path, buyer, 'POST', {feature:'popular', message:'有哪些热门绘本'});
  assert.equal(result.status, 200);
  const payload = await result.json();
  assert.ok(payload.products.length > 0 && payload.products.length <= 3);
  for (const product of payload.products) {
    assert.ok(db.prepare("SELECT 1 FROM products WHERE id=? AND status='ONLINE' AND stock_available>0").get(product.id));
    assert.ok(product.reason);
  }
  assert.equal((await call(path, merchant, 'POST', {feature:'shop', message:'店铺经营建议'})).status, 200);
  assert.equal((await call(path, admin, 'POST', {feature:'trends', message:'平台趋势'})).status, 200);
  const askAdmin = async (message:string,feature='trends') => {
    const response=await call(path,admin,'POST',{feature,message});
    assert.equal(response.status,200);
    return response.json();
  };
  assert.match((await askAdmin('你好')).text,/你好/);
  assert.match((await askAdmin('有多少件商品缺货？')).text,/缺货/);
  assert.match((await askAdmin('近30天有多少笔订单？')).text,/近 30 天有 \d+ 笔订单/);
  assert.ok((await askAdmin('最近有哪些热门绘本？')).products.length>0);
  assert.match((await askAdmin('今天北京天气怎么样？','heat')).text,/绘梦/);
  const config = await (await call('/admin/ai/config', admin)).json();
  assert.ok(config.usage.some((entry: {role:string; succeeded:number}) => entry.role === 'USER' && entry.succeeded === 2));
  assert.match((await (await call(path,buyer,'POST',{message:'你好'})).json()).text,/^你好/);
  assert.match((await (await call(path,merchant,'POST',{message:'谢谢'})).json()).text,/^不客气/);
  assert.match((await (await call(path,merchant,'POST',{message:'我店里有几件绘本缺货？'})).json()).text,/^你的店铺目前有 \d+ 件在售绘本缺货/);
  assert.equal((await call('/admin/ai/config', admin, 'PUT', {endpoint:'', model:'', enabled:false})).status, 200);
  assert.equal((await call(path, buyer, 'POST', {feature:'reading', message:'阅读建议'})).status, 503);
});

test('age ranges and new preferences override previous conversation turns', () => {
  assert.deepEqual(recommendationIntent('3岁到6岁').age,[3,6]);
  assert.deepEqual(recommendationIntent('18个月').age,[1.5,1.5]);
  assert.equal(recommendationIntent('不限年龄',['5 岁']).age,undefined);
  const preferences=recommendationIntent('主题不限，预算 30 元',['5 岁','科普绘本，预算 50 元']);
  assert.deepEqual(preferences.age,[5,5]);
  assert.equal(preferences.budget,3000);
  assert.deepEqual(preferences.wanted,[]);
});

test('all twelve feature entries reach the provider with scoped data', async () => {
  const admin=await login('admin','ADMIN');
  const buyer=await login('xingxing','USER');
  const merchant=await login('merchant_a','MERCHANT');
  const config=await call('/live/admin/ai/config',admin,'PUT',{
    endpoint:'https://example.invalid/v1',model:'fixture-live',apiKey:'fixture-live-key',enabled:true,
  });
  assert.equal(config.status,200);
  assert.deepEqual(testedModels,['fixture-live']);
  assert.equal((await config.json()).verified,true);
  const connection=await call('/live/admin/ai/connection-test',admin,'POST',{});
  assert.equal(connection.status,200);
  assert.equal((await connection.json()).mode,'live');
  const timestamp=new Date().toISOString();
  const addPost=db.prepare(`INSERT INTO community_posts(id,author_user_id,author_role,title,content,status,created_at,updated_at)
    VALUES(?,'U10001','USER',?,?,?,?,?)`);
  addPost.run('AI_PUBLIC','恐龙绘本','用于统计的公开讨论','PUBLIC',timestamp,timestamp);
  addPost.run('AI_HIDDEN','恐龙绘本','这条讨论已隐藏','HIDDEN',timestamp,timestamp);
  const addComment=db.prepare(`INSERT INTO comments(id,post_id,user_id,content,status,created_at)
    VALUES(?,?,'U10001','想看更多恐龙绘本',?,?)`);
  addComment.run('AI_PUBLIC_COMMENT','AI_PUBLIC','PUBLIC',timestamp);
  addComment.run('AI_HIDDEN_COMMENT','AI_PUBLIC','HIDDEN',timestamp);
  addComment.run('AI_HIDDEN_POST_COMMENT','AI_HIDDEN','PUBLIC',timestamp);

  for(const [token,features] of [
    [buyer,['recommend','popular','reading','chat']],
    [merchant,['selection','copy','demand','shop']],
    [admin,['trends','heat','operations','analysis']],
  ] as const){
    for(const feature of features){
      const result=await call('/live/ai/chat',token,'POST',{feature,message:'请看看适合 5 岁的绘本'});
      assert.equal(result.status,200,feature);
      assert.equal((await result.json()).text,`已处理 ${feature}`);
    }
  }
  const received=(feature:string)=>providerInputs.find(context=>context.feature===feature)!;
  assert.ok(received('recommend').products.length>0);
  assert.ok(received('heat').products.length>0);
  assert.equal(received('chat').metrics,undefined);
  const shop=received('shop').metrics!;
  assert.ok(shop.total>0&&shop.total<received('trends').metrics!.total);
  assert.equal(received('trends').dailyOrders?.length,30);
  assert.equal(received('trends').dailyOrders?.reduce((total,day)=>total+day.orders,0),received('trends').metrics!.total);
  const own=received('copy').shopProducts!;
  assert.ok(own.length>0&&own.length<=6);
  for(const product of own)assert.ok(db.prepare("SELECT 1 FROM products WHERE id=? AND merchant_id='M20001'").get(product.id));
  assert.equal(received('demand').shopProducts,undefined);
  assert.deepEqual(received('demand').communityTopics?.find(topic=>topic.topic==='恐龙'),{topic:'恐龙',posts:1,comments:1});
  assert.equal((await call('/live/ai/chat',buyer,'POST',{feature:'shop',message:'给我店铺订单'})).status,403);
  assert.equal(providerInputs.length,12);
  const update=(endpoint:string,model:string,apiKey?:string)=>call('/live/admin/ai/config',admin,'PUT',{endpoint,model,apiKey,enabled:true});
  assert.equal((await update('https://second.invalid/v1','fixture-second')).status,400);
  assert.equal((await update('https://second.invalid/v1','offline-model','fixture-second-key')).status,502);
  const unchanged=await (await call('/live/admin/ai/config',admin)).json();
  assert.equal(unchanged.config.model,'fixture-live');
  assert.equal((await update('https://second.invalid/v1','fixture-second','fixture-second-key')).status,200);
  for(const [token,message,expected] of [
    [buyer,'推荐一本适合 5 岁孩子的恐龙绘本','recommend'],
    [merchant,'帮我给商品写一段介绍文案','copy'],
    [admin,'近 30 天订单有什么变化？','trends'],
    [merchant,'我的店铺哪些商品缺货？','shop'],
  ] as const){
    const result=await call('/live/ai/chat',token,'POST',{message});
    assert.equal(result.status,200);
    assert.equal((await result.json()).text,`已处理 ${expected}`);
  }
  assert.deepEqual(replyModels.slice(-4),Array(4).fill('fixture-second'));
  assert.equal(providerInputs.at(-1)?.metrics?.total,shop.total);
  assert.equal(providerInputs.at(-1)?.dailyOrders,undefined);
  const followUp=await call('/live/ai/chat',buyer,'POST',{message:'5 岁，喜欢恐龙',history:[
    {role:'user',text:'能推荐一本绘本吗？'},{role:'assistant',text:'孩子多大？喜欢什么主题？'},
  ]});
  assert.equal(followUp.status,200);
  assert.equal((await followUp.json()).text,'已处理 recommend');
});
