# XtermSharp

Source: https://github.com/migueldeicaza/XtermSharp
Commit: 1bed529cd20748ef9da43753e8dd553f65fc5582
License: MIT (see LICENSE).

The engine source is vendored for native NUI rendering. The local project targets net8.0 and
excludes the unused local PTY launcher; SSH is provided by a separate managed transport.
Any engine changes must be recorded here with regression evidence.

Local compatibility patch: explicitly alias System.Rune in SelectionService and InputHandler
to avoid ambiguity with System.Text.Rune on net8.0.

Native cell-layout patches: enable the upstream wcwidth helper in Print; mark wide trailing
cells with width zero; retain bounded combining marks in CharData instead of overwriting their
base rune. TerminalScreen converts Unicode scalar codes with .NET rather than NStack's padded
Rune.ToString and maps code-zero cells to blanks. Tests cover CJK cell positions, zero-width
combining marks, blank cells and packet-split UTF8; other terminal operations continue to use the upstream engine.
The upstream wcwidth helper's binary-search maximum is corrected from length to length-1;
calling it for non-ASCII previously indexed beyond the combining-character table.

Input/clipboard integration patches:
- Expose registration of a specific OSC handler through Terminal without replacing its VT core.
- Repair OSC payload accumulation: the upstream batch loop skipped the first byte and could
  read past packet boundaries. A bounded byte accumulator (1,400,000 bytes) decodes only at
  termination and preserves UTF8 continuation bytes across packets. Overflow discards the
  whole command through its terminator; it never dispatches a truncated clipboard payload.
- SGR no-button hover motion remains a motion event rather than being mistaken for a release.
- Use the existing BracketedPasteMode property; reset it on terminal reset. Remote terminal
  diagnostics no longer write payloads into application logs.

Portable interaction regressions cover packet-split OSC52 and Unicode titles, oversized OSC
recovery, one-shot gesture authorization, expiry/cancellation/retirement, tracked SGR drag,
rectangular fallback, negotiated paste and bounded scrollback. Target input verification is
recorded separately in the Tizen test report.

Herdr RGB compatibility patch:
- Implement upstream MatchColor rather than throwing NotImplementedException on RGB SGR.
  CharData remains an indexed-color engine: RGB maps to the nearest fixed xterm cube/gray
  entry (16-255), avoiding theme-dependent 0-15. This is 256-color quantization, not lossless
  true-color rendering. No unbounded RGB cache is created.
- Guard incomplete foreground/background extended-color arguments against out-of-range reads.
- Regression: actual isolated Herdr startup bytes previously throw in CharAttributes;
  the same replay passes after the patch. Portable tests verify both RGB channels and malformed
  argument recovery. Emulator acceptance is tracked separately.

Bounded control-sequence patches:
- Keep the original VT state table. Limit parameter lists to256 and intermediate bytes to16;
  discard the entire overflowing sequence. Numeric parameters saturate at65535 instead of
  wrapping a signed integer. Unknown CSI fallback does not write remote-driven logs.
- Bound streamed DCS payload delivery to4096 bytes across parser calls. IDcsHandler.Unhook
  receives a completion flag, so overflow/cancel/reset/dispose cannot execute a truncated
  prefix. DECRQSS clears its buffered data on every unhook. Balance fallback hook/unhook.
- Restore CAN/SUB precedence over local executable rules; canceled CSI/OSC/DCS recover to
  normal parsing instead of keeping partial payloads active.
- Ignore titles over4096 characters, keep32 entries in each window/icon title stack, initialize
  empty titles, and correct the swapped title-only/icon-only restore commands.
- Limit insert/delete/scroll line work to the affected visible region. Ignore IL/DL outside
  margins; SD inserts blanks at the top of the region (the upstream code only cleared its last
  row). SU/SD blanks retain the current erase background. Bound tab-step work to the columns.

Host regressions first reproduced unbounded parameter dispatch, unbounded streamed DCS,
large-count allocations and independent-title restore failures. Tests cover limits/recovery,
abort semantics, title bounds and scroll/margin/background behavior. Actual target evidence
is recorded separately; this is not a full VT conformance claim.

### Viewport clipping and margins (2026-10-20)

- Resize alternate-buffer lines even though that buffer does not reflow. The original shrink
  path only truncated normal-buffer rows, so growing an alternate screen resurrected clipped
  cells that had remained in the backing array.
- Clear a clipped wide-character primary cell when its trailing cell is removed by a line
  resize. Never leave a two-column glyph in the last single column.
- Let an originally full-width horizontal margin follow viewport growth; keep an explicitly
  restricted region when it still fits. Normal-buffer scrollback continues to reflow.

Portable regressions first reproduced clipped alternate cells returning and a half-wide glyph
at the new right edge. Tests also cover full-width/restricted margins and normal-buffer text
retention. Separately, observed wrapped prompt fragments in the own Herdr fixture were present
in Herdr's source pane read as well as the app's native labels; these patches do not sanitize or
rewrite remote output to hide that discrepancy.


### Explicit model retirement (2026-10-20)

Terminal and InputHandler now expose disposal of the existing parser and its callbacks.
Terminal disposal releases both row buffers, saved titles and outgoing event references;
Feed/Resize reject use of the disposed engine. Parser disposal also releases accumulated
OSC/parameter storage and the print-reset callback. Normal parser Reset retains its callback
and DCS abort semantics; the existing reset regression remains part of the test suite.
The Hexgate wrapper serializes disposal against feed/snapshot and makes late retired-session
feed inert. This does not change parsing rules or add a replacement VT parser.
