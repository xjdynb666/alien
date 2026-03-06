package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.impl.combat.KillAura;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import java.awt.Color;

public class TargetHUD extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private LivingEntity target;
    private float smoothHealth;

    // 基础设置（适配你端的SliderSetting）
    public final SliderSetting xPos = add(new SliderSetting("X", 20, 0, 1920, 1));
    public final SliderSetting yPos = add(new SliderSetting("Y", 20, 0, 1080, 1));
    public final SliderSetting width = add(new SliderSetting("Width", 180, 100, 300, 1));
    public final SliderSetting height = add(new SliderSetting("Height", 55, 30, 100, 1));
    public final SliderSetting radius = add(new SliderSetting("Radius", 6, 0, 20, 1));
    public final SliderSetting alpha = add(new SliderSetting("Alpha", 220, 50, 255, 1));

    // 构造函数：使用字符串分类，避免枚举冲突
    public TargetHUD() {
        super("TargetHUD", "显示攻击目标信息（带头像）", "Render");
    }

    // 适配你端的onRender3D方法签名
    @Override
    public void onRender3D(MatrixStack matrices) {
        super.onRender3D(matrices);

        // 基础空值校验
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            target = null;
            return;
        }

        // 获取战斗目标（优先级：KillAura > AutoCrystal > AutoAnchor）
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

        // 2D渲染初始化（1.21.1适配，移除VertexSorter参数）
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().loadIdentity();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 0, 1000, 3000));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // 1. 绘制圆角背景
        Render2DUtil.drawRound(matrices, x, y, w, h, r, new Color(20, 20, 20, a));

        // 2. 绘制玩家头像（1.21.1原生API，彻底修复drawTexture）
        if (target instanceof PlayerEntity player) {
            drawPlayerHead(matrices, player, x + 5, y + 5, h - 10, h - 10);
        }

        // 3. 绘制血条
        float barX = x + h;
        float barY = y + h - 18;
        float barW = w - h - 5;
        float barH = 8;
        Render2DUtil.drawRect(matrices, barX, barY, barW, barH, new Color(50, 50, 50));
        Render2DUtil.drawRect(matrices, barX, barY, barW * healthPercent, barH, getHealthColor(healthPercent));

        // 4. 绘制目标名称（适配1.21.1 TextRenderer）
        mc.textRenderer.drawWithShadow(matrices, Text.literal(target.getName().getString()), barX, barY - 12, Color.WHITE.getRGB());

        // 5. 绘制血量文字（居中）
        String healthText = String.format("%.0f / %.0f", smoothHealth, target.getMaxHealth());
        float textX = barX + barW / 2f - mc.textRenderer.getWidth(healthText) / 2f;
        mc.textRenderer.drawWithShadow(matrices, Text.literal(healthText), textX, barY + 1, Color.WHITE.getRGB());

        // 恢复渲染状态
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().popMatrix();
    }

    // 彻底修复的头像绘制（1.21.1 兼容，无mc.drawTexture依赖）
    private void drawPlayerHead(MatrixStack matrices, PlayerEntity player, float x, float y, float w, float h) {
        Identifier skin = player.getSkinTextures().texture();
        RenderSystem.setShaderTexture(0, skin);
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(w / 8f, h / 8f, 1f);
        // 使用 RenderSystem.drawTexture 替代 mc.drawTexture
        RenderSystem.drawTexture(matrices.peek().getPositionMatrix(), 0, 0, 0, 8, 8, 8, 8, 64, 64);
        if (player.canRenderCape()) {
            RenderSystem.drawTexture(matrices.peek().getPositionMatrix(), 0, 0, 0, 40, 8, 8, 8, 64, 64);
        }
        matrices.pop();
    }

    // 自动适配模块管理器，获取战斗目标
    private LivingEntity getCombatTarget() {
        try {
            // 适配两种模块管理器获取方式
            Object moduleManager;
            if (Alien.class.getDeclaredField("moduleManager") != null) {
                moduleManager = Alien.class.getDeclaredField("moduleManager").get(null);
            } else {
                Object instance = Alien.class.getField("INSTANCE").get(null);
                moduleManager = instance.getClass().getField("moduleManager").get(instance);
            }

            // 获取KillAura目标
            Object killAura = moduleManager.getClass().getMethod("getModuleByName", String.class).invoke(moduleManager, "KillAura");
            if (killAura != null && (boolean) killAura.getClass().getMethod("isOn").invoke(killAura)) {
                return (LivingEntity) killAura.getClass().getMethod("getTarget").invoke(killAura);
            }

            // 获取AutoCrystal目标
            Object autoCrystal = moduleManager.getClass().getMethod("getModuleByName", String.class).invoke(moduleManager, "AutoCrystal");
            if (autoCrystal != null && (boolean) autoCrystal.getClass().getMethod("isOn").invoke(autoCrystal)) {
                return (LivingEntity) autoCrystal.getClass().getField("displayTarget").get(autoCrystal);
            }

            // 获取AutoAnchor目标
            Object autoAnchor = moduleManager.getClass().getMethod("getModuleByName", String.class).invoke(moduleManager, "AutoAnchor");
            if (autoAnchor != null && (boolean) autoAnchor.getClass().getMethod("isOn").invoke(autoAnchor)) {
                return (LivingEntity) autoAnchor.getClass().getMethod("getTarget").invoke(autoAnchor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // 血条颜色渐变（绿→黄→红）
    private Color getHealthColor(float percent) {
        return percent > 0.6 ? new Color(85, 255, 85) : percent > 0.3 ? new Color(255, 200, 85) : new Color(255, 85, 85);
    }
}
