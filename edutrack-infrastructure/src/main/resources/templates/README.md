# EduTrack goal templates

A template is a reusable plan for a goal: a roadmap, the competencies it builds, the measures it tracks,
practice routines and starter flashcards. Applying one creates an ordinary goal; nothing stays linked to the
template afterwards.

## Where templates live

| Kind | Location |
| --- | --- |
| Built in | `templates/en/*.json` and `templates/es/*.json` on the classpath (this folder). Files are listed in file-name order. A language without its own folder falls back to `en`. The same template has the **same `id` in every language**. |
| Yours | `<id>.json` files in `${user.home}/.edutrack/templates/` (property `edutrack.templates-dir`). |
| Shared | Any `.json` file in this format. *Import* stores it as one of your templates (under a new id if its id is already taken); *Export* writes one out. |

## Format

UTF-8 JSON, one object per file. Only `id`, `name` and a non-empty `steps` list are required.

```json
{
  "id": "language-exam",
  "name": "Language exam preparation",
  "description": "Level up a language and pass an official exam.",
  "durationWeeks": 16,
  "weeklyTargetMinutes": 420,
  "steps": [
    { "title": "Diagnose your level", "description": "Take a placement test.", "hours": 4 }
  ],
  "competencies": [
    { "name": "Listening", "description": "Understand recordings at exam speed.", "target": 4 }
  ],
  "measures": [
    { "name": "Mock exam", "unit": "%", "max": 100, "target": 75,
      "direction": "HIGHER_IS_BETTER", "combine": "AVERAGE", "competency": "Listening" }
  ],
  "routines": [
    { "title": "Vocabulary flashcards", "cadence": "DAILY", "minutes": 15, "step": 0 },
    { "title": "Speaking practice", "cadence": "WEEKLY", "timesPerWeek": 3, "minutes": 25 }
  ],
  "cards": [
    { "front": "CEFR B2: what can you do?", "back": "Understand the main ideas of complex texts.", "reversible": false }
  ]
}
```

### Top level

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | text, required | 1 to 64 letters, digits, `-` or `_`. Also the file name of a user template. |
| `name` | text, required | Shown in the template list; the default title of the goal. Up to 120 characters. |
| `description` | text | Becomes the goal's description. |
| `durationWeeks` | whole number, 1 to 520 | The goal's deadline is the start date plus this many weeks. Leave it out for an open-ended habit. |
| `weeklyTargetMinutes` | whole number, 1 to 10080 | The goal's weekly study target. |
| `steps` | list, required | The roadmap, in order. |
| `competencies`, `measures`, `routines`, `cards` | lists | Optional; a missing list is empty. |

### Entries

* **steps**: `title` (required), `description`, `hours` (estimated hours, up to one decimal).
* **competencies**: `name` (required, unique ignoring case), `description`, `target` (level wanted, 1 to 5).
* **measures**: `name` (required), `unit` (up to 20 characters), `max` (scale, greater than 0), `target` (not above `max`),
  `direction` (`HIGHER_IS_BETTER` by default, or `LOWER_IS_BETTER`), `combine` (how the parts of one attempt become its value:
  `NONE` by default, `AVERAGE` or `SUM`), `competency` (the **name** of one of the template's competencies).
* **routines**: `title` (required), `cadence` (`DAILY` or `WEEKLY`), `timesPerWeek` (1 to 7; ignored for `DAILY`, required for
  `WEEKLY`), `minutes` (1 to 600), `step` (the **0-based index** into `steps` that the routine belongs to).
* **cards**: `front`, `back` (both required, Markdown), `reversible` (also ask back to front; `false` by default).

Enum values are not case sensitive. A template is checked with the same rules as anything typed by hand (a bad value is
reported with the usual messages) plus its own: the id format, at least one step, unique competency names, and `competency`
and `step` references that exist. A file that fails any rule is rejected with the error `template-invalid`.

## Writing a good template

* Keep it subject-agnostic: a plan other people in the same situation can reuse.
* Give every step a realistic `hours` estimate; the roadmap's progress and the pace calculations use them.
* Prefer three to six competencies with targets you can honestly rate yourself against.
* Starter cards are examples of the habit, not a course: five to fifteen are plenty.
* For a translation, keep `id` identical and translate every text, including the competency names that measures refer to.
