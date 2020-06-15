package io.github.apace100.origins.mixin;

import io.github.apace100.origins.component.OriginComponent;
import io.github.apace100.origins.power.ModDamageSources;
import io.github.apace100.origins.power.ModifyDamageDealtPower;
import io.github.apace100.origins.power.PowerTypes;
import io.github.apace100.origins.power.VariableIntPower;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Nameable;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements Nameable, CommandOutput {

    @Shadow public abstract boolean damage(DamageSource source, float amount);

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    // ModifyDamageDealt
    @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), name = "f", ordinal = 0)
    public float modifyDamage(float f) {
        OriginComponent component = ModComponents.ORIGIN.get(this);
        DamageSource source = DamageSource.player((PlayerEntity)(Object)this);
        for (ModifyDamageDealtPower p : component.getPowers(ModifyDamageDealtPower.class)) {
            if (p.doesApply(source)) {
                f = p.apply(f);
            }
        }
        return f;
    }

    // NO_COBWEB_SLOWDOWN
    @Inject(at = @At("HEAD"), method = "slowMovement", cancellable = true)
    public void slowMovement(BlockState state, Vec3d multiplier, CallbackInfo info) {
        if (PowerTypes.NO_COBWEB_SLOWDOWN.isActive(this)) {
            info.cancel();
        }
    }

    // AQUA_AFFINITY
    @ModifyConstant(method = "getBlockBreakingSpeed", constant = @Constant(ordinal = 0, floatValue = 5.0F))
    private float modifyBlockBreakingSpeed(float in) {
        if(PowerTypes.AQUA_AFFINITY.isActive(this)) {
            return 1F;
        }
        return in;
    }

    // WATER_BREATHING
    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo info) {
        if(PowerTypes.WATER_VULNERABILITY.isActive(this) && this.isWet()) {
            VariableIntPower waterCounter = PowerTypes.WATER_VULNERABILITY.get(this);
            if(waterCounter.getValue() <= 0) {
                waterCounter.setValue(waterCounter.getMax());
                this.damage(ModDamageSources.HURT_BY_WATER, world.getDifficulty() == Difficulty.EASY ? 1.0F : 2.0F);
            } else {
                waterCounter.decrement();
            }
        }
        if(PowerTypes.WATER_BREATHING.isActive(this)) {
            if(!this.isSubmergedIn(FluidTags.WATER)) {
                int landGain = this.getNextAirOnLand(0);
                this.setAir(this.getNextAirUnderwater(this.getAir()) - landGain);
                if (this.getAir() == -20) {
                    this.setAir(0);
                    Vec3d vec3d = this.getVelocity();

                    for(int i = 0; i < 8; ++i) {
                        double f = this.random.nextDouble() - this.random.nextDouble();
                        double g = this.random.nextDouble() - this.random.nextDouble();
                        double h = this.random.nextDouble() - this.random.nextDouble();
                        this.world.addParticle(ParticleTypes.BUBBLE, this.getX() + f, this.getY() + g, this.getZ() + h, vec3d.x, vec3d.y, vec3d.z);
                    }

                    this.damage(ModDamageSources.NO_WATER_FOR_GILLS, 2.0F);
                }
            } else if(this.getAir() < this.getMaxAir()){
                this.setAir(this.getNextAirOnLand(this.getAir()));
            }
        }
    }
}