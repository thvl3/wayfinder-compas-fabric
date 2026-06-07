package com.cse310.wayfinder;

import com.cse310.wayfinder.item.WayfinderCompassItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entry point. Registers the Wayfinder Compass item and adds
 * it to a creative tab so it can be picked up in-game.
 */
public class WayfinderMod implements ModInitializer {

    public static final String MOD_ID = "wayfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // A single shared instance of the item, registered below.
    public static final Item WAYFINDER_COMPASS =
            new WayfinderCompassItem(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "wayfinder_compass"),
                WAYFINDER_COMPASS);

        // Show the compass in the "Tools & Utilities" creative tab.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(WAYFINDER_COMPASS));

        LOGGER.info("Wayfinder Compass registered.");
    }
}
