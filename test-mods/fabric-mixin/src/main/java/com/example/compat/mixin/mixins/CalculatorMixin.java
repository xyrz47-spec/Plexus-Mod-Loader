package com.example.compat.mixin.mixins;

import com.example.compat.mixin.Calculator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Mixin into the mod's own class: Mixin's {@code @ModifyArg} and MixinExtras' {@code @WrapOperation}. */
@Mixin(Calculator.class)
public abstract class CalculatorMixin {

    @ModifyArg(
            method = "sum",
            at = @At(value = "INVOKE", target = "Lcom/example/compat/mixin/Calculator;add(II)I"),
            index = 1)
    private static int fabricMixin$tenfold(int b) {
        return b * 10;
    }

    @WrapOperation(
            method = "product",
            at = @At(value = "INVOKE", target = "Lcom/example/compat/mixin/Calculator;multiply(II)I"))
    private static int fabricMixin$wrap(int a, int b, Operation<Integer> original) {
        return original.call(a, b) * 10;
    }
}
