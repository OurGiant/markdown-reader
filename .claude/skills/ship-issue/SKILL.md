---
name: ship-issue
description: The standard workflow for shipping a bug fix or feature to MD Print Pro — file a GitHub issue, branch off main, implement, verify, bump the patch version, and open a PR. Use whenever picking up a bug fix or feature for this repo.
---

# Shipping a change to MD Print Pro

Follow `java-swing-ship-issue` (the generic workflow shared across the
Java Swing project family) with these MD Print Pro specifics:

- **Project path**: `/projects/markdown-reader` inside the build
  container.
- **Verify**: use this repo's own `.claude/skills/verify/SKILL.md` for
  build/launch mechanics, including a confirmed `pom.xml` bind-mount
  staleness gotcha worth knowing about before you conclude an edit "isn't
  taking effect."
- **Branch naming**: `fix/<issue#>-short-description` (or
  `feature/<issue#>-short-description`), confirmed convention as of the
  package-standardization pass — check `git branch -a` / recent merged
  PRs if it's been a while, in case that's drifted.
- **Two distinct "theme" concepts — don't conflate them**: this app has
  `RetroTheme` (`model/`, loaded from `themes.json` via `core/ThemeLoader`)
  controlling the *rendered markdown content's* colors/fonts, and
  `ThemeManager` (top-level package) controlling the *Swing chrome's*
  FlatLaf look-and-feel. The UI reflects this as the toolbar's `Theme:`
  button (content) vs. the **View > Look & Feel** menu (chrome) — keep
  that naming split if either grows a UI entry point, so a future menu
  item doesn't silently collide with the other concept.
- **`core/` stays Swing-free**: `PathValidator`, `MarkdownHtmlRenderer`,
  `ThemeLoader`, and `PaginationPlanner` have no `javax.swing.*` imports
  by design (`java-swing-project-setup` §3) and are covered by real unit
  tests (`src/test/java/.../core/`). If a change adds logic to `core/`,
  add or extend a test there rather than only checking it through the
  live UI — `gui/MainWindow` and `gui/SmartHtmlPrintable` are the only
  classes that should need reflection-harness verification instead of a
  unit test.
- **`PathValidator.validateAndNormalizePath` is a security boundary**
  (rejects paths that don't resolve to an existing regular file) — a
  change anywhere near file-opening should keep or extend
  `PathValidatorTest`'s coverage, not just eyeball it working once.
- No CI is wired up yet (issue #8, tracked as a separate, deliberate
  follow-up per `java-swing-project-setup` §8's kickoff guidance) — until
  it lands, `gh pr checks --watch` has nothing to watch; rely on the
  local `mvn test`/`mvn package` + live-UI verification steps instead.
