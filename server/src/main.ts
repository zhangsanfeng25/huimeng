import express from 'express';
import helmet from 'helmet';
import { existsSync, mkdirSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.js';
import { authRouter } from './modules/auth.js';
import { adminRouter } from './modules/admin.js';
import { shopRouter } from './modules/shop.js';
import { communityRouter } from './modules/community.js';
import { syncRouter } from './modules/sync.js';
import { customerServicesRouter } from './modules/customer-services.js';
import './core/db.js';

mkdirSync(config.uploadDir,{recursive:true});
const app=express();
app.disable('x-powered-by');
if(process.env.HUIMENG_DEBUG==='1'){
  app.use((req,res,next)=>{
    if(req.path==='/api/v1/health')return next();
    const started=Date.now();
    res.on('finish',()=>console.debug(`[DEBUG] ${req.method} ${req.path} ${res.statusCode} ${Date.now()-started}ms`));
    next();
  });
}
app.use(helmet({crossOriginResourcePolicy:{policy:'cross-origin'},contentSecurityPolicy:{directives:{'img-src':["'self'",'data:','blob:','https:','http:']}}}));
app.use(express.json({limit:'1mb'}));
app.use('/api', (_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
app.use('/public/default-avatars',express.static(fileURLToPath(new URL('../assets/default-avatars/',import.meta.url)),{fallthrough:false,maxAge:'1d'}));
// Merchant uploads take precedence; bundled title artwork supplies the historical demo URLs.
app.use('/public/books',express.static(join(config.uploadDir,'books')),express.static(fileURLToPath(new URL('../assets/books/',import.meta.url)),{fallthrough:false,maxAge:'1d'}));
app.use('/public',express.static(config.uploadDir,{fallthrough:false}));
app.get('/api/v1/health',(_req,res)=>res.json({status:'ok',service:'huimeng-api',time:new Date().toISOString()}));
app.use('/api/v1/auth',authRouter);
app.use('/api/v1',shopRouter);
app.use('/api/v1',communityRouter);
app.use('/api/v1',syncRouter);
app.use('/api/v1',customerServicesRouter);
app.use('/api/v1',adminRouter);

const webDist=resolve(process.cwd(),'../html/dist');
if(existsSync(webDist)){
  app.use('/assets',(req,res,next)=>{
    res.vary('Accept-Encoding');
    const file=basename(req.path);
    if(!['GET','HEAD'].includes(req.method)||req.path!==`/${file}`||!req.acceptsEncodings('gzip')||!/^.+\.(js|css)$/.test(file))return next();
    const compressed=join(webDist,'assets',`${file}.gz`);
    if(!existsSync(compressed))return next();
    res.type(file.endsWith('.js')?'application/javascript':'text/css').set('Content-Encoding','gzip');
    return res.sendFile(compressed,{maxAge:'1y',immutable:true});
  },express.static(join(webDist,'assets'),{maxAge:'1y',immutable:true}));
  app.use(express.static(webDist,{maxAge:'1d',setHeaders:(res,path)=>{if(path.endsWith('index.html'))res.setHeader('Cache-Control','no-cache');}}));
  app.get(/^(?!\/api\/|\/public\/).*/,(_req,res)=>res.sendFile(join(webDist,'index.html')));
}

app.use((error:any,_req:express.Request,res:express.Response,_next:express.NextFunction)=>{
  if(error?.status===404)return res.status(404).json({message:'图片或资源不存在'});
  console.error(error);
  if(error?.code==='SQLITE_CONSTRAINT_UNIQUE')return res.status(409).json({message:'数据已存在，请勿重复提交'});
  if(error?.code?.startsWith('SQLITE_CONSTRAINT'))return res.status(400).json({message:'数据有关联或不符合业务规则'});
  return res.status(500).json({message:config.demoMode?String(error?.message||'服务器内部错误'):'服务器内部错误'});
});

app.listen(config.port,config.host,()=>console.log(`绘梦 API 已启动：http://${config.host}:${config.port}`));
