package dev.sirbepy.mc.tag.task;

import dev.sirbepy.mc.tag.TagService;

public final class PointsTask implements Runnable {

    private final TagService service;

    public PointsTask(TagService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.pointsTick();
    }
}
