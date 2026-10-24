package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.client.mixin.AnimalModelAccessor;
import com.nakami.mcheadfunction.client.mixin.ModelPartAccessor;
import com.nakami.mcheadfunction.head.HeadShakeAccess;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.LivingEntity;

/** Adds a damped hit reaction after vanilla animation; restores shared models after drawing. */
public final class HeadShakeRender {
    private final Map<ModelPart, Pose> saved = new IdentityHashMap<>();

    public void apply(LivingEntity entity, EntityModel<?> model, float delta) {
        restore();
        float remaining = ((HeadShakeAccess) entity).mhf$headShakeTicks() - delta;
        if (remaining <= 0 || HeadlessAccess.isHeadless(entity)) return;
        if (model instanceof BipedEntityModel<?> biped) {
            save(biped.head);
            save(biped.hat);
        }
        if (model instanceof AnimalModelAccessor animal) animal.mhf$getHeadParts().forEach(this::save);
        if (model instanceof SinglePartEntityModel<?> single) collect(single.getPart());
        float elapsed = HeadShakeAccess.DURATION - remaining;
        float roll = (float) Math.sin((elapsed + 1) * 1.15) * 0.48F * remaining / HeadShakeAccess.DURATION;
        float yaw = (float) Math.sin((elapsed + 1) * 0.9) * 0.28F * remaining / HeadShakeAccess.DURATION;
        saved.forEach((part, pose) -> { part.roll = pose.roll + roll; part.yaw = pose.yaw + yaw; });
    }

    private void save(ModelPart part) { saved.putIfAbsent(part, new Pose(part.roll, part.yaw)); }

    private void collect(ModelPart root) {
        ((ModelPartAccessor) (Object) root).mhf$children().forEach((name, child) -> {
            if (name.contains("head") || name.equals("skull") || name.equals("hat")) save(child);
            else collect(child);
        });
    }

    public void restore() {
        saved.forEach((part, pose) -> { part.roll = pose.roll; part.yaw = pose.yaw; });
        saved.clear();
    }

    private record Pose(float roll, float yaw) { }
}
