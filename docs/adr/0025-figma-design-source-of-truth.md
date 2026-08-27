# ADR-0025 — Figma is the design-system source of truth, for visual decisions only

**Status:** Accepted

## Context
Until now the app's visual design had no external reference. The Aura theme was
generated once by the `vaadin-skills:aura-theme` skill from a prose brief ("Vaadin
brand blue, Inter, polished weights"), and every view's spacing was chosen by
whoever built it. Nothing was wrong, and nothing was checkable: two views could
disagree and neither was in breach.

A real design now exists — Figma file `Irsp3cgi1WX3GiLGpJZECa`, page `88:12278`
("Visual Design") — covering the shell, the report list and detail, the reference
tables and the dialogs. Adopting it raises a question that outlives any one view:
**when the design and the code disagree, which one is wrong?** Left unanswered,
every per-view issue re-litigates it, and the answer drifts with whoever is asking.

The toolchain that reads the design is a remote **Figma MCP server** plus four
skills, and the spike behind #143 found it needs guardrails rather than trust: the
Figma Aura kit emits `--lumo-*` custom properties (F-062), a vendored skill cited a
reference file that does not exist upstream (F-060), the documented way to detect
light/dark modes silently reports one mode for any file that consumes the kit as a
library (F-063), and the design's hand-drawn values systematically miss the Aura
token scale (F-064).

The `/figma-theme` run for #144 added one more, and it is the one that shapes this
ADR: frame `116:4444` declares `Typography/Font-family: Instrument Sans` and then
renders most of its text in **Inter** and **Public Sans** — three families in one
frame. The same frame's field radii, button heights and label sizes land exactly on
Aura tokens, while the cards drawn around them land between tokens. The design is
not uniformly authoritative. Some of it is the design system; some of it is a
drawing.

## Decision

**1. Figma is the source of truth for visual decisions.** Colour, typography,
spacing, radius, elevation, and the composition of a screen. Where the app and the
design disagree on one of those, the design wins unless a decision recorded in the
theme record says otherwise. "It looked fine before" is not a counter-argument.

**2. It governs nothing else.** The design does not decide domain behaviour,
validation, wording of errors, route structure, permissions, or the data model. A
frame showing an editable amount on a generated line does not amend ADR-0024. Where
the design implies a behaviour change, that is a new issue, not an implementation
detail of applying the design.

**3. ADR-0020's accessibility floor outranks the design.** WCAG 2.1 AA contrast,
touch-target sizes and focus visibility are constraints, not preferences. A design
value that breaks one is a design bug to report, and the app keeps the accessible
value in the meantime — recorded as a **settled** divergence, not carried as a
silent deviation.

**4. The design's *variables* and *kit components* are authoritative; its
hand-drawn pixels are a proposal.** This is the distinction the font-family finding
forces. A value bound to a Figma variable, or inherited from an Aura kit component,
is a design-system decision and is taken as-is. A value typed by hand into a
rectangle is an intent that must be reconciled against the token scale before it
becomes code — by correcting the design, overriding the derived property, defining
one project-level property, or accepting the nearest token. Without this split,
"the design is the source of truth" would have made the app render three font
families and would have promoted every stray pixel to a requirement.

**5. Global visual decisions are settled once, in the design spec, before any
per-view work.** [`docs/design/`](../design/) holds it: `foundations/` carries the
decided values and off-scale values per concern, each marked **settled** or **open**,
`tokens/token-reference.md` the resolved scale, and `components/` one file per
component. A difference already decided is never re-raised as a per-view finding.
This is what stops the drift; the spec, not this ADR, is where the values live,
because values are data that gets refreshed and an ADR is prose that does not.

**The spec is a contract, not a transcript.** It records what the design asks for, so
it is authored from the design and *before* the code, and implementation conforms to
it. That direction is load-bearing: a spec written from the finished component would
describe the implementation, which is both readable from source already and unable to
say whether the implementation is right. One skill writes it — `figma-survey`, which
is also where each divergence is decided with a human, since the calls that matter
are judgement rather than transcription. `figma-theme` applies the settled spec to
the theme CSS and owns exactly one part of it: the resolved-values table, which only
a running app can produce. Implementation never edits a spec file to match what it
built.

**6. `--lumo-*` is forbidden, and the hazard is the toolchain's, not a
preference.** The Figma Aura kit genuinely names variables with `lumo-` prefixes, so
`get_design_context` output is saturated with them — several dozen per frame, every
one carrying a hardcoded fallback like
`rounded-[var(--lumo-border-radius-m,9px)]`. Under Aura those properties are
undefined, so the fallback always wins: the CSS *renders*, at a frozen literal that
looks correct today and silently stops tracking the theme forever. Nothing errors
and dark mode is where it eventually surfaces. Every Figma-facing skill in this repo
carries an override forbidding it and naming the `--aura-*` / `--vaadin-*`
replacement, and CLAUDE.md states the rule for hand-written code (F-062).
`LumoIcon` is the exception that must survive the ban: it is a supported Vaadin 25.2
icon set, on this app's classpath, and what the Figma annotations correctly
prescribe — an agent pattern-matching on "lumo" will wrongly swap in `VaadinIcon`
and change the rendered icon size.

**7. The skills are pinned by provenance, not by the lockfile.**
`skills-lock.json` covers the 24 skills vendored from `mattpocock/skills` via the
`skills.sh` CLI, and the four Figma-facing skills are deliberately **not** in it.
`figma-to-vaadin` and `figma-visual-verification` are project-owned copies of
`juuso-vaadin/figma-to-vaadin-skill`, each carrying a `## Provenance` section naming
the upstream commit it was taken from and every project-local change made to it;
`figma-survey` and `figma-theme` are this repo's own and have no upstream. Re-syncing
is therefore a deliberate diff against the recorded commit, not a lockfile bump —
the project overrides (the `--lumo-*` ban above all) are the reason the copies exist,
and an automated re-sync would drop them. F-060 is why provenance records the gaps
too: a vendored skill can cite a reference file that was never published.

**8. The MCP server is a precondition, and its absence is loud.** The Figma skills
need the server approved and authenticated per-session (`/mcp`) plus a Figma seat on
the file. Unauthenticated, they do not error — they degrade to inventing layout from
layer names, which is worse. `DEVELOPMENT.md` names the symptom ("layout invented
from thin air → check `/mcp` first") rather than only the setup step, and a session
that changes `.mcp.json` cannot exercise the server until it restarts (F-059).

## Considered options
- **Treat the design as advisory** — implement by judgement, use Figma as
  inspiration. Rejected: it is the status quo, and it makes every visual difference
  unfalsifiable. The point of adopting a source of truth is that a survey can
  *conclude* something.
- **Treat the design as authoritative down to the pixel** — no reconciliation
  against the token scale. Rejected outright by the evidence: it would ship three
  font families from one frame, and it would replace tokens with raw px on every
  card, which is the F-062 failure mode by another route (renders today, stops
  tracking the theme forever).
- **Correct the design back to the Aura scale in every case** — cleanest in theory.
  Rejected as the blanket rule: it is right for a 1 px slip but wrong for the 24 px
  page heading, where Aura's type scale simply stops below what the design needs and
  no base font size reaches it. Kept as one of the four options per value.
- **Record the decided values in this ADR instead of the theme record.** Rejected:
  ADRs are immutable and the values are refreshed on a Vaadin upgrade or a theme
  change. An immutable document holding mutable data goes stale silently, and a
  survey reading it would trust stale numbers. The ADR holds the rule; the record
  holds the values.
- **Add the Figma skills to `skills-lock.json`** so all skills are pinned uniformly.
  Rejected: the lockfile models "vendored verbatim from upstream, re-syncable by
  hash", and these are forks whose whole value is the local overrides. A hash bump
  would silently reintroduce `--lumo-*`.
- **Depend on the upstream skills directly** rather than keeping copies. Rejected
  for the same reason, plus F-060: upstream's own bundled reference was missing, so
  the copy is where the substitute technique is recorded.
- **Generate the theme with `figma-to-aura-theme`** (the upstream skill for this
  job). Rejected by the #143 spike, which is why `/figma-theme` exists: the skill
  cites four value tables that do not exist at the pinned commit, and two of its
  stated value sets are wrong (`--aura-base-radius` documented as a discrete set
  including `-1`, which is outside the real 0–10 range).

## Consequences
- **A global theme change lands before per-view work, by construction.** #144 moved
  the base font size from 15 to 14 and the font family from Inter to Instrument Sans,
  which reflows every screen. Any spacing tuned against the old scale would have been
  thrown away — so the ordering is not a preference, it is the only order in which
  per-view work is not wasted.
- **The app now carries four project-level custom properties** (`--em-card-radius`,
  `--em-card-padding`, `--em-section-gap`, `--em-font-size-title`) for design values
  Aura cannot derive. They are a deliberate, bounded escape hatch: each is recorded in
  the off-scale table with the value it replaces. Adding a fifth is a decision for the
  record, not a per-view convenience.
- **Two divergences are accepted and visible**: expense row titles render 16 px where
  the design says 15, and tight intra-row gaps render 8 px where it says 10. Both are
  named in the record so a survey does not rediscover them.
- **`--em-*` properties are unreferenced until per-view work consumes them.** A reader
  will find declarations nothing uses; the record says why.
- **The design has known defects, and reporting them is part of the workflow.** The
  stray Inter and Public Sans text nodes and the design's own 12 px "S" step (against
  Aura's 13) are design bugs, not app bugs. Decision 4 means the app does not
  reproduce them, and the record names them so the next survey does not file them
  again.
- **Applying the theme now depends on the spec existing and being settled.**
  `figma-theme` refuses a spec whose rows are still **open**, which makes the ordering
  enforced rather than advisory: no theme survey, no theme. The cost is a second step
  where there used to be one; the benefit is that the decisions get reviewed as a spec
  before they arrive as a diff.
- **A theme change stales measured figures elsewhere in the spec.** Moving an input
  moves every derived value, so a component file quoting a resolved number or a contrast
  ratio is suspect afterwards even though its token names remain correct. `figma-theme`
  names what it staled; refreshing it is a survey run.
- **The shell is the one large area still governed by nothing.** The design's top
  header bar, its orange gradient, the 220/250 nav widths and the 80 px page inset are
  all deferred to #146; the current side-nav rules are comment-marked as pending it
  rather than deleted, so the nav is not left unstyled in between.
