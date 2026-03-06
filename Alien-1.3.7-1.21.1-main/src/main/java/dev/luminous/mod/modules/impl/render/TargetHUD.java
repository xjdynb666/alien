package dev.luminous.mod.modules.impl.render;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.KillAura;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.api.utils.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;

public class TargetHUD extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private LivingEntity target;
    private float smoothHealth;

    // 基础设置
    public final SliderSetting xPos = add(new SliderSetting("X", 20, 0, 1920, 1));
    public final SliderSetting yPos = add(new SliderSetting("Y", 20, 0, 1080, 1));
    public final SliderSetting width = add(new SliderSetting("Width", 180, 100, 300, 1));
    public final SliderSetting height = add(new SliderSetting("Height", 55, 30, 100, 1));
    public final SliderSetting radius = add(new SliderSetting("Radius", 6, 0, 20, 1));
    public final SliderSetting alpha = add(new SliderSetting("Alpha", 220, 50, 255, 1));

    public TargetHUD() {
        super("TargetHUD", Category.Render);
        setChinese("目标HUD");
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        super.onRender2D(context, tickDelta);

        // 基础空值校验
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            target = null;
            return;
        }

        // 获取战斗目标（优先级：KillAura > AutoCrystal）
        target = getCombatTarget();
        if (target == null || !target.isAlive()) {
            smoothHealth = 0;
            return;
        }

        // 平滑血条动画
        smoothHealth += (target.getHealth() - smoothHealth) * 0.15f;
        float healthPercent = Math.max(0, Math.min(1, smoothHealth / target.getMaxHealth()));

        // 读取自定义配置
        float x = xPos.getValueFloat();
        float y = yPos.getValueFloat();
        float w = width.getValueFloat();
        float h = height.getValueFloat();
        float r = radius.getValueFloat();
        int a = alpha.getValueInt();
        MatrixStack matrices = context.getMatrices();

        // 1. 绘制圆角背景
        Render2DUtil.drawRound(matrices, x, y, w, h, r, new Color(20, 20, 20, a));

        // 2. 绘制玩家头像
        if (target instanceof PlayerEntity player) {
            drawPlayerHead(context, matrices, player, x + 5, y + 5, h - 10, h - 10);
        }

        // 3. 绘制血条
        float barX = x + h;
        float barY = y + h - 18;
        float barW = w - h - 5;
        float barH = 8;
        Render2DUtil.drawRect(matrices, barX, barY, barW, barH, new Color(50, 50, 50));
        Render2DUtil.drawRect(matrices, barX, barY, barW * healthPercent, barH, getHealthColor(healthPercent));

        // 4. 绘制目标名称
        context.drawTextWithShadow(mc.textRenderer, Text.literal(target.getName().getString()), (int)barX, (int)barY - 12, Color.WHITE.getRGB());

        // 5. 绘制血量文字（居中）
        String healthText = String.format("%.0f / %.0f", smoothHealth, target.getMaxHealth());
        float textX = barX + barW / 2f - mc.textRenderer.getWidth(healthText) / 2f;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(healthText), (int)textX, (int)barY + 1, Color.WHITE.getRGB());
    }

    // 玩家头像绘制
    private void drawPlayerHead(DrawContext context, MatrixStack matrices, PlayerEntity player, float x, float y, float w, float h) {
        // 获取玩家皮肤纹理
        Identifier skin = player.getSkinTextures().texture();
        mc.getTextureManager().bindTexture(skin);

        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(w / 8f, h / 8f, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绘制皮肤头部区域（8x8像素）
        context.drawTexture(matrices, 0, 0, 8, 8, 8, 8, 8, 8, 64, 64);

        // 绘制头盔层
        context.drawTexture(matrices, 0, 0, 8, 8, 40, 8, 8, 8, 64, 64);

        matrices.pop();
    }

    // 获取战斗目标
    private LivingEntity getCombatTarget() {
        // 获取KillAura目标
        if (KillAura.INSTANCE != null && KillAura.INSTANCE.isOn() && KillAura.target instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        // 获取AutoCrystal目标
        if (AutoCrystal.INSTANCE != null && AutoCrystal.INSTANCE.isOn() && AutoCrystal.INSTANCE.displayTarget != null) {
            return AutoCrystal.INSTANCE.displayTarget;
        }

        return null;
    }

    // 血条颜色渐变（绿→黄→红）
    private Color getHealthColor(float percent) {
        return percent > 0.6 ? new Color(85, 255, 85) : percent > 0.3 ? new Color(255, 200, 85) : new Color(255, 85, 85);
    }
}
