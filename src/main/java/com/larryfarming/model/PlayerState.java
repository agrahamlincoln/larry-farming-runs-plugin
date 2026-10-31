package com.larryfarming.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<Skill, SkillState> skills;
    private final Map<String, QuestStatus> quests;
    /**
     * Run energy, in hundredths of a percent: 0 to 10,000, not 0 to 100. This is the
     * client's own unit; {@code EnergyTopUp} does the conversion.
     *
     * <p><b>{@link #RUN_ENERGY_UNREAD} until the orb has been read, which is not zero.</b>
     * Zero is a real reading (an empty bar), so it cannot double as "not read yet". If it
     * did, an empty bar would get no potion offer and the plugin would push a fresh reading
     * every tick.
     *
     * <p>{@code transient} because it is a live reading. A value restored on login would
     * read as known and suppress the first real push.
     */
    private transient int runEnergy = RUN_ENERGY_UNREAD;

    /**
     * Whether the bar has run low enough to pack a top-up, held until it has recovered
     * well past that. {@code EnergyTopUp.lowAfter} moves it. Without the gap, a bar
     * hovering at the line would add and drop the potion with every step.
     */
    private transient boolean runEnergyLow;

    /** Run energy we have not read yet, told apart from an empty bar. */
    public static final int RUN_ENERGY_UNREAD = -1;

    private List<ItemStack> inventory;
    private final Map<EquipmentSlot, ItemStack> equipment;
    /** Quest stage counters, by quest name, for quests whose stage gates a route. */
    private final Map<String, Integer> questProgress;
    private List<ItemStack> bank;
    private Instant bankLastSeen;
    /** Seed vault contents, reported only when the player opens the vault in the Farming
     *  Guild. An established account keeps most seeds here rather than in the bank.
     *  Field-level init, with a null guard in the getter, so the list is never null whatever
     *  a save holds. */
    private List<ItemStack> seedVault = new ArrayList<>();
    /** When the seed vault was last observed, or null if never. Tells an empty vault apart
     *  from one this plugin has not been shown. */
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

    public Map<Skill, SkillState> getSkills() { return skills; }

    public Map<String, QuestStatus> getQuests() { return quests; }

    /** Run energy in hundredths of a percent, or {@link #RUN_ENERGY_UNREAD}. See the field. */
    public int getRunEnergy() { return runEnergy; }
    public void setRunEnergy(int runEnergy) { this.runEnergy = runEnergy; }

    public boolean isRunEnergyLow() { return runEnergyLow; }
    public void setRunEnergyLow(boolean runEnergyLow) { this.runEnergyLow = runEnergyLow; }

    public List<ItemStack> getInventory() { return inventory; }
    public void setInventory(List<ItemStack> inventory) { this.inventory = new ArrayList<>(inventory); }

    public Map<EquipmentSlot, ItemStack> getEquipment() { return equipment; }

    public Map<String, Integer> getQuestProgress() { return questProgress; }

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

    public List<ItemStack> getBank() { return bank; }
    public void setBank(List<ItemStack> bank) { this.bank = new ArrayList<>(bank); }

    public Instant getBankLastSeen() { return bankLastSeen; }
    public void setBankLastSeen(Instant bankLastSeen) { this.bankLastSeen = bankLastSeen; }

    public List<ItemStack> getSeedVault() {
        // Defensive: Gson writes a save's null straight into the field.
        return seedVault != null ? seedVault : (seedVault = new ArrayList<>());
    }
    public void setSeedVault(List<ItemStack> seedVault) { this.seedVault = new ArrayList<>(seedVault); }

    public Instant getSeedVaultLastSeen() { return seedVaultLastSeen; }
    public void setSeedVaultLastSeen(Instant seen) { this.seedVaultLastSeen = seen; }

    public List<ItemStack> getSeedBox() {
        // Defensive: Gson writes a save's null straight into the field.
        return seedBox != null ? seedBox : (seedBox = new ArrayList<>());
    }
    public void setSeedBox(List<ItemStack> seedBox) { this.seedBox = new ArrayList<>(seedBox); }

    public Instant getSeedBoxLastSeen() { return seedBoxLastSeen; }
    public void setSeedBoxLastSeen(Instant seen) { this.seedBoxLastSeen = seen; }

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
    private transient Instant seedBoxVerifiedAt;

    public Instant getSeedBoxVerifiedAt() { return seedBoxVerifiedAt; }
    public void setSeedBoxVerifiedAt(Instant seen) { this.seedBoxVerifiedAt = seen; }

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
    private transient Instant seedBoxWatchedAt;

    public Instant getSeedBoxWatchedAt() { return seedBoxWatchedAt; }
    public void setSeedBoxWatchedAt(Instant seen) { this.seedBoxWatchedAt = seen; }

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
        return seedBoxWatchedNet != null ? seedBoxWatchedNet
                : (seedBoxWatchedNet = new HashMap<>());
    }

    /**
     * When each farming contract was handed in, oldest first, over the last
     * {@link ContractTally#WINDOW_DAYS} days, and since when hand-ins have been watched.
     * Persisted, so the count spans sessions.
     */
    private List<Instant> contractsHandedIn = new ArrayList<>();
    private Instant contractsWatchedSince;

    public List<Instant> getContractsHandedIn() {
        return contractsHandedIn != null ? contractsHandedIn : List.of();
    }

    public void setContractsHandedIn(List<Instant> handedIn) {
        this.contractsHandedIn = new ArrayList<>(handedIn);
    }

    public Instant getContractsWatchedSince() { return contractsWatchedSince; }
    public void setContractsWatchedSince(Instant since) { this.contractsWatchedSince = since; }

    /**
     * Contracts handed in a day on average, or null until enough days have been watched.
     * Worked out by the plugin from the hand-ins against the clock, which the planner does
     * not read.
     */
    private transient Double contractsPerDay;

    public Double getContractsPerDay() { return contractsPerDay; }
    public void setContractsPerDay(Double perDay) { this.contractsPerDay = perDay; }

    public Integer getRegionId() { return regionId; }
    public void setRegionId(Integer regionId) { this.regionId = regionId; }
}
