# ADR-011: Cyber visual design with system fonts

## Status
Accepted (implemented; not yet checked visually on a device)

## Date
2026-10-12

## Context
The app used an indigo and mint Material palette with the default type scale. It looked generic next to a security product, and some labels used the same weight as body text, so hierarchy was flat. The launcher icon was a padlock on a ring, which did not read as "security" at small sizes.

The app is offline and ships no font files. Fonts also cannot be fetched from Google Fonts at build time without network access during the build, and the project keeps the INTERNET permission out of the manifest.

## Decision
- **Palette:** near-black navy background, electric cyan (`#2EE6FF`) for actions and focus, acid green (`#7CFF8A`) for "secure" state, amber (`#FFB547`) for warnings. Light theme uses the same hues with darker values for contrast.
- **Typography:** system `FontFamily.SansSerif` for body text and `FontFamily.Monospace` for titles and labels (`ui/theme/Type.kt`). The monospace labels give the "security console" feel without shipping a font.
- **Shapes:** slightly tighter corners (8, 14, 20, 28 dp) for a more precise look.
- **Icon:** shield outline with a padlock and keyhole, in cyan and green on navy. The monochrome themed icon uses the same geometry in white.
- **Performance:** at most three thumbnail decrypts run at once (`Semaphore(3)` in `ThumbnailLoader`), so fast scrolling does not start one decrypt per visible cell.

## Alternatives Considered

### Bundle a custom font (for example JetBrains Mono or Inter)
- Pros: a more distinctive look.
- Cons: the font files must be added by hand with their licences. The shell tooling used for this project blocks downloads, so the files cannot be fetched here.
- Deferred. If the user adds the `.ttf` files under `res/font/`, `Type.kt` can switch to `Font(R.font.…)` with no other changes.

### Material You dynamic colour
- Pros: follows the wallpaper.
- Cons: the security look depends on a fixed palette; wallpaper colours can make warnings and secure states hard to tell apart.
- Rejected.

## Consequences
- Visual changes are not verified. `FLAG_SECURE` blocks screenshots, so layout and colour must be checked by eye on a device or with `uiautomator` text dumps (which check content, not looks).
- Contrast of the cyan on navy and the monospace labels has to be checked on a real phone in sunlight and at night.
- The palette is a set of tokens in `Theme.kt`; changing the brand means editing those values and the icon colours in `icon_colors.xml` and the vector drawables.
