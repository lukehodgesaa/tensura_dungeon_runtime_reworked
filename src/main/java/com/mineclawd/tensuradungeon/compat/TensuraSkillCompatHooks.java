package com.mineclawd.tensuradungeon.compat;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.event.ActiveRoomProtectionEvents;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompatStatus;
import com.mineclawd.tensuradungeon.runtime.DungeonReturnWhistleChannel;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class TensuraSkillCompatHooks {
    private static boolean initialized;
    private static boolean skillGriefHookRegistered;
    private static boolean incapacitationSkillHooksRegistered;
    private static String lastInitializationStatus = "NOT_INITIALIZED";
    private static int skillGriefEventsSeen;
    private static int skillGriefBlocked;
    private static String lastSkillGriefDecision = "NONE";

    private TensuraSkillCompatHooks() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerSkillGriefPreHook();
        registerIncapacitationSkillHooks();
    }

    private static void registerIncapacitationSkillHooks() {
        TensuraCompatStatus status = TensuraCompat.getStatus();
        if (status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return;
        }
        try {
            Class<?> skillEventsClass = Class.forName(
                "io.github.manasmods.manascore.skill.api.SkillEvents"
            );
            Class<?> eventClass = Class.forName("dev.architectury.event.Event");
            Class<?> eventResultClass = Class.forName("dev.architectury.event.EventResult");
            Method registerMethod = eventClass.getMethod("register", Object.class);
            Method interruptFalseMethod = eventResultClass.getMethod("interruptFalse");
            Method passMethod = eventResultClass.getMethod("pass");

            registerSkillGate(skillEventsClass, registerMethod, interruptFalseMethod, passMethod,
                "ACTIVATE_SKILL", "SkillActivationEvent");
            registerSkillGate(skillEventsClass, registerMethod, interruptFalseMethod, passMethod,
                "TOGGLE_SKILL", "SkillToggleEvent");
            registerSkillGate(skillEventsClass, registerMethod, interruptFalseMethod, passMethod,
                "SKILL_PRE_TICK", "SkillTickEvent");
            incapacitationSkillHooksRegistered = true;
        } catch (ClassNotFoundException ex) {
            lastInitializationStatus = "MISSING_SKILL_GATE_DEPENDENCY:" + ex.getMessage();
        } catch (ReflectiveOperationException ex) {
            lastInitializationStatus = "SKILL_GATE_REFLECTION_FAILED:"
                + ex.getClass().getSimpleName();
        } catch (RuntimeException ex) {
            lastInitializationStatus = "SKILL_GATE_RUNTIME_FAILED:"
                + ex.getClass().getSimpleName();
        } catch (LinkageError ex) {
            lastInitializationStatus = "SKILL_GATE_LINKAGE_FAILED:"
                + ex.getClass().getSimpleName();
        }
    }

    private static void registerSkillGate(
        Class<?> skillEventsClass,
        Method registerMethod,
        Method interruptFalseMethod,
        Method passMethod,
        String eventField,
        String listenerName
    ) throws ReflectiveOperationException {
        Class<?> listenerClass = Class.forName(skillEventsClass.getName() + "$" + listenerName);
        Object event = skillEventsClass.getField(eventField).get(null);
        InvocationHandler handler = (proxy, method, args) -> handleSkillGateInvocation(
            args, interruptFalseMethod, passMethod, !"SKILL_PRE_TICK".equals(eventField)
        );
        Object listener = Proxy.newProxyInstance(
            listenerClass.getClassLoader(), new Class<?>[] { listenerClass }, handler
        );
        registerMethod.invoke(event, listener);
    }

    private static Object handleSkillGateInvocation(
        Object[] args,
        Method interruptFalseMethod,
        Method passMethod,
        boolean interruptsWhistle
    ) throws ReflectiveOperationException {
        if (args != null) {
            for (Object arg : args) {
                if (interruptsWhistle && arg instanceof net.minecraft.server.level.ServerPlayer player) {
                    DungeonReturnWhistleChannel.interruptIfChanneling(
                        player, "a skill was activated"
                    );
                }
                if (arg instanceof LivingEntity entity
                    && entity.level() instanceof ServerLevel level
                    && RoomControllerBlockEntity.isPlayerGravityIncapacitated(level, entity)) {
                    return interruptFalseMethod.invoke(null);
                }
            }
        }
        return passMethod.invoke(null);
    }

    private static void registerSkillGriefPreHook() {
        TensuraCompatStatus status = TensuraCompat.getStatus();
        if (status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            lastInitializationStatus = status.integrationState().name();
            return;
        }
        try {
            Class<?> skillGriefEventClass = Class.forName("io.github.manasmods.tensura.event.TensuraSkillEvents$SkillGriefEvent");
            Class<?> tensuraSkillEventsClass = Class.forName("io.github.manasmods.tensura.event.TensuraSkillEvents");
            Class<?> eventClass = Class.forName("dev.architectury.event.Event");
            Class<?> eventResultClass = Class.forName("dev.architectury.event.EventResult");
            Object skillGriefPreEvent = tensuraSkillEventsClass.getField("SKILL_GRIEF_PRE").get(null);
            Method registerMethod = eventClass.getMethod("register", Object.class);
            Method interruptFalseMethod = eventResultClass.getMethod("interruptFalse");
            Method passMethod = eventResultClass.getMethod("pass");

            InvocationHandler handler = (proxy, method, args) -> handleSkillGriefInvocation(
                method,
                args,
                interruptFalseMethod,
                passMethod
            );
            Object listener = Proxy.newProxyInstance(
                skillGriefEventClass.getClassLoader(),
                new Class<?>[] { skillGriefEventClass },
                handler
            );
            registerMethod.invoke(skillGriefPreEvent, listener);
            skillGriefHookRegistered = true;
            lastInitializationStatus = "REGISTERED";
        } catch (ClassNotFoundException ex) {
            lastInitializationStatus = "MISSING_DEPENDENCY:" + ex.getMessage();
        } catch (ReflectiveOperationException ex) {
            lastInitializationStatus = "REFLECTION_FAILED:" + ex.getClass().getSimpleName();
        } catch (RuntimeException ex) {
            lastInitializationStatus = "RUNTIME_FAILED:" + ex.getClass().getSimpleName();
        } catch (LinkageError ex) {
            lastInitializationStatus = "LINKAGE_FAILED:" + ex.getClass().getSimpleName();
        }
    }

    private static Object handleSkillGriefInvocation(
        Method method,
        Object[] args,
        Method interruptFalseMethod,
        Method passMethod
    ) throws ReflectiveOperationException {
        if (!"grief".equals(method.getName()) || args == null || args.length < 6) {
            return passMethod.invoke(null);
        }

        if (!(args[1] instanceof Level level)) {
            return passMethod.invoke(null);
        }

        skillGriefEventsSeen++;
        BlockPos targetPos = BlockPos.containing(
            ((Double) args[3]).doubleValue(),
            ((Double) args[4]).doubleValue(),
            ((Double) args[5]).doubleValue()
        );
        if (!ActiveRoomProtectionEvents.isBlockProtected(level, targetPos)) {
            lastSkillGriefDecision = "PASS_UNPROTECTED";
            return passMethod.invoke(null);
        }

        skillGriefBlocked++;
        lastSkillGriefDecision = "BLOCK_DIRECT_PROTECTED";
        return interruptFalseMethod.invoke(null);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isSkillGriefHookRegistered() {
        return skillGriefHookRegistered;
    }

    public static boolean areIncapacitationSkillHooksRegistered() {
        return incapacitationSkillHooksRegistered;
    }

    public static String getLastInitializationStatus() {
        return lastInitializationStatus;
    }

    public static int getSkillGriefEventsSeen() {
        return skillGriefEventsSeen;
    }

    public static int getSkillGriefBlocked() {
        return skillGriefBlocked;
    }

    public static String getLastSkillGriefDecision() {
        return lastSkillGriefDecision;
    }
}
