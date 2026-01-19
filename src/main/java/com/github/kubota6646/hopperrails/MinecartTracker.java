package com.github.kubota6646.hopperrails;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinecartTracker implements Listener {
    
    private final HopperRails plugin;
    private final Map<UUID, MinecartData> trackedMinecarts;
    private BukkitTask trackingTask;
    
    public MinecartTracker(HopperRails plugin) {
        this.plugin = plugin;
        this.trackedMinecarts = new HashMap<>();
    }
    
    public void startTracking() {
        int interval = plugin.getCheckInterval();
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllMinecarts, 0L, interval);
    }
    
    public void stopTracking() {
        if (trackingTask != null) {
            trackingTask.cancel();
        }
        trackedMinecarts.clear();
    }
    
    private void checkAllMinecarts() {
        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) {
            if (entity instanceof HopperMinecart) {
                HopperMinecart minecart = (HopperMinecart) entity;
                checkMinecart(minecart);
            }
        }
        
        // すべてのワールドをチェック
        for (int i = 1; i < Bukkit.getWorlds().size(); i++) {
            for (Entity entity : Bukkit.getWorlds().get(i).getEntities()) {
                if (entity instanceof HopperMinecart) {
                    HopperMinecart minecart = (HopperMinecart) entity;
                    checkMinecart(minecart);
                }
            }
        }
    }
    
    private void checkMinecart(HopperMinecart minecart) {
        UUID uuid = minecart.getUniqueId();
        MinecartData data = trackedMinecarts.computeIfAbsent(uuid, k -> new MinecartData());
        
        Location location = minecart.getLocation();
        Inventory inventory = minecart.getInventory();
        
        // 上にチェストがあるかチェック
        Block aboveBlock = location.getBlock().getRelative(BlockFace.UP);
        boolean hasChestAbove = aboveBlock.getState() instanceof Chest;
        
        // インベントリ内のアイテム数をカウント
        int currentItemCount = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) != null) {
                currentItemCount += inventory.getItem(i).getAmount();
            }
        }
        
        MinecartMode previousMode = data.getMode();
        MinecartMode newMode = previousMode;
        
        // モードの判定
        if (hasChestAbove) {
            // チェストの下にいる場合
            if (currentItemCount > data.getPreviousItemCount()) {
                // アイテムが増えている = 吸収中
                newMode = MinecartMode.ABSORBING;
            } else if (previousMode == MinecartMode.ABSORBING && currentItemCount == data.getPreviousItemCount()) {
                // 吸収モードで、アイテム数が変わっていない = 吸収完了
                newMode = MinecartMode.MOVING;
                // レッドストーン信号を送信
                sendRedstoneSignal(location);
            } else if (previousMode == MinecartMode.MOVING) {
                // 移動モードから戻ってきた場合、待機モードに
                newMode = MinecartMode.WAITING;
            } else if (previousMode != MinecartMode.ABSORBING) {
                // その他の場合は待機モード
                newMode = MinecartMode.WAITING;
            }
        } else {
            // チェストの下にいない場合
            if (previousMode == MinecartMode.MOVING) {
                // 移動モードを維持
                newMode = MinecartMode.MOVING;
            }
        }
        
        // モードが変わった場合のログ
        if (newMode != previousMode) {
            data.setMode(newMode);
            logModeChange(minecart, newMode);
        }
        
        // 前回のアイテム数を更新
        data.setPreviousItemCount(currentItemCount);
    }
    
    private void sendRedstoneSignal(Location location) {
        Block railBlock = location.getBlock();
        Block blockBelow = railBlock.getRelative(BlockFace.DOWN);
        
        // レールの下のブロックにレッドストーン信号を送る
        // これは、レールの下のブロックを一時的にレッドストーンブロックに変更することで実現
        Material originalMaterial = blockBelow.getType();
        
        if (originalMaterial != Material.REDSTONE_BLOCK) {
            blockBelow.setType(Material.REDSTONE_BLOCK);
            
            // 指定された時間後に元に戻す
            int duration = plugin.getRedstoneSignalDuration();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (blockBelow.getType() == Material.REDSTONE_BLOCK) {
                    blockBelow.setType(originalMaterial);
                }
            }, duration);
            
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("レッドストーン信号を送信: " + location);
            }
        }
    }
    
    private void logModeChange(HopperMinecart minecart, MinecartMode mode) {
        if (plugin.isDebugMode()) {
            String messageKey = switch (mode) {
                case ABSORBING -> "メッセージ.吸収モード開始";
                case MOVING -> "メッセージ.移動モード開始";
                case WAITING -> "メッセージ.待機モード開始";
            };
            
            String message = plugin.getConfig().getString(messageKey, "");
            plugin.getLogger().info(message.replace("&a", "").replace("&e", "").replace("&c", "").replace("&7", ""));
        }
    }
    
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof HopperMinecart) {
            trackedMinecarts.remove(event.getVehicle().getUniqueId());
        }
    }
    
    private static class MinecartData {
        private MinecartMode mode = MinecartMode.WAITING;
        private int previousItemCount = 0;
        
        public MinecartMode getMode() {
            return mode;
        }
        
        public void setMode(MinecartMode mode) {
            this.mode = mode;
        }
        
        public int getPreviousItemCount() {
            return previousItemCount;
        }
        
        public void setPreviousItemCount(int count) {
            this.previousItemCount = count;
        }
    }
}
