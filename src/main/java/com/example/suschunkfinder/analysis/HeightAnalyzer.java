package com.example.suschunkfinder.analysis;

import com.example.suschunkfinder.data.Model.Zone;
import com.example.suschunkfinder.Finder;

/** Strefa tylko KLASYFIKUJE trafienie - nie ogranicza zakresu skanu. */
public final class HeightAnalyzer {
    private HeightAnalyzer() {}

    public static Zone zone(int y, Finder m) {
        if (y < m.undergroundMaxY.get()) return Zone.UNDERGROUND;
        if (y <= m.surfaceMaxY.get()) return Zone.SURFACE;
        return Zone.ABOVE_Y100;
    }

    public static boolean enabled(Zone z, Finder m) {
        return switch (z) {
            case UNDERGROUND -> m.scanUnderground.get();
            case SURFACE -> m.scanSurface.get();
            case ABOVE_Y100 -> m.scanAbove100.get();
        };
    }
}
