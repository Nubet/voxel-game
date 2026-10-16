package com.norbertfila.commands;

import com.norbertfila.player.PlayerController;

public interface CommandSource {
    boolean setMode(PlayerController.Mode mode);

    int give(byte block, int count);
}
