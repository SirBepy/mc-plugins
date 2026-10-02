package dev.sirbepy.mc.tag.task;

import dev.sirbepy.mc.tag.TagService;

/** Catches a head removed by `/clear` or similar and puts it back on. */
public final class HeadWatchdogTask implements Runnable {

    private final TagService service;

    public HeadWatchdogTask(TagService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.ensureHeadWorn();
    }
}
