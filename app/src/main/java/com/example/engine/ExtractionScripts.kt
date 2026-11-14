package com.example.engine

/**
 * Port of `crates/obscura-js/src/markdown.rs` and DOM extraction scripts
 * used by `--dump`, `--eval`, and MCP `browser_snapshot`.
 */
object ExtractionScripts {

    /**
     * Verbatim port of `HTML_TO_MARKDOWN_JS` from `crates/obscura-js/src/markdown.rs`.
     */
    const val HTML_TO_MARKDOWN_JS: String = """
        (function() {
            function toMd(el, depth, children, cells) {
                if (!el) return '';
                if (el.nodeType === 3) return el.textContent || '';
                if (el.nodeType !== 1) return '';
                if (depth === 128) {
                    var stack = [{ node: el, kids: el.childNodes || [], index: 0, children: '', cells: [] }];
                    while (stack.length) {
                        var frame = stack[stack.length - 1];
                        if (frame.index < frame.kids.length) {
                            var child = frame.kids[frame.index++];
                            if (child.nodeType === 3) frame.children += child.textContent || '';
                            else if (child.nodeType === 1) stack.push({ node: child, kids: child.childNodes || [], index: 0, children: '', cells: [] });
                            continue;
                        }
                        var out = toMd(frame.node, -1, frame.children, frame.cells);
                        stack.pop();
                        if (!stack.length) return out;
                        var parent = stack[stack.length - 1];
                        parent.children += out;
                        parent.cells.push(out);
                    }
                }
                var tag = (el.tagName || '').toLowerCase();
                if (depth !== -1) {
                    children = '';
                    cells = tag === 'tr' ? [] : null;
                    var cn = el.childNodes || [];
                    for (var i = 0; i < cn.length; i++) {
                        var converted = toMd(cn[i], depth + 1);
                        children += converted;
                        if (cells && cn[i].nodeType === 1) cells.push(converted);
                    }
                }
                children = children.replace(/\n{3,}/g, '\n\n');
                switch(tag) {
                    case 'h1': return '\n# ' + children.trim() + '\n\n';
                    case 'h2': return '\n## ' + children.trim() + '\n\n';
                    case 'h3': return '\n### ' + children.trim() + '\n\n';
                    case 'h4': return '\n#### ' + children.trim() + '\n\n';
                    case 'h5': return '\n##### ' + children.trim() + '\n\n';
                    case 'h6': return '\n###### ' + children.trim() + '\n\n';
                    case 'p': return '\n' + children.trim() + '\n\n';
                    case 'br': return '\n';
                    case 'hr': return '\n---\n\n';
                    case 'strong': case 'b': return '**' + children + '**';
                    case 'em': case 'i': return '*' + children + '*';
                    case 'code': return '`' + children + '`';
                    case 'pre': return '\n```\n' + children + '\n```\n\n';
                    case 'blockquote': return '\n> ' + children.trim().replace(/\n/g, '\n> ') + '\n\n';
                    case 'a':
                        var href = el.getAttribute('href') || '';
                        if (href && children.trim()) return '[' + children.trim() + '](' + href + ')';
                        return children;
                    case 'img':
                        var src = el.getAttribute('src') || '';
                        var alt = el.getAttribute('alt') || '';
                        return '![' + alt + '](' + src + ')';
                    case 'ul': case 'ol':
                        return '\n' + children + '\n';
                    case 'li':
                        var listParent = el.parentNode;
                        var isOrdered = listParent && listParent.tagName && listParent.tagName.toLowerCase() === 'ol';
                        var bullet = isOrdered ? '1. ' : '- ';
                        return bullet + children.trim() + '\n';
                    case 'table': return '\n' + children + '\n';
                    case 'thead': case 'tbody': case 'tfoot': return children;
                    case 'tr':
                        for (var j = 0; j < cells.length; j++) cells[j] = cells[j].trim();
                        return '| ' + cells.join(' | ') + ' |\n';
                    case 'th': case 'td': return children;
                    case 'script': case 'style': case 'noscript': case 'link': case 'meta': return '';
                    case 'div': case 'section': case 'article': case 'main': case 'aside': case 'nav': case 'header': case 'footer':
                        return '\n' + children;
                    case 'span': return children;
                    default: return children;
                }
            }
            var body = document.body || document.documentElement;
            var md = toMd(body, 0);
            md = md.replace(/\n{3,}/g, '\n\n').trim();
            return md;
        })()
    """

    const val EXTRACT_TEXT_JS: String = """
        (function() {
            var body = document.body || document.documentElement;
            return body ? (body.innerText || body.textContent || '').trim() : '';
        })()
    """

    const val EXTRACT_HTML_JS: String = """
        (function() {
            return document.documentElement ? document.documentElement.outerHTML : '';
        })()
    """

    const val EXTRACT_LINKS_JS: String = """
        (function() {
            var links = Array.from(document.querySelectorAll('a[href]'));
            return links.map(function(a) {
                var href = a.href || a.getAttribute('href') || '';
                var text = (a.textContent || '').replace(/\s+/g, ' ').trim();
                return text ? (href + '\t' + text) : href;
            }).join('\n');
        })()
    """

    const val EXTRACT_ASSETS_JS: String = """
        (function() {
            var items = [];
            document.querySelectorAll('script[src]').forEach(function(el) {
                items.push(JSON.stringify({ type: 'script', url: el.src }));
            });
            document.querySelectorAll('link[rel="stylesheet"][href]').forEach(function(el) {
                items.push(JSON.stringify({ type: 'stylesheet', url: el.href }));
            });
            document.querySelectorAll('img[src]').forEach(function(el) {
                items.push(JSON.stringify({ type: 'image', url: el.src, alt: el.alt || '' }));
            });
            document.querySelectorAll('video[src], audio[src], source[src]').forEach(function(el) {
                items.push(JSON.stringify({ type: 'media', url: el.src }));
            });
            return items.join('\n');
        })()
    """

    const val EXTRACT_SNAPSHOT_JS: String = """
        (function() {
            var els = Array.from(document.querySelectorAll('a[href], button, input, select, textarea, [role="button"]'));
            var refs = [];
            for (var i = 0; i < Math.min(els.length, 80); i++) {
                var el = els[i];
                var refId = 'e' + (i + 1);
                el.setAttribute('data-obscura-ref', refId);
                var label = (el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('placeholder') || '').replace(/\s+/g, ' ').trim().slice(0, 80);
                refs.push({
                    ref: refId,
                    tag: (el.tagName || '').toLowerCase(),
                    text: label,
                    href: el.getAttribute('href') || '',
                    type: el.getAttribute('type') || ''
                });
            }
            return JSON.stringify(refs);
        })()
    """
}
