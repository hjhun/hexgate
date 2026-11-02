# Terminal engine provenance

Source: https://github.com/termux/termux-app
Commit: 8629e632fcb95da272221be327db653fb24befe9
Copied terminal-emulator and terminal-view Java/resources only. These libraries are Apache-2.0 exceptions in upstream LICENSE.md; the GPL application is not included.
Local changes: replace local-process TerminalSession with an SSH byte-stream adapter; exclude JNI subprocess execution; standalone Gradle files. Preserve upstream notices.

Additional Hexgate patches: terminal View exposes local mouse-report suppression for selection mode; IME commits are bounded/coalesced and multi-character insertions use emulator bracketed-paste semantics unless Ctrl, Alt or Shift is active. No local subprocess launcher or JNI is included.

Font: JetBrainsMono Nerd Font Mono Regular from ryanoasis/nerd-fonts, patched-fonts/JetBrainsMono/Ligatures/JetBrainsMonoNerdFontMono-Regular.ttf. OFL notice is bundled under app/src/main/assets/licenses/. Android system font fallback provides CJK glyphs; Korean text and box/powerline characters were exercised on the emulator.

Pending IME composition is finalized before ordinary key-event input so Korean syllables precede punctuation and Enter. The active input connection is released when the terminal view detaches.
