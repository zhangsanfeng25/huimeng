import {db,now} from '../core/db.js';
import {audit} from '../core/http.js';
import type {BusinessContext,BusinessResult} from './context.js';

/** Live inventory adjustment never changes a revision or publication state. */
export function productStock(ctx:BusinessContext):BusinessResult {
 const {stock,expectedStock}=ctx.payload??{};
 if(!Number.isInteger(stock)||stock<0||stock>2147483647||!Number.isInteger(expectedStock)||expectedStock<0||expectedStock>2147483647)
  return ctx.reply(400,{message:'库存必须为有效的非负整数，请重新读取当前库存'});
 const id=ctx.params.productId;
 const result=db.transaction(()=>{
  const old=db.prepare('SELECT stock_available stock FROM products WHERE id=? AND merchant_id=?').get(id,ctx.actor.merchantId) as {stock:number}|undefined;
  if(!old)return ctx.reply(404,{message:'商品不存在'});
  if(old.stock!==expectedStock)return ctx.reply(409,{message:'库存已被订单或其他操作更新，请重新打开库存调整后再试'});
  const delta=stock-old.stock;
  if(delta!==0){
   const stamp=now();
   db.prepare('UPDATE products SET stock_available=?,updated_at=? WHERE id=?').run(stock,stamp,id);
   db.prepare("INSERT INTO stock_movements(product_id,type,available_delta,reserved_delta,created_at) VALUES(?,'ADJUST',?,0,?)").run(id,delta,stamp);
   audit(ctx.actor.id,ctx.actor.name,'商品','调整库存',id,'成功',old.stock+' → '+stock);
  }
  return ctx.reply(200,{ok:true,stock,changed:delta!==0});
 })();
 if(result.status===200&&result.data.changed)ctx.emit('products','stock');
 return result;
}
