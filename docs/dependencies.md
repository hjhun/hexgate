# Dependency provenance and maintenance

Versions are pinned in Gradle files and renderers/package-lock.json. Compatibility evidence is the API 37 emulator integration run, compile/target API 36, and min API 26 manifest; minimum-API runtime testing remains a separate matrix. Do not infer compatibility on every Android version from compilation alone.

| Component | Pinned source/version | License and use |
| --- | --- | --- |
| Termux terminal-emulator/terminal-view | 8629e632fcb95da272221be327db653fb24befe9 | Apache-2.0 library exceptions; upstream GPL application excluded |
| SSHJ | 0.41.2 | Apache-2.0; SSH transport |
| asn-one / SLF4J API | 0.6.0 / 2.0.17 | Apache-2.0 / MIT; SSHJ transitives, notices bundled |
| Bouncy Castle bcprov/bcutil/bcpkix | 1.86 | Bouncy Castle license; OpenSSH crypto/key loading |
| Compose / AndroidX | BOM 2025.06.01; Activity 1.10.1; Lifecycle 2.9.1 | Apache-2.0; Android UI/lifecycle |
| Mermaid | 12.2.0 | MIT; packaged local renderer |
| marked | 18.1.1 | MIT; Markdown |
| DOMPurify | 3.4.17 | Apache-2.0 OR MPL-2.0; sanitization |
| draw.io viewer | 17b367b91dbab91804f852645788d7ffb46dcb79 | Apache-2.0; official viewer static assets |
| JetBrains Mono / Fira Code / SauceCodePro Nerd Font Mono | added faces from Nerd Fonts v3.4.0; all hashes in renderers/vendor-checksums.json | OFL-1.1; individual and aggregate glyph licenses bundled |

Local terminal modifications and exact upstream provenance are in vendor/UPSTREAM.md. The SSH adapter replaces subprocess/JNI execution; no native toolchain is required. Android's reduced BC provider is replaced inside this app process with the bundled provider when connecting. SSH host verification uses explicitly pinned SHA256 fingerprints.

The dependencies are from established upstream projects; pinned versions were inspected during development rather than assuming Android compatibility from JVM compatibility. SSHJ encrypted-key, handshake and streaming behavior were exercised on Android. Gradle/lint reports available updates; version pinning is deliberate, not a claim that all versions are the newest. Reassess maintenance and advisories before a production release.

Renderer npm audit reported zero findings at installation. This is a point-in-time result, not a continuing security guarantee. Local JS/font hashes are recorded in renderers/vendor-checksums.json; the draw.io viewer bytes were independently compared with its pinned upstream source. Notices for bundled JS dependencies/transitives, terminal engine, font and SSH crypto are packaged under app/src/main/assets/licenses, with Gradle dependency notices retained where applicable.

Upstreams: https://github.com/termux/termux-app ; https://github.com/hierynomus/sshj ; https://www.bouncycastle.org/download/ ; https://github.com/mermaid-js/mermaid ; https://github.com/jgraph/drawio ; https://github.com/cure53/DOMPurify .

The debug APK is intended for local first-version evaluation. It is not a published store release and uses the local Android debug signing identity.

UI refresh adds two regular-weight Mono faces without changing their font binaries or patched family names. Source Code Pro is packaged as SauceCodePro to respect the upstream Reserved Font Name. Both individual font licenses and Nerd Fonts aggregate license are distributed with the app; fonts are not sold independently. Fonts are selectable terminal assets, not a claim of unrestricted rights.

Public theme data is bundled from Gogh under its MIT option, with original authors and pinned commit recorded in docs/themes.md and assets/themes/public-palettes.json. Local UI adaptations and an original Amber preset are distinguished from the upstream data. No theme installer is executed.
