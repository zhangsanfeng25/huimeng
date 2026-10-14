import type {RequestHandler} from 'express';
import {broadcast} from '../core/events.js';
import type {Actor} from '../sync/types.js';
export type BusinessResult={status:number;data:any};
export type BusinessContext={actor:Actor;payload:any;params:Record<string,string>;headers:Record<string,string|undefined>;emit:(resource:string,action:string)=>void;reply:(status:number,data:any)=>BusinessResult};
export type BusinessHandler=(context:BusinessContext)=>BusinessResult;
export function runBusiness(handler:BusinessHandler,actor:Actor,payload:unknown,params:Record<string,string>={},headers:Record<string,string|undefined>={}){
 const events:Array<{resource:string;action:string}>=[];
 const result=handler({actor,payload,params,headers,emit:(resource,action)=>{events.push({resource,action});},reply:(status,data)=>({status,data})});
 return {...result,events};
}
export function businessRoute(handler:BusinessHandler):RequestHandler{return (req,res,next)=>{try{const result=runBusiness(handler,req.actor!,req.body,req.params as Record<string,string>,req.headers as Record<string,string|undefined>);res.status(result.status).json(result.data);if(result.status<400)for(const event of result.events)broadcast(event.resource,event.action);}catch(error){next(error);}};}
