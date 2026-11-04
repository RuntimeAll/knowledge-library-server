package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.publication.PublicationModels.Input;
import java.util.List;

/** Escaped offline view; all text stays text and all executable code is fixed. */
final class PublicationOfflinePage {
    private PublicationOfflinePage() { }
    static String render(Input input, List<PublicationBundleService.FileFact> files) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; base-uri 'none'; form-action 'none'">
                <title>玉米资料库 · 发布资料下载</title><style>
                *{box-sizing:border-box}body{margin:0;background:#e9eee8;color:#193d34;font:14px/1.6 sans-serif}main{max-width:460px;margin:auto;background:#f8f9f4;padding:18px 16px 95px;min-height:100vh}h1{font-size:18px;margin:12px 0}h2{font-size:16px}button,a{color:#235a49}button{padding:9px 12px;border:1px solid #c4d0bd;background:white;border-radius:9px;cursor:pointer}.images{display:grid;grid-template-columns:repeat(3,1fr);gap:14px 8px}.images img{width:100%;aspect-ratio:.71;object-fit:contain;background:white;border:1px solid #dbe1d3;border-radius:8px}figure{margin:0}figcaption{font-size:11px;overflow-wrap:anywhere}article{background:white;border:1px solid #dce4d4;border-radius:12px;padding:13px;margin:12px 0}.row{display:flex;justify-content:space-between;align-items:center;gap:10px}pre{font:inherit;white-space:pre-wrap;overflow-wrap:anywhere;margin:12px 0}nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(460px,100%);display:flex;gap:10px;background:#f8f9f4;border-top:1px solid #dce4d4;padding:12px 16px}nav button{flex:1}.hint{font-size:12px;color:#687a6b}.collapsed{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:4;overflow:hidden}.expand{border:0;padding:2px 0;background:none;font-size:12px}.copy-status{min-height:20px}textarea{width:100%;min-height:150px;font:inherit} [hidden]{display:none!important}
                </style></head><body><main><header>禾 · 玉米资料库</header>
                """);
        html.append("<h1>").append(escape(input.title)).append("</h1><p class=hint>离线下载副本 · 已下载内容不会随后台修改更新</p><section id=images><h2>发布图片</h2><div class=images>");
        for (var file : files) {
            if (file.assetId() != null) { html.append("<figure><img src=\"").append(file.path()).append("\" alt=\"")
                    .append(escape(file.caption())).append("\"><figcaption>").append(escape(file.caption()))
                    .append(" <a download href=\"").append(file.path()).append("\">保存</a></figcaption></figure>"); }
        }
        html.append("</div><p class=hint>图片文件已全部包含在images目录；下载包不代表已保存到相册。</p></section><section id=text hidden><h2>发布文案</h2>");
        block(html, "标题", "title", input.postTitle); block(html, "正文", "body", PublicationCopyText.body(input.postBody));
        block(html, "商品上架描述", "description", input.listingDescription); block(html, "百度网盘信息", "baidu", PublicationBundleService.baidu(input));
        html.append("""
                <p class=hint>五类关联的ID与版本快照见manifest.json；实时关系请在知识库后台查看。</p></section><p id=message class=copy-status role=status></p>
                <div id=manual hidden><p>请长按或全选复制完整文字</p><textarea id=copyText readonly></textarea></div>
                <nav><button data-tab=images>▧ 图片</button><button data-tab=text>▤ 文案</button></nav></main><script>
                const body=document.getElementById('body'),expand=document.getElementById('expand');function measureBody(){if(body.clientWidth)expand.hidden=body.scrollHeight<=parseFloat(getComputedStyle(body).lineHeight)*4+2}expand.addEventListener('click',()=>{const collapsed=body.classList.toggle('collapsed');expand.textContent=collapsed?'展开全文':'收起正文';expand.setAttribute('aria-expanded',String(!collapsed))});window.addEventListener('resize',measureBody);
                document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>{['images','text'].forEach(id=>document.getElementById(id).hidden=id!==b.dataset.tab);window.scrollTo(0,0);requestAnimationFrame(measureBody)}));
                document.querySelectorAll('[data-copy]').forEach(b=>b.addEventListener('click',async()=>{const text=document.getElementById(b.dataset.copy).textContent;try{if(!navigator.clipboard)throw new Error();await navigator.clipboard.writeText(text);document.getElementById('message').textContent='完整文字已复制'}catch(e){document.getElementById('manual').hidden=false;const box=document.getElementById('copyText');box.value=text;box.focus();box.select()}}));
                </script></body></html>
                """);
        return html.toString();
    }
    private static void block(StringBuilder html, String label, String id, String text) {
        html.append("<article><div class=row><strong>").append(label).append("</strong><button data-copy=").append(id)
                .append(">复制</button></div><pre id=").append(id).append("body".equals(id) ? " class=collapsed>" : ">").append(escape(text)).append("</pre>");
        if ("body".equals(id)) { html.append("<button id=expand class=expand aria-expanded=false hidden>展开全文</button>"); }
        html.append("</article>");
    }
    private static String escape(String value) {
        return PublicationBundleService.text(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
