package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonShopActionResultPayload;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class DungeonClientSoundEffects {
    private DungeonClientSoundEffects() {}

    public static void playMenuHover() {
        play(ModSoundEvents.UI_MENU_HOVER.get(), 0.88f, 0.28f);
    }

    public static void playMenuSelect() {
        play(ModSoundEvents.UI_MENU_SELECT.get(), 1.0f, 0.52f);
    }

    public static void playMenuOpen() {
        play(ModSoundEvents.UI_MENU_OPEN.get(), 0.86f, 0.65f);
    }

    public static void playMenuClose() {
        play(ModSoundEvents.UI_MENU_CLOSE.get(), 0.84f, 0.50f);
    }

    public static void playShopConfirmationOpen() {
        play(ModSoundEvents.SHOP_CONFIRMATION_OPEN.get(), 1.0f, 0.58f);
    }

    public static void playShopPurchaseSuccess() {
        play(ModSoundEvents.SHOP_PURCHASE_SUCCESS.get(), 1.0f, 0.72f);
    }

    public static void playShopPurchaseFailure() {
        play(ModSoundEvents.SHOP_PURCHASE_FAIL.get(), 0.82f, 0.52f);
    }

    public static void playShopMajorUnlock() {
        play(ModSoundEvents.SHOP_MAJOR_UNLOCK.get(), 1.0f, 0.78f);
    }

    public static void playShopActionResult(DungeonShopActionResultPayload result) {
        if (result == null) {
            return;
        }
        if (result.status() == DungeonShopActionResultPayload.ResultStatus.SUCCESS) {
            String message = result.message().toLowerCase(Locale.ROOT);
            if (message.contains("unlocked") || message.contains("satchel upgrade")) {
                playShopMajorUnlock();
            } else {
                playShopPurchaseSuccess();
            }
        } else {
            playShopPurchaseFailure();
        }
    }

    public static void playLoadoutSelection(DungeonPreparationLoadoutData.Category category) {
        if (category == null) {
            playMenuSelect();
            return;
        }
        SoundEvent sound = switch (category) {
            case HELMET -> ModSoundEvents.LOADOUT_EQUIPPED_HELMET.get();
            case CHESTPLATE -> ModSoundEvents.LOADOUT_EQUIPPED_CHEST.get();
            case LEGGINGS -> ModSoundEvents.LOADOUT_EQUIPPED_LEGS.get();
            case BOOTS -> ModSoundEvents.LOADOUT_EQUIPPED_SHOES.get();
            case PRIMARY_SWORD, OFF_HAND -> ModSoundEvents.LOADOUT_EQUIPPED_WEAPON.get();
            default -> null;
        };
        if (sound == null) {
            playMenuSelect();
        } else {
            play(sound, 1.0f, 0.60f);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
