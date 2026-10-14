import {createHash} from 'node:crypto';
import {existsSync,mkdirSync,readFileSync,copyFileSync,writeFileSync,realpathSync} from 'node:fs';
import {resolve,join,sep,extname} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';

const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
function argument(name){const index=process.argv.indexOf(name);if(index<0||!process.argv[index+1])throw Error(`Required: ${name}`);return process.argv[index+1];}
const source=resolve(argument('--database')),userId=argument('--user'),output=resolve(argument('--output'));
const assetRoot=join(root,'android-app/app/src/main/assets/bootstrap');
if(!existsSync(source))throw Error('Database not found');
process.env.DATABASE_PATH=source;process.env.UPLOAD_DIR=join(root,'server/uploads');process.chdir(join(root,'server'));
const {db}=await import(pathToFileURL(join(root,'server/dist/core/db.js')));
const {readSyncResources}=await import(pathToFileURL(join(root,'server/dist/sync/snapshots.js')));
try{
 const user=db.prepare("SELECT id,nickname name FROM users WHERE id=? AND status='ACTIVE'").get(userId);if(!user)throw Error('Selected account is unavailable');
 const roles=db.prepare('SELECT role FROM user_roles WHERE user_id=?').all(userId).map(row=>row.role);
 const merchant=db.prepare("SELECT id FROM merchants WHERE owner_user_id=? AND approval_status='APPROVED' AND business_status='OPEN'").get(userId);
 const actor={...user,roles:roles.filter(role=>role!=='MERCHANT'||merchant),merchantId:merchant?.id,activeRole:'USER'};
 const names=['categories','products','profile','addresses','cart','favorites','posts','comments','likes','notifications','merchantApplication','merchantProducts','checkouts','buyerOrders','merchantOrders','productRatings','ownRatings'];
 const accountResources=db.transaction(()=>Object.fromEntries(names.map(name=>[name,readSyncResources(actor,name)])))();
 const publicResources=db.transaction(()=>Object.fromEntries(['categories','products','posts','comments','productRatings'].map(name=>[name,readSyncResources({id:'',name:'',roles:[]},name)])))();
 publicResources.notifications=db.prepare("SELECT id,title,content,audience,created_at,0 is_read FROM notifications WHERE user_id IS NULL AND audience='全部用户' AND status='SENT' ORDER BY created_at DESC").all();
 const urls=new Set();function inspect(value){if(typeof value==='string'){if(value.startsWith('/public/'))urls.add(value);else if(value.startsWith('[')||value.startsWith('{'))try{inspect(JSON.parse(value));}catch{}}else if(value&&typeof value==='object')for(const child of Object.values(value))inspect(child);}
 inspect(publicResources);const media={},missing=[];mkdirSync(join(assetRoot,'media'),{recursive:true});
 for(const url of urls){const defaults=url.startsWith('/public/default-avatars/'),base=defaults?join(root,'server/assets/default-avatars'):join(root,'server/uploads');const relative=url.slice(defaults?24:8),file=resolve(base,relative);
  if(!file.startsWith(resolve(base)+sep)||!existsSync(file)){missing.push(url);continue;}const actual=realpathSync(file);if(!actual.startsWith(realpathSync(base)+sep))throw Error('Media escapes allowed directory');
  const hash=createHash('sha256').update(readFileSync(actual)).digest('hex'),target='media/'+hash+extname(file).toLowerCase();copyFileSync(actual,join(assetRoot,target));media[url]=target;
 }
 function snapshot(resources,userId){const revision=createHash('sha256').update(JSON.stringify(resources)).digest('hex');return {format:1,userId,origin:'http://10.0.2.2:3000/api/v1/',revision,createdAt:new Date().toISOString(),resources};}
 mkdirSync(output,{recursive:true});writeFileSync(join(assetRoot,'public.json'),JSON.stringify({...snapshot(publicResources,''),media}));
 writeFileSync(join(output,'legacy-account.json'),JSON.stringify(snapshot(accountResources,userId)));
 const summary={userId,account:Object.fromEntries(Object.entries(accountResources).map(([name,rows])=>[name,rows.length])),public:Object.fromEntries(Object.entries(publicResources).map(([name,rows])=>[name,rows.length])),media:Object.keys(media).length,missing};
 writeFileSync(join(output,'export-summary.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify(summary,null,2));
}finally{db.close();}
