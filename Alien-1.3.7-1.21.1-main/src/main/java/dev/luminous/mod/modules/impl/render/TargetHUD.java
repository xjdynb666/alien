package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.impl.combat.KillAura;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;

public class TargetHUD extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private LivingEntity target;
    private float smoothHealth;

    // 位置自定义（SliderSetting 适配你的端）
    public final SliderSetting xPos = add(new SliderSetting("X", 20, 0, 1920, 1));
    public final SliderSetting yPos = add(new SliderSetting("Y", 20, 0, 1080, 1));
    // 大小自定义
    public final SliderSetting width = add(new SliderSetting("Width", 180, 100, 300, 1));
    public final SliderSetting height = add(new SliderSetting("Height", 55, 30, 100, 1));
    // 样式自定义
    public final SliderSetting radius = add(new SliderSetting("Radius", 6, 0, 20, 1));
    public final SliderSetting alpha = add(new SliderSetting("Alpha", 220, 50, 255, 1));

    public TargetHUD() {
        super("TargetHUD", "显示攻击模块锁定的目标信息", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        super.onRender3D(event);

        // 基础空值校验
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            target = null;
            return;
        }

        // 三合一目标获取 + 优先级：KillAura > AutoCrystal > AutoAnchor
        target = null;
        // 1. KillAura（优先）
        KillAura killAura = (KillAura) Alien.moduleManager.getModuleByName("KillAura");
        if (killAura != null && killAura.isOn()) {
            try {
                target = (LivingEntity) killAura.getClass().getMethod("getTarget").invoke(killAura);
            } catch (Exception e) {
                try {
                    target = (LivingEntity) killAura.getClass().getField("target").get(killAura);
                } catch (Exception ignored) {}
            }
        }

        // 2. AutoCrystal（次之，用你的displayTarget）
        if (target == null) {
            AutoCrystal autoCrystal = (AutoCrystal) Alien.moduleManager.getModuleByName("AutoCrystal");
            if (autoCrystal != null && autoCrystal.isOn() && autoCrystal.displayTarget != null && autoCrystal.displayTarget.isAlive()) {
                target = autoCrystal.displayTarget;
            }
        }

        // 3. AutoAnchor（最后）
        if (target == null) {
            AutoAnchor autoAnchor = (AutoAnchor) Alien.moduleManager.getModuleByName("AutoAnchor");
            if (autoAnchor != null && autoAnchor.isOn()) {
                try {
                    target = (LivingEntity) autoAnchor.getClass().getMethod("getTarget").invoke(autoAnchor);
                } catch (Exception e) {
                    try {
                        target = (LivingEntity) autoAnchor.getClass().getField("target").get(autoAnchor);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 目标非空/存活校验
        if (target == null || !target.isAlive()) {
            smoothHealth = 0;
            return;
        }

        // 平滑血条动画
        smoothHealth += (target.getHealth() - smoothHealth) * 0.15f;
        float healthPercent = Math.max(0, Math.min(1, smoothHealth / target.getMaxHealth()));

        // 读取自定义配置（SliderSetting 用 getValueFloat() 获取值）
        float x = xPos.getValueFloat();
        float y = yPos.getValueFloat();
        float w = width.getValueFloat();
        float h = height.getValueFloat();
        float r = radius.getValueFloat();
        int a = alpha.getValueInt();

        MatrixStack matrices = event.getMatrixStack();

        // 开启2D渲染（保留3D状态，不影响游戏）
        RenderSystem.getModelViewStack().push();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.setProjectionMatrix(mc.gameRenderer.getProjectionMatrix());
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();

        // 1. 绘制圆角背景
        Render2DUtil.drawRound(matrices, x, y, w, h, r, new Color(20, 20, 20, a));

        // 2. 绘制玩家头像（仅玩家实体显示）
        if (target instanceof PlayerEntity player) {
            drawPlayerHead(matrices, player, x + 5, y + 5, h - 10, h - 10);
        }

        // 3. 绘制血条背景
        float barX = x + h;
        float barY = y + h - 18;
        float barW = w - h - 5;
        float barH = 8;
        Render2DUtil.drawRect(matrices, barX, barY, barW, barH, new Color(50, 50, 50));

        // 4. 绘制血条前景（颜色渐变）
        Render2DUtil.drawRect(matrices, barX, barY, barW * healthPercent, barH, getHealthColor(healthPercent));

        // 5. 绘制目标名称
        mc.textRenderer.drawWithShadow(matrices, target.getName().getString(), barX, barY - 12, Color.WHITE.getRGB());

        // 6. 绘制血量文字（居中）
        String healthText = String.format("%.0f / %.0f", smoothHealth, target.getMaxHealth());
        float textX = barX + barW / 2f - mc.textRenderer.getWidth(healthText) / 2f;
        mc.textRenderer.drawWithShadow(matrices, healthText, textX, barY + 1, Color.WHITE.getRGB());

        // 恢复3D渲染状态，避免游戏画面异常
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();
    }

    // 玩家头像绘制（基于MC原生皮肤纹理，无额外依赖）
    private void drawPlayerHead(MatrixStack matrices, PlayerEntity player, float x, float y, float w, float h) {
        mc.getTextureManager().bindTexture(player.getSkinTexture());
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(w / 8f, h / 8f, 1f);
        // 绘制皮肤头部区域（8x8）
        mc.drawTexture(matrices, 0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        // 绘制头盔（如果有）
        if (player.needsRibbon()) {
            mc.drawTexture(matrices, 0, 0, 8, 8, 40, 8, 8, 8, 64, 64);
        }
        matrices.pop();
    }

    // 血条颜色渐变（绿→黄→红）
    private Color getHealthColor(float percent) {
        if (percent > 0.6) return new Color(85, 255, 85);
        if (percent > 0.3) return new Color(255, 200, 85);
        return new Color(255, 85, 85);
    }
}
