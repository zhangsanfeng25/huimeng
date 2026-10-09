const {chromium}=require('C:/Users/Hugo-Gao/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('fs'),assert=require('assert/strict');
(async()=>{
 const out='D:/bishe/huimeng/output/dashboard-refinement-20261003',base='http://127.0.0.1:3300';
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const context=await browser.newContext({viewport:{width:2560,height:1270}}),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const login=await context.request.post(base+'/api/v1/auth/login',{data:{username:'admin',password:'huimeng123',role:'ADMIN'}});assert.equal(login.status(),200);
  const {token}=await login.json();await context.addInitScript(token=>sessionStorage.setItem('huimeng_admin_session',token),token);
  await page.goto(base);await page.locator('.dashboard-timeline-item').first().waitFor();
  assert.equal(await page.locator('.dashboard-mini-trend').count(),4);
  for(const group of ['.dashboard-v3-analysis','.dashboard-v3-bottom']){
   const bounds=await page.locator(group+'>article').evaluateAll(nodes=>nodes.map(v=>{const b=v.getBoundingClientRect();return{top:b.top,bottom:b.bottom};}));
   assert.ok(Math.abs(bounds[0].top-bounds[1].top)<1);assert.ok(Math.abs(bounds[0].bottom-bounds[1].bottom)<1);
  }
  assert.ok(await page.locator('.dashboard-timeline').evaluate(v=>v.scrollHeight>v.clientHeight));
  await page.locator('.dashboard-v3-bottom').scrollIntoViewIfNeeded();await page.screenshot({path:out+'/dashboard-bottom.png'});
  await page.locator('.dashboard-column').nth(1).hover();await page.getByRole('tooltip').waitFor();assert.match(await page.getByRole('tooltip').innerText(),/订单量.*成交额/);await page.screenshot({path:out+'/analysis-hover.png'});
  await page.locator('.dashboard-progress').first().hover();assert.match(await page.getByRole('tooltip').innerText(),/件.*占比/);await page.screenshot({path:out+'/category-hover.png'});
  await page.locator('.dashboard-progress').first().focus();await page.keyboard.press('Escape');await page.getByRole('tooltip').waitFor({state:'hidden'});
  await page.goto(base+'/community');await page.locator('tbody tr').first().waitFor();assert.equal(await page.getByRole('columnheader',{name:'图片',exact:true}).count(),0);assert.equal(await page.locator('tbody img').count(),0);
  await page.locator('tbody tr').filter({hasText:'今晚的亲子共读时光'}).getByRole('button',{name:'查看',exact:true}).click();await page.getByRole('heading',{name:'社区帖子详情'}).waitFor();
  await page.locator('.media-gallery img').first().waitFor();await page.waitForFunction(()=>{const img=document.querySelector('.media-gallery img');return img&&img.complete&&img.naturalWidth>0;});await page.screenshot({path:out+'/post-detail-images.png'});
  await page.getByRole('button',{name:'预览帖子图片 1',exact:true}).click();await page.getByRole('heading',{name:'帖子图片预览'}).waitFor();assert.ok(await page.locator('.media-preview img').evaluate(v=>v.naturalWidth>0));
  for(const width of [320,390,768,1024,1440]){await page.setViewportSize({width,height:900});await page.goto(base);await page.locator('.dashboard-mini-trend').first().waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'overflow at '+width);}
  assert.deepEqual(errors,[]);fs.writeFileSync(out+'/ui-result.json',JSON.stringify({passed:true,checks:['detail-button-images','image-preview','aligned-card-boundaries','internal-scrolling','four-real-sparklines','chart-tooltips','keyboard-dismiss','responsive-widths'],errors},null,2));console.log('Dashboard refinement QA passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
