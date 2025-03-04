package net.runelite.client.plugins.nateplugins.skilling.natefishing;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;


public class FishingScript extends Script {

    public static double version = 1.3;

    public boolean run(FishingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;

            try {
                if (Microbot.isMoving() || Microbot.isAnimating() || Microbot.pauseAllScripts) {
                    return;
                }
                if (Rs2Inventory.isFull()) {
                    if (config.Fish().getName().equals("shrimp")) {
                        Rs2Inventory.dropAll(x -> x.slot == 0);
                        return;
                    } else {
                        Rs2Inventory.dropAll(x -> x.slot == 4);
                        return;
                    }

                } else {
                    for (int fishingSpotId:
                            config.Fish().getFishingSpot() ) {
                        NPC fishingspot = Rs2Npc.getNpc(fishingSpotId);
                        if(fishingspot != null && !Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())){
                            validateInteractable(fishingspot);
                        }
                        Rs2Npc.interact(fishingSpotId,config.Fish().getAction());
                        Microbot.status = "Fishing...";
                    }

                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
