# KOIStock Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android launcher icon with the supplied KOI artwork, preserving the complete fish mark and “KOI” wordmark on legacy and adaptive launchers.

**Architecture:** Keep the manifest’s existing mipmap resource names. Resample the approved square source into every existing density-specific WebP resource, and use a full-color PNG drawable as the foreground of both adaptive icon definitions over a matching solid-orange background.

**Tech Stack:** Android resource system, adaptive icons, Python Pillow, Gradle Android plugin

## Global Constraints

- Keep the supplied artwork unchanged: orange background, centered fish mark, and “KOI” wordmark.
- Preserve `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- Do not change application behavior, in-app branding, or theme colors.
- Do not stage or modify unrelated working-tree changes.

---

### Task 1: Replace legacy and adaptive launcher resources

**Files:**
- Replace: `app/src/main/res/mipmap-mdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp`
- Replace: `app/src/main/res/mipmap-hdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp`
- Replace: `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp`
- Replace: `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp`
- Replace: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/drawable-nodpi/ic_launcher_artwork.png`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: user-supplied 864 × 864 PNG artwork.
- Produces: existing `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` resources for the manifest.

- [ ] **Step 1: Generate density-specific legacy WebP assets**

Use Pillow with Lanczos resampling to write the complete square image into both standard and round filenames at Android launcher sizes: `48`, `72`, `96`, `144`, and `192` pixels for `mdpi` through `xxxhdpi`. Save as lossless WebP so the current filenames and manifest references remain unchanged.

Run: bundled Python with an inline script that opens `C:/Users/LOQ/AppData/Local/Temp/codex-clipboard-566cdc3a-48f7-4935-8594-49fbe36bef48.png`, converts it to RGBA, and saves each target with `format="WEBP", lossless=True, method=6`.

Expected: ten WebP files decode successfully and have their density-specific square dimensions.

- [ ] **Step 2: Add the adaptive icon artwork drawable**

Copy the approved source without modification to `app/src/main/res/drawable-nodpi/ic_launcher_artwork.png`.

Expected: the copied file is byte-identical to the approved source and reports `864 × 864` pixels.

- [ ] **Step 3: Replace the adaptive foreground and background definitions**

Set `ic_launcher_background.xml` to a 108 dp vector containing a single full-canvas path filled with the artwork’s sampled orange background color. Set `ic_launcher_foreground.xml` to a layer list containing a bitmap that references `@drawable/ic_launcher_artwork`, uses anti-aliasing, and fills the adaptive icon canvas.

Remove the existing Android-template paths. In both adaptive icon XML files, retain the background and foreground references and remove the stale Android-logo `monochrome` reference so themed launchers do not render the previous icon silhouette.

Expected: both adaptive resources reference the KOI artwork and contain no Android-template icon reference.

- [ ] **Step 4: Verify asset integrity**

Run a Pillow validation script that opens the source, adaptive PNG, and ten WebP files, calls `verify()`, reopens each output, and asserts exact dimensions. Compare SHA-256 hashes of the source and adaptive PNG.

Expected: all assertions pass; source and adaptive PNG hashes match.

- [ ] **Step 5: Verify Android resource compilation**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` with no Android resource-linking errors.

- [ ] **Step 6: Inspect the final icon visually**

Open `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` and confirm the fish mark and the complete “KOI” wordmark are visible, centered, and surrounded by the orange background.

Expected: the generated icon matches the supplied artwork at launcher resolution.

- [ ] **Step 7: Commit only launcher icon assets**

```powershell
git add -- app/src/main/res/drawable-nodpi/ic_launcher_artwork.png app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/mipmap-mdpi/ic_launcher.webp app/src/main/res/mipmap-mdpi/ic_launcher_round.webp app/src/main/res/mipmap-hdpi/ic_launcher.webp app/src/main/res/mipmap-hdpi/ic_launcher_round.webp app/src/main/res/mipmap-xhdpi/ic_launcher.webp app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
git commit -m "feat: use KOI artwork as launcher icon"
```

Expected: the commit contains only the launcher resources listed above.
