package net.caffeinemc.mods.lithium.common.ai.brain;

import net.caffeinemc.mods.lithium.mixin.ai.useless_sensors.BrainAccessor;
import net.caffeinemc.mods.lithium.mixin.ai.useless_sensors.SensorAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class SensorHelper {

    public static void disableSensor(LivingEntity brainedEntity, SensorType<?> sensorType) {
        if (brainedEntity.level().isClientSide()) {
            return;
        }
        Brain<?> brain = brainedEntity.getBrain();
        Sensor<?> sensor = ((BrainAccessor<?>) brain).getSensors().get(sensorType);
        if (sensor instanceof SensorAccessor sensorAccessor) {
            //Disable the sensor by setting the maximum last sense time, which will make it count down almost forever
            // Removing the whole sensor could be an issue, since it may be serialized and used in a future version.

            //Instead of setting to Long.MAX_VALUE, we want to be able to recover the random offset of the sensor:
            long lastSenseTime = sensorAccessor.getTimeToTick();
            int senseInterval = sensorAccessor.getSenseInterval(); //Usual values: 20,40,80,200

            long maxMultipleOfSenseInterval = Long.MAX_VALUE - (Long.MAX_VALUE % senseInterval);
            maxMultipleOfSenseInterval -= senseInterval;
            maxMultipleOfSenseInterval += lastSenseTime;

            sensorAccessor.setTimeToTick(maxMultipleOfSenseInterval);
        }
    }

    public static <T extends LivingEntity, U extends Sensor<T>> void enableSensor(T brainedEntity, SensorType<U> sensorType) {
        enableSensor(brainedEntity, sensorType, false);
    }

    public static <T extends LivingEntity, U extends Sensor<T>> void enableSensor(T brainedEntity, SensorType<U> sensorType, boolean extraTick) {
        if (brainedEntity.level().isClientSide()) {
            return;
        }

        Brain<?> brain = brainedEntity.getBrain();
        //noinspection unchecked
        U sensor = (U) ((BrainAccessor<?>) brain).getSensors().get(sensorType);
        if (sensor instanceof SensorAccessor sensorAccessor) {
            long lastSenseTime = sensorAccessor.getTimeToTick();
            int senseInterval = sensorAccessor.getSenseInterval();

            //Recover the random offset of the sensor:
            if (lastSenseTime > senseInterval) {
                lastSenseTime = lastSenseTime % senseInterval;
                if (extraTick) {
                    ((SensorAccessor) sensor).setTimeToTick(0L);
                    sensor.tick((ServerLevel) brainedEntity.level(), brainedEntity);
                }
            }
            sensorAccessor.setTimeToTick(lastSenseTime);
        }
    }
}
