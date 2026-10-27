package com.ruoyi.knowledge.review;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** Single-instance quality gate. Always acquired before any transaction or database row lock. */
@Component
public class KbReviewGate {
    private final ReentrantLock lock = new ReentrantLock(true);
    public void enter() { lock.lock(); }
    public void leave() { lock.unlock(); }
    public boolean held() { return lock.isHeldByCurrentThread(); }
}
