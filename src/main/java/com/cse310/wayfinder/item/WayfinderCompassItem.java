package com.cse310.wayfinder.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Wayfinder Compass item itself.
 *
 * <p>The interesting behaviour (raycasting for a target, saving waypoints, and
 * rendering the trail) all lives on the client so that the rendering state has a
 * single owner. This class therefore stays small: it gives the item its identity
 * and adds a helpful tooltip explaining the controls.</p>
 */
public class WayfinderCompassItem extends Item {

    public WayfinderCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Right-click: set waypoint where you aim (or your feet)")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + right-click: clear the waypoint")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Press N: cycle saved waypoints")
                .formatted(Formatting.DARK_GRAY));
    }
}
