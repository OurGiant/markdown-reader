---
name: verify
description: How to build, launch, and drive MD Print Pro to verify a Swing UI change actually works on this dev setup. Use before trusting the generic verify-java-swing skill's screenshot step; read that skill too for the underlying techniques (modal-dialog deadlock, synthetic MouseEvent dispatch, process safety).
---

# Verifying MD Print Pro

This is the project-specific companion to the generic `verify-java-swing`
skill (techniques) and `java-swing-project-setup` (build/structure
standard this project follows). Read those first — this file is what to
actually type for *this* project.

## Build here, run there

Maven only exists in the Docker container, not on the host:

```bash
docker exec festive_bardeen bash -c "cd /projects/markdown-reader && mvn -q package -DskipTests"
```

If `festive_bardeen` doesn't respond, find the current container:
`docker ps -a --format '{{.Names}} {{.Status}} {{.Image}}'` and
`docker start <name>` if stopped — the name can drift across sessions.

`/projects` is bind-mounted from the host's `~/projects`, so the jar lands
at `target/md-print-pro-all.jar`, visible on the host. The container is
headless (no `DISPLAY`) — run the jar on the **host**, not inside the
container, or it dies at `JFrame` construction with `HeadlessException`.

```bash
java -jar target/md-print-pro-all.jar [path/to/file.md]
```

Main class: `com.ourgiant.markdown.Main`. With no argument it opens a
file-chooser dialog on startup instead of loading a file directly — pass
a path (or drive `MainWindow.openFile(String)` via reflection, see below)
to skip that dialog in an automated check.

## Confirmed: bind-mount staleness on `pom.xml` — don't trust a build that "just doesn't pick up" an edit

Hit this directly while adding the `<resources>` filtering block for
issue #7: the container's view of `pom.xml` didn't include an edit made
moments earlier on the host, so `version.properties` built with the
literal `${project.version}` placeholder unsubstituted instead of the
real version. Confirmed via
`docker exec festive_bardeen grep -n "<resources>" /projects/markdown-reader/pom.xml`
showing the block missing container-side while it was present on the
host. Fixed with a forced sync:

```bash
docker cp pom.xml festive_bardeen:/projects/markdown-reader/pom.xml
```

If a build seems to ignore a just-made edit to `pom.xml` (or any file),
suspect this before suspecting your own change — `java-swing-project-setup`
§2 flags it as a known risk on both `pom.xml` and `.java` files, and it's
now been observed here specifically, not just hypothetically.

## Screenshots: Robot confirmed working here

`Robot.createScreenCapture(...)` returns genuine, non-black screenshots on
this dev host (`DISPLAY=:1`, a real X11 session) — confirmed by sampling
pixel values (average well above 0, e.g. ~29 for a light FlatLaf theme
and ~9 for a dark one), not just eyeballing the PNG. Try it first here.

## Driving the UI: reflection harness pattern that worked

`MainWindow` (in `gui/`) is a normal instance-based `JFrame` (constructor
builds the UI; `Main.java` calls `setVisible(true)` separately), so a
standalone harness compiled against `target/md-print-pro-all.jar` can:

- Construct it via `mainWindowClass.getDeclaredConstructor().newInstance()`
  on the EDT (`SwingUtilities.invokeAndWait`).
- Call the public `openFile(String)` / `promptOpenFile()` methods directly
  — no reflection needed for those, they're public entry points by design.
- Reach the private `editPane` field via reflection
  (`getDeclaredField("editPane")`, `setAccessible(true)`) to read rendered
  HTML back out with `.getText()`.
- For `AboutDialog` (package-private, single-arg constructor takes a
  `Frame`): construct it via reflection but **don't call `setVisible(true)`**
  unless you're prepared to handle the modal-dialog deadlock from
  `verify-java-swing` §3 — just constructing it and reading its component
  tree (it's already built in the constructor) is enough to verify content
  without ever showing it.

This pattern (construct real window -> drive real public methods -> read
back live component state) caught nothing broken across the #3 package
restructure, #4 FlatLaf/logging wiring, and #7 version display work, and
is the reference approach for the next UI-touching change here.

## Live file-watcher check

`MainWindow.startWatcher` runs on a virtual thread and reloads the
`editPane` via `SwingUtilities.invokeLater` when the open file changes on
disk. To verify it's still wired after a refactor: open a file through the
harness, then `Files.writeString(path, ..., TRUNCATE_EXISTING)` on it from
the same process, and poll `editPane.getText()` for the new content for a
few seconds (don't use a short, tight timeout — see
`verify-java-swing` §4).

## Nothing else confirmed yet

No other project-specific gotchas (first-run state location beyond
`~/.md-print-pro/logs/`, native print-dialog behavior, etc.) have been
found and confirmed here yet. Add them to this file as they turn up.
