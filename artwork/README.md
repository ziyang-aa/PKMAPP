# Artwork workflow

## Where to place generated images

- `source/style_reference/`: approved forest-storybook style boards.
- `source/characters/bulbasaur/`: Bulbasaur master and individual actions.
- `source/characters/treecko/`: Treecko master and individual actions.
- `source/characters/turtwig/`: Turtwig master and individual actions.
- `source/characters/group/`: illustrations containing two or three characters.
- `source/backgrounds/`: page backgrounds, banners, and seamless paper textures.
- `source/empty_states/`: empty, offline, success, and no-result illustrations.
- `source/decorations/`: leaves, vines, dividers, frames, and savings decorations.
- `source/branding/`: launcher icon and splash artwork.

Keep original high-resolution PNG files in `source/`. Do not place unprocessed
generation output directly in Android resources.

## Processing and final delivery

1. Remove unwanted backgrounds and generation artifacts.
2. Crop with safe margins intact and export in sRGB.
3. Convert approved raster artwork to WebP with transparency when required.
4. Put files awaiting import in `processed/android_ready/`.
5. Copy final raster artwork into `app/src/main/res/drawable-nodpi/`.
6. Put XML vector operation icons in `app/src/main/res/drawable/`.
7. Put launcher-icon density variants in the existing `mipmap-*` directories.

Android resource directories are flat: do not create subdirectories inside
`drawable`, `drawable-nodpi`, or `mipmap-*`.

## Naming

Use lowercase English letters, digits, and underscores only. Recommended
prefixes:

- `char_`: character artwork, for example `char_bulbasaur_ledger.webp`
- `group_`: multi-character artwork, for example `group_saving_complete.webp`
- `empty_`: empty states, for example `empty_transactions.webp`
- `bg_`: full backgrounds, for example `bg_savings_forest_path.webp`
- `banner_`: banners, for example `banner_profile_camp.webp`
- `decor_`: decorative elements, for example `decor_leaf_corner.webp`
- `app_`: branding, for example `app_splash_forest.webp`
- `ic_`: vector UI icons, for example `ic_delete.xml`

Do not use spaces, hyphens, Chinese characters, uppercase letters, or duplicate
resource names.
