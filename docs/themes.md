# Local app and terminal themes

Settings provides seven choices: Hexgate Dark, Light, Follow system, Amber, Dracula, Gruvbox Dark and Solarized Dark. Selection persists across app/process restarts. Follow system resolves to the local dark/light preset and responds to Android configuration changes.

The app chrome, terminal defaults/16-color palette, and local document container follow the selected theme. Markdown links/tables/code blocks and Mermaid use matching light/dark presentation. Remote programs may emit explicit terminal colors, and HTML/draw.io files may contain their own explicit styles; those document/program choices are preserved rather than rewritten. Theme changes do not reconnect SSH or close the selected remote document. A preview WebView is recreated for a new fixed theme while keeping the same HTML-vs-trusted-renderer boundary.

Public palettes are copied as local data from Gogh-Co/Gogh, commit 2fbe9bbf3715173fc6f477d4992b66f11afca2b1, data/themes.json. Only Dracula, Gruvbox Dark and Solarized Dark are included. Their original palette/author metadata is retained in app/src/main/assets/themes/public-palettes.json. Gogh's MIT license and copyright notice are bundled under assets/licenses; this project selects the MIT licensing option. No downloaded theme code, shell installer or remote runtime is executed.

The app's light/dark surfaces and controls adapt these palettes for Android. Solarized default foreground is raised to #93A1A1 for readability; its other terminal palette entries are retained. Amber is a local high-contrast yellow foreground and border preset, not an upstream Gogh theme. Font licensing is independent and documented in dependencies.md.

Source: https://github.com/Gogh-Co/Gogh/tree/2fbe9bbf3715173fc6f477d4992b66f11afca2b1
