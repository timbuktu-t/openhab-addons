package org.openhab.binding.becker.internal.socket;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.becker.internal.handler.BeckerBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// becker requires Origin header or will report 400 Bad Request
// becker uses binary subprotocol and encodes with UTF-8 and null-termination
// socket may receive multiple responses in one method invocation

@NonNullByDefault
final class BeckerSocketQueue {

    private final Logger logger = LoggerFactory.getLogger(BeckerSocketQueue.class);
    private final SortedMap<Long, @Nullable Entry<?>> entries = new TreeMap<>();

    private final BeckerBridgeHandler bridgeHandler;

    private long lastId = -1L;

    BeckerSocketQueue(BeckerBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }

    synchronized <R extends BeckerCommand.Result> Entry<R> enqueue(BeckerCommand<R> command) {
        while (entries.size() >= bridgeHandler.config().queueCapacity) {
            evict(entries.firstKey(), new CancellationException("Cancelled due to queue capacity"));
        }
        Entry<R> entry = new Entry<>(command);
        entries.put(entry.id, entry);
        logger.debug("Enqueued command {} with id {}", command, entry.id);
        return entry;
    }

    synchronized @Nullable Entry<?> dequeue(long id) {
        Entry<?> entry = entries.remove(id);
        if (entry != null) {
            logger.debug("Dequeued command {} with id {}", entry.command, id);
            return entry;
        }
        logger.debug("Command with id {} was missing from queue", id);
        return null;
    }

    // map is sorted so abort when above minimum age

    synchronized void evictDated() {
        if (entries.size() > 0) {
            long min = System.currentTimeMillis() - bridgeHandler.config().queueTimeout * 1000L;
            while (entries.size() > 0) {
                Entry<?> entry = entries.get(entries.firstKey());
                if (entry != null) {
                    if (entry.timestamp >= min) {
                        break;
                    }
                    evict(entry.id, new CancellationException("Cancelled due to queue timeout"));
                }
            }
        }
    }

    synchronized void evictAll(Throwable t) {
        if (entries.size() > 0) {
            while (entries.size() > 0) {
                evict(entries.firstKey(), t);
            }
        }
    }

    synchronized void evict(long id, Throwable t) {
        Entry<?> entry = entries.remove(id);
        if (entry != null) {
            logger.debug("Evicting command {} with id {} with {}", entry.command, id, t);
            entry.future.completeExceptionally(t);
        }
    }

    final class Entry<R extends BeckerCommand.Result> {

        final long id;
        final long timestamp;
        final BeckerCommand<R> command;
        final CompletableFuture<R> future;

        private Entry(BeckerCommand<R> command) {
            this.id = ++lastId;
            this.timestamp = System.currentTimeMillis();
            this.command = command;
            this.future = new CompletableFuture<>();
        }
    }
}
