package shit.zen.modules.impl.movement;

import net.minecraft.client.Options;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;

public class Fly extends Module {
    public static Fly INSTANCE;

    public Fly() {
        super("Fly", Category.MOVEMENT);
        INSTANCE = this;
    }

    @EventTarget
    public void onTick(GameTickEvent tickEvent){
        Player player = mc.player;
        if (player == null) return;

        // 检查你的飞行开关（例如按键绑定）

            // 1. 获取移动输入（按键状态）
            Options input = mc.options;
            boolean forward = input.keyUp.isDown();
            boolean back = input.keyDown.isDown();
            boolean left = input.keyLeft.isDown();
            boolean right = input.keyRight.isDown();
            boolean jump = input.keyJump.isDown();
            boolean sneak = input.keyShift.isDown();

            // 2. 计算水平速度方向（基于玩家视角）
            Vec3 lookVec = player.getLookAngle();
            // 获取玩家的水平朝向（忽略俯仰）
            Vec3 forwardVec = new Vec3(lookVec.x, 0, lookVec.z).normalize();
            Vec3 rightVec = forwardVec.cross(new Vec3(0, 1, 0)).normalize();

            // 根据输入合成移动方向
            double moveForward = 0;
            double moveStrafe = 0;
            if (forward) moveForward += 1;
            if (back) moveForward -= 1;
            if (right) moveStrafe += 1;
            if (left) moveStrafe -= 1;

            // 归一化（防止斜向速度过大）
            double length = Math.sqrt(moveForward * moveForward + moveStrafe * moveStrafe);
            if (length > 1.0) {
                moveForward /= length;
                moveStrafe /= length;
            }

            // 3. 计算水平速度向量
            double speed = 0.44; // 可配置
            Vec3 horizontalVelocity = new Vec3(0, 0, 0);
            if (moveForward != 0 || moveStrafe != 0) {
                // 方向向量 = 前方向 * moveForward + 右方向 * moveStrafe
                Vec3 dir = forwardVec.scale(moveForward).add(rightVec.scale(moveStrafe));
                horizontalVelocity = dir.scale(speed);
            }

            // 4. 计算垂直速度
            double verticalSpeed = 0.44; // 可配置
            double yMotion;
            if (jump) {
                yMotion = verticalSpeed;
            } else if (sneak) {
                yMotion = -verticalSpeed;
            } else {
                yMotion = 0.0; // 悬停
            }

            // 5. 应用速度
            player.setDeltaMovement(horizontalVelocity.x, yMotion, horizontalVelocity.z);

            // 6. 可选：设置在地面，减少服务端干扰
            player.setOnGround(true);
            player.fallDistance = 0;

    }


}
