package com.carryon.common;

import com.mongodb.MongoException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work in a MongoDB transaction and retries it when MongoDB reports a transient conflict.
 * Unlike PostgreSQL (where the second writer waits on a row lock), MongoDB aborts the loser with a
 * WriteConflict, so retrying the whole transaction is the documented, correct handling. Each retry re-reads
 * fresh state, so the loser then sees "trip full" or "already accepted" and fails with a clean 409.
 */
@Component
public class Tx {
    private static final int MAX_ATTEMPTS = 40;
    private final TransactionTemplate template;

    public Tx(PlatformTransactionManager tm) { this.template = new TransactionTemplate(tm); }

    public <T> T run(Supplier<T> work) {
        // Already inside a transaction: join it. Only the outermost caller may retry.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return work.get();
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (RuntimeException e) {
                if (attempt >= MAX_ATTEMPTS || !retryable(e)) throw e;
                try { Thread.sleep(ThreadLocalRandom.current().nextLong(2, 8L + attempt * 3L)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
            }
        }
    }

    public void runVoid(Runnable work) { run(() -> { work.run(); return null; }); }

    private static boolean retryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConcurrencyFailureException) return true;   // includes optimistic locking
            if (t instanceof MongoException m
                    && (m.hasErrorLabel("TransientTransactionError") || m.hasErrorLabel("UnknownTransactionCommitResult")
                        || m.getCode() == 112)) return true;
            if (t.getMessage() != null && t.getMessage().contains("WriteConflict")) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
