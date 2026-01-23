package mod.test.mymodtest.entity.data;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * 存储单个玩家与特定商人的交易数据
 */
public class PlayerTradeData {

    private final UUID playerUUID;
    private int tradeCount;
    private boolean secretUnlocked;

    public PlayerTradeData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.tradeCount = 0;
        this.secretUnlocked = false;
    }

    // Getters
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public boolean isSecretUnlocked() {
        return secretUnlocked;
    }

    // Setters
    public void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }

    public void setSecretUnlocked(boolean secretUnlocked) {
        this.secretUnlocked = secretUnlocked;
    }

    public void incrementTradeCount() {
        this.tradeCount++;
    }

    // NBT 序列化
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("TradeCount", this.tradeCount);
        nbt.putBoolean("SecretUnlocked", this.secretUnlocked);
        return nbt;
    }

    public static PlayerTradeData fromNbt(UUID uuid, NbtCompound nbt) {
        PlayerTradeData data = new PlayerTradeData(uuid);
        data.tradeCount = nbt.getInt("TradeCount").orElse(0);
        data.secretUnlocked = nbt.getBoolean("SecretUnlocked").orElse(false);
        return data;
    }
}
