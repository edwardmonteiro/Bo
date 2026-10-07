const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
  let executablePath=process.env.BO_CHROMIUM;
  let args=['--no-sandbox'];
  if(!executablePath && process.env.BO_USE_SPARTICUZ){const imported=require('@sparticuz/chromium'); const c=imported.default || imported; executablePath=await c.executablePath();args=c.args;}
  const browser=await chromium.launch({headless:true,executablePath,args});
  try{
    const page=await browser.newPage({viewport:{width:390,height:844}});
    const source=fs.readFileSync(path.join(__dirname,'../core/src/main/assets/bo/reader.js'),'utf8');
    const html=`<html><title>Catálogo</title><body><h1>Produtos</h1><p>Mochila R$ 249,90</p>
      <a href="https://example.com/item">Ver mochila</a><a href="javascript:alert(1)">Ruim</a>
      <label for="q">Buscar produto</label><input id="q" type="search"><button id="go" onclick="document.querySelector('#result').textContent=document.querySelector('#q').value">Pesquisar</button><p id="result"></p>
      <label>Senha<input type="password" value="SECRET_PASSWORD"></label>
      <input placeholder="email" value="SECRET_EMAIL"><textarea placeholder="Nota">SECRET_TEXTAREA</textarea>
      <p hidden>SECRET_HIDDEN</p><div style="display:none"><a href="https://hidden.example">Secret hidden link</a></div>
      <div contenteditable="true">SECRET_EDITABLE</div><script>const secret='SECRET_SCRIPT';</script>
      <button disabled>Desativado</button><button aria-disabled="true">Aria desativado</button>
      </body></html>`;
    await page.route('https://example.com/**',r=>r.fulfill({body:html,contentType:'text/html'}));
    await page.goto('https://example.com/');
    const run=(mode,arg)=>page.evaluate(({source,mode,arg})=>(0,eval)(source)(mode,arg),{source,mode,arg});
    let count=0;
    const check=(name,fn)=>{fn();console.log('PASS '+name);count++;};
    let s=await run('snapshot',{id:'s1'});
    check('extracts live text, links, headings, prices and source',()=>{assert.equal(s.url,'https://example.com/');assert.equal(s.prices[0],'R$ 249,90');assert.equal(s.links[0].url,'https://example.com/item');assert.equal(s.headings[0],'Produtos');assert.equal(s.status,'ready');assert.ok(Date.parse(s.capturedAt));});
    check('omits hidden content, scripts, input values and editable text',()=>{assert.ok(!JSON.stringify(s).includes('SECRET_'));assert.ok(!s.links.some(l=>l.url.includes('hidden')));});
    check('omits sensitive and disabled controls',()=>{assert.ok(!s.elements.some(e=>/Senha|email|Desativado|Aria desativado/.test(e.name)));});
    let r=await run('action',{snapshotId:'bad',type:'click',elementId:'e1'});
    check('rejects stale snapshot',()=>assert.equal(r.error,'stale_snapshot'));
    let input=s.elements.find(e=>e.name==='Buscar produto');
    r=await run('action',{snapshotId:'s1',type:'fill',elementId:input.id,value:'mochila'});
    check('fills eligible search field',()=>assert.equal(r.ok,true));assert.equal(await page.locator('#q').inputValue(),'mochila');
    let button=s.elements.find(e=>e.name==='Pesquisar');
    r=await run('action',{snapshotId:'s1',type:'click',elementId:button.id});
    check('executes a valid element click',()=>assert.equal(r.ok,true));assert.equal(await page.locator('#result').textContent(),'mochila');
    await page.locator('#go').evaluate(e=>e.textContent='Comprar agora');
    r=await run('action',{snapshotId:'s1',type:'click',elementId:button.id});
    check('rejects changed element while awaiting approval',()=>assert.equal(r.error,'element_changed'));
    const bad=s.elements.find(e=>e.name==='Ruim');
    r=await run('action',{snapshotId:'s1',type:'click',elementId:bad.id});
    check('rejects javascript links',()=>assert.equal(r.error,'unsafe_link'));
    await page.locator('#q').evaluate(e=>e.type='password');
    r=await run('action',{snapshotId:'s1',type:'fill',elementId:input.id,value:'blocked'});
    check('rejects control changed to password',()=>assert.equal(r.error,'element_changed'));
    await page.evaluate(()=>history.pushState({},'', '/next'));
    r=await run('action',{snapshotId:'s1',type:'click',elementId:'e1'});
    check('rejects same-document navigation',()=>assert.equal(r.error,'stale_snapshot'));
    await page.setContent('<title>Just a moment</title><p>Verify you are human</p>');
    s=await run('snapshot',{id:'s2'});
    check('reports human challenge instead of fake results',()=>assert.equal(s.status,'needs_human'));
    await page.setContent('<title>Empty</title>');s=await run('snapshot',{id:'s3'});
    check('reports empty pages',()=>assert.equal(s.status,'empty'));
    await page.setContent('<p>'+'x'.repeat(30000)+'</p>'+Array.from({length:200},(_,i)=>`<a href="https://example.com/${i}">Link ${i}</a>`).join(''));
    s=await run('snapshot',{id:'s4'});
    check('bounds model context and element count',()=>{assert.equal(s.text.length,16000);assert.equal(s.elements.length,80);});
    await page.setContent(fs.readFileSync(path.join(__dirname,'../core/src/main/assets/bo/demo.html'),'utf8'));
    s=await run('snapshot',{id:'demo'});check('offline demonstration is extractable',()=>{assert.deepEqual(s.prices,['R$ 249,90','R$ 89,00']);assert.ok(s.text.includes('fictícios'));});
    fs.mkdirSync(path.join(__dirname,'output'),{recursive:true});await page.screenshot({path:path.join(__dirname,'output/demo-mobile.png'),fullPage:true});
    console.log(`${count} reader checks passed. Android device and live OpenAI tests are separate.`);
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
