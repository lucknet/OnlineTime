/*
 * MIT License
 *
 * Copyright (c) 2019 Niklas Seyfarth
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mr.minecraft15.onlinetime.bungee;

import mr.minecraft15.onlinetime.api.PluginProxy;
import mr.minecraft15.onlinetime.common.AccumulatingOnlineTimeStorage;
import mr.minecraft15.onlinetime.common.StorageException;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.UUID;
import java.util.logging.Level;

public class OnlineTimeAccumulatorBungeeListener implements Listener {

    private final PluginProxy plugin;
    private final AccumulatingOnlineTimeStorage timeStorage;

    public OnlineTimeAccumulatorBungeeListener(PluginProxy plugin, AccumulatingOnlineTimeStorage timeStorage) {
        this.plugin = plugin;
        this.timeStorage = timeStorage;
    }

    @EventHandler
    public void onPlayerPostLogin(PostLoginEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final long now = System.currentTimeMillis();
        plugin.getScheduler().runAsyncOnce(() -> {
            try {
                timeStorage.registerOnlineTimeStart(uuid, now);
            } catch (StorageException ex) {
                plugin.getLogger().log(Level.WARNING, "could not start accumulating online time for player " + uuid, ex);
            }
        });
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final long now = System.currentTimeMillis();
        plugin.getScheduler().runAsyncOnce(() -> {
            try {
                timeStorage.saveOnlineTimeAfterDisconnect(uuid, now);
            } catch (StorageException ex) {
                plugin.getLogger().log(Level.WARNING, "error while stopping accumulation of online time for player " + uuid, ex);
            }
        });
    }
}
