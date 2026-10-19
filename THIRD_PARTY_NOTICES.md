# Third-party notices

Hexgate includes third-party code and assets under their original licenses. First-party
licensing does not replace or restrict those upstream grants. Full notices are retained in the
source tree and in `app/src/main/assets/licenses/` where applicable.

| Component | License | Notice / provenance |
| --- | --- | --- |
| Termux terminal-emulator and terminal-view | Apache-2.0 library exception | [Upstream license](vendor/UPSTREAM-LICENSE.md), [Apache text](vendor/LICENSE-APACHE-2.0), [local changes](vendor/UPSTREAM.md) |
| SSHJ, asn-one, AndroidX / Compose | Apache-2.0 | [Dependency inventory](docs/dependencies.md), packaged notices |
| SLF4J API | MIT | Packaged `slf4j-api.txt` |
| Bouncy Castle | Bouncy Castle license | Packaged `bcprov-jdk18on.txt`, `bcutil-jdk18on.txt`, `bcpkix-jdk18on.txt` |
| Mermaid, marked | MIT | Packaged `mermaid-LICENSE`, `marked-LICENSE` |
| DOMPurify | Apache-2.0 OR MPL-2.0 | Both upstream license texts are packaged |
| draw.io viewer | Apache-2.0 | [Renderer license](renderers/DRAWIO-LICENSE), packaged `drawio.txt` |
| JetBrains Mono, Fira Code, SauceCodePro Nerd Font Mono | OFL-1.1 and bundled glyph notices | Packaged font OFL texts and `NerdFonts-LICENSE.txt` |
| Gogh palette data | MIT option selected | [Theme provenance](docs/themes.md), packaged Gogh notices |

The upstream Termux application's GPL license is not applied to Hexgate by assertion: only the
terminal libraries covered by the upstream Apache-2.0 exception are vendored. The GPL application,
local subprocess launcher, and JNI execution components are not included.

Exact pinned revisions and local modifications are listed in [dependency provenance](docs/dependencies.md),
[terminal provenance](vendor/UPSTREAM.md), and [renderer provenance](renderers/README.md).
`renderers/vendor-checksums.json` records the approved renderer, font, palette, and icon bytes.

The launcher/header illustration is original generated artwork with its provenance in
[assets/branding/README.md](assets/branding/README.md). No endorsement by a game publisher or
third-party project is implied.
