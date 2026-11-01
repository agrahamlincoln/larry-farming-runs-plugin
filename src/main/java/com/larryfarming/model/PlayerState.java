package com.larryfarming.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * What the farm-run planner knows about the account: skills, quests, varbits, and where the
 * player's items are.
 *
 * <p>Saved per RS profile and restored at login, because the bank and the seed vault are only
 * reported while open. A plan made before the player next opens their bank still needs to
 * know what is in it.
 *
 * <p>Written on the client thread. The maps the panel can read on the EDT are concurrent.
 */
public class PlayerState {
    @Getter
    private final Map<Skill, SkillState> skills;
    @Getter
    private final Map<String, QuestStatus> quests;
    @Getter
    private List<ItemStack> inventory;
    @Getter
    private final Map<EquipmentSlot, ItemStack> equipment;
    /** Quest stage counters, by quest name, for quests whose stage gates a route. */
    @Getter
    private final Map<String, Integer> questProgress;
    @Getter
    private List<ItemStack> bank;
    @Getter
    @Setter
    private Instant bankLastSeen;
    /** Seed vault contents, reported only when the player opens the vault in the Farming
     *  Guild. An established account keeps most seeds here rather than in the bank.
     *  Field-level init, with a null guard in the getter, so the list is never null whatever
     *  a save holds. */
    private List<ItemStack> seedVault = new ArrayList<>();
    /** When the seed vault was last observed, or null if never. Tells an empty vault apart
     *  from one this plugin has not been shown. */
    @Getter
    @Setter
    private Instant seedVaultLastSeen;
    /** Seed box contents. The box travels with the player, so its seeds count wherever the
     *  box itself is; see {@code SeedBox}.
     *  <p>This is a tally, not a snapshot: the game never sends inventory {@code SEED_BOX}
     *  (573), so it is summed from the chat lines that announce seeds going in and out.
     *  Field-level init, with a null guard in the getter, so the list is never null whatever
     *  a save holds. */
    private List<ItemStack> seedBox = new ArrayList<>();
    /** When the seed box was last heard about, or null if never. Tells an empty box apart
     *  from one nobody has looked in. */
    @Getter
    @Setter
    private Instant seedBoxLastSeen;
    /** Field-level init because Gson bypasses the constructor.
     *  Concurrent: written on the client thread, read on the EDT. */
    private Map<Integer, Integer> varbits = new ConcurrentHashMap<>();
    /** Rune pouch contents (rune item ID → quantity), read from the pouch varbits.
     *  Pouched runes leave the inventory item array, so rune counts add them back from here.
     *  Concurrent: written on the client thread, read on the EDT. */
    private Map<Integer, Integer> runePouch = new ConcurrentHashMap<>();

    /**
     * The map region the player is standing in, or null when unknown.
     *
     * <p>{@code transient} because it is a live reading. The planner reads it to decide a
     * teleport is already paid for, so a region restored from the last session would drop a
     * teleport from the packing list of a player who logged in elsewhere.
     */
    @Getter
    @Setter
    private transient Integer regionId;

    public PlayerState() {
        this.skills = new EnumMap<>(Skill.class);
        // Concurrent: the panel reads quest completion on the EDT while the client thread
        // writes it.
        this.quests = new ConcurrentHashMap<>();
        this.inventory = new ArrayList<>();
        this.equipment = new EnumMap<>(EquipmentSlot.class);
        this.questProgress = new ConcurrentHashMap<>();
        this.bank = new ArrayList<>();

        // Hitpoints starts at 10 / 1,154 XP; every other skill at 1 / 0.
        for (Skill skill : Skill.values()) {
            if (skill == Skill.HITPOINTS) {
                skills.put(skill, new SkillState(10, 1154));
            } else {
                skills.put(skill, new SkillState(1, 0));
            }
        }
    }

    public int getSkillLevel(Skill skill) {
        SkillState state = skills.get(skill);
        return state != null ? state.getLevel() : 1;
    }

    public void setInventory(List<ItemStack> inventory) { this.inventory = new ArrayList<>(inventory); }

    public Map<Integer, Integer> getVarbits() {
        // Gson may bypass both constructor and field initializers (via Unsafe.allocateInstance),
        // so a loaded save can leave this null.
        if (varbits == null) { varbits = new ConcurrentHashMap<>(); }
        return varbits;
    }

    /** Rune pouch contents (rune item ID → quantity). Never null. */
    public Map<Integer, Integer> getRunePouch() {
        // Same Gson null guard as getVarbits.
        if (runePouch == null) { runePouch = new ConcurrentHashMap<>(); }
        return runePouch;
    }

    /** Replaces the rune pouch contents with a freshly-read snapshot. */
    public void setRunePouch(Map<Integer, Integer> contents) {
        Map<Integer, Integer> next = new ConcurrentHashMap<>();
        if (contents != null) { next.putAll(contents); }
        this.runePouch = next;
    }

    public void setBank(List<ItemStack> bank) { this.bank = new ArrayList<>(bank); }

    public List<ItemStack> getSeedVault() {
        // Defensive: Gson writes a save's null straight into the field.
        return seedVault != null ? seedVault : (seedVault = new ArrayList<>());
    }
    public void setSeedVault(List<ItemStack> seedVault) { this.seedVault = new ArrayList<>(seedVault); }

    public List<ItemStack> getSeedBox() {
        // Defensive: Gson writes a save's null straight into the field.
        return seedBox != null ? seedBox : (seedBox = new ArrayList<>());
    }
    public void setSeedBox(List<ItemStack> seedBox) { this.seedBox = new ArrayList<>(seedBox); }

    /**
     * When the box was last <em>read</em> whole, as opposed to last heard about.
     *
     * <p>{@link #seedBoxLastSeen} moves whenever a chat line announces seeds going in or
     * out. That tally cannot correct an over-count, so its stamp says only that something
     * happened, not that the contents are true.
     *
     * <p>A whole-box statement sets this stamp. The item container never provides one:
     * inventory {@code SEED_BOX} (573) never fires. The Check option does: it lists the
     * contents in chat, in an empty wording and an itemised one, and either listing sets
     * this stamp.
     *
     * <p>{@code transient} because the listing vouches only for the box at the moment it was
     * read. A session that starts from a remembered tally has read nothing.
     */
    @Getter
    @Setter
    private transient Instant seedBoxVerifiedAt;

    /**
     * When seeds were last <em>watched</em> going into or out of the box, this session.
     *
     * <p>A chat line saying seeds went in is ground truth about that movement, so a tally
     * watched this session is right about at least what was watched. It cannot vouch for
     * what was in the box before. Unlike {@link #seedBoxVerifiedAt}, this stamp costs the
     * player no Check click.
     *
     * <p>{@code transient} by design: it is null on every restart, so a tally inherited from
     * a previous session earns nothing until the player moves seeds again.
     */
    @Getter
    @Setter
    private transient Instant seedBoxWatchedAt;

    /**
     * How many of each seed, by item id, were watched going into the box this session, less
     * those watched leaving it, never below zero.
     *
     * <p>A movement vouches only for what it moved. Storing guam seeds says nothing about the
     * whiteberry seeds a previous session left in the tally, and storing one whiteberry seed
     * says nothing about four already there. A seed leaving vouches for nothing: what stays
     * behind is the part nobody watched.
     *
     * <p>{@code transient}, and guarded in the getter because Gson bypasses field
     * initialisers.
     */
    private transient Map<Integer, Integer> seedBoxWatchedNet;

    public Map<Integer, Integer> getSeedBoxWatchedNet() {
        return seedBoxWatchedNet != null ? seedBoxWatchedNet : (seedBoxWatchedNet = new HashMap<>());
    }
}
