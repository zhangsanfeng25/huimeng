import {Router} from 'express';
import {requireAuth} from '../core/http.js';
import {businessRoute} from '../services/context.js';
import {serviceAction,serviceDetail,serviceList,orderAfterSale} from '../services/customer-services.js';
import {SyncError} from '../sync/types.js';
import {subscribe} from '../core/events.js';

export const customerServicesRouter=Router();
customerServicesRouter.use('/service',requireAuth());
customerServicesRouter.use('/service',(req,res,next)=>{
 const requested=req.header('X-Service-Role');
 if(requested&&requested!==req.actor!.activeRole)return res.status(403).json({message:'请使用当前登录身份访问'});
 next();
});
customerServicesRouter.get('/service/events',(req,res)=>{res.set({'Content-Type':'text/event-stream','Cache-Control':'no-cache','Connection':'keep-alive'});res.flushHeaders();res.write('event: ready\ndata: {}\n\n');subscribe(res,req.actor!.id);});
customerServicesRouter.get('/service/order-after-sale/:orderId',(req,res)=>res.json({data:orderAfterSale(req.actor!,String(req.params.orderId))}));
customerServicesRouter.get('/service/admin/:kind',(req,res)=>res.json({data:serviceList(req.actor!,String(req.params.kind),true)}));
customerServicesRouter.get('/service/admin/:kind/:id',(req,res)=>res.json({data:serviceDetail(req.actor!,String(req.params.kind),String(req.params.id),true)}));
customerServicesRouter.post('/service/admin/:kind/:id',(req,res,next)=>{
 if(!req.actor!.roles.some(r=>['ADMIN','OPERATOR'].includes(r)))return res.status(403).json({message:'当前账号只能查看管理数据'});
 return businessRoute(serviceAction)(req,res,next);
});
customerServicesRouter.get('/service/:kind',(req,res)=>res.json({data:serviceList(req.actor!,String(req.params.kind))}));
customerServicesRouter.get('/service/:kind/:id',(req,res)=>{
 const before=Number(req.query.before||0);if(!Number.isSafeInteger(before)||before<0)return res.status(400).json({message:'分页参数不正确'});
 res.json({data:serviceDetail(req.actor!,String(req.params.kind),String(req.params.id),false,before)});
});
customerServicesRouter.post('/service/:kind',(req,res,next)=>{req.body={...req.body,action:'create'};return businessRoute(serviceAction)(req,res,next);});
customerServicesRouter.post('/service/:kind/:id',businessRoute(serviceAction));
customerServicesRouter.use((error:unknown,_req:import('express').Request,res:import('express').Response,next:import('express').NextFunction)=>{
 if(error instanceof SyncError)return res.status(error.status).json({message:error.message,code:error.code});next(error);
});
