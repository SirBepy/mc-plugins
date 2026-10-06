package dev.sirbepy.mc.cartel.phone;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneListenerTest extends CartelTestBase {

    private Phone phone;
    private PhoneListener listener;
    private final List<Player> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        phone = new Phone(plugin);
        listener = new PhoneListener(phone, () -> config, store, opened::add);
    }

    private PlayerInteractEvent rightClickAir(Player player, ItemStack held) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
    }

    @Test
    void theOwnerOpensThePhoneMenu() {
        ItemStack item = phone.create();
        owner.getInventory().setItemInMainHand(item);

        PlayerInteractEvent event = rightClickAir(owner, item);
        listener.onUse(event);

        assertEquals(List.of(owner), opened);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void anyoneElseGetsADeadLine() {
        ItemStack item = phone.create();

        listener.onUse(rightClickAir(victim, item));

        assertTrue(opened.isEmpty());
        assertTrue(chat(victim).contains("The line is dead. This phone isn't yours."));
    }

    @Test
    void aPlainClockDoesNothing() {
        listener.onUse(rightClickAir(owner, new ItemStack(Material.CLOCK)));

        assertTrue(opened.isEmpty());
    }

    @Test
    void thePhoneCantBeThrownAway() {
        Item dropped = world.dropItem(owner.getLocation(), phone.create());
        PlayerDropItemEvent event = new PlayerDropItemEvent(owner, dropped);

        listener.onDrop(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void nobodyElseCanPickItUp() {
        Item lying = world.dropItem(victim.getLocation(), phone.create());

        EntityPickupItemEvent byVictim = new EntityPickupItemEvent(victim, lying, 0);
        listener.onPickup(byVictim);
        assertTrue(byVictim.isCancelled());

        EntityPickupItemEvent byOwner = new EntityPickupItemEvent(owner, lying, 0);
        listener.onPickup(byOwner);
        assertFalse(byOwner.isCancelled());
    }

    @Test
    void thePhoneIsKeptThroughDeath() {
        ItemStack item = phone.create();
        ItemStack dirt = new ItemStack(Material.DIRT);
        List<ItemStack> drops = new ArrayList<>(List.of(item, dirt));
        PlayerDeathEvent event = new PlayerDeathEvent(owner, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, null);

        listener.onDeath(event);

        assertEquals(List.of(dirt), event.getDrops());
        assertTrue(event.getItemsToKeep().stream().anyMatch(phone::isPhone));
    }

    @Test
    void theOwnerGetsThePhoneOnTheirFirstJoinOnly() {
        listener.onJoin(new PlayerJoinEvent(owner, net.kyori.adventure.text.Component.empty()));
        listener.onJoin(new PlayerJoinEvent(owner, net.kyori.adventure.text.Component.empty()));
        listener.onJoin(new PlayerJoinEvent(victim, net.kyori.adventure.text.Component.empty()));

        long phones = java.util.Arrays.stream(owner.getInventory().getContents()).filter(phone::isPhone).count();
        assertEquals(1, phones);
        assertTrue(store.phoneGiven(owner.getUniqueId()));
        assertFalse(java.util.Arrays.stream(victim.getInventory().getContents()).anyMatch(phone::isPhone));
    }

    @Test
    void thePhoneIsAMarkedClockWithThePackLook() {
        ItemStack item = phone.create();

        assertEquals(Material.CLOCK, item.getType());
        assertTrue(phone.isPhone(item));
        assertTrue(item.getItemMeta().getCustomModelDataComponent().getStrings().contains(Phone.MODEL));
        assertFalse(phone.isPhone(new ItemStack(Material.CLOCK)));
    }
}
