import type {RecommendedProduct} from './ai-catalog.js';
import {aiCommunityTopics, aiDailyOrders, aiMetrics, aiShopProducts, recommendProducts, recommendationIntent} from './ai-catalog.js';

export const features = {
  USER: ['recommend','popular','reading','chat'],
  MERCHANT: ['selection','copy','demand','shop'],
  ADMIN: ['trends','heat','operations','analysis'],
} as const;

export type AiRole = keyof typeof features;
export type AiTurn = {role:'user'|'assistant'; text:string};
const intentRules:Record<AiRole,ReadonlyArray<readonly [string,RegExp]>> = {
  USER:[
    ['reading',/共读|阅读习惯|阅读方法|怎么读|怎么讲|如何读|如何讲|培养.*阅读/],
    ['popular',/热门|热销|最火|人气|排行榜/],
    ['recommend',/推荐.*(?:绘本|书|故事)|选书|买.*(?:绘本|书)|(?:绘本|书).*推荐|适合.*(?:绘本|书)|(?:绘本|书).*适合|有(?:没有|哪些).*(?:绘本|图书)/],
    ['reading',/阅读|讲故事/],
  ],
  MERCHANT:[
    ['copy',/文案|卖点|商品介绍|商品描述|宣传语|商品标题/],
    ['demand',/社区.*(?:讨论|需求|关注|分析|话题|反馈)|家长.*(?:需求|喜欢|关注)|需求分析/],
    ['selection',/选品|进货|上新|热门|热销|畅销|什么.*好卖/],
    ['shop',/店铺|本店|订单|销量|库存|缺货|补货|经营|运营|商品.*上架/],
  ],
  ADMIN:[
    ['heat',/热门|热度|热销|畅销|社区.*(?:商品|绘本)|哪些.*好卖/],
    ['operations',/运营|怎么.*(?:提升|改善|优化)|库存|缺货|补货|在售|上架/],
    ['analysis',/数据分析|统计|同比|环比|对比/],
    ['trends',/趋势|订单|销量|销售|成交|变化|平台.*表现/],
  ],
};

export function inferFeature(role:AiRole,message:string,history:AiTurn[]) {
  const classify=(text:string)=>intentRules[role].find(([,rule])=>rule.test(text))?.[0];
  const current=classify(message);
  if(current)return current;
  const isFollowUp=(text:string)=>/^(那|这些|那些|它|这本|还有|再|换|继续|其中|如果)/.test(text);
  const addsPreferences=(text:string)=>role==='USER'&&/岁|个月|预算|元|喜欢|主题|不限/.test(text);
  if(isFollowUp(message)||addsPreferences(message)){
    for(let i=history.length-1;i>=0;i--){
      const turn=history[i];
      if(turn.role!=='user')continue;
      const previousIntent=classify(turn.text);
      if(previousIntent&&(isFollowUp(message)||previousIntent==='recommend'))return previousIntent;
      if(previousIntent||!isFollowUp(turn.text)&&!addsPreferences(turn.text))break;
    }
  }
  return role==='USER'?'chat':'general';
}

export type AiCredentials = {endpoint:string; model:string; apiKey:string};
type Metrics = ReturnType<typeof aiMetrics>;
export type AiContext = {role:AiRole; feature:string; message:string; history:AiTurn[];
  products:RecommendedProduct[]; metrics?:Metrics; clarification?:string;
  dailyOrders?:ReturnType<typeof aiDailyOrders>; shopProducts?:ReturnType<typeof aiShopProducts>;
  communityTopics?:ReturnType<typeof aiCommunityTopics>};

export interface AiProvider {
  test(credentials:AiCredentials, signal:AbortSignal):Promise<void>;
  reply(credentials:AiCredentials, input:AiContext, signal:AbortSignal):Promise<string>;
}

export function assistantContext(role:AiRole, feature:string, message:string, history:AiTurn[], merchantId?:string):AiContext {
  if(role==='MERCHANT'&&!merchantId)throw new Error('AI_MERCHANT_SCOPE_REQUIRED');
  const asksForProducts = ['recommend','popular','selection','heat','demand'].includes(feature);
  const preferences=recommendationIntent(message,feature==='recommend'
    ?history.filter(turn=>turn.role==='user').reverse().map(turn=>turn.text):[]);
  const questions = history.filter(turn=>turn.role==='assistant').length;
  const clarification = feature==='recommend' && !preferences.clear && questions<2
    ? '孩子多大？有没有偏好的主题，比如动物、科普或睡前故事？' : undefined;
  const products = asksForProducts && !clarification ? recommendProducts(preferences) : [];
  const needsMetrics = ['shop','trends','operations','analysis'].includes(feature);
  return {role,feature,message,history,products,clarification,
    metrics:needsMetrics ? aiMetrics(role==='MERCHANT' ? merchantId : undefined) : undefined,
    dailyOrders:role==='ADMIN'&&['trends','analysis'].includes(feature)?aiDailyOrders():undefined,
    shopProducts:role==='MERCHANT'&&feature==='copy'&&merchantId?aiShopProducts(merchantId,message):undefined,
    communityTopics:role==='MERCHANT'&&feature==='demand'?aiCommunityTopics():undefined};
}

export function demoAdminResponse(message:string):{text:string;products:RecommendedProduct[]} {
  const reply=(text:string)=>({text,products:[]});
  const input=message.trim().replace(/[。！？!?.～~]+$/,'');
  if(/^(你好|您好|嗨|哈喽|在吗|hi|hello)$/i.test(input)){
    return reply('你好，我是绘梦的运营助手。你可以问我近期订单、在售库存或社区热门绘本。');
  }
  if(/^(谢谢|多谢|感谢|好的|ok)$/i.test(input))return reply('不客气。还有其他绘梦运营问题吗？');
  if(/用户密码|API\s*Key|密钥|手机号|收货地址/i.test(input)){
    return reply('这里可以查询绘梦的汇总数据。账户凭据和个人资料请通过对应管理功能处理。');
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
    if (['recommend','popular','selection','heat'].includes(feature)) {
      return products.length
        ? '下面是当前符合条件的在售绘本。每张卡片附有商品资料或近期社区热度。'
        : '暂时没有符合这些条件的在售绘本。可以放宽年龄、预算或主题后再试。';
    }
    if (metrics) {
      const scope = role==='MERCHANT' ? '你的店铺' : '全平台';
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
        },10_000);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}
