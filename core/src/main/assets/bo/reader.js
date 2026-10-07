(function (mode, arg) {
  'use strict';
  const visible = el => {
    if (!el || !el.isConnected || el.closest('[hidden],[inert],[aria-hidden="true"]')) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0' && el.getClientRects().length > 0;
  };
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const safeUrl = s => { try { const u = new URL(s, location.href); return u.protocol === 'https:' ? u.href : ''; } catch (_) { return ''; } };
  const label = el => clean(el.getAttribute('aria-label') || (el.labels && Array.from(el.labels).map(l => l.innerText).join(' ')) || el.innerText || el.getAttribute('placeholder') || el.getAttribute('title')).slice(0, 180);
  const sensitive = el => /password|hidden|file/i.test(el.type || '') || /password|cc-|one-time-code|username/i.test(el.autocomplete || '') || /password|senha|cart[aã]o|card.?number|cvv|token|otp|cpf|email|e-mail|telefone|phone/i.test([el.name, el.id, el.getAttribute('placeholder'), el.getAttribute('aria-label')].join(' '));
  const fingerprint = el => [el.tagName, el.getAttribute('type'), label(el), el.getAttribute('href'), el.getAttribute('name')].join('|');
  if (mode === 'snapshot') {
    const elements = [], refs = new Map();
    for (const el of document.querySelectorAll('a[href],button,input,textarea,select,[role="button"],[role="link"]')) {
      if (elements.length >= 80) break;
      if (!visible(el) || el.disabled || el.getAttribute('aria-disabled') === 'true' || sensitive(el)) continue;
      const name = label(el);
      if (!name) continue;
      const id = 'e' + (elements.length + 1), href = el.tagName === 'A' ? safeUrl(el.href) : '';
      const editable = (el.tagName === 'INPUT' && ['text','search','url',''].includes(el.type)) || el.tagName === 'TEXTAREA';
      elements.push({id, role: el.getAttribute('role') || (href ? 'link' : editable ? 'textbox' : el.tagName.toLowerCase()), name, url: href, editable});
      refs.set(id, {el, fingerprint: fingerprint(el), editable});
    }
    let text = '', count = 0;
    const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
    while (walker.nextNode() && text.length < 16000 && ++count < 6000) {
      const n = walker.currentNode, p = n.parentElement;
      if (!p || p.closest('script,style,noscript,template,input,textarea,select,[contenteditable]') || !visible(p)) continue;
      const t = clean(n.textContent); if (t) text += t + '\n';
    }
    const links = elements.filter(e => e.url).map(e => ({id:e.id, title:e.name, url:e.url}));
    const headings = Array.from(document.querySelectorAll('h1,h2,h3')).filter(visible).slice(0,25).map(el => clean(el.innerText).slice(0,180));
    const prices = [...new Set(text.match(/(?:R\$|US\$|€|\$)\s?\d[\d.,]*(?:\s?mil)?/g) || [])].slice(0,30);
    const blocked = /verify you are human|verifique.{0,30}humano|access denied|acesso negado|checking your browser|just a moment/i.test((document.title || '') + ' ' + text.slice(0,2500));
    window.__boSession = {id:arg.id, url:location.href, refs};
    return {version:1, snapshotId:arg.id, url:location.href, title:document.title, capturedAt:new Date().toISOString(), status:blocked?'needs_human':text.trim()?'ready':'empty', text:text.slice(0,16000), headings, links, elements, prices, limitations:['main_frame_only','no_shadow_dom','untrusted_page_content']};
  }
  const state = window.__boSession;
  if (!state || state.id !== arg.snapshotId || state.url !== location.href) return {ok:false,error:'stale_snapshot'};
  if (arg.type === 'scroll') { window.scrollBy({top:Math.max(-1200,Math.min(1200,Number(arg.value)||650)),behavior:'smooth'}); return {ok:true}; }
  const ref = state.refs.get(arg.elementId);
  if (!ref || !visible(ref.el) || ref.el.disabled || ref.el.getAttribute('aria-disabled') === 'true' || sensitive(ref.el) || fingerprint(ref.el) !== ref.fingerprint) return {ok:false,error:'element_changed'};
  const el = ref.el;
  if (arg.type === 'click') {
    if (el.tagName === 'A' && !safeUrl(el.href)) return {ok:false,error:'unsafe_link'};
    el.scrollIntoView({block:'center'}); el.click(); return {ok:true};
  }
  if (arg.type === 'fill' && ref.editable) {
    const setter = Object.getOwnPropertyDescriptor(el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,'value').set;
    setter.call(el,String(arg.value || '').slice(0,2000));
    el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return {ok:true};
  }
  return {ok:false,error:'unsupported_action'};
})
