import assert from 'node:assert/strict';

const base=process.env.API_URL||'http://127.0.0.1:3000/api/v1';
async function call(path,{token,method='GET',body,headers={}}={}){
  const response=await fetch(`${base}${path}`,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{}) ,...headers},body:body?JSON.stringify(body):undefined});
  const data=await response.json();if(!response.ok)throw new Error(`${method} ${path}: ${response.status} ${data.message||''}`);return data;
}
const login=async(username,password,role)=>(await call('/auth/login',{method:'POST',body:{username,password,role}})).token;

const user=await login('xingxing','demo12345','USER');
const merchant=await login('merchant_a','demo12345','MERCHANT');
const admin=await login('admin','huimeng123','ADMIN');
const products=await call('/catalog/products');assert.ok(products.data.length>=20);
const addresses=await call('/me/addresses',{token:user});assert.ok(addresses.data.length>=1);
const key=`smoke-${Date.now()}`;
const checkout=await call('/checkout',{token:user,method:'POST',headers:{'Idempotency-Key':key},body:{addressId:addresses.data[0].id,items:[{productId:'P0001',quantity:1}]}});
const repeated=await call('/checkout',{token:user,method:'POST',headers:{'Idempotency-Key':key},body:{addressId:addresses.data[0].id,items:[{productId:'P0001',quantity:1}]}});assert.equal(repeated.checkoutGroupId,checkout.checkoutGroupId);
await call(`/checkout/${checkout.checkoutGroupId}/pay`,{token:user,method:'POST',headers:{'Idempotency-Key':`${key}-pay`},body:{}});
const merchantOrders=await call('/merchant/orders',{token:merchant});const order=merchantOrders.data.find(item=>item.checkout_group_id===checkout.checkoutGroupId);assert.ok(order);
for(let step=0;step<4;step++)await call(`/merchant/orders/${order.id}/advance`,{token:merchant,method:'POST',body:{}});
await call(`/me/orders/${order.id}/confirm`,{token:user,method:'POST',body:{}});
const detail=await call(`/me/orders/${order.id}`,{token:user});assert.equal(detail.data.status,'COMPLETED');assert.equal(detail.data.logistics_status,'COMPLETED');assert.ok(detail.data.items.length>=1);assert.ok(detail.data.events.length>=5);
const post=await call('/community/posts',{token:user,method:'POST',body:{title:'测试共读记录',content:'同步检查 '+key}});
const posts=await call('/community/posts',{token:user});assert.ok(posts.data.some(item=>item.id===post.id));
const notice=await call('/notices',{token:admin,method:'POST',body:{title:'测试通知',content:key,audience:'指定账号',account:'xingxing'}});
const notifications=await call('/me/notifications',{token:user});assert.ok(notifications.data.some(item=>item.id===notice.data.id));
console.log(JSON.stringify({ok:true,checkoutGroupId:checkout.checkoutGroupId,orderId:order.id,communityPosts:posts.data.length,notifications:notifications.data.length,flow:'下单→幂等重试→模拟支付→准备出库→发货→运输→派送→用户收货→订单详情'},null,2));
