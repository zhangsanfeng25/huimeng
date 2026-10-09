// Real Android image picker/crop/upload verification on the isolated QA service.
const {chromium}=require('C:/Users/Hugo-Gao/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {execFileSync}=require('child_process'),fs=require('fs'),assert=require('assert/strict');
const adb='D:/ruanjian/Android-Studio-SDK/platform-tools/adb.exe',out='D:/bishe/huimeng/output/interface-completion-20261004',base='http://127.0.0.1:3300/api/v1/';
const shell=(...args)=>execFileSync(adb,['shell',...args],{encoding:'utf8',timeout:20000}),delay=ms=>new Promise(r=>setTimeout(r,ms));
async function nodes(){shell('uiautomator','dump','/sdcard/product-qa.xml');return [...shell('cat','/sdcard/product-qa.xml').matchAll(/<node\s+([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(v=>[v[1],v[2]])));}
const id=id=>v=>v['resource-id']==='com.huimeng.bookshop:id/'+id,text=text=>v=>v.text===text;
async function find(p,timeout=15000){let until=Date.now()+timeout;while(Date.now()<until){const v=(await nodes()).find(p);if(v)return v;await delay(200);}throw Error('Missing '+p);}
async function tap(p){const v=await find(p),b=v.bounds.match(/\d+/g).map(Number);shell('input','tap',String((b[0]+b[2])/2|0),String((b[1]+b[3])/2|0));}
async function enter(field,value){await tap(id(field));shell('input','text',value.replace(/ /g,'%s'));shell('input','keyevent','KEYCODE_BACK');}
async function scroll(field){for(let i=0;i<7;i++){if((await nodes()).some(id(field)))return;shell('input','swipe','530','1900','530','750','350');}throw Error('Missing '+field);}
function shot(name){fs.writeFileSync(out+'/screenshots/'+name+'.png',execFileSync(adb,['exec-out','screencap','-p']));}
(async()=>{
 const account=JSON.parse(fs.readFileSync(out+'/integration-result.json')).account;
 if(process.argv[2]==='start'){
  const browser=await chromium.launch({channel:'chrome',headless:true}),page=await browser.newPage({viewport:{width:720,height:960}});
  await page.setContent('<body style="margin:0;background:#f3ecfc;color:#543b77;display:grid;place-items:center;font:40px Georgia;height:960px"><main style="text-align:center;border:4px solid #a38cc3;padding:70px"><div style="font-size:100px">BOOK</div><p>QA Reading House</p><p style="font:24px sans-serif">Image upload test fixture</p></main></body>');
  await page.screenshot({path:out+'/qa-cover.jpg',type:'jpeg'});await browser.close();
  execFileSync(adb,['push',out+'/qa-cover.jpg','/sdcard/Download/qa-cover.jpg']);shell('am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Download/qa-cover.jpg');
  shell('am','force-stop','com.huimeng.bookshop');shell('am','start','-n','com.huimeng.bookshop/.ui.WelcomeActivity');await enter('accountInput',account);await enter('passwordInput','demo12345');await tap(id('loginButton'));await find(text('我的'));await tap(text('我的'));await tap(id('switchRole'));await tap(id('merchantCard'));await find(text('概览'));await tap(text('商品'));await tap(id('add'));await find(id('name'));await enter('name','QA Image Book');await enter('summary','A QA picture book with a real uploaded cover.');await enter('age','3-6');await enter('price','28.5');await enter('stock','12');await scroll('addImages');shot('app-product-images-empty');await tap(id('addImages'));await delay(1200);console.log(JSON.stringify(await nodes()));shot('app-product-picker');return;
 }
 if(process.argv[2]==='pick'){
  await tap(v=>v['content-desc'].startsWith('Photo taken on Oct 4, 2026')&&v.clickable==='true');await tap(v=>v.text.startsWith('Add ('));await find(text('使用这张图片'));shot('app-product-crop');await tap(text('使用这张图片'));await find(v=>v.text.includes('已选 1/9'));shot('app-product-uploaded-cover');await tap(v=>v['content-desc']==='商品图片 1，封面');await delay(500);shot('app-product-cover-preview');shell('input','keyevent','KEYCODE_BACK');console.log('Real picker, crop and preview passed');return;
 }
 if(process.argv[2]==='save'){
  await find(v=>v.text.includes('已选 1/9'));shot('app-product-uploaded-cover');await scroll('save');await tap(id('save'));await find(text('商品管理'));await find(v=>v.text.includes('QA Image Book'));
  const response=await fetch(base+'auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:account,password:'demo12345',role:'MERCHANT'})});assert.equal(response.status,200);const session=await response.json(),headers={Authorization:'Bearer '+session.token};const products=await (await fetch(base+'merchant/products',{headers})).json(),product=products.data.find(v=>v.name==='QA Image Book');assert.ok(product);const details=(await (await fetch(base+'merchant/products/'+product.id,{headers})).json()).data;assert.equal(details.detailImages.length,1);assert.equal(details.coverUrl,details.detailImages[0]);assert.equal((await fetch('http://127.0.0.1:3300'+details.coverUrl)).status,200);
  await tap(text('管理'));await find(v=>v.text.includes('已选 1/9'));await scroll('imageHint');shot('app-product-editor-saved');
  fs.writeFileSync(out+'/product-image-result.json',JSON.stringify({status:'passed',productId:product.id,coverUrl:details.coverUrl,checks:['real picker and crop','real upload','cover preview','save','reopen retains images','shared HTTP image']},null,2));console.log('Product image QA passed');return;
 }
 throw Error('Use start or save');
})().catch(e=>{console.error(e);process.exit(1)});
