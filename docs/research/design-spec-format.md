# Design spec format — what already exists, and what to adopt

Research note, 2026-08-29. Question put by the owner: **`docs/design/` is messy and poorly
structured — what existing format, schema or convention could we adopt or start from,
instead of inventing one?**

Short answer: **something already exists for every layer of this problem, and one of them
was written for exactly this repo's situation.** Adobe's *Spectrum Design Data
Specification* and the *Design System Doc Spec* (DSDS) are both machine-readable design
system documentation formats with the two properties this repo has been hand-rolling —
typed prose blocks and a provenance field saying whether the doc was authored before the
code or extracted from it. Neither is 1.0. The W3C DTCG format is stable and solves only
the token layer. The pragmatic answer is a hybrid, and it is smaller than it sounds,
because most of what is wrong with `docs/design/` is not the *format* — it is that
**nothing checks it**.

## What could not be reached

The egress proxy in this session allows `raw.githubusercontent.com` and web search, and
blocks nearly everything else. Concretely blocked: `tr.designtokens.org`,
`www.designtokens.org`, `design-tokens.github.io`, `w3c.github.io`, `www.w3.org`,
`www.figma.com`, `developers.figma.com`, `styledictionary.com`, `primer.style`,
`api.github.com`, `data.jsdelivr.com`. Everything below is therefore cited to the
**source repository** that owns the artifact rather than to its rendered site, which is
the better citation anyway — it is the on-disk shape we care about.

The **Figma MCP server is unauthenticated and unusable in this session** (`mcp.figma.com`
is refused at the proxy as well as unauthenticated), so nothing here was verified against
the project's own Figma file. That does not affect the conclusions: the questions asked
are about file formats, not about this design's content. The **Vaadin docs MCP tools are
also not present in this session**, so Vaadin's documentation structure was read from
`vaadin/docs` on GitHub instead, which is the same source the site is built from.

---

## 1. Diagnosis — what is actually messy

The spec is unusually *well written*. Read it as prose and it is one of the better
artifacts in the repo. Every problem below is a **structural** one: a claim the format
allows you to make but gives nobody a way to check.

I number these D-1…D-10 so the rest of the note can refer to them.

### D-1. The pseudo-frontmatter is prose in an enum's clothing

Every component file opens with five bold-label lines
(`docs/design/components/README.md:100-106` defines them). They are read by agents as
fields, but they are not fields. The `Implementation` value is documented as an enum of
four (`README.md:65-69`) and actually holds:

- `conforms` (badge.md:5, button.md:5, editor-dialog.md:5, empty-state.md:5,
  error-summary.md:5)
- `drifted — see [Divergence](#divergence)` (expense-line-card.md:5, status-history.md:5,
  totals-card.md:5, travel-card.md:5)
- `none — nothing in the app renders a metric card yet` (metric-card.md:5)
- `unaudited — \`.section-label\` exists; the disclosure and the count do not`
  (report-list-section.md:5)
- `unaudited — origin not established` (status-callout.md:5)

`Category` is worse. The README's template offers four values
(`README.md:102`); the files carry seven distinct strings, five of them `composite` plus a
parenthetical: `composite (CSS)`, `composite (CSS on a \`Div\`/\`VerticalLayout\`)`,
`composite (CSS on a \`RouterLink\`)`, `composite (CSS, extends the expense line card)`,
`composite (CSS on a \`Details\`, or a \`VerticalLayout\` + header row)`. And
`status-callout.md:4` writes its `Origin` as `**unresolved**` where every other file
writes the bare word.

None of this is a *mistake* — the parentheticals are useful. The problem is that an enum
and a note have been packed into one line, so neither can be read reliably.

### D-2. Two inventories of the same facts, already disagreeing

`components/README.md:24-40` is a table of Category/Origin/Implementation for all fifteen
components. Each component file repeats the same three facts in its own header. They are
kept in sync by hand, and they already differ: the README says `themed primitive`
(README.md:26-27) where the files and the README's *own template* say
`themed Vaadin primitive` (button.md:3, badge.md:3, README.md:102). The README says
`composite`, flat, where the files carry the parentheticals from D-1.

### D-3. Three places record "open", and they disagree

`README.md:56-64` declares a two-value status vocabulary (**settled** / **open**) and says
"Every decision row carries one". In practice:

- **`foundations/*.md`** carry a real `Status` column and use it —
  `spacing.md:11` (`Page inset … open`), `motion.md:12` (`Reduced-motion handling … open`),
  `color.md:24` (`settled (#146), open with the designer as #160`).
- **`components/*.md` have no status column at all.** Fifteen files, zero decision rows
  carrying a status. Their open questions are prose: `report-card.md:125-131` ("The hover
  row is a known problem, not a specification"), `app-shell.md:174`, `report-card.md:133`.
- **`README.md:66-74`** is a third, hand-kept list of five open items, which is not
  derived from either of the other two. It omits `motion.md`'s open row and `color.md`'s
  open row entirely, and its first row — "App shell — top header bar, gradient, nav widths
  (220/250), page inset (80px) | #146" — is **stale**: `app-shell.md:14-16` records that
  #146 shipped, and `foundations/color.md:22-25` marks all five shell rows settled.

`figma-theme`'s central safety rule is "It refuses a spec whose rows are still **open**"
(`README.md:48`, `.agents/skills/figma-theme/SKILL.md:36-38`). There is no way to
enumerate the open rows. The refusal is only as good as a human reading fifteen files.

### D-4. Sixty resolved pixel values are copied into component files

`grep -o '`--[a-z-]*` ([0-9]+)' docs/design/components/*.md` returns **60 hits** —
`--vaadin-radius-m` (9), `--em-card-padding` (20), `--aura-font-size-xs` (12) and so on.
Every one duplicates a number that `tokens/token-reference.md` owns, and every one is
derived from `--aura-base-size` / `--aura-base-radius` / `--aura-base-font-size` through
formulas the token reference caches (`token-reference.md:142-158`).

ADR-0025 already names the consequence: "A theme change stales measured figures elsewhere
in the spec … `figma-theme` names what it staled". Sixty of them, in fifteen files, found
by eye. This is the single largest source of future drift in the folder and it is a pure
format problem: the token column should not be allowed to contain a number.

### D-5. "The complete list" is not complete, and says so in prose

`tokens/token-reference.md:26-41` — heading *Set by this app*, subtitle "The complete list
— everything else is Aura stock" — lists nine properties. Two of them,
`--em-font-size-total` and `--em-font-size-metric`, are **not in `aura-theme.css`**
(verified: the file declares `--em-section-gap`, `--em-font-size-title`,
`--em-card-radius`, `--em-card-padding`, `--em-header-color`, `--em-header-text-color`,
and nothing else). Lines 46-51 explain this at length in prose immediately below the
table. So the table is false, the paragraph is true, and a reader who trusts the table
ships a view that renders unset.

That is the correct *decision* — a spec is a contract, and being ahead of the code is what
`Implementation: none` is for. It is the wrong *encoding*: "decided" and "declared in CSS"
are two different facts sharing one row.

### D-6. Table column headers are per-file conventions

Under the mandatory `## Tokens used` heading:

| Header | Files |
|---|---|
| `\| Property \| Token \|` | 10 |
| `\| Part \| Token \|` | button.md |
| `\| Part \| Token \| Design value \|` | app-shell.md |
| *(no table — prose only)* | badge.md, editor-dialog.md, theme-switcher.md |

Three of fifteen components have no token table under the heading that exists to hold one.
`badge.md:24-28` explains why in prose ("Stock Aura badge styling — this app overrides
nothing"), which is a legitimate answer, but it is indistinguishable from an omission.

### D-7. The mandatory six states are not six states in three files

`README.md:120-122` and `figma-survey` SKILL.md:310-313 both say: six rows, `n/a` with a
reason, "never leave a row blank and never drop the row", and the README devotes a whole
closing section (`README.md:134-142`) to why. Three files collapse three of them:

- `editor-dialog.md:59` — `| hover / active / focus | stock Aura on the buttons and fields inside |`
- `empty-state.md:55` — `| hover / active / focus | n/a on the container; … |`
- `theme-switcher.md:45` — `| hover / active / focus | stock Aura menu-bar |`

Each of the three answers is *reasonable*. The rule the README says is the most valuable
in the whole spec is silently violated in 20% of the files, and nothing reported it.

### D-8. Sections drift because the template is advisory

`README.md:96-97`: "Every file follows this order. Omit a section only when it genuinely
does not apply, and say so rather than dropping the heading silently." Actual:

- **Order violated:** `report-card.md` and `report-list-section.md` put `## Divergence`
  before `## Code example`; the template and the other four drifted files put it after.
- **Undeclared sections:** `## Navigation` and `## Small screens` (app-shell.md),
  `## Copy` (metric-card.md), `## Responsive rules` (travel-card.md),
  `## The dependency that will break it` (theme-switcher.md), `### Collapsed` /
  `### Empty` (report-list-section.md), `### Empty` (metric-card.md).
- **No file says "omitted because…"** for a dropped section. `## Divergence` is simply
  absent from the nine non-drifted files, which is correct behaviour, and
  indistinguishable from forgetting.

The undeclared sections are the interesting ones: `## Copy`, `## Responsive rules` and
`### Empty` are real, recurring concerns the template has no slot for. The template is too
small, not the files too loose.

### D-9. Nothing has a stable identifier

Components are addressed by filename. Decisions, divergence rows, off-scale values,
measured figures and open questions have no identifiers at all. So:

- A ticket cannot cite a spec row; it cites a whole file, or quotes prose.
- `figma-survey` cannot say "row X was settled in run N" — it re-derives everything.
- The Divergence tables (`report-card.md:141-150`, six more) cannot be closed row by row.
- Cross-references are markdown links to files, so `metric-card.md:130-133` points at
  four files rather than four claims.

Compare `foundations/*.md`, where the `Property` column *is* effectively an identifier and
the tables are consequently the most reusable thing in the folder.

### D-10. Foundations and the token reference overlap by construction

`--em-card-padding` appears in **10 of 23 files**. The radius scale is tabulated in
`foundations/radius.md:16-21` and again in `tokens/token-reference.md:66-71`; the spacing
scale in `foundations/spacing.md:22-30` and again at `token-reference.md:78-86`; the type
scale in `foundations/typography.md:33-40` and again at `token-reference.md:90-99`. Each
pair carries a slightly different "when to use" column, which is genuinely useful editorial
work — and is exactly the thing that silently forks.

`README.md:19-33` is explicit that `foundations/` holds "the decisions, per concern" and
`tokens/` holds the "master map". That is a good split. The scales are on the wrong side of
it: they are derived data, they belong to `tokens/`, and `foundations/` should reference
them.

### What is *not* wrong

Worth saying, because a rewrite could easily destroy it:

- The **two-axis model** (Origin × Implementation, `README.md:42-84`) is right, and better
  than what most published design systems do. Keep it verbatim.
- The **Status column in `foundations/`** is the correct shape. It should be extended to
  components, not replaced.
- The **prose is load-bearing**. `report-card.md:40-44` ("One row is one trip, not one
  leg"), `metric-card.md:108-114` (why the design's own sample data is the defect),
  `app-shell.md:104-132` (the pinned text colour and F-072) are the reason the spec is
  worth having. Any format that squeezes them out is a downgrade — which is precisely why
  the *typed prose block* idea in §2.6 and §2.7 matters.

---

## 2. Survey of the candidate formats

### 2.1 W3C DTCG — Design Tokens Format Module

**What / owner.** A JSON interchange format for design token *values*, from the W3C Design
Tokens Community Group. Repo: `github.com/design-tokens/community-group`. Not a W3C
Standard and not on the Standards Track (community-group reports never are).

**Status.** The current revision is **2025.10**. The editors' source in the repo is marked
`specStatus: 'CG-DRAFT'` with
`latestVersion: 'https://www.designtokens.org/TR/2025.10/format/'`
([`technical-reports/format/index.html`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/index.html),
lines 6-19). Web search reports a W3C CG-FINAL report dated 2025-10-28 at
`w3c.github.io/cg-reports/design-tokens/CG-FINAL-format-20251028/` and a CG announcement
"Design Tokens specification reaches first stable version"; **both URLs are blocked from
this session and I could not verify them directly**. Adobe and DSDS both treat 2025.10 as
the stable revision, and Style Dictionary v5 adopted it as its base format.

**On-disk shape.** JSON, extension `.tokens` or `.tokens.json`, MIME
`application/design-tokens+json`
([`file-format.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/file-format.md)).
Any object with a `$value` is a token; any object without one is a group.

```json
{
  "$schema": "https://www.designtokens.org/schemas/2025.10/format.json",
  "type styles": {
    "heading-level-1": {
      "$type": "typography",
      "$value": {
        "fontFamily": "Roboto",
        "fontSize": { "value": 42, "unit": "px" },
        "fontWeight": 700,
        "letterSpacing": { "value": 0.1, "unit": "px" },
        "lineHeight": 1.2
      }
    },
    "microcopy": {
      "$type": "typography",
      "$value": {
        "fontFamily": "{font.serif}",
        "fontSize": "{font.size.smallest}",
        "fontWeight": "{font.weight.normal}",
        "letterSpacing": { "value": 0, "unit": "px" },
        "lineHeight": 1
      }
    }
  }
}
```

— verbatim from
[`composite-types.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/composite-types.md).

Token properties: `$value` (required), `$type`, `$description`, `$extensions`,
`$deprecated`
([`design-token.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/design-token.md)).
Groups may carry `$description`, `$type` (inherited by children), `$extends`,
`$deprecated`, `$extensions`
([`groups.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/groups.md)
lines 62-76). Aliases are `{group.token}` curly-brace references, with JSON Pointer as a
required alternative
([`aliases.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/aliases.md)).
Composite types: `strokeStyle`, `border`, `transition`, `shadow`, `gradient`, `typography`.
Primitive types: `color`, `dimension` (`{value, unit}`, unit ∈ `px`|`rem`), `fontFamily`,
`fontWeight`, `duration`, `cubicBezier`, `number`.

`$deprecated` is a nice precedent for this repo's status problem: `true` | `false` |
*string explanation*, overridable at group level.

**Layer solved.** Tokens only. Explicitly not components, not guidance, not states.

**Limits, for this repo — three, and they matter.**

1. **No modes.** The word "mode" does not appear in the format spec in that sense. A
   light/dark pair cannot be expressed. This repo is `color-scheme: light dark` with
   `light-dark()` semantics baked into Aura (`foundations/color.md:62-64`).
2. **The scale is not ours.** DTCG models tokens you *define*. `--vaadin-radius-m` is
   defined by Aura and *derived* from `--aura-base-radius` through a formula
   (`token-reference.md:142-158`) with no compatibility promise. Writing those into a
   `.tokens.json` as if we owned them would be a lie in a machine-readable file — worse
   than the same lie in prose, because a tool would then generate CSS from it.
3. **No build step here.** The token layer this app actually owns is nine custom
   properties, hand-written into `aura-theme.css` by `/figma-theme`. DTCG's payoff is
   multi-platform generation. Nine properties on one platform is not that.

**Verdict.** Adopt DTCG for the ~9 properties this app *owns*, where it is a genuine fit
and costs nothing. Do not model Aura's derived scale as DTCG tokens.

### 2.2 DTCG Resolver Module — the missing modes half

Same group, same 2025.10 revision, separate module
([`technical-reports/resolver/index.html`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/resolver/index.html)).
Its opening line is the exact problem statement:

> Consumers of design tokens often need to express alternate values that apply in different
> contexts. Such examples include, but are not limited to: **Theming**, such as light mode,
> dark mode, and high contrast color modes …

A resolver document has `sets`, `modifiers` (each with a `contexts` map and a `default`),
and a required `resolutionOrder`
([`syntax.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/resolver/syntax.md)):

```json
{
  "$schema": "https://www.designtokens.org/schemas/2025.10/resolver.json",
  "modifiers": {
    "theme": {
      "description": "Color theme",
      "contexts": {
        "light": [{ "$ref": "theme/light.json" }],
        "lightHighContrast": [
          { "$ref": "theme/light.json" },
          { "$ref": "theme/dark-high-contrast.json" }
        ],
        "dark": [{ "$ref": "theme/dark.json" }]
      },
      "default": "light"
    }
  }
}
```

**Verdict.** Know it exists; do not adopt it now. This app has exactly one modifier with
two contexts, and Aura resolves it in CSS via `light-dark()` rather than by producing two
bundles. Adopting a resolver document to express one binary would be ceremony. Revisit if a
high-contrast or density mode ever lands.

### 2.3 Style Dictionary

**What / owner.** Amazon's build system for turning token files into platform outputs.
`github.com/amzn/style-dictionary`, version **5.5.2** at the time of writing
(`package.json`).

**Status / DTCG support.** The docs source in the repo still describes v4 and says:
"Currently, Style Dictionary is forward-compatible with the Design Token Community Group
spec … The biggest difference is that the DTCG uses `$value`, `$type` and `$description`
whereas the original format uses `value`, `type` and `comment`. In version 4 you can use
either format, pick one though as they cannot be combined inside a single Style Dictionary
instance."
([`docs/src/content/docs/info/tokens.md`](https://raw.githubusercontent.com/amzn/style-dictionary/main/docs/src/content/docs/info/tokens.md)
lines 11-16). Web search reports v5 adopted DTCG 2025.10 as its base format and that the
`usesDtcg` flag / `.tokens.json` detection govern which syntax is assumed; the rendered doc
page for that is on `styledictionary.com`, **which is blocked here**, so treat the v5
detail as second-hand.

**Layer solved.** Transform and generate, not describe. It has no opinion about component
specs, states, guidance or status.

**Limits.** It is a **Node build step**. This repo's CI is `./mvnw test` and nothing else
(`.github/workflows/tests.yml`). Vaadin does bring its own Node down for
`prepare-frontend`, so it is *possible* — but the design goal here is a spec a designer can
read on GitHub without running anything.

**Verdict.** Not now. If the `--em-*` set ever grows past a dozen and starts needing to
exist in Java as well as CSS, this is the tool. Writing the nine properties in DTCG shape
today keeps that door open for free.

### 2.4 Figma's own formats as a schema source

Since `/figma-survey` produces the spec *from* Figma, the shape Figma hands over is the
natural upstream schema.

**Variables REST API.** Figma publishes its own OpenAPI spec:
`github.com/figma/rest-api-spec`. From
[`dist/api_types.ts`](https://raw.githubusercontent.com/figma/rest-api-spec/main/dist/api_types.ts):

```ts
export type LocalVariable = {
  id: string
  name: string
  key: string
  variableCollectionId: string
  resolvedType: VariableResolvedDataType
  valuesByMode: { [key: string]: boolean | number | string | RGBA | VariableAlias }
  remote: boolean
  description: string
  hiddenFromPublishing: boolean
  scopes: VariableScope[]
  codeSyntax: VariableCodeSyntax
  deletedButReferenced?: boolean
}

export type VariableCodeSyntax = { WEB?: string; ANDROID?: string; iOS?: string }
```

`LocalVariableCollection` carries `modes: { modeId, parentModeId?, name }[]` and
`defaultModeId`. Two things follow.

First, **`valuesByMode` + named modes is the modes model DTCG lacks**, and it is the shape
`figma-survey` step 3 already walks (`SKILL.md:94-101`). A spec that stores per-mode values
keyed by *mode name* is storing what Figma gave it, unflattened — which is exactly the
failure F-063 was about.

Second, **`codeSyntax.WEB` is where the `--lumo-*` problem is supposed to be solved.** A
variable can carry the CSS custom property name the code should use. If the Aura kit
populated `codeSyntax.WEB`, the translation table in `report-card.md:99-104` and
`metric-card.md:64-70` would be data rather than folklore. Worth checking against the real
file, and worth raising with the designer alongside #160 — from this session the Figma MCP
is unauthenticated so I could not check.

**Availability.** The endpoint description in
[`openapi/openapi.yaml`](https://raw.githubusercontent.com/figma/rest-api-spec/main/openapi/openapi.yaml)
opens: "**This API is available to full members of Enterprise orgs.**" And: "Note that
`GET /v1/files/:file_key/variables/published` does not return modes." So the REST route is
plan-gated *and* the non-gated half loses modes. The plugin-API binding walk the survey
already does stays the only viable route.

**Code Connect.** `github.com/figma/code-connect`. From its README: template files are now
the only maintained approach; parser-based integrations exist for React/React Native,
Storybook, HTML, SwiftUI and Jetpack Compose — **no Java, no Vaadin Flow** — and
"Code Connect is available on Organization and Enterprise plans and requires a full Design
or Dev Mode seat." Not applicable.

**Verdict.** Do not adopt a Figma format as the spec's format. Do borrow its two good
ideas: keep per-mode values keyed by mode name, and treat `codeSyntax.WEB` as the intended
home of the kit-variable → real-token mapping.

### 2.5 Component API manifests — CEM, react-docgen, Storybook CSF

**custom-elements-manifest.** `github.com/webcomponents/custom-elements-manifest`,
`schema.json`, JSON Schema draft-07. A `CustomElementDeclaration` has `tagName`,
`attributes`, `members`, `events`, `slots`, `cssProperties`, `cssParts`, `cssStates`,
`demos`, `deprecated`, `summary`, `description`. `CssCustomProperty` carries
`name` ("including leading `--`"), `syntax` (a CSS `@property` syntax string), `default`,
`description`, `deprecated`.

**Storybook CSF.** "an open standard based on ES6 modules … stories and component metadata
are defined as ES Modules"
([`docs/api/csf/index.mdx`](https://raw.githubusercontent.com/storybookjs/storybook/next/docs/api/csf/index.mdx)).
The metadata is a JS default export; the value is *executable examples*, not a contract.

**Limit, and it is decisive.** All three describe the **API surface**, and all three are
**generated from source**. `components/README.md:17-20` rules that out in one sentence: "It
does **not** duplicate the constructor signature or the class javadoc; those are readable
from the source and would go stale here." A CEM for `com.vaadin.flow.component.button.Button`
would tell us what Vaadin's Button can do; the spec's job is to say which of that this app
is *entitled* to use.

**One idea worth stealing:** `cssProperties[].name` + `syntax` + `default` is the right
shape for "which custom properties does this component read". That is a component-scoped
token contract, and it is machine-checkable against the stylesheet.

**Verdict.** No.

### 2.6 Adobe Spectrum Design Data — the closest published precedent

**What / owner.** `github.com/adobe/spectrum-design-data` (renamed from `spectrum-tokens`).
It contains `@adobe/spectrum-tokens`, `@adobe/spectrum-component-api-schemas`,
`@adobe/spectrum-design-data` ("the canonical Spectrum dataset — tokens, components,
fields, mode-sets, guidelines, and registry data") and `@adobe/design-data-spec`
("normative spec artifacts").

**Status.** `specVersion: "1.0.0-draft"` throughout. Pre-1.0, actively moving.

**On-disk shape — three things this repo should copy.**

**(a) Mode-sets on the token, with per-token `$schema`.** From
[`packages/tokens/src/color-palette.json`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/tokens/src/color-palette.json):

```json
"blue-100": {
  "$schema": "https://opensource.adobe.com/spectrum-design-data/schemas/token-types/color-set.json",
  "uuid": "482037db-2e5c-4deb-bfa5-986a46cdcf33",
  "private": true,
  "sets": {
    "light":     { "$schema": ".../color.json", "value": "rgb(245, 249, 255)", "uuid": "bb610367-…" },
    "dark":      { "$schema": ".../color.json", "value": "rgb(14, 23, 63)",    "uuid": "7d56ac58-…" },
    "wireframe": { "$schema": ".../color.json", "value": "rgb(246, 248, 252)", "uuid": "05ffb7a9-…" }
  }
}
```

and the schema that validates it
([`schemas/token-types/color-set.json`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/tokens/schemas/token-types/color-set.json))
is JSON Schema 2020-12 with `"required": ["light", "dark", "wireframe"]` — i.e. **mode
coverage is a schema error**, not a review comment. Note also `uuid`: a stable identifier
per token that survives renames (D-9), and `deprecated` / `deprecated_comment` / `renamed`
as first-class fields (`packages/tokens/README.md`).

**(b) Two-layer validation, with a stable rule catalog.** From
[`packages/design-data-spec/README.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/README.md):

> 1. **JSON Schemas** (Draft 2020-12) — structural (per-file) validation …
> 2. **Rule catalog** (`rules/rules.yaml`) — semantic rules (`SPEC-001` … `SPEC-006`).
> 3. **Conformance fixtures** — valid/invalid examples and expected diagnostics.

and the catalog entries themselves
([`rules/rules.yaml`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/rules/rules.yaml)):

```yaml
  - id: SPEC-001
    name: alias-target-exists
    severity: error
    category: reference-integrity
    assert: Every alias $ref MUST resolve to an existing token in the dataset.
    message: "Alias target not found for $ref: {path}"
    spec_ref: spec/token-format.md#alias-ref
    introduced_in: "1.0.0-draft"
```

**(c) A normative component declaration, with typed prose.**
[`spec/component-format.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/spec/component-format.md)
defines a one-JSON-file-per-component contract with required `$id`, `name`, `displayName`,
`meta` and optional `options`, `slots`, `anatomy`, `states`, `lifecycle`, `tokenBindings`,
`documentBlocks`, `accessibility` — and "No properties beyond those listed above are
permitted at the top level … Additional fields **MUST** cause a Layer 1 schema error."

Its `states` block is worth reading against D-7:

```json
"states": [
  { "name": "hover",    "trigger": "interaction", "precedence": 50 },
  { "name": "focus",    "trigger": "interaction", "precedence": 60, "layered": true },
  { "name": "disabled", "trigger": "prop",        "precedence": 100 }
]
```

and its `tokenBindings` against D-4 — a component declares which tokens it uses **by name
only**, validated to exist (`SPEC-027`), with a free-text `context` for the human:

```json
"tokenBindings": [
  { "token": "component-height-100",           "context": "Minimum height" },
  { "token": "corner-radius-full",             "context": "Rounding" },
  { "token": "button-background-color-accent", "context": "Fill background" }
]
```

**Document blocks** (`spec/document-blocks.md`) are the answer to "how do you keep the
prose and still validate the file". Five types — `purpose`, `guideline`, `accessibility`,
`do-dont`, `examples` — each `{type, content}` required, plus an optional **`agents`**
field: "LLM-tuned rephrasing of `content` for agent consumption". Adobe credits the idea to
DSDS (below).

**Limits.** `1.0.0-draft`, moving weekly, and enormous — a Rust CLI, a moon monorepo, an
MCP server, four visualizers. Adopting the *artifacts* is not realistic. Adopting the
*shape* is free.

**Verdict.** The best available model for the component layer. Copy the vocabulary
(`anatomy`, `states` with `trigger`, `tokenBindings` with `context`, `lifecycle`,
`documentBlocks`) and the two-layer validation idea. Do not take a dependency.

### 2.7 DSDS — Design System Doc Spec

**What / owner.** `github.com/somerandomdude/design-system-documentation-schema`,
"A standard, machine-readable format for design system documentation", **v0.15.2**
(`package.json`). A small named contributor list; site at `designsystemdocspec.org`
(blocked here — everything below is from the repo).

**Status.** Pre-1.0 and honest about it; it ships migration scripts for 0.7, 0.8, 0.10 and
0.14, which tells you how much it has moved. Its credibility marker is that **Adobe cites
it as prior art** for Spectrum's document-block model.

**Positioning, stated in its own README:**

> The W3C Design Tokens Community Group defines a format for trading token **values**
> between tools. DSDS defines a format for the **documentation** around them … DSDS does
> not duplicate token values or platform identifiers. The W3C Design Tokens Format file is
> the source of truth for values. Use the `source` property on a token entity to link it
> back to its DTCG definition.

That is exactly the split this note recommends.

**On-disk shape.** JSON, one document per system or per entity, `$schema` pointing at
`https://designsystemdocspec.org/v0.15.2/dsds.bundled.schema.json`, validated with
`npx ajv validate --spec=draft2020`. Entities: component, token, token-group, theme,
foundation, pattern, guide, chunk. From
[`spec/examples/minimal/component.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/examples/minimal/component.json):

```json
{
  "kind": "component",
  "identifier": "button",
  "name": "Button",
  "description": "A clickable element that triggers an action.",
  "metadata": { "status": "stable", "category": "action", "tags": ["action", "submit"] },
  "documentBlocks": [
    { "kind": "use-cases",
      "items": [
        { "description": "When the user needs to trigger an action…", "stance": "recommended" },
        { "description": "When the action is navigation to another page.",
          "stance": "discouraged",
          "alternative": { "identifier": "link",
                           "rationale": "Links communicate navigation; buttons communicate actions." } }
      ] } ],
  "agentDocumentBlocks": [
    { "kind": "guidelines",
      "items": [
        { "guidance": "Do not use for navigating to a different page or URL.", "level": "must-not" },
        { "guidance": "Limit each surface to one primary-emphasis button.",    "level": "must" }
      ] } ]
}
```

**Three fields worth stealing outright.**

`docOrigin` — from
[`spec/schema/metadata/doc-origin.schema.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/schema/metadata/doc-origin.schema.json):

> How this entity's documentation came to exist, tracked on two separate scales:
> 1. `origin` — how the entity relates to the code (ex: written first, generated from it,
>    extracted from it, or reconstructed from memory).
> 2. `authorship` — what generated the entity (ex: a person, an agent, a script, or some
>    mix). … This metadata allows people and agents judge how much to trust the doc.

`docOriginValue` ∈ `authored | generated | extracted | reconstructed` —
"'authored': written before or alongside the code, as its spec … 'extracted': written by a
person or agent reading the existing code, stories, or tests after the fact …
**For API accuracy, agents SHOULD prefer 'generated' or 'extracted'; for design intent,
prefer 'authored'.**" `authorshipValue` ∈
`human | ai-assisted | ai-generated | machine-assisted | machine-generated`, and the object
form allows per-block overrides.

That is `components/README.md:47-60`'s Origin axis, already written down by someone else,
with a second axis this repo does not have and arguably needs — every spec file in
`docs/design/` is `ai-assisted` at best, and nothing says so.

`verificationMode` — from
[`spec/schema/common/criterion.schema.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/schema/common/criterion.schema.json):

> 'automated': a fully objective test checked programmatically; a `check` MUST be present.
> 'assisted': a tool surfaces candidates but remains a subjective decision …
> 'manual': pure subjective judgment; no `check` applies.

This is the missing encoding for D-5-adjacent problems: `button.md:65-72`'s measured
contrast ratios are `assisted`; `report-card.md:132-134`'s "**unverified**" is a criterion
with no result yet. And `conformanceLevel` ∈ `must | should | should-not | must-not`
(RFC 2119, lowercased) is the vocabulary for guideline strength.

**Limits.** v0.15.2, one primary author, JSON-only (no Markdown authoring path), and it
would replace *all* the prose with `content:` strings inside JSON — which fails the
"designer reads it on GitHub" test badly.

**Verdict.** Do not adopt the file format. **Do adopt its vocabulary**: `docOrigin` /
`authorship`, `verificationMode`, `conformanceLevel`, `stance` +
`alternative.rationale`. Those are four naming problems solved for free, and each maps onto
something this repo is already trying to say in prose.

### 2.8 How mature design systems actually shape a per-component doc

Four repos, read on disk.

**GitHub Primer — `primer/design`.** `content/components/*.mdx`, YAML frontmatter, MDX
body. `button.mdx`:

```yaml
---
title: Button
description: Button is used to initiate actions on a page or form.
reactId: button
railsIds:
  - Primer::Beta::Button
  - Primer::ButtonComponent
figmaId: button
rails: https://primer.style/view-components/components/beta/button
cssId: button
---
```

`src/layouts/component-layout.tsx:18` destructures exactly
`{title, description, reactId, railsIds, figmaId, cssId}` and feeds them to
`includeReact` / `includeRails` / `includeFigma` / `includeCSS` — so the frontmatter's job
is **cross-implementation identity**, which is precisely this repo's `Code:` and `Design:`
lines, done as fields.

Section skeleton for `button.mdx`: `## Usage`, `## Anatomy`, `## Options`,
`## Best practices`, `## Accessibility and usability expectations`.

**But it is not enforced.** `banner.mdx` has `componentId` and `tags` and no `cssId`;
`action-list.mdx` has no `componentId`; Banner's heading is `## Accessibility` (not
"Accessibility and usability expectations") and it has a `## Layouts` section Button
doesn't. Primer has this repo's D-1 and D-8 too, at ~100× the scale. **The lesson is not
"copy Primer's frontmatter"; it is "frontmatter without a schema drifts the same way bold
labels do."**

**Shopify Polaris — `Shopify/polaris`.**
`polaris.shopify.com/content/components/actions/button.mdx`:

```yaml
---
title: Button
shortDescription: Used primarily for actions like 'Add', 'Close', 'Cancel', or 'Save'.…
category: Actions
webComponent:
  name: s-button
  url: https://shopify.dev/docs/api/app-home/polaris-web-components/button
  type: polaris
keywords: [CTA, call to action, primary, action, outline, plain, destructive, …]
examples:
  - fileName: button-default.tsx
    title: Default
    description: Used most in the interface. Only use another style if a button requires
                 more or less visual weight.
  - fileName: button-plain.tsx
    title: Plain
    description: Use for less important or less commonly used actions…
---
```

The frontmatter is **typed in TypeScript** — `polaris.shopify.com/src/types.ts:57-91`
declares `FrontMatter` with `title`, `category`, `status?: Status`,
`examples?: Example[]`, `keywords`, `variants?`, `primitives?`, `webComponent?` and
`StatusName` ∈ `New | Deprecated | Alpha | Beta | Information | Legacy | Warning |
Internal`. A type is not a validator at author time (the loader types it `any` at
`types.ts:104`), but it is a written-down contract, which Primer's is not. Body headings:
`## Best practices`, `## Content guidelines`, `## Related components`, `## Accessibility`.

The genuinely reusable idea: **`examples[]` as data**, each with `fileName`, `title` and a
`description` that says *when to reach for that variant*. That is a much better home for
this repo's variant tables (`button.md:15-20`, `badge.md:39-44`) than a markdown table,
because the description travels with the variant.

**IBM Carbon — `carbon-design-system/carbon-website`.** One directory per component, one
MDX per tab, and the tabs are declared in frontmatter:

```yaml
---
title: Button
description: Buttons are used to initialize an action…
tabs: ['Usage', 'Style', 'Code', 'Accessibility']
---
```

`usage.mdx` skeleton: `## Overview` → `### Variants`, `## Formatting` → `### Anatomy`,
`### Button sizes`, `### Emphasis`, `## Content`, `## Universal behaviors` → `### States`,
`### Interactions`, then one `##` per variant.

**And `style.mdx` is the thing to copy.** Its tables are the exact shape this repo's
`## Tokens used` and `## States` sections are groping towards:

```markdown
### Primary button color

| Element   | Property         | Color token       |
| --------- | ---------------- | ----------------- |
| Label     | text-color       | `$text-on-color`  |
| Icon      | svg              | `$icon-on-color`  |
| Container | background-color | `$button-primary` |

| State    | Element   | Property         | Color token               |
| -------- | --------- | ---------------- | ------------------------- |
| Hover    | Container | background-color | `$button-primary-hover`   |
| Focus    | Container | border           | `$focus`                  |
|          |           | inset            | `$focus-inset`            |
| Active   | Container | background-color | `$button-primary-active`  |
| Disabled | Label     | text-color       | `$text-on-color-disabled` |
```

Four canonical columns: **State × Element (anatomy part) × CSS property × token.** No
resolved values. It is checkable by regex, it dissolves D-4 and D-6, and it makes the
Anatomy table load-bearing rather than decorative, because `Element` must name a declared
part.

**Salesforce Lightning — `salesforce-ux/design-system`.** `ui/components/*/docs.mdx`, MDX
importing React helpers; the styling-hooks table is a component
(`import StylingHooksTable from '../../../shared/components/StylingHooksTable'`), i.e.
**generated from metadata rather than hand-written**. Same conclusion as Carbon: the token
table should be data.

**Material Design 3 / Apple HIG.** M3's guidance site has no open source-of-truth repo;
`material-components/material-web/docs/components/button.md` is the closest public artifact
and is implementation docs with per-variant `### … tokens` tables and a Google-internal
freshness header (`freshness: { owner: … reviewed: '2026-07-31' }` — a nice idea, a
review-date field). Apple HIG has no public repo at all. **Neither offers a format to
adopt; I am not going to invent one for them.**

**Atlassian.** `atlassian/design-system` and `atlassian/design-system-website` are not
public under those names; I could not reach a primary source and am not going to
characterise it from blog posts.

### 2.9 Vaadin's own docs structure

`vaadin/docs`, AsciiDoc with YAML frontmatter. `articles/components/badge/index.adoc`:

```yaml
---
title: Badge
page-title: Badge component | Vaadin components
description: Badges are colored text elements containing small bits of information.
meta-description: The Vaadin component Badge is a colored text element used for labeling…
page-links:
  - 'API: …/vaadin-badge[TypeScript] / …/Badge.html[Java]'
  - 'Source: …/packages/badge[TypeScript] / …/vaadin-badge-flow-parent[Java]'
---

= [since:com.vaadin:vaadin@V25.1]#Badge#
```

Section headings are per-component: Badge has `== Label`, `== Icons`, `== Number`,
`== Use Cases`, `== Best Practices`; Button has `== Buttons with Icons`, `== Disabled`,
`== Focus`, `== Keyboard Usage`, `== Best Practices`, `== Related Components`. Only
*Best Practices* and *Related Components* recur.

Two mechanisms are worth noting. `[.example,themes="lumo,aura"]` scopes an example to a
theme — Vaadin's docs already model "this differs by theme", which is the axis this repo
lives on. And `// tag::description[]` marks a reusable fragment.

**Verdict.** There is no Vaadin component-spec skeleton to align to; aligning headings
would buy nothing. Two things are worth borrowing: a `page-links`-style pair of stable
links (Java API + source) in frontmatter, and the `[since:…]` version marker, which is
`lifecycle.introduced` under another name.

### 2.10 Agent-oriented conventions

**`llms.txt`** — `github.com/AnswerDotAI/llms-txt`, v2 (2026-08-10 revision), Jeremy
Howard. Proposes `/llms.txt`: an H1 project name (the only required section), a blockquote
summary, free prose, then H2-delimited lists of `[name](url): notes`. Adopted widely
enough that Chrome Lighthouse audits for it. **It is a format for serving docs to agents
over HTTP**, not for an in-repo spec. The transferable idea is narrow but real: *one small
index file with a fixed, parseable shape, whose job is to route to the detail.* That is
what `docs/design/README.md` should become — and today it is instead a fourth place where
content lives (D-3).

**GitHub spec-kit** — `github/spec-kit`,
[`templates/spec-template.md`](https://raw.githubusercontent.com/github/spec-kit/main/templates/spec-template.md).
A Markdown template with `**Status**: Draft`, `## User Scenarios & Testing *(mandatory)*`,
prioritised user stories, Given/When/Then acceptance scenarios — and, for undecided values,
an inline marker:

```markdown
- **FR-006**: System MUST authenticate users via [NEEDS CLARIFICATION: auth method not
  specified - email/password, SSO, OAuth?]
```

Relevant only as a counter-example. `[NEEDS CLARIFICATION: …]` is a greppable in-prose
marker, which is strictly weaker than a status column — it cannot carry an owner, an issue
number or a date, and it disappears the moment someone rewrites the sentence. It is a
feature-spec format, not a design-system format; there is nothing else here to take.

---

## 3. Comparison against this repo's real constraints

The constraints, restated from `CLAUDE.md` and `docs/design/README.md`: written by an agent
from Figma; read by an agent writing Vaadin Flow Java + scoped CSS; must express *decided
vs open*; must express states and variants; must survive round-tripping (never edited to
match code); must be diff-reviewable in a PR; must be readable by a designer who will not
run a build step.

| | DTCG format | DTCG resolver | Style Dictionary | Figma variables | CEM / CSF | Spectrum design data | DSDS | Primer / Polaris / Carbon MD | Today |
|---|---|---|---|---|---|---|---|---|---|
| Tokens | **yes** | modes only | consumes | source | no | **yes** | links out | prose | prose |
| Component spec | no | no | no | no | API only | **yes** | **yes** | **yes** | prose |
| States / variants | no | no | no | variants | attrs | **yes** (`trigger`, `precedence`) | **yes** | tables | tables (D-7) |
| Decided vs open | `$deprecated` only | no | no | no | `deprecated` | `lifecycle` + rules | `status`, `conformanceLevel` | Polaris `status` | foundations only (D-3) |
| Verified vs asserted | no | no | no | no | no | conformance fixtures | **`verificationMode`** | no | prose |
| Provenance (design vs code origin) | no | no | no | no | no | partial | **`docOrigin` + `authorship`** | Primer ids | bold label (D-1) |
| Light/dark modes | **no** | **yes** | via config | **yes** (`valuesByMode`) | no | **yes** (`sets`) | `theme` entity | prose | prose |
| Stable row ids | token path | — | — | `id`/`key` | — | `uuid`, `SPEC-NNN` | `identifier` | filename | **none** (D-9) |
| Machine-validatable | JSON Schema | JSON Schema | ajv/ts | OpenAPI | JSON Schema | **2 layers** | ajv draft2020 | none in practice | **none** |
| Diff-reviewable | fair (JSON churn) | fair | n/a | n/a | poor | fair | poor (deep JSON) | **good** | good |
| Designer-readable, no build | poor | poor | no | no | no | poor | poor | **good** | **good** |
| Keeps the load-bearing prose | `$description` | no | no | no | `description` | `documentBlocks` | `documentBlocks` | **native** | **native** |
| Vaadin/Java fit | neutral | neutral | neutral | neutral | web-components only | neutral | neutral | neutral | native |
| Cost to adopt | low (9 tokens) | med | med (Node CI) | n/a | n/a | high | high | **low** | zero |

Two rows decide it. **"Designer-readable, no build"** eliminates every JSON-first option as
the *authoring* format for component specs. **"Machine-validatable"** eliminates today's
format. Only the last-but-one column is strong on both, and it is strong on both precisely
because the structure lives in frontmatter and fixed headings rather than in a serialisation.

---

## 4. Recommendation

**Adopt three things, none of them a dependency.**

**A. DTCG JSON for the nine properties this app owns** — and *only* those. They are the
only tokens the project defines rather than observes. Everything Aura derives stays as a
measured cache with the formula and the version that produced it, which is what it actually
is.

**B. Fixed-heading Markdown + JSON-Schema-validated YAML frontmatter for component
specs** — the Primer/Polaris/Carbon shape, with the schema Primer and Polaris lack.
Carbon's four-column token/state tables in the body. Spectrum/DSDS vocabulary for the
fields.

**C. A ~200-line validator in CI** — the only part that actually fixes D-1 through D-10.
Format without enforcement is what produced the current mess; Primer proves that at scale.

### 4.1 Proposed file layout

```
docs/design/
├── README.md                     index + how it is consumed. Generated sections marked.
│                                 "What is still open" becomes GENERATED (D-3).
├── spec.schema.json              JSON Schema 2020-12 for component frontmatter
├── rules.yaml                    body-lint rule catalog, stable EM-NNN ids (Spectrum shape)
├── tokens/
│   ├── em.tokens.json            DTCG. The 9 properties this app SETS. Normative.
│   ├── aura-observed.json        measured cache: Aura's inputs, formulas, resolved scale,
│   │                             the Vaadin version and date it holds for. NOT DTCG —
│   │                             we do not own these.
│   └── token-reference.md        GENERATED from the two above. Never hand-edited.
├── foundations/                  unchanged in spirit: the DECISIONS, per concern.
│   ├── color.md  typography.md  spacing.md  radius.md  elevation.md  motion.md
│   └──                           scale tables DELETED (they move to token-reference.md);
│                                 the Decisions table gains ids and stays hand-written.
└── components/
    ├── README.md                 the two axes + the template. Inventory becomes GENERATED.
    └── <role>.md                 frontmatter + fixed headings + Carbon-shaped tables
```

Three files gain a generated marker (`token-reference.md`, the README's open list, the
components inventory). "Generated" here means a script writes them from the other files —
it does not mean a build step is needed to *read* the spec, and the generated files stay
committed so GitHub renders them.

### 4.2 Proposed frontmatter schema

Fifteen keys, all of them things currently smuggled into bold labels or prose. Validated by
`docs/design/spec.schema.json` (JSON Schema 2020-12, the draft Spectrum and DSDS both use).

```yaml
# --- identity -------------------------------------------------------------
spec: component/v1          # required. format version, so a migration is detectable
id: metric-card             # required. ^[a-z][a-z0-9-]*$, == filename stem, stable
name: Metric card           # required. display name
category: composite         # required. enum: themed-vaadin-primitive | composite
                            #                 | shared-java-component | shell
                            #   (the parenthetical from D-1 moves to `renders_as`)
renders_as: "CSS on a Div/VerticalLayout"   # optional free text. was the parenthetical

# --- provenance (DSDS docOrigin/authorship, D-1) --------------------------
origin: design              # required. enum: design | code | unresolved
authorship: ai-assisted     # required. enum: human | ai-assisted | ai-generated
authored_by: figma-survey   # optional. the skill or person
implementation: none        # required. enum: none | conforms | drifted | unaudited
                            #   AUDIT-OWNED. Note only; no prose in the value.
implementation_note: "nothing in the app renders a metric card yet"

# --- the two ends ---------------------------------------------------------
design:                     # required when origin != code; else `~` with a reason
  nodes:
    - id: "88:12918"
      layer: metrics-row
    - id: "88:12919"
      layer: metric-card
  also:
    - id: "327:11681"
      note: Approvals — identical but for the copy
code:                       # required. empty lists are legal and meaningful
  java: []
  css: []
  classes: [metrics-row, metric-card, metric-card-label, metric-card-value,
            metric-card-sub, metric-card-sub-alert]

# --- decisions: the fix for D-3 and D-9 -----------------------------------
decisions:
  - id: metric-card.figure-unit          # required. globally unique, citable in a ticket
    status: settled                      # required. enum: settled | open
    summary: The caption disambiguates count vs amount; no unit on the figure
    since: "#147"                        # optional. issue or ADR that settled it
  - id: metric-card.needs-you-grouping
    status: settled
    summary: Drafts and rejected count together; the design's sample data is the defect
    since: "#147"
    raised_with_designer: "#160"

# --- measured figures: the fix for D-5 (DSDS verificationMode) ------------
measurements:
  - id: metric-card.sub-alert-contrast
    verification: unverified             # enum: unverified | measured | automated
    note: red sub-line fragment on --aura-surface-color, both schemes

resolved_against:           # required when the body cites any derived value
  vaadin: 25.2.1
  tokens: docs/design/tokens/aura-observed.json

cross_references: [totals-card, report-card, report-list-section]
adrs: [ADR-0025]
findings: [F-062, F-064]
issues: ["#147"]
```

Schema-enforceable, and each rule maps to a diagnosis:

| Rule | Enforces | Fixes |
|---|---|---|
| `category`, `origin`, `implementation`, `status`, `verification`, `authorship` are `enum` | no prose in an enum | D-1 |
| `additionalProperties: false` at the top level (Spectrum's rule) | no invented fields | D-1 |
| `id` matches filename stem; `decisions[].id` starts with it and is globally unique | addressability | D-9 |
| `implementation: drifted` requires a `## Divergence` section (body lint) | the pair holds | D-1 |
| `design` required unless `origin: code` | no silent gap | — |

### 4.3 Proposed body rules (checkable by regex, no schema needed)

`docs/design/rules.yaml`, Spectrum-shaped, stable ids so a diagnostic can be cited:

```yaml
spec_version: "component/v1"
rules:
  - id: EM-001
    name: heading-skeleton
    severity: error
    assert: >
      H2s MUST be drawn from, and appear in the order of:
      Overview, Anatomy, Tokens, States, Behaviour, API, Code example,
      Divergence, Cross-references. An omitted section MUST be replaced by the
      heading plus the single line "Not applicable — <reason>".
  - id: EM-002
    name: extra-sections-are-h3
    severity: error
    assert: >
      Anything outside the skeleton (Copy, Responsive rules, Small screens,
      Navigation, Empty, Collapsed) MUST be an H3 nested under Behaviour.
  - id: EM-003
    name: token-column-is-tokens-only
    severity: error
    assert: >
      In the Tokens and States tables, the Token column MUST contain only
      var-names matching ^--(aura|vaadin|em)-[a-z0-9-]+$, a colour literal
      declared in the frontmatter, or the word "stock". A bare number is an error.
    message: "Resolved value {value} in the Token column; it belongs to token-reference.md"
  - id: EM-004
    name: token-exists
    severity: error
    assert: >
      Every --em-* named MUST exist in tokens/em.tokens.json; every --aura-*/--vaadin-*
      MUST exist in tokens/aura-observed.json.
    message: "Undefined custom property {name} — the F-062 failure mode, in the spec"
  - id: EM-005
    name: six-states
    severity: error
    assert: >
      The States table MUST have exactly six rows keyed default, hover, active,
      focus, disabled, error — one row each, never combined — and no cell may be empty.
  - id: EM-006
    name: element-is-declared-anatomy
    severity: error
    assert: The Element column in Tokens/States MUST name a Part declared in Anatomy.
  - id: EM-007
    name: table-headers-are-canonical
    severity: error
    assert: >
      Tokens table header MUST be `| Element | Property | Token | Note |`;
      States table header MUST be `| State | Element | Property | Token | Behaviour |`.
  - id: EM-008
    name: open-decisions-are-reachable
    severity: error
    assert: >
      Every decisions[] entry with status "open" MUST appear in the generated
      "What is still open" list, and MUST carry a `since` or an owner.
  - id: EM-009
    name: measured-figures-declared
    severity: warning
    assert: >
      A contrast ratio or a rendered px figure in the body MUST correspond to a
      measurements[] entry; verification "unverified" MUST NOT be stated as fact.
  - id: EM-010
    name: inventory-is-generated
    severity: error
    assert: >
      components/README.md's inventory table MUST equal the frontmatter of the
      files it lists.
```

EM-004 is the one that pays for the whole exercise: it is the F-062 failure mode
(`CLAUDE.md` §Theming) applied to the *spec* rather than the CSS, and it catches
`--aura-border-color-secondary` (`metric-card.md:69-70`, `report-card.md:102`) mechanically
instead of by the author remembering.

### 4.4 Worked example — `docs/design/components/metric-card.md`, rewritten in full

Nothing is dropped. Everything the current file says survives; the difference is where each
claim lives and whether a script can check it.

````markdown
---
spec: component/v1
id: metric-card
name: Metric card
category: composite
renders_as: "CSS on a Div/VerticalLayout"

origin: design
authorship: ai-assisted
authored_by: figma-survey
implementation: none
implementation_note: nothing in the app renders a metric card yet

design:
  nodes:
    - id: "88:12918"
      layer: metrics-row
    - id: "88:12919"
      layer: metric-card
    - id: "88:12923"
      layer: metric-card
    - id: "88:12927"
      layer: metric-card
  also:
    - id: "327:11681"
      note: Approvals — identical but for the copy, so build it parameterised
code:
  java: []
  css: []
  classes:
    - metrics-row
    - metric-card
    - metric-card-label
    - metric-card-value
    - metric-card-sub
    - metric-card-sub-alert

decisions:
  - id: metric-card.not-interactive
    status: settled
    summary: >
      A summary of the list below it, never a control. No hover, no focus, no
      click. A figure that should filter the list is a different component and
      needs a design.
  - id: metric-card.figure-unit
    status: settled
    summary: >
      The figure is a count on two cards and an amount on the third; the caption
      disambiguates. Do not append a unit to the figure.
  - id: metric-card.sub-line-nesting
    status: settled
    summary: >
      The alert fragment is one Span inside another, not two siblings with a
      separator, so the "·" stays in the secondary colour.
  - id: metric-card.zeroes-not-blanks
    status: settled
    summary: >
      A user with no reports gets 0 / €0.00 and a rendered sub-line. Never hidden,
      never a dash — a missing card reads as a broken page.
    since: "#147"
  - id: metric-card.year-is-data
    status: settled
    summary: >
      The year in caption 3 is returned by the service, not a literal, so caption
      and figure can never disagree.
    since: "#147"
  - id: metric-card.needs-you-grouping
    status: settled
    summary: >
      "Needs you" counts drafts and rejected together, with rejected broken out in
      the sub-line. The design's own sample data contradicts this and is the defect.
    since: "#147"
    raised_with_designer: "#160"
  - id: metric-card.kit-variables-not-copied
    status: settled
    summary: >
      The design's reference code names --shades/surface-3,
      --aura-border-color-secondary and --text-colors/header-text. Aura defines
      none of the three; each ships a fallback, so a literal translation freezes
      forever (F-062). Use the tokens in the Tokens table.

measurements:
  - id: metric-card.sub-alert-contrast
    verification: unverified
    note: --aura-red-text on --aura-surface-color, both schemes
  - id: metric-card.caption-contrast
    verification: unverified
    note: --vaadin-text-color-secondary at 12px on --aura-surface-color

resolved_against:
  vaadin: 25.2.1
  tokens: docs/design/tokens/aura-observed.json

cross_references: [totals-card, report-card, report-list-section]
adrs: [ADR-0025]
findings: [F-062]
issues: ["#147"]
---

# Metric card

## Overview

One at-a-glance aggregate above a list: a caption, one large figure, and a one-line
breakdown under it. Three sit side by side in a `metrics-row` so the row reads as a single
band of context before the user reaches the filters.

It is a **summary of the list below it, never a control** —
`metric-card.not-interactive`. Nothing in it is clickable and it has no hover or focus
affordance.

**Not** for a single report's money: a report's own net/VAT/total breakdown is
[`totals-card`](totals-card.md), which is a labelled table inside one report rather than an
aggregate across many.

The same component serves **My Expenses** and **Approvals** — the two frames are identical
but for the copy, so build it parameterised by caption/figure/sub-line rather than
hard-coding this view's three.

## Anatomy

| Part | Class | Content |
|---|---|---|
| Row | `.metrics-row` | three cards, equal width, `flex: 1 0 0` with `min-width: 0` |
| Card | `.metric-card` | column |
| Caption | `.metric-card-label` | "Needs you", "In flight", "Reimbursed 2026" |
| Figure | `.metric-card-value` | `2`, `1`, `€100.00` — a count *or* an amount |
| Sub-line | `.metric-card-sub` | "€1234.00 · 1 rejected", "waiting 36 days" |
| Alert | `.metric-card-sub-alert` | the "1 rejected" fragment only |

The figure is a count on two cards and an amount on the third
(`metric-card.figure-unit`). The sub-line is partially coloured
(`metric-card.sub-line-nesting`).

## Tokens

| Element | Property | Token | Note |
|---|---|---|---|
| Row | `gap` | `--vaadin-gap-l` | |
| Card | `padding` | `--em-card-padding` | |
| Card | `gap` | `--vaadin-gap-m` | |
| Card | `border-radius` | `--em-card-radius` | |
| Card | `background` | `--aura-surface-color` | design draws `--shades/surface-3`; see `metric-card.kit-variables-not-copied` |
| Card | `border` | `--vaadin-border-color-secondary` | 1px solid. The design's `--aura-border-color-secondary` is one prefix away and undefined |
| Caption | `font-size` | `--aura-font-size-xs` | |
| Caption | `font-weight` | `--aura-font-weight-medium` | |
| Caption | `color` | `--vaadin-text-color-secondary` | design draws `#64748b`, the kit's literal for this |
| Figure | `font-size` | `--em-font-size-metric` | **not yet declared in `aura-theme.css`** — see token-reference |
| Figure | `font-weight` | `--aura-font-weight-semibold` | design draws 700; Aura's scale stops at 600 |
| Figure | `color` | `--vaadin-text-color` | design draws `#0f172a`, the kit's literal for this |
| Sub-line | `font-size` | `--aura-font-size-xs` | |
| Sub-line | `color` | `--vaadin-text-color-secondary` | |
| Alert | `color` | `--aura-red-text` | |

## States

| State | Element | Property | Token | Behaviour |
|---|---|---|---|---|
| default | Card | — | — | the only visual state |
| hover | Card | — | — | **n/a** — not interactive, and must not appear to be |
| active | Card | — | — | n/a — not interactive |
| focus | Card | — | — | n/a — not a tab stop; it holds no control and no link |
| disabled | Card | — | — | n/a — a metric has no enabled/disabled axis |
| error | Card | — | — | n/a — a metric that cannot be computed shows a zero, not an error |

## Behaviour

### Empty

Zeroes, not blanks — `metric-card.zeroes-not-blanks`. Asserted in the service tests
for #147.

### Copy

Exactly as drawn, because each line is doing specific work:

| Card | Caption | Figure | Sub-line |
|---|---|---|---|
| 1 | `Needs you` | count of drafts + rejected | `€<total> · <n> rejected` |
| 2 | `In flight` | count of submitted | `waiting <n> days` |
| 3 | `Reimbursed <year>` | total € approved this year | `<n> approved` |

The year is data, not a literal (`metric-card.year-is-data`). The grouping behind caption 1
and the design defect it contradicts are `metric-card.needs-you-grouping`.

## API

Not applicable — nothing implements this yet. When built it should take
`(caption, figure, subLine)` with the alert fragment optional, so Approvals reuses it.

## Code example

```java
var card = new VerticalLayout(
        span("Needs you", "metric-card-label"),
        span(String.valueOf(metrics.needsYouCount()), "metric-card-value"),
        subLine(metrics));
card.addClassName("metric-card");
card.setPadding(false);
card.setSpacing("var(--vaadin-gap-m)");
```

## Divergence

Not applicable — `implementation: none`.

## Cross-references

[`totals-card`](totals-card.md) — one report's money, not many ·
[`report-card`](report-card.md) — the list the metrics summarise ·
[`report-list-section`](report-list-section.md) ·
[`../foundations/typography.md`](../foundations/typography.md) — why 28px has a property
````

**What changed, and why it is better**

| Before | After | Fixes |
|---|---|---|
| `**Implementation:** none — nothing in the app renders…` | `implementation: none` + `implementation_note` | D-1 |
| `**Category:** composite (CSS on a Div/VerticalLayout)` | `category: composite` + `renders_as` | D-1 |
| Design nodes as one prose line | `design.nodes[]` with node + layer | D-9 |
| `--vaadin-gap-l` (16), ×9 in this file | token names only; numbers live in `token-reference.md` | D-4 |
| `| Property | Token |` | `| Element | Property | Token | Note |`, Carbon's shape | D-6 |
| Six decisions buried in prose | six `decisions[]` with ids, citable from a ticket | D-3, D-9 |
| Contrast never mentioned | two `measurements[]`, both `unverified` | D-5 |
| `## Copy`, `### Empty` invented ad hoc | `### Copy`, `### Empty` under `## Behaviour` | D-8 |
| `## API` / `## Divergence` silently shaped | explicit "Not applicable — <reason>" | D-8 |

The file got **longer** and the prose got **shorter**, which is the intended trade: the
paragraph that explained the kit-variable trap became a decision with an id, so
`report-card.md` can reference `metric-card.kit-variables-not-copied` instead of repeating
it (it currently repeats it, at `report-card.md:93-108`).

### 4.5 `tokens/em.tokens.json` — the DTCG half

Nine properties, DTCG 2025.10, with the project's own metadata under `$extensions` — which
is what `$extensions` is for ("tools MAY add proprietary, user-, team- or vendor-specific
data … reverse domain name notation is recommended").

```json
{
  "$schema": "https://www.designtokens.org/schemas/2025.10/format.json",
  "em": {
    "$description": "The properties this app sets. Everything else is Aura stock.",
    "card": {
      "radius": {
        "$type": "dimension",
        "$value": { "value": 12, "unit": "px" },
        "$description": "Off-scale: between --vaadin-radius-m 9 and --vaadin-radius-l 15.",
        "$extensions": {
          "com.vaadin.expensemanager": {
            "cssProperty": "--em-card-radius",
            "status": "settled",
            "declaredIn": "aura-theme.css",
            "decision": "radius.card-radius"
          }
        }
      }
    },
    "font-size": {
      "metric": {
        "$type": "dimension",
        "$value": { "value": 28, "unit": "px" },
        "$description": "The metric card's figure. Beyond --aura-font-size-xl 18.",
        "$extensions": {
          "com.vaadin.expensemanager": {
            "cssProperty": "--em-font-size-metric",
            "status": "settled",
            "declaredIn": null,
            "decision": "typography.display-ramp"
          }
        }
      }
    }
  }
}
```

`declaredIn: null` is D-5, encoded. A one-line check — *every token with
`status: settled` and `declaredIn: "aura-theme.css"` is actually declared there, and every
`--em-*` in `styles.css` resolves to a declared token* — makes the "complete list" claim
true or fails the build. That check is worth more than the whole DTCG adoption on its own,
and DTCG is simply the least surprising place to hang it.

`tokens/aura-observed.json` stays deliberately un-DTCG: it is a measurement of a third
party's derived scale, carrying `vaadin: "25.2.1"`, the date, the three inputs, the
formulas from `token-reference.md:142-158`, and per-token
`{ value, lineHeight?, verification }`. Modelling it as DTCG would assert ownership we do
not have, and would invite a generator to write Aura's own scale back out as CSS — which is
the one thing `figma-theme` is built to avoid ("Set only what differs from the theme's own
default", SKILL.md:64).

---

## 5. Migration notes

### What each skill changes

**`figma-survey`** — step 8 currently says "following any template its own `README`
defines" (SKILL.md:260). That stays true; the template just becomes a schema. Concretely:

- Emit frontmatter, not bold labels. The five current fields map 1:1 plus
  `authorship: ai-assisted`, which the skill can always assert about its own output.
- Step 6's decision table (SKILL.md:196) already has `Property | Design | App | Decided |
  From | Status`. Give each row an `id` and it becomes `decisions[]` unchanged. This is the
  smallest change in the whole plan and it is the one that fixes D-3.
- Step 8's "leave the slot marked as unverified" (SKILL.md:320-323) becomes a
  `measurements[]` entry with `verification: unverified` — a slot a tool can find, rather
  than a word a reader has to notice.
- **Delete** the instruction to restate resolved values beside tokens. Step 5 computes them
  (SKILL.md:170-175); they belong in `aura-observed.json`, referenced by
  `resolved_against`.

**`figma-theme`** — step 1's "refuse an unsettled spec" (SKILL.md:36-38) stops being a
reading exercise: `jq`/grep for `status: open` across `decisions[]` and the foundations
tables. Step 4's "write the measurements back" flips `verification: unverified` →
`measured` and fills the value, and its closing "say what this run **staled**"
(SKILL.md:133-135) becomes exact rather than advisory — bumping `aura-observed.json`
invalidates every `resolved_against` pinned to the old version, and that is a list, not a
recommendation.

**`figma-to-vaadin` step 5b** and **`implement-use-case` step 4** need no wording change.
They say "take tokens and states from the component's file"; the file just gets more
checkable. `implement-use-case`'s "account for all six states with `n/a` plus a reason"
gains a linter (EM-005) instead of relying on the author.

**A new, small audit step** owns `implementation:`. `components/README.md:71-74` already
says "This field belongs to an audit, not to a human", and the 2026-08 note admits "No
audit tooling exists yet". Two of the ten rules — EM-004 (token exists) and EM-003 (no
numbers in the token column) — *are* a mechanical conformance audit, and a third
(every class in `code.classes` exists in `styles.css`) is a five-line grep. That is enough
to move several `unaudited` rows honestly.

### What CI can validate

CI today is `./mvnw -B test` and nothing else (`.github/workflows/tests.yml`). Adding a
docs job is the only new infrastructure, and it is deliberately kept to one step:

| Check | How | Cost |
|---|---|---|
| Frontmatter against `spec.schema.json` | `npx ajv validate --spec=draft2020` — the command DSDS documents | Node, already downloaded by `vaadin-maven-plugin` |
| EM-001…EM-010 body lint | one script over 15 files | ~200 lines |
| `--em-*` in spec ⇄ `aura-theme.css` | grep both, diff | trivial, kills D-5 |
| `--aura-*`/`--vaadin-*` in spec ⊆ `aura-observed.json` | set difference | trivial, EM-004, the F-062 check |
| Generated files are current | regenerate, `git diff --exit-code` | trivial, kills D-2 and D-3 |
| `decisions[].id` globally unique, referenced ids resolve | one pass | trivial, D-9 |
| Six states present, tables have canonical headers | regex | trivial, D-6, D-7 |

Deliberately **not** validated: whether a decision is *right*, whether a component is the
right one for the design's intent, whether the prose is good.
`components/README.md:71-74` already makes this point about `conforms` and it stays true —
"no mechanical divergence found" is all any of this can mean.

### Migration order, and what is throwaway

1. **Write `spec.schema.json` and `rules.yaml` first, against the files as they are.**
   Every rule will fail somewhere; that failure list *is* the migration plan, and it will
   be more accurate than D-1…D-10 because it is exhaustive.
2. **`tokens/`.** Hand-write `em.tokens.json` (nine tokens) and `aura-observed.json` from
   the existing `token-reference.md`, then generate `token-reference.md` back and diff
   against the committed one. A clean diff proves the extraction lost nothing.
3. **One component, end to end** — `metric-card` (above) or `badge` (smallest, and one of
   the three with no token table, so it exercises the "Not applicable — <reason>" path).
   Get the schema and lint green on that one file before touching the other fourteen.
4. **The remaining fourteen**, mechanically. The bold-label → frontmatter conversion is
   scriptable; extracting `decisions[]` from prose is not, and is the real work — roughly
   4-8 decisions per file.
5. **`foundations/`** last: delete the duplicated scale tables (D-10), add ids to the
   Decisions rows, keep everything else.
6. **Turn the generated sections on**: `token-reference.md`, the components inventory, the
   README's open list.

**Throwaway** — write these expecting to delete them:

- The bold-label → YAML converter script.
- The current `components/README.md` inventory table and the README's "What is still open"
  table, both of which become generated (D-2, D-3).
- The 60 parenthesised resolved values (D-4). They are not migrated, they are deleted.
- The duplicated scale tables in `foundations/radius.md`, `spacing.md`, `typography.md`
  (D-10).

**Not throwaway, do not touch:** the two-axis Origin/Implementation model
(`components/README.md:42-84`), ADR-0025, and every paragraph of judgement in the component
files. Those are the asset. This whole proposal exists to stop them being diluted by
mechanical facts that a script should be maintaining.

---

## 6. Open questions for the owner

Ordered by how much they change the shape of the answer.

1. **Is a Node step in CI acceptable?** Everything in §4 that actually fixes D-1…D-10 needs
   one linter. Vaadin already downloads Node for `prepare-frontend`, so the marginal cost is
   small, but it makes the docs job depend on the frontend toolchain. The alternative is a
   Java validator (`networknt/json-schema-validator` in a test, run by `./mvnw test` with no
   new CI step) — more code, zero new infrastructure, and it puts spec validation in the
   same suite as everything else. **My inclination: the Java test.** It matches
   ADR-0012's testing strategy and needs no workflow change. Your call.

2. **How much structure does frontmatter get before a designer stops reading the file?**
   §4.4's frontmatter is ~80 lines above a ~90-line body. That is a real cost and it lands
   entirely on the human. Three honest options: (a) as proposed; (b) frontmatter carries
   only identity + status and `decisions[]` stay as a body table with an `Id` column —
   less validatable, much lighter; (c) split, `metric-card.md` + `metric-card.spec.yaml`,
   which is worst for review and best for tooling. **I recommend (b) if the answer to Q1 is
   "no linter", and (a) if there is one** — the frontmatter is only worth its weight if
   something reads it.

3. **Does `decisions[]` replace the Divergence tables, or sit beside them?** A Divergence
   row (`report-card.md:141-150`) is a decision with a scheduled closing date. Modelling it
   as `decisions[{status: open, closes_in: "#NNN"}]` unifies them and makes the whole
   backlog one query; keeping both is less disruptive and keeps the Divergence table
   readable as a table. Unifying is cleaner and is a bigger migration.

4. **Do we adopt DTCG at all, or just JSON?** For nine tokens, DTCG's `$value`/`$type`
   ceremony buys interoperability nobody has asked for. Against: if the `--em-*` set grows,
   or if the values ever need to exist in Java as well as CSS, retrofitting the format is
   worse than starting in it, and `$deprecated`/`$extensions` are genuinely the right shape.
   **I lean adopt** — it is nine tokens, so the cost of being wrong is an hour.

5. **`aura-observed.json` — is a cached measurement of someone else's theme a thing we want
   to own?** `token-reference.md:181-183` already commits to re-measuring on every Vaadin
   minor. Making it a data file makes the staleness detectable (`resolved_against.vaadin`
   ≠ the pom's version → warn) and makes the obligation more visible. It also means a
   Vaadin bump now fails a docs check, which some will read as friction and I would read as
   the point.

6. **Should `authorship` be recorded at all?** DSDS argues it lets readers judge how much
   to trust a doc, and every file in `docs/design/` is agent-written. Recording
   `ai-assisted` on all fifteen is either usefully honest or uniformly uninformative. It
   becomes interesting only if some files are later human-reviewed and change to `human`.
   **Worth adopting only if there is an intent to review.**

7. **Does the `code` origin need the same rigour?** `error-summary`, `empty-state`,
   `editor-dialog`, `theme-switcher` are code-origin: the spec documents, the code leads
   (`components/README.md:52`). For those, DSDS would say `docOrigin: extracted`, and much
   of §4's machinery — decisions with ids, `verification`, `resolved_against` — is
   over-engineered. A lighter `component/v1` profile for code-origin files is possible;
   two profiles is also two things to keep straight.

8. **Should Figma's `codeSyntax.WEB` carry the token mapping?** If the Aura kit populated it,
   the kit-variable translation tables (`report-card.md:99-104`, `metric-card.md:64-70`)
   would come out of `figma-survey` as data instead of being rediscovered per component. I
   could not check the real file — the Figma MCP is unauthenticated in this session. Worth
   checking, and worth raising with the designer alongside #160 if it is empty.

9. **Is any of this worth doing before the app is further along?** Fifteen component files
   and one theme is small enough that the current format still works if you read carefully.
   The counter-argument is the one in `README.md:3-7` and ADR-0025: the whole spec exists
   because "an agent with no memory of the last session will otherwise invent a plausible
   value", and *reading carefully* is exactly what such an agent will not do. The cost of
   migrating grows linearly with the number of components and the format problems compound;
   fifteen is a much better number to migrate than forty.

---

## Sources

All fetched 2026-08-29 from the repository that owns the artifact, since the rendered doc
sites listed at the top of this note are blocked from this session.

**DTCG** — [`design-tokens/community-group`](https://github.com/design-tokens/community-group):
[`technical-reports/format/index.html`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/index.html) ·
[`design-token.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/design-token.md) ·
[`groups.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/groups.md) ·
[`aliases.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/aliases.md) ·
[`types.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/types.md) ·
[`composite-types.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/composite-types.md) ·
[`file-format.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/format/file-format.md) ·
[`resolver/index.html`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/resolver/index.html) ·
[`resolver/introduction.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/resolver/introduction.md) ·
[`resolver/syntax.md`](https://raw.githubusercontent.com/design-tokens/community-group/main/technical-reports/resolver/syntax.md).
CG-FINAL publication (2025-10-28) reported by web search at
`https://w3c.github.io/cg-reports/design-tokens/CG-FINAL-format-20251028/` and
`https://www.w3.org/community/design-tokens/2025/10/28/design-tokens-specification-reaches-first-stable-version/`
— **both unreachable from this session; unverified.**

**Style Dictionary** — [`amzn/style-dictionary`](https://github.com/amzn/style-dictionary):
[`package.json`](https://raw.githubusercontent.com/amzn/style-dictionary/main/package.json) (v5.5.2) ·
[`docs/src/content/docs/info/tokens.md`](https://raw.githubusercontent.com/amzn/style-dictionary/main/docs/src/content/docs/info/tokens.md) ·
[`docs/src/content/docs/versions/v5/migration.md`](https://raw.githubusercontent.com/amzn/style-dictionary/main/docs/src/content/docs/versions/v5/migration.md).
v5 DTCG-as-base-format and the `usesDtcg` flag: web search summary of
`https://styledictionary.com/info/dtcg/` and `https://styledictionary.com/reference/utils/tokens/`
— **site blocked; second-hand.**

**Figma** — [`figma/rest-api-spec`](https://github.com/figma/rest-api-spec):
[`dist/api_types.ts`](https://raw.githubusercontent.com/figma/rest-api-spec/main/dist/api_types.ts) ·
[`openapi/openapi.yaml`](https://raw.githubusercontent.com/figma/rest-api-spec/main/openapi/openapi.yaml).
[`figma/code-connect` README](https://raw.githubusercontent.com/figma/code-connect/main/README.md).

**Adobe Spectrum** — [`adobe/spectrum-design-data`](https://github.com/adobe/spectrum-design-data):
[root `README.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/README.md) ·
[`packages/tokens/README.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/tokens/README.md) ·
[`packages/tokens/src/color-palette.json`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/tokens/src/color-palette.json) ·
[`packages/tokens/schemas/token-types/color-set.json`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/tokens/schemas/token-types/color-set.json) ·
[`packages/design-data-spec/README.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/README.md) ·
[`rules/rules.yaml`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/rules/rules.yaml) ·
[`spec/component-format.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/spec/component-format.md) ·
[`spec/document-blocks.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data-spec/spec/document-blocks.md) ·
[`packages/design-data/components/badge.json`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/design-data/components/badge.json) ·
[`packages/component-schemas/README.md`](https://raw.githubusercontent.com/adobe/spectrum-design-data/main/packages/component-schemas/README.md).

**DSDS** — [`somerandomdude/design-system-documentation-schema`](https://github.com/somerandomdude/design-system-documentation-schema):
[`README.md`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/README.md) ·
[`package.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/package.json) (v0.15.2) ·
[`spec/examples/minimal/component.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/examples/minimal/component.json) ·
[`spec/schema/metadata/doc-origin.schema.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/schema/metadata/doc-origin.schema.json) ·
[`spec/schema/common/criterion.schema.json`](https://raw.githubusercontent.com/somerandomdude/design-system-documentation-schema/main/spec/schema/common/criterion.schema.json).

**Primer** — [`primer/design`](https://github.com/primer/design):
[`content/components/button.mdx`](https://raw.githubusercontent.com/primer/design/main/content/components/button.mdx) ·
[`content/components/banner.mdx`](https://raw.githubusercontent.com/primer/design/main/content/components/banner.mdx) ·
[`content/components/action-list.mdx`](https://raw.githubusercontent.com/primer/design/main/content/components/action-list.mdx) ·
[`src/layouts/component-layout.tsx`](https://raw.githubusercontent.com/primer/design/main/src/layouts/component-layout.tsx).

**Polaris** — [`Shopify/polaris`](https://github.com/Shopify/polaris):
[`polaris.shopify.com/content/components/actions/button.mdx`](https://raw.githubusercontent.com/Shopify/polaris/main/polaris.shopify.com/content/components/actions/button.mdx) ·
[`polaris.shopify.com/src/types.ts`](https://raw.githubusercontent.com/Shopify/polaris/main/polaris.shopify.com/src/types.ts) ·
[`documentation/Component READMEs.md`](https://raw.githubusercontent.com/Shopify/polaris/main/documentation/Component%20READMEs.md).

**Carbon** — [`carbon-design-system/carbon-website`](https://github.com/carbon-design-system/carbon-website):
[`src/pages/components/button/usage.mdx`](https://raw.githubusercontent.com/carbon-design-system/carbon-website/main/src/pages/components/button/usage.mdx) ·
[`style.mdx`](https://raw.githubusercontent.com/carbon-design-system/carbon-website/main/src/pages/components/button/style.mdx).
[`carbon-design-system/carbon` `packages/themes/README.md`](https://raw.githubusercontent.com/carbon-design-system/carbon/main/packages/themes/README.md).

**SLDS** — [`salesforce-ux/design-system`](https://github.com/salesforce-ux/design-system):
[`ui/components/buttons/docs.mdx`](https://raw.githubusercontent.com/salesforce-ux/design-system/main/ui/components/buttons/docs.mdx).

**Material** — [`material-components/material-web`](https://github.com/material-components/material-web):
[`docs/components/button.md`](https://raw.githubusercontent.com/material-components/material-web/main/docs/components/button.md).
No open source-of-truth repo exists for m3.material.io or Apple HIG.

**Vaadin** — [`vaadin/docs`](https://github.com/vaadin/docs):
[`articles/components/badge/index.adoc`](https://raw.githubusercontent.com/vaadin/docs/main/articles/components/badge/index.adoc) ·
[`articles/components/button/index.adoc`](https://raw.githubusercontent.com/vaadin/docs/main/articles/components/button/index.adoc).

**Web Components / Storybook** —
[`webcomponents/custom-elements-manifest` `schema.json`](https://raw.githubusercontent.com/webcomponents/custom-elements-manifest/main/schema.json) ·
[`storybookjs/storybook` `docs/api/csf/index.mdx`](https://raw.githubusercontent.com/storybookjs/storybook/next/docs/api/csf/index.mdx).

**Agent conventions** —
[`AnswerDotAI/llms-txt` `nbs/index.qmd`](https://raw.githubusercontent.com/AnswerDotAI/llms-txt/main/nbs/index.qmd) ·
[`github/spec-kit` `templates/spec-template.md`](https://raw.githubusercontent.com/github/spec-kit/main/templates/spec-template.md).
