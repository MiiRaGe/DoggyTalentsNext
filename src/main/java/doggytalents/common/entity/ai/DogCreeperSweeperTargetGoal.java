package doggytalents.common.entity.ai;

import doggytalents.DoggyTalents;
import doggytalents.common.entity.Dog;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Lets an Aggressive dog which mastered Creeper Sweeper actively hunt Creepers
 * near its owner. Creepers never hurt the owner until they explode, so the
 * reactive owner-hurt goals would otherwise never pick them up.
 */
public class DogCreeperSweeperTargetGoal extends DogNearestToOwnerAttackableTargetGoal<Creeper> {

    private final Dog dog;

    public DogCreeperSweeperTargetGoal(Dog dog) {
        super(dog, Creeper.class, false);
        this.dog = dog;
    }

    @Override
    public boolean canUse() {
        if (this.dog.getDogLevel(DoggyTalents.CREEPER_SWEEPER) < 5)
            return false;
        return super.canUse();
    }
}
