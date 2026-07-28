# KOIStock Launcher Icon Design

## Goal

Use the supplied square KOI artwork as the Android application launcher icon while preserving the complete fish mark and the “KOI” text.

## Design

- Keep the artwork unchanged: orange background, centered fish mark, and “KOI” wordmark.
- Generate density-specific legacy launcher resources for `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi`.
- Update both standard and round launcher icon resources.
- Update the Android 8.0+ adaptive icon so newer launchers use the same artwork and can apply their system mask without clipping meaningful content.
- Preserve the existing manifest resource names (`@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`) so no application code or manifest behavior changes.

## Source and Output

The user-supplied 864 × 864 PNG is the source of truth. Generated launcher resources remain under `app/src/main/res`, following the project’s existing mipmap and adaptive-icon structure.

## Validation

- Confirm all generated files have the expected dimensions and can be decoded.
- Build the Android app to verify resource linking and manifest references.
- Inspect a generated icon to ensure the full fish mark and “KOI” text remain visible.

## Scope

This change affects only launcher icon assets and their resource definitions. It does not alter application functionality, in-app branding, or theme colors.
