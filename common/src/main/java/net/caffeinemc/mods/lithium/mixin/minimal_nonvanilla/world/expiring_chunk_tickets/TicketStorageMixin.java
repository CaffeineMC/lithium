package net.caffeinemc.mods.lithium.mixin.minimal_nonvanilla.world.expiring_chunk_tickets;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin {

    @SuppressWarnings("ShadowModifiers")
    @Shadow
    @Final
    public Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Unique
    private final Long2ObjectOpenHashMap<List<Ticket>> positionsWithExpiringTicket = new Long2ObjectOpenHashMap<>();

    @Unique
    private static boolean canNoneExpire(List<Ticket> tickets) {
        if (!tickets.isEmpty()) {
            for (Ticket ticket : tickets) {
                if (ticket.getType().hasTimeout()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Mark all locations that have tickets that can expire as such. Allows iterating only over locations with
     * tickets that can expire when purging expired tickets.
     */
    @Inject(
            method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z")
    )
    private void registerExpiringTicket(long position, Ticket ticket, CallbackInfoReturnable<Boolean> cir, @Local List<Ticket> ticketsAtPos) {
        if (ticket.getType().hasTimeout()) {
            this.positionsWithExpiringTicket.put(position, ticketsAtPos);
        }
    }

    @Inject(
            method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z")
    )
    private void unregisterExpiringTicket(long position, Ticket ticket, CallbackInfoReturnable<Boolean> cir, @Local List<Ticket> ticketsAtPos) {
        if (ticket.getType().hasTimeout()) {
            if (canNoneExpire(ticketsAtPos)) {
                this.positionsWithExpiringTicket.remove(position);
            }
        }
    }


    @ModifyReceiver(method = "removeTicketIf(Ljava/util/function/Predicate;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
            at = @At(
                    value = "INVOKE", remap = false,
                    target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;long2ObjectEntrySet()Lit/unimi/dsi/fastutil/longs/Long2ObjectMap$FastEntrySet;"
            )
    )
    private Long2ObjectOpenHashMap<List<Ticket>> getExpiringTicketsByPosition(Long2ObjectOpenHashMap<List<Ticket>> allTicketPositions, @Local(argsOnly = true) Long2ObjectOpenHashMap<List<Ticket>> nullIfTickingDownTickets) {
        return nullIfTickingDownTickets == null ? this.positionsWithExpiringTicket : allTicketPositions;
    }

    @ModifyExpressionValue(method = "removeTicketIf(Ljava/util/function/Predicate;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;isEmpty()Z"
            )
    )
    private boolean removeIfNoTickables(boolean isEmpty, @Local Long2ObjectMap.Entry<List<Ticket>> entry, @Local(argsOnly = true) Long2ObjectOpenHashMap<List<Ticket>> nullIfTickingDownTickets, @Local ObjectIterator<Long2ObjectMap.Entry<List<Ticket>>> objectIterator) {
        if (!isEmpty) {
            if (canNoneExpire(entry.getValue())) {
                if (nullIfTickingDownTickets == null) {
                    objectIterator.remove();
                } else {
                    this.positionsWithExpiringTicket.remove(entry.getLongKey());
                }
            }
        }
        return isEmpty;
    }

    @Inject(method = "removeTicketIf(Ljava/util/function/Predicate;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectIterator;remove()V"
            )
    )
    private void fixOtherCollection(CallbackInfo ci, @Local Long2ObjectMap.Entry<List<Ticket>> entry, @Local(argsOnly = true) Long2ObjectOpenHashMap<List<Ticket>> nullIfTickingDownTickets) {
        if (nullIfTickingDownTickets == null) {
            this.tickets.remove(entry.getLongKey());
        } else {
            this.positionsWithExpiringTicket.remove(entry.getLongKey());
        }
    }
}
