# Hexgate UI refresh — 2026-10-19

User request: improve the native Android UI using the installed Claude Design skill, include freely redistributable font choices, and capture the League of Legends Hexgate inspiration in an original icon. No change of coding model or hosted Claude Design service is implied.

Surface: Command / Inspect. Terminal work and the current remote document dominate. Preserve native rendering, SSH lifetimes, trust dialogs and preview isolation.

Direction: midnight navy surfaces, pale readable text, restrained warm gold for app identity/primary actions and electric blue for active inspection. No continuous glow, textures or animation behind terminal content. The portal artwork belongs in launcher branding and the empty state. Use compact structured app chrome, a separate connection-state strip, tactile 44dp minimum key targets, explicit selected states, and restrained 8–12dp radii. Keep the existing size-based split threshold and drag interaction.

Typography: retain JetBrains Mono Nerd Font Mono as default; add FiraCode Nerd Font Mono and SauceCodePro Nerd Font Mono from pinned Nerd Fonts v3.4.0, with individual font licenses and aggregate glyph licenses. Preserve patched family names. Load selected faces off the UI thread; cache them, persist the selected ID, and retain Android CJK fallback. A compact Appearance sheet offers family, size, sample and a license summary. Do not imply that a free font lacks license conditions.

Alternatives considered: a plain graphite console would be restrained but lose the user's gate identity; a fully ornate game UI would crowd terminal work. The selected direction uses the gate identity only at key landmarks.

Validation: build/lint, retain core SSH/preview tests, inspect actual narrow and wide AVD screens, exercise every font's native ASCII cell metrics/Nerd glyph/Korean display and persistence, and fold/rotate/divider behavior. Rebuild a separate v0.1.1 evaluation APK; preserve v0.1 artifacts. No commits/publication or existing developer session changes.

Design audit before implementation: primary issues are ungrouped equal-looking text actions and weak connection/preview hierarchy, with default app typography. Repair composition and action states, rather than adding effects. Target no feature-card grid, decorative numbers, glass/gradient background or centered content in the working terminal.

## Source and asset review

The generated portal image is saved under assets/branding/hexgate-portal-v1.png with the exact prompt in its README. App artwork is a local resource, not an external URL. The previous v0.1 APK is preserved. Preview CSS is retuned to the navy/cyan palette while preserving the separate HTML trust mode and CSP. No new UI dependencies were introduced.

Post-layout slop audit: no grid of feature cards, accent rails, glass blur, decorative metrics or gradients in app chrome. The empty state centers a single connection action; the working terminal uses Command/Inspect composition. Android native UI typography is retained deliberately for platform legibility; terminal families are explicitly chosen. The ornate icon is limited to branding/empty state.

## Approved follow-up: collapsible workspace panel and themes

The latest user instruction supersedes the earlier top-right/hover preview proposal. Keep one left-edge menu entry; the collapsible panel shows saved connections at the top, connection add/manage actions, Preview near the bottom, and Settings anchored at the bottom. Remove standalone font and preview actions from the top bar. Opening/closing the panel overlays the workspace and must not resize the PTY.

Reconnect to the last saved SSH profile once on a fresh launch by default, with a persistent opt-out in Settings. Never store key passphrases. If the imported key is encrypted, ask for its passphrase before connecting; host identity verification remains mandatory. Canceling the prompt or manually disconnecting must not create a reconnect loop. Rotation retains the same ViewModel/connection. Saved-profile switching closes only this client's SSH transport, never the remote Herdr server.

Settings owns fonts, size, startup reconnection, and themes. Include Hexgate Dark, Light, System, public licensed Dracula/Gruvbox Dark/Solarized Dark palettes, and an Amber high-contrast yellow foreground/border option. Apply themes to chrome, terminal defaults/ANSI palette, and local preview presentation while preserving remote explicit document styles and the HTML/script trust boundary. Theme switching must preserve live SSH and the current document.

Validation adds default startup reconnect, encrypted-key prompt/cancel, drawer actions/PTY stability, light/dark/public theme colors and saved preferences. Existing process-recovery tests use an explicit debug-only skip-startup flag so their controlled reconnect assertions remain meaningful rather than racing the new product default.
