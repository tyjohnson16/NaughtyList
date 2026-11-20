package com.sk89q.worldguard.protection;

import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Collections;
import java.util.Iterator;

/** Compile-only stub. */
public interface ApplicableRegionSet extends Iterable<ProtectedRegion> {
    StateFlag.State queryState(RegionAssociable associable, StateFlag... flags);
    int size();
    @Override
    default Iterator<ProtectedRegion> iterator() { return Collections.emptyIterator(); }
}
