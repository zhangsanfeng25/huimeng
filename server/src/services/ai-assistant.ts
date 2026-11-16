import type {RecommendedProduct} from './ai-catalog.js';
import {aiAdminOverview, aiCommunityTopics, aiDailyOrders, aiMetrics, aiShopProducts, recommendProducts, recommendationIntent} from './ai-catalog.js';

export const features = {
  USER: ['recommend','popular','reading','chat','catalog'],
  MERCHANT: ['selection','copy','demand','shop'],
  ADMIN: ['trends','heat','operations','analysis'],
} as const;

export type AiRole = keyof typeof features;
export type AiTurn = {role:'user'|'assistant'; text:string};
const adminCatalog=/商品|绘本|书目/;
const adminReviews=/审核|待审|审批/;
const adminFeedback=/反馈|功能问题|bug/i;
const functionalFeedback=/功能问题|bug/i;
const directRecommendation=/^(?:(?:请|能不能|可以)?(?:帮我|给我)?推荐(?:一下|点|一些|几本|商品|绘本|几本绘本|什么书)?(?:吧|吗)?[\s。！？?!]*|(?:gei\s*wo\s*|bang\s*wo\s*)?tui\s*jian[\s.!?]*)$/i;

function normalizeBuyerRequest(text:string) {
  return text.normalize('NFKD').replace(/[\u0300-\u036f]/g,'')
    .replace(/\b(?:gei\s*wo\s*|bang\s*wo\s*)?tui\s*jian\b/gi,'推荐')
    .replace(/\bji\s*ben\b/gi,'几本')
    .replace(/(\d{1,2})\s*sui\b/gi,'$1岁')
    .replace(/\b(?:hui\s*ben|shu\s*ji|shu)\b/gi,'书');
}
const intentRules:Record<AiRole,ReadonlyArray<readonly [string,RegExp]>> = {
  USER:[
    ['reading',/共读|阅读习惯|阅读方法|怎么读|怎么讲|如何读|如何讲|培养.*阅读/],
    ['popular',/热门|热销|最火|人气|排行榜/],
    ['recommend',directRecommendation],
    ['recommend',/推荐.*(?:绘本|书|故事|商品)|选书|买.*(?:绘本|书)|(?:绘本|书).*推荐|适合.*(?:绘本|书)|(?:绘本|书).*适合/],
    ['catalog',/(?:查看|查找|搜索|看看|有哪些|有没有).*(?:绘本|图书|商品)|《[^》]+》|这(?:本|件|些).*(?:价格|库存|有货|多少钱)/],
    ['reading',/阅读|讲故事/],
  ],
  MERCHANT:[
    ['copy',/文案|卖点|商品介绍|商品描述|宣传语|商品标题/],
    ['demand',/社区.*(?:讨论|需求|关注|分析|话题|反馈)|家长.*(?:需求|喜欢|关注)|需求分析/],
    ['selection',/选品|进货|上新|热门|热销|畅销|什么.*好卖/],
    ['shop',/店铺|店里|本店|订单|销量|库存|缺货|补货|经营|运营|商品|绘本/],
  ],
  ADMIN:[
    ['heat',/热门|热度|热销|畅销|社区.*(?:商品|绘本)|哪些.*好卖/],
    ['operations',/运营|怎么.*(?:提升|改善|优化)|库存|缺货|补货|在售|上架|审核|待审|审批|反馈|功能问题|bug/i],
    ['analysis',/数据分析|统计|同比|环比|对比/],
    ['trends',/趋势|订单|销量|销售|成交|变化|平台.*表现/],
    ['operations',adminCatalog],
  ],
};

export function inferFeature(role:AiRole,message:string,history:AiTurn[]) {
  const classify=(text:string)=>intentRules[role].find(([,rule])=>rule.test(text))?.[0];
  if(role==='USER')message=normalizeBuyerRequest(message);
  const current=classify(message);
  if(current)return current;
  const isFollowUp=(text:string)=>/^(那|这些|那些|它|这本|还有|再|换|继续|其中|如果)/.test(text);
  const addsPreferences=(text:string)=>role==='USER'&&recommendationIntent(text).clear;
  if(isFollowUp(message)||addsPreferences(message)){
    for(let i=history.length-1;i>=0;i--){
      const turn=history[i];
      if(turn.role!=='user')continue;
      const previousText=role==='USER'?normalizeBuyerRequest(turn.text):turn.text;
      const previousIntent=classify(previousText);
      if(previousIntent&&(isFollowUp(message)||previousIntent==='recommend'))return previousIntent;
      if(previousIntent||!isFollowUp(previousText)&&!addsPreferences(previousText))break;
    }
  }
  return role==='USER'?'chat':'general';
}

export type AiCredentials = {endpoint:string; model:string; apiKey:string};
type Metrics = ReturnType<typeof aiMetrics>;
export type AiContext = {role:AiRole; feature:string; message:string; history:AiTurn[];
  products:RecommendedProduct[]; metrics?:Metrics; clarification?:string;
  dailyOrders?:ReturnType<typeof aiDailyOrders>; shopProducts?:ReturnType<typeof aiShopProducts>;
  communityTopics?:ReturnType<typeof aiCommunityTopics>; adminOverview?:ReturnType<typeof aiAdminOverview>;
  defaultRecommendation?:boolean};

export interface AiProvider {
  test(credentials:AiCredentials, signal:AbortSignal):Promise<void>;
  reply(credentials:AiCredentials, input:AiContext, signal:AbortSignal):Promise<string>;
}

export function assistantContext(role:AiRole, feature:string, message:string, history:AiTurn[], merchantId?:string):AiContext {
  if(role==='MERCHANT'&&!merchantId)throw new Error('AI_MERCHANT_SCOPE_REQUIRED');
  const asksForProducts = ['recommend','catalog','popular','selection','heat','demand'].includes(feature);
  const userHistory=history.filter(turn=>turn.role==='user').reverse().map(turn=>turn.text);
  const request=role==='USER'?normalizeBuyerRequest(message):message;
  const isDirect=directRecommendation.test(request);
  const preferences=recommendationIntent(request,feature==='recommend'&&!isDirect?userHistory.map(normalizeBuyerRequest):[]);
  const clarification = feature==='recommend' && !preferences.clear && !isDirect &&
    !history.some(turn=>turn.role==='assistant'&&turn.text.startsWith('孩子多大？'))
    ? '孩子多大？有没有偏好的主题，比如动物、科普或睡前故事？' : undefined;
  const title=feature==='catalog'?(message.match(/《([^》]+)》/)??userHistory.join(' ').match(/《([^》]+)》/))?.[1]:undefined;
  const products = asksForProducts && !clarification ? recommendProducts(preferences,title,feature==='catalog'?message:undefined) : [];
  const adminOverview=role==='ADMIN'&&['trends','operations','analysis','heat'].includes(feature)?aiAdminOverview():undefined;
  const needsMetrics = feature==='shop' || Boolean(adminOverview);
  return {role,feature,message,history,products,clarification,
    defaultRecommendation:role==='USER'&&feature==='recommend'&&isDirect,
    adminOverview,
    metrics:needsMetrics ? aiMetrics(role==='MERCHANT' ? merchantId : undefined) : undefined,
    dailyOrders:role==='ADMIN'&&['trends','analysis'].includes(feature)?aiDailyOrders():undefined,
    shopProducts:role==='MERCHANT'&&['copy','shop'].includes(feature)&&merchantId?aiShopProducts(merchantId,message):undefined,
    communityTopics:role==='MERCHANT'&&feature==='demand'?aiCommunityTopics():undefined};
}

export function recommendationGuidance(context:AiContext) {
  if(context.role!=='USER'||(!context.defaultRecommendation&&!context.clarification))return;
  const intro=context.clarification??(context.products.length
    ?'先推荐几本当前在售且有库存的绘本。想选得更合适，可以点选孩子的年龄，或告诉我喜欢的主题和预算。'
    :'目前暂无有库存的在售绘本。');
  const suggestions=context.clarification||context.products.length?['0–3岁','3–6岁','6–9岁','9岁以上']:[];
  const text=suggestions.length?`${intro}\n也可以回复年龄，例如「6–9岁」。`:intro;
  return {text,products:context.products,suggestions};
}

export function adminCountAnswer(context:AiContext) {
  const {adminOverview:data,message}=context;
  if(!data||!/多少|几[件条项个]|总数|数量/.test(message)||/趋势|变化|建议|原因|今天|昨天|本周|本月|上周|上月|最近|过去|近\s*\d|订单|销量|销售额|缺货/.test(message))return;
  const lines:string[]=[];
  const asksProductCount=message.split(/[，,、；;。？?]|还有|以及/).some(part=>adminCatalog.test(part)&&!adminReviews.test(part));
  if(asksProductCount){
    lines.push(`平台商品共 ${data.products.total} 件，其中在售 ${data.products.online} 件。`);
  }
  if(adminReviews.test(message)){
    if(!/商家|入驻/.test(message)||adminCatalog.test(message)){
      const {total,pending,approved,rejected}=data.productReviews;
      lines.push(`商品待审核 ${pending} 条，审核记录共 ${total} 条（已通过 ${approved} 条，已驳回 ${rejected} 条）。`);
    }
    if(/商家|入驻/.test(message))lines.push(`商家入驻待审核 ${data.pendingMerchants} 条。`);
  }
  if(adminFeedback.test(message)){
    const {total,unresolved,functionalTotal,functionalUnresolved}=data.feedback;
    lines.push(functionalFeedback.test(message)?`功能问题反馈共 ${functionalTotal} 条，其中 ${functionalUnresolved} 条未解决。`
      :`反馈共 ${total} 条，其中 ${unresolved} 条未解决。`);
  }
  return lines.length?lines.join('\n'):undefined;
}

export function businessFallback(context:AiContext) {
  const {role,feature,products,metrics,shopProducts,adminOverview}=context;
  if(['recommend','catalog','popular','selection'].includes(feature)){
    return products.length ? (feature==='catalog'?'以下是目录中查到的绘本。':'以下是当前在售且有库存的绘本。')
      : '目前没有查到符合条件的绘本，可以换个书名或放宽筛选条件。';
  }
  if(role==='MERCHANT'&&feature==='shop'&&metrics){
    const summary=`你的店铺共 ${metrics.totalProducts} 件商品，其中在售 ${metrics.online} 件、在售缺货 ${metrics.outOfStock} 件。近 30 天有 ${metrics.total} 笔订单。`;
    const items=shopProducts?.map(item=>`${item.name} · 库存 ${item.stock} 件`).join('\n');
    return items?`${summary}\n部分商品库存（最多 6 件）\n${items}`:summary;
  }
  if(adminOverview&&metrics){
    const {products:catalog,productReviews,pendingMerchants,feedback,pendingProducts}=adminOverview;
    const lines=[`平台商品共 ${catalog.total} 件，其中在售 ${catalog.online} 件。近 30 天有 ${metrics.total} 笔订单。`,
      `商品待审核 ${productReviews.pending} 条，商家入驻待审核 ${pendingMerchants} 条。`,
      `反馈共 ${feedback.total} 条，未解决 ${feedback.unresolved} 条。其中功能问题 ${feedback.functionalTotal} 条，未解决 ${feedback.functionalUnresolved} 条。`];
    if(pendingProducts.length)lines.push(`较早提交的待审核商品（最多 6 条）\n${pendingProducts.map(item=>`${item.name} · ${item.shop}`).join('\n')}`);
    return lines.join('\n');
  }
}

export function demoAdminResponse(context:AiContext):{text:string;products:RecommendedProduct[]} {
  const {message,adminOverview}=context;
  const reply=(text:string)=>({text,products:[]});
  const input=message.trim().replace(/[。！？!?.～~]+$/,'');
  if(/^(你好|您好|嗨|哈喽|在吗|hi|hello)$/i.test(input)){
    return reply('你好，我是绘梦的运营助手。你可以问我近期订单、在售库存或社区热门绘本。');
  }
  if(/^(谢谢|多谢|感谢|好的|ok)$/i.test(input))return reply('不客气。还有其他绘梦运营问题吗？');
  if(/用户密码|API\s*Key|密钥|手机号|收货地址/i.test(input)){
    return reply('这里可以查询绘梦的汇总数据。账户凭据和个人资料请通过对应管理功能处理。');
  }
  if(adminOverview && (adminReviews.test(input)||adminFeedback.test(input))){
    const {pending}=adminOverview.productReviews;
    const {total,unresolved,functionalTotal,functionalUnresolved}=adminOverview.feedback;
    return reply(`商品待审核 ${pending} 条，商家入驻待审核 ${adminOverview.pendingMerchants} 条。\n反馈共 ${total} 条，未解决 ${unresolved} 条。其中功能问题 ${functionalTotal} 条，未解决 ${functionalUnresolved} 条。`);
  }
  if(/商品热度|热门|热销|推荐.*绘本|社区热度/.test(input)){
    const products=recommendProducts(recommendationIntent(input));
    return {text:products.length?'下面是按近期社区热度整理的在售绘本。热度来自关联帖子、点赞和公开评论。'
      :'暂时没有符合条件的在售绘本。可以调整年龄、预算或主题。',products};
  }
  if(/缺货|库存|上架|在售|补货/.test(input)){
    const {online,outOfStock}=aiMetrics();
    return reply(`当前有 ${online} 件上架商品，其中 ${outOfStock??0} 件缺货。`);
  }
  if(/订单|趋势|数据分析|运营建议/.test(input)){
    const {total,online,outOfStock}=aiMetrics();
    if(/今天|昨天|本周|上周|本月|上月|销售额|成交额|利润/.test(input)){
      return reply(`当前模拟助手可查询近 30 天订单笔数，共 ${total} 笔。指定日期或金额可在管理后台查看。`);
    }
    if(/运营建议/.test(input)){
      const suggestion=outOfStock?'先核对缺货商品，安排补货。':'目前上架商品暂无缺货，可以结合社区热度安排重点展示。';
      return reply(`当前有 ${online} 件上架商品。${suggestion}`);
    }
    const orders=`全平台近 30 天有 ${total} 笔订单。`;
    return reply(/趋势|数据分析/.test(input)?`${orders}当前有 ${online} 件商品上架，其中 ${outOfStock??0} 件缺货。`:orders);
  }
  return reply('当前是绘梦的模拟运营助手。试着问「近 30 天有多少笔订单」或「最近有哪些热门绘本」。');
}

export const demoAiProvider:AiProvider = {
  async test() {throw new Error('AI_DEMO_CONNECTION_UNAVAILABLE');},
  async reply(_credentials, context) {
    const {role,feature,message,products,metrics} = context;
    const input=message.trim().replace(/[。！？!?.～~]+$/,'');
    if(/^(你好|您好|嗨|哈喽|在吗|hi|hello)$/i.test(input)){
      return role==='MERCHANT'
        ?'你好，我是绘梦 AI 助手。你可以问我本店的库存和订单，也可以聊选品和商品介绍。'
        :'你好，我是绘梦 AI 助手。你可以告诉我孩子的年龄和兴趣，或者问我亲子共读的问题。';
    }
    if(/^(谢谢|多谢|感谢)$/i.test(input))return '不客气。还有什么想聊的？';
    if (context.clarification) return context.clarification;
    if (feature==='demand') {
      return products.length
        ? '可以从下面这些商品关联的公开讨论入手，比较读者关心的主题与适读年龄，再决定补充哪些品类。'
        : '暂时没有匹配的在售绘本。你可以换个主题查看公开社区讨论。';
    }
    if (['recommend','catalog','popular','selection','heat'].includes(feature)) {
      return products.length
        ? '下面是当前符合条件的在售绘本。每张卡片附有商品资料或近期社区热度。'
        : '暂时没有符合这些条件的在售绘本。可以放宽年龄、预算或主题后再试。';
    }
    if (metrics) {
      const scope = role==='MERCHANT' ? '你的店铺' : '全平台';
      if(role==='MERCHANT'&&/商品|绘本/.test(input)&&!/多少|几[件本]|订单|经营/.test(input)){
        const rows=context.shopProducts?.filter(item=>!/缺货|售罄/.test(input)||item.status==='ONLINE'&&item.stock===0)??[];
        const details=rows.map(item=>`${item.name}，库存 ${item.stock} 件`).join('\n');
        return details?`${scope}的商品（最多列出 6 件）\n${details}`:`${scope}目前没有符合条件的商品。`;
      }
      if(/缺货|库存|上架|在售|补货/.test(input)&&!/订单|销量|销售/.test(input)){
        return `${scope}目前有 ${metrics.outOfStock??0} 件在售绘本缺货，共有 ${metrics.online} 件绘本在售。`;
      }
      if(/订单/.test(input)&&!/库存|缺货|补货|在售/.test(input)){
        return `${scope}近 30 天有 ${metrics.total} 笔订单。`;
      }
      return `${scope}近 30 天有 ${metrics.total} 笔订单，当前有 ${metrics.online} 件上架商品，其中 ${metrics.outOfStock ?? 0} 件缺货。可以先核对缺货商品的补货安排，再结合社区讨论调整选品。`;
    }
    if (feature==='copy') {
      return '可以按「适读年龄 → 故事亮点 → 亲子共读场景」组织商品介绍。发给我绘本书名、主题和适读年龄，就可以据此整理文案。';
    }
    if (feature==='reading') {
      return '共读时可以先请孩子观察封面，再停下来聊一聊角色接下来会做什么。你想聊选书、阅读习惯，还是某本绘本？';
    }
    if (context.role==='MERCHANT') {
      return '你好，可以直接问我本店订单和库存，也可以聊选品、社区需求或商品介绍。';
    }
    return '可以聊聊孩子喜欢的故事、最近在读的绘本，或者你想一起解决的阅读问题。';
  },
};

export async function withAiDeadline<T>(run:(signal:AbortSignal)=>Promise<T>):Promise<T> {
  const controller = new AbortController();
  let timer:ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      Promise.resolve().then(()=>run(controller.signal)),
      new Promise<never>((_resolve,reject)=>{
        timer=setTimeout(()=>{
          controller.abort();
          reject(new Error('AI_TIMEOUT'));
        },30_000);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}
