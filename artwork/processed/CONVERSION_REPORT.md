# Image conversion report

## Converted for the frontend prototype

The following opaque PNG assets were converted to quality-88 WebP files in
`android_ready/`. Original PNG files remain unchanged in `宝可梦账本素材/`.

- `bg_paper_texture.webp`
- `bg_forest_adventure_vertical.webp`
- `bg_forest_camp_landscape.webp`
- `empty_search_group_sign.webp`
- `empty_transactions_bulbasaur.webp`
- `empty_chart_treecko_vine.webp`
- `empty_exchange_offline_treecko.webp`
- `empty_savings_turtwig.webp`
- `group_sprout_growth_a.webp`
- `group_sprout_growth_b.webp`
- `group_record_success.webp`

## Transparent decoration assets

The 21 regenerated RGBA PNG files in
`宝可梦账本素材/装饰素材/重新生成版/` were converted to lossless WebP files.
Their 2048 x 2048 canvas, RGB pixels, soft alpha edges, and fully transparent
pixels were preserved exactly. Android-safe names use the `decor_` prefix:

- `decor_treasure_chest.webp`
- `decor_notebook.webp`
- `decor_seedling_01.webp`
- `decor_seedling_02.webp`
- `decor_yellow_flower.webp`
- `decor_yellow_leaf.webp`
- `decor_tape.webp`
- `decor_footprint.webp`
- `decor_coin.webp`
- `decor_coin_pile.webp`
- `decor_wood_sign.webp`
- `decor_pencil.webp`
- `decor_acorn.webp`
- `decor_acorn_saving_jar.webp`
- `decor_eraser.webp`
- `decor_small_tree.webp`
- `decor_envelope.webp`
- `decor_leaf_charm.webp`
- `decor_quill.webp`
- `decor_seed_bag.webp`
- `decor_forest_sticker_set.webp`

The converted set is 26,158,842 bytes, down from 55,943,894 bytes for the PNG
sources (53.24% smaller). Original PNG files remain unchanged.

## Transparent character assets

The 12 regenerated character cutouts in the same source folder were converted
to lossless WebP files. All 2048 x 2048 pixels and the complete alpha channel
were preserved exactly:

- `bulbasaur_accounting.webp`
- `bulbasaur_saving_coins.webp`
- `bulbasaur_sleeping.webp`
- `treecko_checking_coins.webp`
- `treecko_empty_ledger.webp`
- `treecko_running.webp`
- `treecko_waiting.webp`
- `turtwig_acorn_saving_jar.webp`
- `turtwig_carrying_acorn_jar.webp`
- `turtwig_celebrating.webp`
- `turtwig_empty_saving_jar.webp`
- `turtwig_watering.webp`

The character set is 19,851,186 bytes, down from 44,794,552 bytes for the PNG
sources (55.68% smaller). Original PNG files remain unchanged.

## Prototype-only warning

The reviewed source images contain a visible `豆包AI生成` watermark. Converted
files preserve it exactly. They are suitable as temporary frontend assets but
should be replaced with clean approved files before the final build.
