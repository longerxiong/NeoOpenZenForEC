package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import shit.zen.ZenClient;
import shit.zen.event.impl.*;
import shit.zen.modules.impl.movement.Sprint;
import shit.zen.asm.Invocation;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(LocalPlayer.class)
public class LocalPlayerPatch {
    @WrapInvoke(
            method = "canStartSprinting",
            desc = "()Z",
            target = "net/minecraft/client/player/ClientInput/hasForwardImpulse",
            targetDesc = "()Z"
    )
    public static boolean onCanStartSprintingInput(LocalPlayer player,
                                                    Invocation<ClientInput, Boolean> original) throws Exception {
        if (Sprint.isFullEnabled()) {
            return player.input.getMoveVector().lengthSquared() > 0.0F;
        }
        return original.call();
    }

    @WrapInvoke(
            method = "shouldStopRunSprinting",
            desc = "()Z",
            target = "net/minecraft/client/player/ClientInput/hasForwardImpulse",
            targetDesc = "()Z"
    )
    public static boolean onShouldStopSprintingInput(LocalPlayer player,
                                                      Invocation<ClientInput, Boolean> original) throws Exception {
        if (Sprint.isFullEnabled()) {
            return player.input.getMoveVector().lengthSquared() > 0.0F;
        }
        return original.call();
    }

    public static MotionEvent onMotion(double x, double y, double z, float yaw, float pitch, boolean onGround, boolean isPost) {
        // MotionEvent's phase names are historically inverted: modules treat post as the
        // mutable, before-send phase and pre as the notification after sendPosition returns.
        MotionEvent event = new MotionEvent(isPost, x, y, z, yaw, pitch, onGround);
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return event;
    }

    public static Vec3 getEventPosition(MotionEvent event) {
        return new Vec3(event.x, event.y, event.z);
    }

    public static SlowdownEvent onSlowDown(boolean slow) {
        if (ZenClient.isReady()) {
            return (SlowdownEvent) ZenClient.instance.getEventBus().call(new SlowdownEvent(slow));
        }
        return new SlowdownEvent(slow);
    }

    @Transform(
            method = "modifyInput",
            desc = "(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;"
    )
    public static void transformModifyInput(MethodNode methodNode) {
        AbstractInsnNode targetCall = null;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode methodInsn
                    && methodInsn.name.equals(ReflectionUtil.getMappedMethodName(LocalPlayer.class, "isUsingItem", "()Z"))
                    && methodInsn.desc.equals("()Z")
                    && methodInsn.getNext() instanceof JumpInsnNode) {
                targetCall = insn;
                break;
            }
        }
        if (targetCall == null) {
            throw new IllegalStateException("Could not find the item-use slowdown check in LocalPlayer.modifyInput");
        }
        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(LocalPlayerPatch.class),
                "onSlowDown",
                "(Z)L" + SlowdownEvent.class.getName().replace(".", "/") + ";",
                false));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                SlowdownEvent.class.getName().replace(".", "/"),
                "isSlowDown",
                "()Z",
                false));
        methodNode.instructions.insert(targetCall, replacement);
    }

    @Inject(
            method = "tick",
            desc = "()V",
            at = @At(value = At.Type.BEFORE_INVOKE, method = "net/minecraft/client/player/AbstractClientPlayer/tick", desc = "()V")
    )
    public static void onTick(LocalPlayer player, CallbackInfo callbackInfo) throws Throwable {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new SprintEvent());
        }
    }

    @Inject(method = "aiStep", desc = "()V")
    public static void onAiStep(LocalPlayer player, CallbackInfo callbackInfo) throws Throwable {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new GameTickEvent());
        }
    }

    @Transform(method = "sendPosition", desc = "()V")
    public static void transformSendPosition(MethodNode methodNode) throws Throwable {
        int motionEventLocal = methodNode.maxLocals++;
        InsnList constructEvent = new InsnList();
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "getX", "()D"));
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "getY", "()D"));
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "getZ", "()D"));
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "getYRot", "()F"));
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "getXRot", "()F"));
        constructEvent.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructEvent.add(invokeGetter(Entity.class, "onGround", "()Z"));
        constructEvent.add(new InsnNode(Opcodes.ICONST_0));
        constructEvent.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(LocalPlayerPatch.class),
                "onMotion",
                "(DDDFFZZ)L" + MotionEvent.class.getName().replace(".", "/") + ";"));
        constructEvent.add(new VarInsnNode(Opcodes.ASTORE, motionEventLocal));

        AbstractInsnNode[] originalInstructions = methodNode.instructions.toArray();
        for (AbstractInsnNode insn : originalInstructions) {
            if (insn instanceof MethodInsnNode methodInsn) {
                String name = methodInsn.name;
                if (name.equals(ReflectionUtil.getMappedMethodName(Entity.class, "position", "()Lnet/minecraft/world/phys/Vec3;"))
                        && methodInsn.desc.equals("()Lnet/minecraft/world/phys/Vec3;")) {
                    redirectToEventPosition(methodNode, methodInsn, motionEventLocal);
                } else if (name.equals(ReflectionUtil.getMappedMethodName(Entity.class, "onGround", "()Z"))) {
                    redirectToEventField(methodNode, methodInsn, motionEventLocal, "onGround", "Z");
                } else if (name.equals(ReflectionUtil.getMappedMethodName(Entity.class, "getYRot", "()F"))) {
                    redirectToEventField(methodNode, methodInsn, motionEventLocal, "yaw", "F");
                } else if (name.equals(ReflectionUtil.getMappedMethodName(Entity.class, "getXRot", "()F"))) {
                    redirectToEventField(methodNode, methodInsn, motionEventLocal, "pitch", "F");
                }
            }
        }
        methodNode.instructions.insert(constructEvent);
        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                methodNode.instructions.insertBefore(insn, createPostMotionEvent());
            }
        }
    }

    private static boolean handlingMoveEvent = false;

    @Inject(method = "move", desc = "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = At.Type.HEAD))
    public static void onMove(LocalPlayer player, MoverType type, Vec3 movement, CallbackInfo ci) {
        if (handlingMoveEvent) return;
        MoveEvent event = new MoveEvent(type, movement.x, movement.y, movement.z);
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.getX() != movement.x || event.getY() != movement.y || event.getZ() != movement.z) {
            handlingMoveEvent = true;
            player.move(type, new Vec3(event.getX(), event.getY(), event.getZ()));
            handlingMoveEvent = false;
            ci.cancel();
        }
    }

    private static MethodInsnNode invokeGetter(Class<?> owner, String mojangName, String desc) {
        return new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/entity/Entity",
                ReflectionUtil.getMappedMethodName(owner, mojangName, desc),
                desc);
    }

    private static void redirectToEventField(MethodNode methodNode, MethodInsnNode callInsn, int eventLocal, String field, String desc) {
        AbstractInsnNode preceding = callInsn.getPrevious();
        if (!(preceding instanceof VarInsnNode varInsn)
                || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 0) {
            return;
        }
        methodNode.instructions.set(preceding, new VarInsnNode(Opcodes.ALOAD, eventLocal));
        methodNode.instructions.set(callInsn, new FieldInsnNode(
                Opcodes.GETFIELD,
                MotionEvent.class.getName().replace(".", "/"),
                field,
                desc));
    }

    private static void redirectToEventPosition(MethodNode methodNode, MethodInsnNode callInsn, int eventLocal) {
        AbstractInsnNode preceding = callInsn.getPrevious();
        if (!(preceding instanceof VarInsnNode varInsn)
                || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 0) {
            return;
        }
        methodNode.instructions.set(preceding, new VarInsnNode(Opcodes.ALOAD, eventLocal));
        methodNode.instructions.set(callInsn, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(LocalPlayerPatch.class),
                "getEventPosition",
                "(L" + MotionEvent.class.getName().replace(".", "/") + ";)Lnet/minecraft/world/phys/Vec3;",
                false));
    }

    private static InsnList createPostMotionEvent() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "getX", "()D"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "getY", "()D"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "getZ", "()D"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "getYRot", "()F"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "getXRot", "()F"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(invokeGetter(Entity.class, "onGround", "()Z"));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(LocalPlayerPatch.class),
                "onMotion",
                "(DDDFFZZ)L" + MotionEvent.class.getName().replace(".", "/") + ";",
                false));
        instructions.add(new InsnNode(Opcodes.POP));
        return instructions;
    }
}
