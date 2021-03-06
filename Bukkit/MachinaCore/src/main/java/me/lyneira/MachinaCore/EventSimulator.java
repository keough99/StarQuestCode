package me.lyneira.MachinaCore;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerExperienceEvent;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import com.gmail.nossr50.events.fake.FakeBlockBreakEvent;

/**
 * Class that simulates events.
 * 
 * @author Lyneira
 */
public class EventSimulator {
    private EventSimulator() {
        // Cannot instantiate
    }

    static Event pretendEvent;
    static boolean pretendEventCancelled;

    @SuppressWarnings("deprecation")
    /**
     * Simulates a block place event on behalf of a player. Returns true if the
     * player could build the new block.
     * 
     * @param target
     *            The target location to place at
     * @param typeId
     *            The typeId of the block to place
     * @param placedAgainst
     *            The block that it will be placed against
     * @param player
     *            The player to simulate for
     * @return True if the player may place a block at the location
     */
    public static boolean blockPlace(BlockLocation target, int typeId, byte data, BlockLocation placedAgainst, Player player) {
        Block placedBlock = target.getBlock();
        BlockState replacedBlockState = placedBlock.getState();
        int oldType = replacedBlockState.getTypeId();
		byte oldData = replacedBlockState.getRawData();
        
        // Set the new state without physics.
        placedBlock.setTypeIdAndData(typeId, data, false);
        BlockPlaceEvent placeEvent = new ArtificialBlockPlaceEvent(placedBlock, replacedBlockState, placedAgainst.getBlock(), null, player, true, EquipmentSlot.HAND);
        MachinaCore.pluginManager.callEvent(placeEvent);

        // Revert to the old state without physics.
        placedBlock.setTypeIdAndData(oldType, oldData, false);
        if (placeEvent.isCancelled())
            return false;

        return true;
    }

    /**
     * Simulates a block break event
     * 
     * @param target
     *            The target location to break at
     * @param player
     *            The player to simulate for
     * @param drops
     *            The list of items that will drop from this block break. Other
     *            plugins may have modified this list when this method returns.
     * @return True if the player may break a block at the location.
     */
    public static boolean blockBreak(BlockLocation target, Player player) {
        Block block = target.getBlock();
        BlockBreakEvent breakEvent = new FakeBlockBreakEvent(block, player);

        MachinaCore.pluginManager.callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return false;
        }
        return true;
    }

    /**
     * Function for a moving machina to test whether it's allowed to move to a
     * new location by protection plugins. Returns true if the player could
     * build (and break) the new block.
     * 
     * This function will fire a blockplace event, collect the cancelled result
     * at the highest possible priority, then cancel its own event to prevent it
     * from being logged by any monitoring plugins.
     * 
     * @param target
     *            The target location to place at
     * @param typeId
     *            The typeId of the block to place
     * @param placedAgainst
     *            The block that it will be placed against
     * @param player
     *            The player to simulate for
     * @return True if the player may place a block at the location
     */
    @SuppressWarnings("deprecation")
	public static boolean blockPlacePretend(BlockLocation target, int typeId, BlockLocation placedAgainst, Player player) {
        Block placedBlock = target.getBlock();
        BlockState replacedBlockState = placedBlock.getState();
        int oldType = replacedBlockState.getTypeId();
        byte oldData = replacedBlockState.getRawData();

        // Set the new state without physics.
        placedBlock.setTypeIdAndData(typeId, (byte) 0, false);

        pretendEvent = new ArtificialBlockPlaceEvent(placedBlock, replacedBlockState, placedAgainst.getBlock(), new ItemStack(Material.AIR), player, true, EquipmentSlot.HAND);
        pretendEventCancelled = true;
        MachinaCore.pluginManager.callEvent(pretendEvent);

        // Revert to the old state without physics.
        placedBlock.setTypeIdAndData(oldType, oldData, false);

        return !pretendEventCancelled;
    }

    /**
     * Pretends a block break event to determine whether it would be allowed to
     * happen. It will collect the cancelled result at the highest possible
     * priority, then cancel its own event to prevent it from being logged by
     * any monitoring plugins.
     * 
     * @param target
     *            The target location to break at
     * @param player
     *            The player to simulate for
     * @return True if the player may break a block at the location
     */
    public static boolean blockBreakPretend(BlockLocation target, Player player) {
        Block block = target.getBlock();
        pretendEvent = new FakeBlockBreakEvent(block, player);
        pretendEventCancelled = true;
        MachinaCore.pluginManager.callEvent(pretendEvent);

        return !pretendEventCancelled;
    }

    /**
     * Simulates a rightclick event on the target block. Returns true if the
     * rightclick interaction is allowed.
     * 
     * @param target
     *            The target location to click on.
     * @param player
     *            The player to simulate for
     * @param clickedFace
     *            The blockface to click on
     * @return True if the player may interact with this block
     */
    public static boolean blockRightClick(BlockLocation target, Player player, BlockFace clickedFace)
    {	
    	ArtificialPlayerInteractEvent event = new ArtificialPlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), target.getBlock(), clickedFace);
        MachinaCore.pluginManager.callEvent(event);

        if (event.isCancelled())
            return false;
        
        return true;
    }

    /**
     * Function for testing whether one or more inventories inside a machina are
     * protected from the player. Intended for moving machina as this will break
     * LWC protections and the like.
     * 
     * @param yaw
     *            The direction of the machina
     * @param player
     *            The player to simulate for
     * @param anchor
     *            The anchor of the machina
     * @param blocks
     *            One or more BlueprintBlocks belonging to the machina
     * @return False if and only if the player may interact with and break all
     *         the given blocks. True otherwise.
     */
    public static boolean inventoryProtected(BlockRotation yaw, Player player, BlockLocation anchor, BlueprintBlock... blocks) {
        BlockFace clickedFace = yaw.getOpposite().getYawFace();
        for (BlueprintBlock b : blocks) {
            BlockLocation target = anchor.getRelative(b.vector(yaw));
            if (!(blockRightClick(target, player, clickedFace) && blockBreakPretend(target, player)))
                return true;
        }
        return false;
    }

    /**
     * Tests whether the inventory in this block is protected from the player.
     * Intended for still machina, this test won't break an LWC protection upon
     * success.
     * 
     * @param player
     *            The player to simulate for
     * @param target
     *            The target block
     * @return False if and only if the player may interact with and break the
     *         given block. True otherwise.
     */
    public static boolean inventoryProtectedStatic(Player player, BlockLocation target) {
        if (!(blockRightClick(target, player, BlockFace.NORTH)))
            return true;
        return false;
    }
}
