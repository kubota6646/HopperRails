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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinecartTracker implements Listener {
    
    // レッドストーン信号の重複防止タイムアウト（ミリ秒）
    private static final long REDSTONE_SIGNAL_TIMEOUT_MS = 2000;
    
    private final HopperRails plugin;
    private final Map<UUID, MinecartData> trackedMinecarts;
    private final Map<String, Long> activeRedstoneBlocks;
    private BukkitTask trackingTask;
    
    public MinecartTracker(HopperRails plugin) {
        this.plugin = plugin;
        this.trackedMinecarts = new ConcurrentHashMap<>();
        this.activeRedstoneBlocks = new ConcurrentHashMap<>();
    }
    
    public void startTracking() {
        int interval = plugin.getCheckInterval();
        // 注意: すべてのワールドのエンティティをスキャンするため、
        // エンティティ数が多いサーバーではパフォーマンスに影響する可能性があります。
        // config.ymlの「チェック間隔」を調整してパフォーマンスを最適化してください。
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllMinecarts, 0L, interval);
    }
    
    public void stopTracking() {
        if (trackingTask != null) {
            trackingTask.cancel();
        }
        trackedMinecarts.clear();
    }
    
    private void checkAllMinecarts() {
        // すべてのワールドをチェック
        // パフォーマンス最適化: getEntitiesByClassを使用してホッパー付きトロッコのみを取得
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (HopperMinecart minecart : world.getEntitiesByClass(HopperMinecart.class)) {
                checkMinecart(minecart);
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
            } else if (previousMode == MinecartMode.MOVING && !data.hasLeftChestArea()) {
                // 移動モードだがまだチェストエリアを離れていない場合は移動モードを維持
                newMode = MinecartMode.MOVING;
            } else if (previousMode == MinecartMode.MOVING && data.hasLeftChestArea()) {
                // 移動モードでチェストエリアから離れて戻ってきた場合
                if (currentItemCount > 0) {
                    // アイテムが残っている場合は待機モードをスキップして移動モードを維持
                    newMode = MinecartMode.MOVING;
                    sendRedstoneSignal(location);
                } else {
                    // アイテムがない場合は待機モードへ
                    newMode = MinecartMode.WAITING;
                }
                data.setLeftChestArea(false);
            } else if (previousMode == MinecartMode.WAITING) {
                // 待機モードを維持（アイテムが増えるまで）
                newMode = MinecartMode.WAITING;
            } else {
                // 初期状態やその他の場合は待機モード
                newMode = MinecartMode.WAITING;
            }
        } else {
            // チェストの下にいない場合
            if (previousMode == MinecartMode.MOVING) {
                // 移動モードを維持し、チェストエリアを離れたことを記録
                newMode = MinecartMode.MOVING;
                data.setLeftChestArea(true);
            } else if (previousMode == MinecartMode.ABSORBING) {
                // 吸収中にチェストの下から離れた場合も移動モードへ
                newMode = MinecartMode.MOVING;
                data.setLeftChestArea(true);
                sendRedstoneSignal(location);
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
        Material originalMaterial = blockBelow.getType();
        String blockKey = blockBelow.getWorld().getName() + ":" + 
                         blockBelow.getX() + "," + blockBelow.getY() + "," + blockBelow.getZ();
        
        // 安全性チェック: 空気、流体、または既にレッドストーンブロックの場合はスキップ
        if (originalMaterial == Material.AIR || 
            originalMaterial == Material.CAVE_AIR || 
            originalMaterial == Material.VOID_AIR ||
            originalMaterial == Material.WATER ||
            originalMaterial == Material.LAVA ||
            originalMaterial == Material.REDSTONE_BLOCK) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("レッドストーン信号送信スキップ（置換不可能なブロック）: " + originalMaterial);
            }
            return;
        }
        
        // 既に処理中のブロックかチェック（競合状態の防止）
        Long existingTimestamp = activeRedstoneBlocks.get(blockKey);
        if (existingTimestamp != null && System.currentTimeMillis() - existingTimestamp < REDSTONE_SIGNAL_TIMEOUT_MS) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("レッドストーン信号送信スキップ（既に処理中）: " + blockKey);
            }
            return;
        }
        
        // 処理中として記録
        activeRedstoneBlocks.put(blockKey, System.currentTimeMillis());
        
        // レッドストーンブロックに変更
        blockBelow.setType(Material.REDSTONE_BLOCK);
        
        // 指定された時間後に元に戻す
        int duration = plugin.getRedstoneSignalDuration();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (blockBelow.getType() == Material.REDSTONE_BLOCK) {
                blockBelow.setType(originalMaterial);
            }
            // 処理完了として記録から削除
            activeRedstoneBlocks.remove(blockKey);
        }, duration);
        
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("レッドストーン信号を送信: " + location);
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
            plugin.getLogger().info(ColorCodeUtil.stripColorCodes(message));
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
        private boolean leftChestArea = false;
        
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
        
        public boolean hasLeftChestArea() {
            return leftChestArea;
        }
        
        public void setLeftChestArea(boolean left) {
            this.leftChestArea = left;
        }
    }
}
