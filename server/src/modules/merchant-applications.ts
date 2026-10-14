import {businessRoute} from '../services/context.js';
import {merchantApply} from '../services/merchant.js';
import { Router } from 'express';



import {requireAuth} from '../core/http.js';
import {subscribe} from '../core/events.js';

export const merchantApplicationRouter=Router();
import {merchantApplication} from '../services/merchant-data.js';
export {merchantApplication} from '../services/merchant-data.js';
merchantApplicationRouter.get('/me/events',requireAuth(),(_req,res)=>{
  res.set({'Content-Type':'text/event-stream','Cache-Control':'no-cache','Connection':'keep-alive'});
  res.flushHeaders();res.write('event: ready\ndata: {}\n\n');subscribe(res);
});
merchantApplicationRouter.get('/me/merchant-application',requireAuth(),(req,res)=>res.json({data:merchantApplication(req.actor!.id)}));
merchantApplicationRouter.post('/me/merchant-application',requireAuth('USER'),businessRoute(merchantApply));
