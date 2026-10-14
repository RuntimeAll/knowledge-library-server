package com.ruoyi.knowledge.importing;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Database-clock leases plus per-item transactional fencing prevent concurrent resume. */
@Component
public class KbImportLeaseManager {
    private final KbImportMapper mapper;
    private final ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "kb-import-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public KbImportLeaseManager(KbImportMapper mapper) {
        this.mapper = mapper;
    }

    public Lease acquire(KbImportBatch batch) {
        String token = UUID.randomUUID().toString();
        if (mapper.claim(batch.getBatchId(), token) != 1) {
            throw new ServiceException("批次仍在执行或已完成，不能抢占；请先查询批次状态", 409);
        }
        batch.setClaimToken(token);
        return new Lease(batch.getBatchId(), token);
    }

    @PreDestroy
    public void close() {
        heartbeat.shutdownNow();
    }

    public final class Lease implements AutoCloseable {
        private final Long batchId;
        private final String token;
        private final ScheduledFuture<?> scheduled;
        private volatile boolean lost;

        private Lease(Long batchId, String token) {
            this.batchId = batchId;
            this.token = token;
            scheduled = heartbeat.scheduleAtFixedRate(this::renew, 20, 20, TimeUnit.SECONDS);
        }

        private void renew() {
            try {
                if (mapper.renewClaim(batchId, token) != 1) lost = true;
            } catch (RuntimeException error) {
                // Fail closed. A subsequent owner may resume after the database lease expires.
                lost = true;
            }
        }

        public void check() {
            if (lost) throw new ServiceException("导入执行租约已失效，请查询后恢复批次", 409);
        }

        @Override
        public void close() {
            scheduled.cancel(false);
            mapper.releaseClaim(batchId, token);
        }
    }
}
