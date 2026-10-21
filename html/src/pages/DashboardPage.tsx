import {
  Activity, Banknote, BookOpen, Box, Clock3, PackageCheck, RefreshCw,
  ShoppingCart, Truck, Users,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { api, realtime } from '../api';
import { StatusTag } from '../components/Ui';

type Task = {
  id: string;
  product: string;
  category: string;
  merchant: string;
  submittedAt: string;
  status: string;
};

type Order = {
  id: string;
  shop: string;
  buyer: string;
  amount: number;
  status: string;
  orderedAt: string;
  after_sale_state?: string;
};

type TrendPoint = { label: string; orders: number; turnover: number; products: number; users: number };

type Dashboard = {
  metrics: {
    orders: number;
    turnover: number;
    products: number;
    users: number;
    pendingReviews: number;
    pendingShipment: number;
  };
  trend: TrendPoint[];
  distribution: Array<{ name: string; value: number }>;
  tasks: Task[];
  recentOrders: Order[];
  operations:Array<{id:string;operator:string;module:string;action:string;objectId:string;createdAt:string}>;
  updatedAt: string;
};

const EMPTY_METRICS = {
  orders: 0, turnover: 0, products: 0, users: 0, pendingReviews: 0, pendingShipment: 0,
};

const CHART_COLORS = ['#45aaf2', '#ff7f5c', '#2fca80', '#8c67f6', '#f3ba3f', '#43b9bf'];

function MiniTrend({ values, color }: { values: number[]; color: string }) {
  const max = Math.max(...values, 1);
  const points = values.map((value, index) => `${index * 16},${30 - (value / max) * 24}`).join(' ');
  return <svg className="dashboard-mini-trend" viewBox="0 0 96 34" aria-hidden="true">
    <polyline points={points} fill="none" stroke={color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}

const moneyFormatter = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' });
function formatMoney(value: number) {
  return moneyFormatter.format(value);
}


export function DashboardPage() {
  const navigate = useNavigate();
  const [days, setDays] = useState<7 | 30>(7);
  const [data, setData] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tooltip, setTooltip] = useState<{text:string;x:number;y:number}|null>(null);
  const describe = (text:string) => ({
    tabIndex:0, role:'img', 'aria-label':text, 'aria-describedby':tooltip?.text===text?'dashboard-chart-tooltip':undefined,
    onMouseEnter:(event:React.MouseEvent<HTMLElement>)=>showTooltip(event.currentTarget,text),
    onMouseLeave:()=>setTooltip(null),
    onFocus:(event:React.FocusEvent<HTMLElement>)=>showTooltip(event.currentTarget,text),
    onBlur:()=>setTooltip(null),
    onClick:(event:React.MouseEvent<HTMLElement>)=>showTooltip(event.currentTarget,text),
    onKeyDown:(event:React.KeyboardEvent<HTMLElement>)=>{if(event.key==='Escape')setTooltip(null);},
  });
  function showTooltip(element:HTMLElement,text:string){const rect=element.getBoundingClientRect();setTooltip({text,x:Math.max(140,Math.min(window.innerWidth-140,rect.left+rect.width/2)),y:Math.max(12,Math.min(window.innerHeight-90,rect.top-65))});}
  useEffect(()=>{const hide=()=>setTooltip(null);window.addEventListener('scroll',hide,true);window.addEventListener('resize',hide);return()=>{window.removeEventListener('scroll',hide,true);window.removeEventListener('resize',hide);};},[]);

  const load = useCallback(async (range = days) => {
    try {
      setError('');
      const result = await api<Dashboard>(`/dashboard?days=${range}`);
      setData(result);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '工作台数据加载失败');
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    setLoading(true);
    load(days);
  }, [days, load]);

  useEffect(() => realtime(() => load(days)), [days, load]);

  const metrics = data?.metrics ?? EMPTY_METRICS;
  const trend = data?.trend ?? [];
  const maxBar = Math.max(...trend.flatMap(item => [item.orders, item.turnover / 100]), 1);
  const totalProducts = (data?.distribution ?? []).reduce((sum, item) => sum + item.value, 0);
  const metricCards = useMemo(() => {
    const recentSpark = trend.slice(-6).map(item => item.orders);
    const amountSpark = trend.slice(-6).map(item => item.turnover);
    return [
    { label: '订单总量', value: metrics.orders.toLocaleString(), note: '全部订单', icon: ShoppingCart, color: '#45aaf2', values: recentSpark, trendLabel: '近6日每日订单量' },
    { label: '成交金额', value: formatMoney(metrics.turnover), note: '已支付，扣除已退款金额', icon: Banknote, color: '#ff7f5c', values: amountSpark, trendLabel: '近6日每日成交额' },
    { label: '在售商品', value: metrics.products.toLocaleString(), note: `${metrics.pendingReviews} 件待审核`, icon: BookOpen, color: '#2fca80', values: trend.slice(-6).map(item=>item.products), trendLabel: '近6日每日新增商品' },
    { label: '注册用户', value: metrics.users.toLocaleString(), note: '平台用户总数', icon: Users, color: '#8c67f6', values: trend.slice(-6).map(item=>item.users), trendLabel: '近6日每日注册用户' },
    ];
  }, [metrics, trend]);

  const activities = [
    ...(data?.operations??[]).map(row=>({id:row.id,title:`${row.operator} · ${row.action}`,detail:`${({products:'商品',reviews:'审核',community:'社区',users:'用户',notices:'通知',merchants:'商家',categories:'分类'} as Record<string,string>)[row.module]||row.module} · ${row.objectId||'—'} · ${new Date(row.createdAt).toLocaleString('zh-CN')}`,time:row.createdAt,tone:'primary'})),
    ...(data?.tasks ?? []).slice(0, 3).map(task => ({
      id: task.id,
      title: `${task.product} 提交商品审核`,
      detail: `${task.merchant} · ${task.submittedAt}`,
      tone: 'warning',time:task.submittedAt,
    })),
    ...(data?.recentOrders ?? []).slice(0, 3).map(order => ({
      id: order.id,
      title: `${order.buyer} 创建订单 ${order.id}`,
      detail: `${formatMoney(Number(order.amount || 0))} · ${order.status}`,
      tone: /完成|收货/.test(order.status) ? 'success' : 'primary',time:order.orderedAt,
    })),
  ].sort((a,b)=>b.time.localeCompare(a.time)).slice(0, 20);

  return <div className="business-dashboard">
    <header className="dashboard-v3-heading">
      <div>
        <h1>经营工作台</h1>
        <p>集中查看商城经营数据、订单趋势与待处理事项。</p>
      </div>
      <div className="dashboard-live-state">
        <button onClick={() => load(days)} disabled={loading} aria-label="刷新工作台数据">
          <RefreshCw className={loading ? 'is-spinning' : ''} size={17} /> 刷新
        </button>
      </div>
    </header>

    {error ? <div className="dashboard-error">{error}<button onClick={() => load(days)}>重新加载</button></div> : null}

    <section className="dashboard-v3-metrics" aria-label="核心经营指标">
      {metricCards.map(({ label, value, note, icon: Icon, color, values, trendLabel }) => <article className="dashboard-v3-metric" key={label}>
        <div className="dashboard-metric-top">
          <div>
            <p>{label}</p>
            <strong>{loading && !data ? '—' : value}</strong>
          </div>
          <span style={{ color, background: `${color}14` }}><Icon size={21} /></span>
        </div>
        <div className="dashboard-metric-bottom">
          <em><Activity size={13} />{note}</em>
          {values.length?<span className="dashboard-spark-wrap" role="img" aria-label={`${trendLabel}：${values.join('、')}`} title={trendLabel}><MiniTrend values={values} color={color}/></span>:null}
        </div>
      </article>)}
    </section>

    <section className="dashboard-v3-analysis">
      <article className="panel dashboard-analysis-card">
        <header>
          <div><h2>经营分析概览</h2><p>订单量与成交额（每百元）对比</p></div>
          <div className="dashboard-range-tabs" role="tablist" aria-label="统计周期">
            <button className={days === 7 ? 'active' : ''} onClick={() => setDays(7)}>近7天</button>
            <button className={days === 30 ? 'active' : ''} onClick={() => setDays(30)}>近30天</button>
          </div>
        </header>
        <div className="dashboard-chart-legend"><span><i className="orders" />订单量</span><span><i className="sales" />成交额（百元）</span></div>
        <div className={`dashboard-column-chart ${loading ? 'is-loading' : ''}`}>
          <div className="dashboard-grid-lines" aria-hidden="true"><i /><i /><i /><i /></div>
          {trend.map((item, index) => <div className="dashboard-column" key={`${item.label}-${index}`} {...describe(`${item.label} · 订单量 ${item.orders} 单 · 成交额 ${formatMoney(item.turnover)}`)}>
            <div className="dashboard-bar-pair">
              <i className="orders-bar" style={{ height: `${item.orders / maxBar * 100}%` }} {...describe(`${item.label} · 订单量 ${item.orders} 单 · 成交额 ${formatMoney(item.turnover)}`)} />
              <i className="sales-bar" style={{ height: `${(item.turnover / 100) / maxBar * 100}%` }} {...describe(`${item.label} · 成交额 ${formatMoney(item.turnover)} · 订单量 ${item.orders} 单`)} />
            </div>
            <span>{item.label}</span>
          </div>)}
          {!loading && trend.length === 0 ? <div className="dashboard-chart-empty">暂无订单趋势数据</div> : null}
        </div>
      </article>

      <article className="panel dashboard-category-card">
        <header><div><h2>商品分类占比</h2><p>按当前在库商品实时统计</p></div><button onClick={() => navigate('/categories')}>分类管理</button></header>
        <div className="dashboard-category-list">
          {(data?.distribution ?? []).filter(item=>item.value>0).map((item, index) => {
            const percent = totalProducts ? item.value / totalProducts * 100 : 0;
            return <div className="dashboard-category-row" key={item.name}>
              <div><span>{item.name}</span><b>{item.value} 件 · {percent.toFixed(1)}%</b></div>
              <div className="dashboard-progress" {...describe(`${item.name} · ${item.value} 件 · 占比 ${percent.toFixed(1)}%`)}><i style={{ width: `${percent}%`, background: CHART_COLORS[index % CHART_COLORS.length] }} /></div>
            </div>;
          })}
          {!loading && totalProducts === 0 ? <div className="dashboard-side-empty"><Box size={30} />暂无商品分类数据</div> : null}
        </div>
        <footer><Box size={15} /><span>商品总数</span><strong>{totalProducts.toLocaleString()} 件</strong></footer>
      </article>
    </section>

    <section className="dashboard-v3-bottom">
      <article className="panel dashboard-orders-card">
        <header>
          <div><h2>最近订单</h2><p>最新产生的商城订单与履约状态</p></div>
          <button onClick={() => navigate('/orders')}>查看全部订单</button>
        </header>
        <div className="table-scroll">
          <table>
            <thead><tr><th>订单编号</th><th>购买用户</th><th>所属商家</th><th>订单金额</th><th>当前状态</th><th>下单时间</th></tr></thead>
            <tbody>{(data?.recentOrders ?? []).map(order => <tr key={order.id}>
              <td><strong>{order.id}</strong></td><td>{order.buyer}</td><td>{order.shop}</td><td>{formatMoney(Number(order.amount || 0))}</td><td><StatusTag value={order.status} />{order.after_sale_state?<StatusTag value={order.after_sale_state}/>:null}</td><td>{new Date(order.orderedAt).toLocaleString('zh-CN')}</td>
            </tr>)}</tbody>
          </table>
          {!loading && !(data?.recentOrders?.length) ? <div className="dashboard-table-empty">暂无订单记录</div> : null}
        </div>
      </article>

      <article className="panel dashboard-activity-card">
        <header><div><h2>最新动态</h2><p>管理操作、审核与订单按时间更新</p></div><Clock3 size={18} /></header>
        <div className="dashboard-timeline">
          {activities.map(item => <div className={`dashboard-timeline-item tone-${item.tone}`} key={`${item.id}-${item.title}`}>
            <i />
            <div><strong>{item.title}</strong><span>{item.detail}</span></div>
          </div>)}
          {!loading && activities.length === 0 ? <div className="dashboard-side-empty"><Clock3 size={30} />暂无最新动态</div> : null}
        </div>
        <footer>
          <button onClick={() => navigate('/reviews')}><PackageCheck size={15} />待审核 {metrics.pendingReviews}</button>
          <button onClick={() => navigate('/orders')}><Truck size={15} />待发货 {metrics.pendingShipment}</button>
        </footer>
      </article>
    </section>
    {tooltip?createPortal(<div id="dashboard-chart-tooltip" role="tooltip" className="dashboard-chart-tooltip" style={{left:tooltip.x,top:tooltip.y}}>{tooltip.text}</div>,document.body):null}
  </div>;
}
