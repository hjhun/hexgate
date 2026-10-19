# Local document renderers

`npm ci && npm run build` produces app/src/main/assets/preview.js from pinned package-lock.json. Current dependencies: Mermaid 12.2.0 (MIT), marked 18.1.1 (MIT), DOMPurify 3.4.17 (Apache-2.0 OR MPL-2.0). npm audit reported zero vulnerabilities at initial install; this is time-specific, not a lasting guarantee.

Draw.io viewer-static.min.js is vendored as app/src/main/assets/drawio-viewer.js from jgraph/drawio commit 17b367b91dbab91804f852645788d7ffb46dcb79, src/main/webapp/js/viewer-static.min.js. See DRAWIO-LICENSE. Remote shape libraries, math engines and network resources are blocked; supported shapes require runtime fixture validation. No online viewer dependency.

Untrusted HTML uses Javascript disabled; Markdown is sanitized before inserting into a trusted renderer page. There is no Android Javascript interface. All external network requests are blocked; relative SFTP resources must remain under the document directory after canonicalization.
