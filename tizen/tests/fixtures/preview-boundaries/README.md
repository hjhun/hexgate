# Native preview boundary fixture

These files contain synthetic test content only. They are not application assets.
Use an isolated SFTP root with `docs/` containing hostile.html, allowed.svg and
styles/allowed.css. Put protected.svg under sibling `private/` and make
`docs/escape.svg` a relative symlink to `../private/protected.svg`.

Start the test auth server with `--sftp-root ROOT`. Its statistics record only total
SFTP opens and opens under `/private/`; filenames and data are never logged.
Bind a loopback HTTP counter to port22224 and expose it to the emulator through its
verified host address10.0.2.2. Before loading the document, make one emulator GET of
`/control` and require the probe to observe it. All other routes count as unexpected.
The fixed address is fixture data, not an application endpoint or public service.

Push the synthetic protected.svg to the app's own data/protected-fixture.svg for
the file-URL check. Remove it afterward. The target package is org.tizen.hexgate.
Open `/docs/hostile.html` through the real native SFTP file picker. A correct result:

- Heading remains STATIC_BOUNDARY_OK, colored green by the legitimate relative CSS.
- The legitimate green SVG is visible. Forbidden red SVGs are not visible.
- Exactly3 SFTP file opens: document, relative stylesheet and allowed SVG.
- No `/private/` open and no unexpected HTTP request. The single control GET remains.
- Script/event handler, external images/CSS, iframe/object and meta refresh cannot escape.

After recording the native result, use a separately authenticated host SFTP client
with the exact fixture host key pinned, `client_keys=[]`, `agent_path=None` and
`preferred_auth="password"` to open `/private/protected.svg`. Require the
private-open counter to increase by1, proving that the zero native count was meaningful.
This positive control is outside the native-preview observation interval.

For cancellation, first open the file picker while the peer responds. Validate the
owned fixture PID/command line, pause only that process with SIGSTOP and request a
file. Confirm SSH request bytes are queued at that stopped peer. Dismiss the picker
and close Hexgate normally while the peer remains stopped. Confirm its app PID is
gone, then always SIGCONT the exact same fixture in a finally block. Do not stop,
reconfigure or inspect developer SSH/Herdr sessions for this test.

The static-HTML fixture does not prove all renderer-format attacks or every possible
path-swap race. Those require separate acceptance evidence.
