'use strict';
window.diagramState='loading';
let diagramSvg=null,serial=0;
function report(state){window.diagramState=state;if(window.NotePocketDiagram)NotePocketDiagram.status(state);}
mermaid.initialize({startOnLoad:false,securityLevel:'strict',suppressErrorRendering:true,maxTextSize:50000,maxEdges:500,theme:'base',fontFamily:'sans-serif',htmlLabels:false,flowchart:{htmlLabels:false,useMaxWidth:false},themeVariables:{primaryColor:'#E4EDDd',primaryTextColor:'#203C32',primaryBorderColor:'#789A83',lineColor:'#477461',secondaryColor:'#F7E9CB',tertiaryColor:'#EEF2F5'},secure:['securityLevel','startOnLoad','maxTextSize','maxEdges','htmlLabels','flowchart','theme','themeVariables','fontFamily']});
window.drawDiagram=async function(code){
 const request=++serial;diagramSvg=null;report('rendering');const stage=document.getElementById('stage');stage.textContent='กำลังสร้างแผนภาพ…';
 try{
  const result=await mermaid.render('notepocketDiagram'+request,code);if(request!==serial)return;
  stage.innerHTML=result.svg;diagramSvg=stage.querySelector('svg');if(!diagramSvg)throw Error('No SVG');
  // No links, images, embedded HTML or diagram actions leave this isolated viewer.
  for(const el of stage.querySelectorAll('script,iframe,object,embed,image,foreignObject'))el.remove();
  for(const el of stage.querySelectorAll('*'))for(const attribute of [...el.attributes])if(/^on/i.test(attribute.name)||['href','xlink:href','target'].includes(attribute.name))el.removeAttribute(attribute.name);
  diagramSvg.setAttribute('role','img');diagramSvg.setAttribute('aria-label','แผนภาพ Mermaid');report('ready');
 }catch(e){if(request!==serial)return;stage.textContent='แสดงแผนภาพไม่ได้ กรุณาตรวจไวยากรณ์ Mermaid ในแท็บโค้ด';report('error');}
};
window.exportDiagram=async function(){
 if(!diagramSvg)return;report('exporting');
 try{
  const svg=diagramSvg.cloneNode(true);const box=diagramSvg.viewBox.baseVal;let w=box.width||diagramSvg.getBoundingClientRect().width,h=box.height||diagramSvg.getBoundingClientRect().height;
  if(!Number.isFinite(w)||!Number.isFinite(h)||w<=0||h<=0)throw Error('Size');
  let scale=Math.min(2,3072/w,3072/h,Math.sqrt(6000000/(w*h)));w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));
  svg.setAttribute('width',w);svg.setAttribute('height',h);svg.setAttribute('xmlns','http://www.w3.org/2000/svg');svg.style.width=w+'px';svg.style.height=h+'px';
  const blob=new Blob([new XMLSerializer().serializeToString(svg)],{type:'image/svg+xml;charset=utf-8'});const url=URL.createObjectURL(blob);
  try{
   const image=new Image();await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=reject;image.src=url;});
   const canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;const ctx=canvas.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,w,h);ctx.drawImage(image,0,0,w,h);
   const data=canvas.toDataURL('image/png').split(',')[1];if(data.length>16000000)throw Error('Export too large');
   NotePocketDiagram.beginPng();let index=0;for(let at=0;at<data.length;at+=48000)NotePocketDiagram.pngChunk(index++,data.slice(at,at+48000));NotePocketDiagram.endPng();report('ready');
  }finally{URL.revokeObjectURL(url);}
 }catch(e){window.diagramExportError=e.name+': '+e.message;console.error('NotePocket PNG '+window.diagramExportError);report('export-error');}
};
report('loaded');
