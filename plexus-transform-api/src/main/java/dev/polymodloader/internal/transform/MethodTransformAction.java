package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * What a transformation rule does to its target method. Each action carries its own precondition,
 * which the planner checks against the real bytecode before any class is loaded. The set of actions
 * is closed so every action has a reviewed implementation in the engine.
 *
 * <p>Several rules can change one method when they declare their order ({@link
 * TransformRuleDefinition#after()}). The engine then applies call redirects first, then call observers, then return
 * hooks, then head hooks, so that the inserted code runs in the declared order: head hooks run first in
 * declared order, and return hooks run in declared order, each receiving the previous one's result.
 * A head hook that cancels or returns a value skips the return hooks. The two constant-replacing
 * actions depend on the whole body's shape and cannot share a method with any other rule.
 */
@ApiStability(StabilityLevel.INTERNAL)
public sealed interface MethodTransformAction
        permits ReplaceReturnedStringConstant,
                ReplaceConstantReturnWithStaticCall,
                InjectStaticCallAtHead,
                InvokeHookAtHead,
                InvokeHookBeforeReturn,
                RedirectCall,
                InvokeHookAtCall {

    /** Human-readable summary for diagnostics and transformation reports. */
    String describe();
}
