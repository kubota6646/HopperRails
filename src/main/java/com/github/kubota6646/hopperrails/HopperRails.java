package com.github.kubota6646.hopperrails;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class HopperRails extends JavaPlugin {
    
    private MinecartTracker minecartTracker;
    
    @Override
    public void onEnable() {
        // 設定ファイルの保存
        saveDefaultConfig();
        
        // トラッカーの初期化
        minecartTracker = new MinecartTracker(this);
        
        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(minecartTracker, this);
        
        // タスクの開始
        minecartTracker.startTracking();
        
        // 有効化メッセージ
        String message = getConfig().getString("メッセージ.プラグイン有効化", "&a[HopperRails] プラグインが有効化されました");
        getLogger().info(ColorCodeUtil.stripColorCodes(message));
    }
    
    @Override
    public void onDisable() {
        // タスクの停止
        if (minecartTracker != null) {
            minecartTracker.stopTracking();
        }
        
        // 無効化メッセージ
        String message = getConfig().getString("メッセージ.プラグイン無効化", "&c[HopperRails] プラグインが無効化されました");
        getLogger().info(ColorCodeUtil.stripColorCodes(message));
    }
    
    public boolean isDebugMode() {
        return getConfig().getBoolean("デバッグモード", false);
    }
    
    public int getRedstoneSignalDuration() {
        return getConfig().getInt("レッドストーン信号持続時間", 20);
    }
    
    public int getCheckInterval() {
        return getConfig().getInt("チェック間隔", 10);
    }
}
