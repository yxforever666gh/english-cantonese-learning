# UI design system

The product is a calm, trustworthy learning assistant for long-form English and Cantonese reading. It is light-only and uses a warm neutral canvas with pine-green brand accents.

## Brand

The mark combines two facing book pages / speech bubbles. Their centre gap becomes a short voice waveform, tying reading, language, and playback together without lettering. Launcher artwork must remain inside the adaptive-icon safe zone. Do not place text, photos, gradients, or fine decoration in the icon.

Use “英粤断句朗读” as the product name and “学习材料” for saved content. The primary destinations are “创建”, “材料库”, and “设置”.

## Tokens

Compose tokens live in `ui/theme/AppTheme.kt`; Android launch resources mirror the same brand colours.

| Role | Value |
| --- | --- |
| Background | `#F6F7F3` |
| Surface | `#FFFFFF` |
| Subtle surface | `#F0F3EF` |
| Pine / deep pine | `#174A43` / `#103832` |
| Mint selection | `#DDEBE5` |
| Text / secondary / muted | `#18211F` / `#66706C` / `#89918E` |
| Divider | `#E2E6E1` |

Spacing is limited to 4, 8, 12, 16, 20, 24, 32, and 40dp. Phone pages use 20dp horizontal padding and content is capped at 720dp. Controls use 12dp corners, cards 16dp, labels 10dp, and sheets 24dp. Minimum touch targets are 48dp.

All interface text uses the system sans-serif family. Page titles are 28/34sp, section titles 20/26sp, card titles 16/22sp, body text 15/22sp, supporting text 13/19sp, and labels 12/16sp. Use weight and spacing—not a second font—to establish hierarchy.

Motion is functional and brief: 150ms for direct feedback, 180ms for normal transitions, and at most 200ms for deliberate state changes. Respect the platform animation scale.

## Component rules

- Use one surface level per group. Never nest outlined cards to create hierarchy.
- Prefer whitespace and 1dp dividers; elevation is limited to 0–2dp.
- Provide default, pressed, selected, disabled, loading, success, warning, and error states where applicable.
- Terracotta is reserved for warnings and destructive actions. Selection uses mint plus an icon or label, never colour alone.
- Keep iconography to the local 24dp vector family. Do not restore `material-icons-extended`.
- Error copy states what happened and what the user can do. Never expose exception classes or stack traces.

## Review checklist

Review creation, library, detail, reader, player, settings, and voice selection at 360×800dp, 412×915dp, and 600×960dp, including 1.3× font scale. Verify long titles, missing translations, IME and gesture insets, offline/error states, 48dp targets, readable contrast, stable loading layouts, and that bottom controls never cover content.
