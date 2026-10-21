# Third-party notices for the Tizen port

The signed TPK includes license texts under `res/licenses/`. The Android application remains
an independent target and its dependency inventory is not replaced by this document.

| Component | Version / source | License / packaged notice |
| --- | --- | --- |
| XtermSharp | `1bed529cd20748ef9da43753e8dd553f65fc5582` | MIT; `XtermSharp-LICENSE`; local patches in `vendor/XtermSharp/UPSTREAM.md` |
| SSH.NET | 2026.0.0, Hexgate receive-window/SFTP-frame adaptation 2 | MIT; `SSH.NET-LICENSE`, from the matching upstream tag |
| NStack.Core | 0.12.0 | MIT; `NStack-LICENSE.md`, upstream NStack license |
| BouncyCastle.Cryptography | 2.7.0 | MIT; `BouncyCastle-LICENSE.md`, from the installed package |
| Microsoft.Extensions.DependencyInjection.Abstractions | 8.0.2 | MIT; `Microsoft.Extensions.DependencyInjection-LICENSE.txt` |
| Microsoft.Extensions.Logging.Abstractions | 8.0.3 | MIT; `Microsoft.Extensions.Logging-LICENSE.txt` |
| JetBrains Mono Nerd Font Mono | Android-bundled font, reused unchanged | OFL-1.1 and glyph notices; `JetBrainsMono-OFL.txt`, `NerdFonts-LICENSE.txt` |
| Gogh theme color data | Android-bundled presets | GPL-3.0 / MIT notices; `Gogh-LICENSE.txt`, `Gogh-LICENSE-MIT.txt` |
| Markdown/Mermaid renderer bundle | Android `preview.js`, reused unchanged | Bundled transitive inventory in `web-dependencies.txt`; Marked/Mermaid/DOMPurify notices |
| draw.io viewer | Android `drawio-viewer.js`, reused unchanged | Apache-2.0; `DRAWIO-LICENSE` |

Tizen.NET 14.0.0.19326 supplies compile-time platform references. Platform-owned assemblies
and the system Chromium/WebView engine are supplied by the selected emulator, not bundled as
replacement platform libraries. The app uses its original Hexgate artwork from the Android
resources; header refresh and fullscreen SVG paths preserve the Android vector geometry.

No third-party source is relicensed by this inventory. Keep the original notices with copies.

SSH.NET is built from the pinned source import with the documented bounded ShellStream
receive-credit and SFTP frame-admission patches. See `vendor/SSH.NET/README.md` and `receive-window.patch`; the package
includes the upstream MIT license and Hexgate adaptation notice. Cryptographic primitives
remain upstream implementations.
