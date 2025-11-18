package com.example.momentag.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central place to describe how icons should look in Momentag.
 *
 * The two inputs are:
 * 1. [IconSizeRole] – functional buckets that group every icon size already
 *    being used in the app.
 * 2. [IconIntent] – semantic tint pairings so that call sites pick a role,
 *    *not* an arbitrary `Color`.
 *
 * Call [StandardIcon.Icon] when you simply need to draw an icon or rely on
 * [IconBlueprint] when you want to define reusable combinations (for example,
 * "top app bar navigation" or "warning banner leading icon").
 */
object StandardIcon {
    /**
     * Draws an [ImageVector] icon with the provided size role and semantic intent.
     */
    @Composable
    fun Icon(
        imageVector: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        sizeRole: IconSizeRole = IconSizeRole.DefaultAction,
        intent: IconIntent = IconIntent.Neutral,
        tintOverride: Color? = null,
    ) {
        val style = style(sizeRole = sizeRole, intent = intent)
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier.size(style.size),
            tint = tintOverride ?: style.tint,
        )
    }

    /**
     * Painter overload so vector drawables, animated icons, or remote icons can also
     * use the exact same sizing/tint system.
     */
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        sizeRole: IconSizeRole = IconSizeRole.DefaultAction,
        intent: IconIntent = IconIntent.Neutral,
        tintOverride: Color? = null,
    ) {
        val style = style(sizeRole = sizeRole, intent = intent)
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.size(style.size),
            tint = tintOverride ?: style.tint,
        )
    }

    /**
     * Exposes the computed [IconStyle] so complex layouts (badges, icon buttons, banners)
     * can apply the values manually.
     */
    @Composable
    fun style(
        sizeRole: IconSizeRole,
        intent: IconIntent,
    ): IconStyle =
        IconStyle(
            size = sizeRole.size,
            tint = intent.resolveTint(),
        )
}

/**
 * Immutable description of how an icon should be rendered.
 */
@Immutable
data class IconStyle internal constructor(
    val size: Dp,
    val tint: Color,
)

/**
 * Icons already in the app use ten distinct sizes. These roles map one-to-one with those use cases
 * so features can opt into a single semantic value rather than sprinkling `Modifier.size(x.dp)` everywhere.
 */
enum class IconSizeRole(
    val size: Dp,
) {
    /**
     * 12.dp – inline dismiss chips such as the delete icon in `Tag.kt`.
     */
    ChipAction(12.dp),

    /**
     * 16.dp – status pills or checkbox overlays (see `AddTagScreen` and selection banners).
     */
    InlineAction(16.dp),

    /**
     * 18.dp – metadata/status indicators (AI badges, inline errors inside album recommendations).
     */
    StatusIndicator(18.dp),

    /**
     * 20.dp – navigation/back/logout icons placed inside top app bars.
     */
    Navigation(20.dp),

    /**
     * 24.dp – the Material baseline for IconButtons, list trailing actions, and most dialogs.
     */
    DefaultAction(24.dp),

    /**
     * 28.dp – warning banner action button icons.
     */
    BannerAction(28.dp),

    /**
     * 32.dp – feature-level actions (album cards, quick actions, FAB-aligned icons).
     */
    Featured(32.dp),

    /**
     * 36.dp – used by dialog level affordances (example: SelectImageScreen preview actions).
     */
    Dialog(36.dp),

    /**
     * 40.dp – spotlight/banner leading icons.
     */
    Banner(40.dp),

    /**
     * 48.dp – hero or empty-state illustrations (Search empty and loading surfaces).
     */
    Overlay(48.dp),
}

/**
 * Semantic tint pairings. Select an intent instead of passing raw colors.
 */
enum class IconIntent {
    /** Default foreground for neutral actions and text-aligned icons. */
    Neutral,

    /** Subdued icons that should feel secondary (filters, inactive chips, placeholders). */
    Muted,

    /** Primary actions or positive confirmations. */
    Primary,

    /** Secondary brand color, often used for multi-action banners. */
    Secondary,

    /** Success/selection feedback – teal family in the current color scheme. */
    Success,

    /** Warning/informational emphasis without the destructive vibe. */
    Warning,

    /** Error/destructive states. */
    Error,

    /** Inverse/on-dark surfaces such as filled icon buttons. */
    Inverse,

    /** Disabled/onSurface with alpha 0.38. */
    Disabled,

    /** Foreground text color on primary containers. */
    OnPrimaryContainer,

    /** Foreground text color on error containers. */
    OnErrorContainer,

    /** Neutral icon that matches the surface tone (use sparingly). */
    Surface,

    /** Foreground on inverse surfaces (surface tint combos). */
    InverseSurface,
}

/**
 * Predefined reusable pairings that cover every iconable element in the project today.
 *
 * Reference examples:
 * * `Tag.kt` – use [ChipDismiss] for the delete icon, [ChipHighlight] for curated tags.
 * * `CommonTopBar.kt` – use [NavigationBack] or [NavigationAlert] for logout/back buttons.
 * * `WarningBanner.kt` – use [BannerLeading] and [BannerAction].
 */
object IconBlueprints {
    val ChipDismiss = IconBlueprint(IconSizeRole.ChipAction, IconIntent.Muted)
    val ChipHighlight = IconBlueprint(IconSizeRole.InlineAction, IconIntent.Success)
    val InlineInfo = IconBlueprint(IconSizeRole.StatusIndicator, IconIntent.Primary)
    val InlineError = IconBlueprint(IconSizeRole.StatusIndicator, IconIntent.Error)
    val NavigationBack = IconBlueprint(IconSizeRole.Navigation, IconIntent.Neutral)
    val NavigationAlert = IconBlueprint(IconSizeRole.Navigation, IconIntent.Warning)
    val DefaultOnSurface = IconBlueprint(IconSizeRole.DefaultAction, IconIntent.Neutral)
    val DefaultPrimary = IconBlueprint(IconSizeRole.DefaultAction, IconIntent.Primary)
    val BannerLeading = IconBlueprint(IconSizeRole.Banner, IconIntent.Warning)
    val BannerAction = IconBlueprint(IconSizeRole.BannerAction, IconIntent.Secondary)
    val EmptyState = IconBlueprint(IconSizeRole.Overlay, IconIntent.Muted)
}

/**
 * Pre-composed value object so features can keep references like `IconBlueprints.NavigationBack`
 * and feed them to `StandardIcon`.
 */
@Immutable
data class IconBlueprint(
    val sizeRole: IconSizeRole,
    val intent: IconIntent,
) {
    /**
     * Returns the computed [IconStyle] – helpful when an icon is part of a more complex layout.
     */
    @Composable
    fun asStyle(): IconStyle = StandardIcon.style(sizeRole, intent)

    /**
     * Renders the blueprint with an [ImageVector].
     */
    @Composable
    fun Icon(
        imageVector: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
    ) {
        StandardIcon.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            sizeRole = sizeRole,
            intent = intent,
        )
    }

    /**
     * Renders the blueprint with a [Painter].
     */
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
    ) {
        StandardIcon.Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            sizeRole = sizeRole,
            intent = intent,
        )
    }
}

@Composable
private fun IconIntent.resolveTint(): Color {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        IconIntent.Neutral -> colors.onSurface
        IconIntent.Muted -> colors.onSurfaceVariant
        IconIntent.Primary -> colors.primary
        IconIntent.Secondary -> colors.secondary
        IconIntent.Success -> colors.tertiary
        IconIntent.Warning -> colors.tertiaryContainer
        IconIntent.Error -> colors.error
        IconIntent.Inverse -> colors.onPrimary
        IconIntent.Disabled -> colors.onSurface.copy(alpha = 0.38f)
        IconIntent.OnPrimaryContainer -> colors.onPrimaryContainer
        IconIntent.OnErrorContainer -> colors.onErrorContainer
        IconIntent.Surface -> colors.surface
        IconIntent.InverseSurface -> colors.inverseOnSurface
    }
}
