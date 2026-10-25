package dev.polymodloader.game.layer.v26_2.entity;

import dev.polymodloader.api.content.entity.ProjectileDefinition;
import dev.polymodloader.api.content.entity.ProjectileHit;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.game.layer.v26_2.Views;
import java.util.Optional;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A mod projectile: a thrown item that calls the mod's behaviour on hit and then disappears. */
public final class PolyProjectile extends ThrowableItemProjectile {

    private static final byte HIT_EVENT = 3;

    PolyProjectile(EntityType<? extends PolyProjectile> type, Level level) {
        super(type, level);
    }

    private ProjectileDefinition definition() {
        return EntityInstaller.projectile(getType());
    }

    @Override
    protected Item getDefaultItem() {
        return Views.item(definition().item());
    }

    @Override
    protected double getDefaultGravity() {
        return definition().gravity();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (level() instanceof ServerLevel server) {
            float damage = definition().damage();
            if (damage > 0) {
                hitResult.getEntity().hurtServer(server, damageSources().thrown(this, getOwner()), damage);
            }
            definition()
                    .behavior()
                    .onHitEntity(hit(Optional.of(Views.entity(hitResult.getEntity())), Optional.empty()));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!level().isClientSide()) {
            definition().behavior().onHitBlock(hit(Optional.empty(), Optional.of(Views.pos(hitResult.getBlockPos()))));
        }
    }

    private ProjectileHit hit(Optional<EntityView> entity, Optional<dev.polymodloader.api.world.BlockPos> block) {
        return new ProjectileHit(
                Views.world(level()),
                Views.entity(this),
                Optional.ofNullable(getOwner()).map(Views::entity),
                entity,
                block);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!level().isClientSide()) {
            level().broadcastEntityEvent(this, HIT_EVENT);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == HIT_EVENT && !getItem().isEmpty()) {
            ItemParticleOption particle =
                    new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(getItem()));
            for (int i = 0; i < 8; i++) {
                level().addParticle(particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
