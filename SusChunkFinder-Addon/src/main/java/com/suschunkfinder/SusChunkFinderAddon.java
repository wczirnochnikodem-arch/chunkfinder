/*
 * This file is part of the Sus Chunk Finder addon.
 * Copyright (c) SusChunkFinder.
 */

package com.suschunkfinder;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.suschunkfinder.modules.SusChunkFinder;

public class SusChunkFinderAddon extends MeteorAddon {
    @Override
    public void init() {
        Modules.get().add(new SusChunkFinder());
    }
}
