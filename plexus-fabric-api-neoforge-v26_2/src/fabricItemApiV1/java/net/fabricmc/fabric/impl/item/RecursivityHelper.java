/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC, Sinytra
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Source: Sinytra Forgified Fabric API, branch 26.2.x, commit 40f8ce0,
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/impl/item/RecursivityHelper.java (written in Forgified Fabric
// API).
// Poly Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.impl.item;

import java.util.function.Supplier;

public final class RecursivityHelper {
    public static final ThreadLocal<Boolean> FORGE_CALL = ThreadLocal.withInitial(() -> false);

    public static <T> T nonRecursiveApiCall(Supplier<T> supplier) {
        FORGE_CALL.set(true);
        T result = supplier.get();
        FORGE_CALL.set(false);
        return result;
    }

    public static boolean allowForgeCall() {
        return !FORGE_CALL.get();
    }

    private RecursivityHelper() {}
}
