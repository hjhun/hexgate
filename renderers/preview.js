import DOMPurify from 'dompurify';
import { marked } from 'marked';
import mermaid from 'mermaid';

// Mermaid owns shared renderer state. Serialize work and discard superseded requests.
let generation=0;
let renderQueue=Promise.resolve();
window.renderDocument = (payload) => {
  const current=++generation;
  window.previewReady=false;
  renderQueue=renderQueue.catch(()=>{}).then(async () => {
    if(current!==generation)return;
    await render(payload,current);
  });
  return renderQueue;
};
async function render(payload,current) {
  mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:payload.light?'default':'dark',maxTextSize:100000});
  const root=document.getElementById('document');
  root.replaceChildren();
  try {
    if(payload.kind==='drawio') {
      const div=document.createElement('div');
      div.className='mxgraph';
      div.dataset.mxgraph=JSON.stringify({xml:payload.text,resize:true,nav:true,toolbar:'zoom layers lightbox',lightbox:false});
      root.append(div);
      GraphViewer.processElements();
    } else {
      root.innerHTML=DOMPurify.sanitize(marked.parse(payload.text),{FORBID_TAGS:['style','iframe','object','embed','form'],FORBID_ATTR:['style'],ALLOW_UNKNOWN_PROTOCOLS:false});
      let i=0;
      for(const code of root.querySelectorAll('code.language-mermaid')) {
        const wrapper=document.createElement('div');
        code.parentElement.replaceWith(wrapper);
        try {const result=await mermaid.render('diagram'+i++,code.textContent);if(current!==generation)return;wrapper.innerHTML=DOMPurify.sanitize(result.svg,{USE_PROFILES:{svg:true,svgFilters:true},ADD_TAGS:['foreignObject'],ADD_ATTR:['xmlns']});}catch(e){wrapper.textContent='Mermaid rendering failed: '+String(e).slice(0,200);}
      }
      let embedded=0;
      for(const image of root.querySelectorAll('img[src]')) {
        const source=image.getAttribute('src');
        if(!/\.(drawio|xml)(?:$|[?#])/i.test(source))continue;
        const div=document.createElement('div');image.replaceWith(div);
        try {
          if(++embedded>8)throw new Error('At most eight embedded diagrams per document');
          const url=new URL(source,location.href);
          if(url.origin!==location.origin || !url.pathname.startsWith('/remote/'))throw new Error('Diagram must be a relative remote file');
          const response=await fetch(url);if(!response.ok)throw new Error('Diagram resource unavailable');
          const xml=await response.text();if(current!==generation)return;
          div.className='mxgraph';div.dataset.mxgraph=JSON.stringify({xml,resize:true,nav:true,lightbox:false});
        }catch(e){div.textContent='draw.io preview unavailable: '+String(e).slice(0,120);}
      }
      if(embedded>0)GraphViewer.processElements();
      for(const a of root.querySelectorAll('a[href]')) {
        if(/\.(drawio|xml)(?:$|[?#])/i.test(a.getAttribute('href')))a.textContent+=' (open draw.io preview)';
      }
    }
    if(current===generation)window.previewReady=true;
  } catch(e) {if(current!==generation)return;root.textContent='Preview failed: '+String(e).slice(0,200);window.previewReady=false;}
};
