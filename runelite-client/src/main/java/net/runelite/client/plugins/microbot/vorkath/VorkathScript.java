/**
 * Credits to Jrod7938
 */

package net.runelite.client.plugins.microbot.vorkath;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.staticwalker.WorldDestinations;
import net.runelite.client.plugins.microbot.util.MicrobotInventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.MicrobotInventorySetup.doesEquipmentMatch;
import static net.runelite.client.plugins.microbot.util.MicrobotInventorySetup.doesInventoryMatch;


enum State {
    BANKING,
    TELEPORT_TO_RELLEKKA,
    WALK_TO_VORKATH_ISLAND,
    WALK_TO_VORKATH,
    PREPARE_FIGHT,
    FIGHT_VORKATH,
    ZOMBIE_SPAWN,
    ACID,
    LOOT_ITEMS,
    TELEPORT_AWAY,
    DEAD_WALK
}

public class VorkathScript extends Script {
    public static double version = 1.0;

    State state = State.BANKING;

    private final int whiteProjectileId = 395;
    private final int redProjectileId = 1481;
    @Getter
    public final int acidProjectileId = 1483;
    private final int acidRedProjectileId = 1482;
    NPC vorkath;
    boolean hasEquipment = false;
    boolean hasInventory = false;
    boolean init = true;
    public static VorkathConfig config;
    @Getter
    private HashSet<WorldPoint> acidPools = new HashSet<>();

    public boolean run(VorkathConfig config) {
        Microbot.enableAutoRunOn = false;
        init = true;
        state = State.BANKING;
        hasEquipment = false;
        hasInventory = false;
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                if (!Microbot.isLoggedIn()) return;
                if (init) {
                    if (Rs2Npc.getNpc(NpcID.VORKATH_8061) != null) {
                        state = State.FIGHT_VORKATH;
                    }
                    if (Rs2Npc.getNpc(NpcID.VORKATH_8059) != null) {
                        state = State.PREPARE_FIGHT;
                    }
                    if (Rs2GameObject.findObjectById(ObjectID.ICE_CHUNKS_31990) != null) {
                        state = State.WALK_TO_VORKATH;
                    }
                    if (isCloseToRelleka()) {
                        state = State.WALK_TO_VORKATH_ISLAND;
                    }
                    if (Rs2Npc.getNpc(NpcID.TORFINN_10406) != null) {
                        state = State.WALK_TO_VORKATH;
                    }
                    if (Rs2Equipment.equipment() == null) {
                        state = State.DEAD_WALK;
                    }
                    init = false;
                }

                switch (state) {
                    case BANKING:
                        hasEquipment = doesEquipmentMatch("vorkath");
                        hasInventory = doesInventoryMatch("vorkath");
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                        }
                        if (!hasEquipment) {
                            Rs2Bank.depositAll();
                            Rs2Bank.depositEquipment();
                            sleep(600);
                            hasEquipment = MicrobotInventorySetup.loadEquipment("vorkath");
                        }
                        if (!hasInventory) {
                            sleep(600);
                            hasInventory = MicrobotInventorySetup.loadInventory("vorkath");
                        }
                        if (hasEquipment && hasInventory) {
                            healAndDrinkPrayerPotion();
                            if (hasEquipment && hasInventory) {
                                state = State.TELEPORT_TO_RELLEKKA;
                            }
                        }
                        break;
                    case TELEPORT_TO_RELLEKKA:
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                        }
                        if (!isCloseToRelleka()) {
                            Rs2Inventory.interact("Rellekka teleport", "break");
                            sleepUntil(this::isCloseToRelleka);
                        }
                        if (isCloseToRelleka()) {
                            state = State.WALK_TO_VORKATH_ISLAND;
                        }
                        break;
                    case WALK_TO_VORKATH_ISLAND:
                        Microbot.getWalker().walkTo(new WorldPoint(2640, 3693, 0));
                        net.runelite.api.NPC torfin = Rs2Npc.getNpc(NpcID.TORFINN_10405);
                        if (torfin != null) {
                            Rs2Npc.interact(torfin, "Ungael");
                            sleepUntil(() -> Rs2Npc.getNpc(NpcID.TORFINN_10406) != null);
                        }
                        if (Rs2Npc.getNpc(NpcID.TORFINN_10406) != null) {
                            state = State.WALK_TO_VORKATH;
                        }
                        break;
                    case WALK_TO_VORKATH:
                        Microbot.getWalker().walkMiniMap(new WorldPoint(2272, 4052, 0));
                        TileObject iceChunks = Rs2GameObject.findObjectById(ObjectID.ICE_CHUNKS_31990);
                        if (iceChunks != null) {
                            Rs2GameObject.interact(ObjectID.ICE_CHUNKS_31990, "Climb-over");
                            sleepUntil(() -> Rs2GameObject.findObjectById(ObjectID.ICE_CHUNKS_31990) == null);
                        }
                        if (Rs2GameObject.findObjectById(ObjectID.ICE_CHUNKS_31990) == null) {
                            state = State.PREPARE_FIGHT;
                        }
                        break;
                    case PREPARE_FIGHT:
                        Rs2Player.toggleRunEnergy(false);

                        boolean result = drinkPotions();

                        if (result) {
                            Rs2Npc.interact(NpcID.VORKATH_8059, "Poke");
                            Rs2Player.waitForWalking();
                            Rs2Npc.interact(NpcID.VORKATH_8059, "Poke");
                            Rs2Player.waitForAnimation(10000);
                            Microbot.getWalkerForKotlin().walkFastLocal(
                                    LocalPoint.fromScene(48, 58)
                            );
                            Rs2Player.waitForWalking();
                            sleepUntil(() -> Rs2Npc.getNpc(NpcID.VORKATH_8061) != null);
                            state = State.FIGHT_VORKATH;
                        }
                        break;
                    case FIGHT_VORKATH:
                        vorkath = Rs2Npc.getNpc(NpcID.VORKATH_8061);
                        if (vorkath == null || vorkath.isDead()) {
                            state = State.LOOT_ITEMS;
                            Rs2Inventory.wield("ruby dragon bolts");
                            togglePrayer(false);
                            sleepUntil(() -> Rs2GroundItem.exists("Superior dragon bones", 20), 15000);
                        }
                        if (Microbot.getClient().getLocalPlayer().isDead()) {
                            state = State.DEAD_WALK;
                            return;
                        }
                        if (Rs2Inventory.getInventoryFood().isEmpty()) {
                            state = State.TELEPORT_AWAY;
                            leaveVorkath();
                            return;
                        }
                        if (Microbot.getClient().getLocalPlayer().getInteracting() == null || Microbot.getClient().getLocalPlayer().getInteracting().getName() == null ||
                                !Microbot.getClient().getLocalPlayer().getInteracting().getName().equalsIgnoreCase("vorkath")) {
                            Rs2Npc.attack("Vorkath");
                        }
                        if (Microbot.getClient().getLocalPlayer().getLocalLocation().getSceneY() >= 59) {
                            Microbot.getWalkerForKotlin().walkFastLocal(
                                    LocalPoint.fromScene(48, 58)
                            );
                        }
                        if (Rs2Inventory.getInventoryFood().isEmpty()) {
                            leaveVorkath();
                        }
                        drinkPotions();
                        handlePrayer();
                        eatAt(60);
                        handleRedBall();
                        if (doesProjectileExistById(whiteProjectileId) || (Rs2Npc.getNpc("Zombified Spawn") != null && !Rs2Npc.getNpc("Zombified Spawn").isDead())) {
                            state = State.ZOMBIE_SPAWN;
                        }
                        if ((doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId))) {
                            state = State.ACID;
                        }
                        if (vorkath.getHealthRatio() < 60 && vorkath.getHealthRatio() != -1 && !Rs2Equipment.isWearing("diamond dragon bolts")) {
                            Rs2Inventory.wield("diamond dragon bolts");
                        } else if (vorkath.getHealthRatio() >= 60 && !Rs2Equipment.isWearing("ruby dragon bolts")) {
                            Rs2Inventory.wield("ruby dragon bolts");
                        }
                        break;
                    case ZOMBIE_SPAWN:
                        if (Rs2Npc.getNpc("Zombified Spawn") != null && Rs2Npc.getNpc("Zombified Spawn").isDead()) {
                            eatAt(60);
                            state = State.FIGHT_VORKATH;
                            return;
                        }
                        togglePrayer(false);
                        eatAt(60);
                        drinkPrayer();
                        Microbot.getWalkerForKotlin().walkFastLocal(
                                LocalPoint.fromScene(48, 58)
                        );
                        sleepUntil(() -> Rs2Npc.getNpc("Zombified Spawn") != null, 10000);
                        NPC zombieSpawn = Rs2Npc.getNpc("Zombified Spawn");
                        if (zombieSpawn != null) {
                            if (config.SLAYERSTAFF().toString().equals("Cast")) {
                                System.out.println("Cast crumble undeaad on zombified spawn!");
                                Rs2Magic.castOn(MagicAction.CRUMBLE_UNDEAD, zombieSpawn);
                                sleep(600);
                            } else {
                                Rs2Inventory.wield(config.SLAYERSTAFF().toString());
                                Rs2Npc.attack("Zombified Spawn");
                                sleep(600);
                            }
                        }
                        togglePrayer(true);
                        if (Rs2Npc.getNpc("Zombified Spawn") == null) {
                            eatAt(60);
                            state = State.FIGHT_VORKATH;
                        }
                        break;
                    case ACID:
                        togglePrayer(false);
                        handleAcidWalk();
                        break;
                    case LOOT_ITEMS:
                        if (Rs2Inventory.isFull()) {
                            boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
                            if (hasFood) {
                                eatAt(100);
                                Rs2Player.waitForAnimation();
                            }
                        }
                        boolean vorkathHead = Rs2GroundItem.loot("Vorkath's head", 20);
                        boolean itemsLeft = Rs2GroundItem.lootAllItemBasedOnValue(5000, 20) || Rs2GroundItem.exists("Vorkath's head", 20);
                        int foodInventorySize = Rs2Inventory.getInventoryFood().size();
                        boolean hasVenom = Rs2Inventory.hasItem("venom");
                        boolean hasSuperAntifire = Rs2Inventory.hasItem("super antifire");
                        boolean hasPrayerPotion = Rs2Inventory.hasItem("prayer potion");
                        boolean hasRangePotion = Rs2Inventory.hasItem(config.rangePotion().toString());

                        if (!itemsLeft && !vorkathHead) {
                            if (foodInventorySize < 7 || !hasVenom || !hasSuperAntifire || !hasPrayerPotion || !hasRangePotion) {
                                leaveVorkath();
                            } else {
                                Microbot.getWalkerForKotlin().walkFastLocal(
                                        LocalPoint.fromScene(48, 58)
                                );
                                Rs2Player.waitForWalking();
                                state = State.PREPARE_FIGHT;
                            }
                        }
                        break;
                    case TELEPORT_AWAY:
                        boolean reachedDestination = Rs2Bank.walkToBank();
                        if (reachedDestination) {
                            healAndDrinkPrayerPotion();
                            state = State.BANKING;
                        }
                        break;
                    case DEAD_WALK:
                        if (isCloseToRelleka()) {
                            Microbot.getWalker().walkTo(new WorldPoint(2640, 3693, 0));
                            torfin = Rs2Npc.getNpc(NpcID.TORFINN_10405);
                            if (torfin != null) {
                                Rs2Npc.interact(torfin, "Collect");
                                sleepUntil(() -> Rs2Widget.hasWidget("Retrieval Service"));
                                if (Rs2Widget.hasWidget("I'm afraid I don't have anything")) { // this means we looted all our stuff
                                    leaveVorkath();
                                    state = State.TELEPORT_AWAY;
                                    return;
                                }
                                final int invSize = Rs2Inventory.size();
                                Rs2Widget.clickWidget(39452678);
                                sleep(600);
                                Rs2Widget.clickWidget(39452678);
                                sleepUntil(() -> Rs2Inventory.size() != invSize);
                                boolean isWearingOriginalEquipment = MicrobotInventorySetup.wearEquipment("vorkath");
                                if (!isWearingOriginalEquipment) {
                                    int finalInvSize = Rs2Inventory.size();
                                    Rs2Widget.clickWidget(39452678);
                                    sleepUntil(() -> Rs2Inventory.size() != finalInvSize);
                                    MicrobotInventorySetup.wearEquipment("vorkath");
                                }
                            }
                        } else {
                            Microbot.getWalker().hybridWalkTo(WorldDestinations.LUMBRIDGE_BANK.getWorldPoint());
                            if (!Rs2Bank.isOpen()) {
                                Rs2Bank.openBank();
                            } else {
                                if (Rs2Inventory.hasItem("Rellekka teleport")) {
                                    Rs2Inventory.interact("Rellekka teleport", "break");
                                    Rs2Player.waitForAnimation();
                                } else {
                                    Rs2Bank.withdrawItem("Rellekka teleport");
                                    Rs2Bank.closeBank();
                                }
                            }
                        }
                        break;
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * will heal and drink pray pots
     */
    private void healAndDrinkPrayerPotion() {
        while (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) != Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS) && !Rs2Inventory.getInventoryFood().isEmpty()) {
            Rs2Bank.closeBank();
            eatAt(99);
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
        while (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) != Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) && Rs2Inventory.hasItem("prayer potion")) {
            Rs2Bank.closeBank();
            Rs2Inventory.interact(config.prayerPotion().toString(), "drink");
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
    }

    private void leaveVorkath() {
        togglePrayer(false);
        Rs2Player.toggleRunEnergy(true);
        state = State.TELEPORT_AWAY;
        Rs2Inventory.interact(config.teleportMode().getItemName(), config.teleportMode().getAction());
        Rs2Player.waitForAnimation();
    }

    private boolean drinkPotions() {
        boolean drinkRangePotion = config.rangePotion().getPotionName().contains("divine") ? !Rs2Player.hasDivineBastionActive() && !Rs2Player.hasDivineRangedActive() : !Rs2Player.hasRangingPotionActive();
        boolean drinkAntiFire = !Rs2Player.hasAntiFireActive() && !Rs2Player.hasSuperAntiFireActive();
        boolean drinkAntiVenom = !Rs2Player.hasAntiVenomActive();

        if (drinkRangePotion) {
            Rs2Inventory.interact(config.rangePotion().toString(), "drink");
            Rs2Player.waitForAnimation(2000);
        }
        if (drinkAntiFire) {
            Rs2Inventory.interact("super antifire", "drink");
            Rs2Player.waitForAnimation(2000);
        }
        if (drinkAntiVenom) {
            Rs2Inventory.interact("venom", "drink");
            Rs2Player.waitForAnimation(2000);
        }

        return !drinkRangePotion && !drinkAntiFire && !drinkAntiVenom;
    }

    public static void togglePrayer(boolean onOff) {
        if (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 77) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, onOff);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, onOff);
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, onOff);
    }

    private void handleRedBall() {
        if (Microbot.getClient().getLocalPlayer().getIdlePoseAnimation() == 1 ||
                doesProjectileExistById(redProjectileId)) {
            redBallWalk();
            Rs2Npc.attack("Vorkath");
        }
    }

    private static void handlePrayer() {
        drinkPrayer();
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        togglePrayer(true);
    }

    private static void drinkPrayer() {
        if ((Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / Microbot.getClient().getRealSkillLevel(Skill.PRAYER) < Random.random(25, 30)) {
            Rs2Inventory.interact(config.prayerPotion().toString(), "drink");
        }
    }

    private boolean doesProjectileExistById(int id) {
        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            if (projectile.getId() == id) {
                //println("Projectile $id found")
                return true;
            }
        }
        return false;
    }

    private boolean isCloseToRelleka() {
        if (Microbot.getClient().getLocalPlayer() == null) return false;
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2670, 3634, 0)) < 70;
    }

    private void eatAt(int percentage) {
        double treshHold = (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int missingHitpoints = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS) - Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (treshHold < percentage) {
            List<Rs2Item> foods = Microbot.getClientThread().runOnClientThread(Rs2Inventory::getInventoryFood);
            for (Rs2Item food : foods) {
                if (missingHitpoints >= 40 && Rs2Inventory.get("Cooked karambwan") != null) {
                    //double eat
                    Rs2Inventory.interact(food, "eat");
                    sleep(1000);
                    Rs2Inventory.interact(Rs2Inventory.get("Cooked karambwan"), "eat");
                } else {
                    Rs2Inventory.interact(food, "eat");
                }
                break;
            }
        }
    }

    private void redBallWalk() {
        WorldPoint currentPlayerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        WorldPoint sideStepLocation = new WorldPoint(currentPlayerLocation.getX() + 2, currentPlayerLocation.getY(), 0);
        if (Random.random(0, 2) == 1) {
            sideStepLocation = new WorldPoint(currentPlayerLocation.getX() - 2, currentPlayerLocation.getY(), 0);
        }
        Microbot.getWalkerForKotlin().walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), sideStepLocation));
        Rs2Player.waitForWalking();
    }

    WorldPoint findSafeTiles() {
        WorldPoint vorkath = Rs2Npc.getNpc("vorkath").getWorldLocation();
        WorldPoint swPoint = new WorldPoint(vorkath.getX() + 1, vorkath.getY() - 8, 0);
        WorldArea wooxWalkArea = new WorldArea(swPoint, 5, 1);

        List<WorldPoint> safeTiles = wooxWalkArea.toWorldPointList().stream().filter(this::isTileSafe).collect(Collectors.toList());

        // Find the closest safe tile by x-coordinate to the player
        return safeTiles.stream().min(Comparator.comparingInt(tile -> Math.abs(tile.getX() - Microbot.getClient().getLocalPlayer().getWorldLocation().getX()))).orElse(null);
    }


    boolean isTileSafe(WorldPoint tile) {
        return !acidPools.contains(tile)
                && !acidPools.contains(new WorldPoint(tile.getX(), tile.getY() + 1, tile.getPlane()))
                && !acidPools.contains(new WorldPoint(tile.getX(), tile.getY() + 2, tile.getPlane()))
                && !acidPools.contains(new WorldPoint(tile.getX(), tile.getY() + 3, tile.getPlane()));

    }

    private void handleAcidWalk() {
        if (!doesProjectileExistById(acidProjectileId) && !doesProjectileExistById(acidRedProjectileId) && Rs2GameObject.findObjectById(ObjectID.ACID_POOL_32000) == null) {
            Rs2Npc.attack("Vorkath");
            state = State.FIGHT_VORKATH;
            return;
        }

        executeAcidWalk(43, 54, () -> Microbot.getClient().getLocalPlayer().getLocalLocation().getSceneX() < 48 || (!doesProjectileExistById(acidProjectileId) && !doesProjectileExistById(acidRedProjectileId) && Rs2GameObject.findObjectById(ObjectID.ACID_POOL_32000) == null));
        executeAcidWalk(51, 54, () -> Microbot.getClient().getLocalPlayer().getLocalLocation().getSceneX() > 48 || (!doesProjectileExistById(acidProjectileId) && !doesProjectileExistById(acidRedProjectileId) && Rs2GameObject.findObjectById(ObjectID.ACID_POOL_32000) == null));

        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);

        acidPools.clear();
        Rs2GameObject.getGameObjects(ObjectID.ACID_POOL_32000).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));
        Rs2GameObject.getGameObjects(ObjectID.ACID_POOL).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));
        Rs2GameObject.getGameObjects(ObjectID.ACID_POOL_37991).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));

        WorldPoint safeTile = findSafeTiles();
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        if (safeTile != null) {
            if (playerLocation.equals(safeTile)) {
                Rs2Npc.attack("vorkath");
            } else {
                eatAt(50);
                Microbot.getWalkerForKotlin().walkFastLocal(
                        LocalPoint.fromScene(safeTile.getX(), safeTile.getY())
                );
            }
        } else {
//            EthanApiPlugin.sendClientMessage("NO SAFE TILES! TELEPORTING TF OUT!");
//            teleToHouse();
//            changeStateTo(State.WALKING_TO_BANK, 1);
//            return;
        }

    }

    public void executeAcidWalk(int x, int y, BooleanSupplier awaitedCondition) {
        if (!doesProjectileExistById(acidProjectileId) && !doesProjectileExistById(acidRedProjectileId)) return;

        eatAt(80);
        while (!awaitedCondition.getAsBoolean()) {
            Microbot.getWalkerForKotlin().walkFastLocal(
                    LocalPoint.fromScene(x, y)
            );
            sleep(400);
        }
    }


    /**
     * Equipment + inventory
     * Relleka teleport
     * Walk to npc on dock
     * travel to npc on dock
     * walk to stone & skip stone
     * pot up & pray & wake up vorkath
     * kill
     * loot
     * varrock teleport
     * walk to bank
     * repeat
     */
}
