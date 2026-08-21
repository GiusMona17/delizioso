---
name: Culinary Clay
colors:
  surface: '#fbf9f8'
  surface-dim: '#dbd9d9'
  surface-bright: '#fbf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f3'
  surface-container: '#efeded'
  surface-container-high: '#eae8e7'
  surface-container-highest: '#e4e2e2'
  on-surface: '#1b1c1c'
  on-surface-variant: '#3f4a3d'
  inverse-surface: '#303030'
  inverse-on-surface: '#f2f0f0'
  outline: '#6f7a6c'
  outline-variant: '#becab9'
  surface-tint: '#006e20'
  primary: '#006e20'
  on-primary: '#ffffff'
  primary-container: '#98ff98'
  on-primary-container: '#007924'
  inverse-primary: '#77dc7a'
  secondary: '#74593f'
  on-secondary: '#ffffff'
  secondary-container: '#fed9b8'
  on-secondary-container: '#795d43'
  tertiary: '#60603e'
  on-tertiary: '#ffffff'
  tertiary-container: '#eceabe'
  on-tertiary-container: '#6a6946'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#93f993'
  primary-fixed-dim: '#77dc7a'
  on-primary-fixed: '#002105'
  on-primary-fixed-variant: '#005316'
  secondary-fixed: '#ffdcbe'
  secondary-fixed-dim: '#e3c0a0'
  on-secondary-fixed: '#2a1704'
  on-secondary-fixed-variant: '#5a422a'
  tertiary-fixed: '#e6e5b9'
  tertiary-fixed-dim: '#cac99f'
  on-tertiary-fixed: '#1d1d03'
  on-tertiary-fixed-variant: '#484828'
  background: '#fbf9f8'
  on-background: '#1b1c1c'
  surface-variant: '#e4e2e2'
typography:
  headline-xl:
    fontFamily: Quicksand
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Quicksand
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-lg-mobile:
    fontFamily: Quicksand
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
  body-lg:
    fontFamily: Quicksand
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 26px
  body-md:
    fontFamily: Quicksand
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  label-md:
    fontFamily: Quicksand
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Quicksand
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.03em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 20px
  stack-gap: 16px
  card-gutter: 12px
  touch-target: 48px
---

## Brand & Style
This design system centers on **Claymorphism**, a playful and tactile aesthetic that transforms the interface into a soft, 3D environment. Designed for a mobile recipe app, the visual language is friendly, approachable, and highly interactive, mimicking the physical qualities of modeling clay or soft silicone. 

The user experience should feel "squishy" and inviting, reducing the friction of cooking with an interface that feels like a kitchen tool itself. The style relies on double inner shadows (light and dark) to create volume, paired with soft outer shadows to lift elements off the "creamy" canvas.

## Colors
The palette is built on fresh, appetizing tones that evoke ingredients and cleanliness.

- **Primary (Mint Green):** Used for "Success" states, primary call-to-actions, and healthy recipe categories. It represents freshness.
- **Secondary (Soft Peach):** Used for highlighting favorites, "Sweet" categories, and secondary interactions.
- **Background (Creamy White):** The stage for all elements. It should be used as the base surface color to maintain warmth compared to a clinical pure white.
- **Accent Shadows:** Shadows should not be neutral grey. Use a slightly darker, more saturated version of the base color (e.g., a warm tan for shadows on Creamy White) to maintain the organic, 3D look.

## Typography
The system uses **Quicksand** across all levels to reinforce the rounded, friendly personality of the clay aesthetic. 

- **Headlines:** Set in Bold (700) with tight letter spacing to give titles a "plump" and significant feel.
- **Body:** Set in Medium (500) rather than Regular to ensure legibility against the vibrant, shadowed backgrounds.
- **Labels:** Always slightly more tracked out (letter-spacing) to maintain clarity at small sizes on mobile screens.
- **Scale:** Maintain high contrast between headlines and body text to help users scan recipes quickly while cooking.

## Layout & Spacing
This system uses a **Fluid Contextual Layout** designed specifically for one-handed mobile use. 

- **Grid:** A standard 4-column mobile grid with generous 20px side margins to prevent clay elements from feeling "cramped" against the screen edge.
- **Rhythm:** Spacing follows an 8px scale. Use larger gaps (24px+) between distinct content sections to allow the 3D shadows room to breathe without overlapping awkwardly.
- **Safe Areas:** Elements must maintain a minimum 12px distance from each other to ensure the "outer glow" and "soft shadow" effects are clearly visible and don't muddy the UI.

## Elevation & Depth
Depth in this design system is achieved through "Claymorphism" physics rather than traditional z-axis stacking.

- **The Clay Effect:** Every primary element must use two inner shadows. 
    1. A top-left inner shadow (White, 40-60% opacity) to simulate a light hit.
    2. A bottom-right inner shadow (Darker version of the element color, 20-30% opacity) to create volume.
- **Outer Elevation:** High-priority items (like floating action buttons) use a large, blurred outer shadow with a slight color tint of the element itself.
- **Inset Depth:** For input fields or "empty" states, use an inner shadow that makes the element look "carved out" of the creamy background.

## Shapes
The shape language is extreme and pill-oriented. 

- **Base Radius:** 24px is the minimum for standard cards.
- **Buttons:** Always use the "Pill" (fully rounded) style.
- **Consistency:** Avoid sharp corners entirely. Even progress bars and icon containers should use maximum rounding to maintain the "molded" aesthetic.

## Components
- **Buttons:** Large, pill-shaped, and high-contrast. Use the primary Mint Green for "Start Cooking" and Soft Peach for "Add to Favorites." When pressed, the inner shadows should deepen to simulate the button being physically squished.
- **Recipe Cards:** 24px corner radius. Features a soft outer shadow. The image inside should have a slightly smaller radius (16px) to sit comfortably within the "clay" frame.
- **Input Fields:** Styled with an "Inset" look. The background of the field should be slightly darker than the page background to look like a hollowed-out space in the clay.
- **Chips/Tags:** Small pill shapes used for dietary labels (e.g., "Vegan"). Use low-opacity versions of the primary/secondary colors with 1px soft borders.
- **Cooking Mode Step Indicators:** Large, circular pods that appear to be embossed from the surface.
- **Navigation Bar:** A floating "dock" style bar with high roundedness and a strong outer glow to separate it from the scrolling content.