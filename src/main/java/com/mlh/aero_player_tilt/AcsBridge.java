package com.mlh.aero_player_tilt;

import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.AeroCamSyncApi;

public final class AcsBridge {
    private AcsBridge() {}

    public static final AcsHandle ACS = AeroCamSyncApi.forMod(AeroPlayerTilt.MODID);
}
