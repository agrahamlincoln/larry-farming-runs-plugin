/*
 * Copyright (c) 2019 Abex
 * Copyright (c) 2026, agrahamlincoln
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.larryfarming.engine.farming;

import com.larryfarming.data.Tables;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every kind of farming patch the plugin tracks, and the table that turns a patch's
 * transmit-varbit byte into a {@link DecodedPatch}.
 *
 * <p>OSRS encodes the full state of a nearby patch — crop species, health, and
 * growth stage — as a single value in the range 0-255. The mapping is dense and
 * irregular (weed filler ranges, one-off "check health" states, and gaps that
 * decode to nothing), so the authoritative source is RuneLite's Time Tracking
 * plugin ({@code PatchImplementation}); every boundary in {@code patch-states.tsv}
 * is transcribed from its per-type {@code forVarbitValue} case.
 *
 * <p>Declaration order is the order {@link FarmingWorldMap#allPatches()} groups patches in.
 */
@Getter
@RequiredArgsConstructor
public enum PatchType {
    HERB(false),
    TREE(true),
    HARDWOOD_TREE(true),
    ALLOTMENT(false),
    FLOWER(false),
    HOPS(false),
    BUSH(true),
    FRUIT_TREE(true),
    SEAWEED(false),
    CACTUS(true),
    MUSHROOM(false),
    BELLADONNA(false),
    CALQUAT(true),
    CELASTRUS(true),
    REDWOOD(true),
    CRYSTAL_TREE(true),
    SPIRIT_TREE(true),
    HESPORI(true),
    ANIMA(false),
    GRAPES(true),
    CORAL(false),
    COMPOST(true),
    BIG_COMPOST(true);

    private static final int VALUES = 256;

    /** Each type's 256 decoded states, by ordinal; a null slot decodes to nothing. */
    private static final DecodedPatch[][] STATES = loadStates();

    /**
     * Whether harvest needs a Check-health click first. For such patches the
     * check-health state decodes as GROWING at the crop's max stage.
     */
    private final boolean healthCheckRequired;

    /** Decode a patch transmit-varbit byte; null when the value is not a known state. */
    public DecodedPatch decode(int varbitValue) {
        return varbitValue < 0 || varbitValue >= VALUES ? null : STATES[ordinal()][varbitValue];
    }

    /**
     * Expands {@code patch-states.tsv}: each row gives values {@code lo..hi} of one type a
     * crop, a state and whether a tree stands there, with the stage at value {@code v} being
     * {@code stage + step * (v - lo)}.
     */
    private static DecodedPatch[][] loadStates() {
        DecodedPatch[][] states = new DecodedPatch[values().length][VALUES];
        for (Tables.Row row : Tables.read("/com/larryfarming/patch-states.tsv", 8)) {
            DecodedPatch[] into = states[row.constant(PatchType.class, 0).ordinal()];
            int lo = row.integer(1);
            int hi = row.integer(2);
            if (lo < 0 || hi >= VALUES || lo > hi) {
                throw row.error("values " + lo + ".." + hi + " are not within 0-255");
            }
            Crop crop = row.constant(Crop.class, 3);
            CropState state = row.constant(CropState.class, 4);
            int stage = row.integer(5);
            int step = row.integer(6);
            boolean fellable = row.flag(7, "fell");
            for (int v = lo; v <= hi; v++) {
                if (into[v] != null) {
                    throw row.error("value " + v + " is already decoded by an earlier row");
                }
                into[v] = new DecodedPatch(crop, state, stage + step * (v - lo), fellable);
            }
        }
        return states;
    }
}
