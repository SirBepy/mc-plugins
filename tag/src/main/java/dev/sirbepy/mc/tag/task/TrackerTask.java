package dev.sirbepy.mc.tag.task;

import dev.sirbepy.mc.tag.TagService;

/** Re-points IT's tracker compass and keeps its action bar readout from fading (~3 s) while held. */
public final class TrackerTask implements Runnable {

    private final TagService service;

    public TrackerTask(TagService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.trackerTick();
    }
}
