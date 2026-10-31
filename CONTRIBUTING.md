# Contributing

Metabasis welcomes contributions that preserve its offline-first, no-telemetry design. Please open an issue before substantial changes and include a concise description, screenshots for UI changes, and the Android version/device used for native behavior.

## Submit a theme

1. Add a lowercase, hyphenated JSON file in `themes/` (for example `themes/ocean.json`).
2. Include exactly five stage colors, five stage names, and near-white glass text colors. Use six-digit `#RRGGBB` hex values.
3. Keep stage order meaningful from first stage to final stage; supply a unique stable `id` and a human-readable `name`.
4. Validate the file as JSON and ensure every stage array contains exactly five entries. Do not include external URLs, scripts, or telemetry.
5. Open a pull request with a brief palette rationale and a preview image if available.

Schema example:

```json
{
  "id": "amber",
  "name": "Amber",
  "stage_names": ["Marigold", "Citrus", "Ember", "Garnet", "Dusk"],
  "stage_colors": ["#FFE0B2", "#FFC266", "#FFA133", "#E0661A", "#A63D0A"],
  "text_color": "#F6F6F4",
  "muted_text_color": "#A5A5A5"
}
```

Themes are data only: do not change progression math to add a palette. Base theme files must remain unmodified by cycle color modifiers.
