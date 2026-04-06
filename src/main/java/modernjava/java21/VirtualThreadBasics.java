package modernjava.java21;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Virtual Threads (JEP 444) - Java 21 정식
 *
 * OS 스레드가 아닌 JVM이 관리하는 경량 스레드.
 * 블로킹 I/O 시 carrier thread를 반납하고, 완료 시 재개.
 * 수십만 개를 동시에 생성해도 메모리 부담 적음.
 */
public class VirtualThreadBasics {

    // ===== VT-01: 10만 Virtual Thread 생성 + Thread.sleep 블로킹 =====

    /**
     * 10만 개의 Virtual Thread를 생성하고, 각각 100ms sleep.
     * OS 스레드로는 불가능한 수준 (메모리 초과).
     * Virtual Thread는 수 KB이므로 가능.
     *
     * @return 소요 시간 (ms)
     */
    public static long create100kVirtualThreads() throws InterruptedException {
        Instant start = Instant.now();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(vt);
        }

        for (Thread t : threads) {
            t.join();
        }

        return Duration.between(start, Instant.now()).toMillis();
    }

    /**
     * Virtual Thread 확인 메서드
     */
    public static boolean isVirtualThread() throws Exception {
        var result = new CompletableFuture<Boolean>();
        Thread.ofVirtual().start(() -> result.complete(Thread.currentThread().isVirtual()));
        return result.get(5, TimeUnit.SECONDS);
    }

    // ===== VT-02: FixedThreadPool(200) vs VirtualThreadPerTask 처리량 비교 =====

    /**
     * 동일한 블로킹 작업을 두 방식으로 실행하여 처리량 비교.
     *
     * @param taskCount 총 작업 수
     * @param sleepMs   각 작업의 블로킹 시간
     * @return [platformMs, virtualMs]
     */
    public static long[] compareThroughput(int taskCount, int sleepMs) throws InterruptedException {
        Runnable blockingTask = () -> {
            try {
                Thread.sleep(Duration.ofMillis(sleepMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Platform Thread Pool (200개 고정)
        long platformMs;
        try (var executor = Executors.newFixedThreadPool(200)) {
            Instant start = Instant.now();
            for (int i = 0; i < taskCount; i++) {
                executor.submit(blockingTask);
            }
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
            platformMs = Duration.between(start, Instant.now()).toMillis();
        }

        // Virtual Thread Per Task
        long virtualMs;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Instant start = Instant.now();
            for (int i = 0; i < taskCount; i++) {
                executor.submit(blockingTask);
            }
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
            virtualMs = Duration.between(start, Instant.now()).toMillis();
        }

        return new long[]{platformMs, virtualMs};
    }

    // ===== VT-03: VT + HttpClient 동시 HTTP 요청 =====

    /**
     * Virtual Thread로 다수의 HTTP 요청을 동시에 보내는 예제.
     * 실제 HTTP 호출 대신 시뮬레이션 (외부 의존 없이 테스트 가능).
     *
     * @param requestCount 동시 요청 수
     * @return 소요 시간 (ms)
     */
    public static long simulateConcurrentHttpRequests(int requestCount, int latencyMs) throws InterruptedException {
        Instant start = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                final int reqId = i;
                futures.add(executor.submit(() -> {
                    // HTTP 호출 시뮬레이션 (latencyMs 만큼 블로킹)
                    Thread.sleep(Duration.ofMillis(latencyMs));
                    return "Response-" + reqId;
                }));
            }

            // 모든 응답 대기
            int successCount = 0;
            for (Future<String> f : futures) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    successCount++;
                } catch (Exception e) {
                    // ignore
                }
            }

            if (successCount != requestCount) {
                throw new RuntimeException("Expected " + requestCount + " but got " + successCount);
            }
        }

        return Duration.between(start, Instant.now()).toMillis();
    }

    // ===== VT-04: synchronized 블록에서 pinning 확인 =====

    /**
     * Java 21에서 synchronized + VT = pinning 발생.
     * Java 24+(JEP 491)에서 해결됨.
     *
     * pinning: Virtual Thread가 carrier thread에 고정되어 반납 못하는 현상.
     * → carrier thread 수가 제한되므로 동시성 저하.
     *
     * 이 메서드는 synchronized 블록 안에서 sleep하여 pinning을 유발.
     * Java 25에서는 pinning이 발생하지 않음 (JEP 491).
     */
    private static final Object LOCK = new Object();

    public static long synchronizedWithVirtualThreads(int threadCount, int sleepMs) throws InterruptedException {
        Instant start = Instant.now();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                synchronized (LOCK) {
                    try {
                        Thread.sleep(Duration.ofMillis(sleepMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            threads.add(vt);
        }

        for (Thread t : threads) {
            t.join();
        }

        return Duration.between(start, Instant.now()).toMillis();
    }

    /**
     * ReentrantLock 버전 (pinning 없음, 모든 Java 21+ 버전에서 안전).
     */
    private static final java.util.concurrent.locks.ReentrantLock REENTRANT_LOCK =
            new java.util.concurrent.locks.ReentrantLock();

    public static long reentrantLockWithVirtualThreads(int threadCount, int sleepMs) throws InterruptedException {
        Instant start = Instant.now();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                REENTRANT_LOCK.lock();
                try {
                    Thread.sleep(Duration.ofMillis(sleepMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    REENTRANT_LOCK.unlock();
                }
            });
            threads.add(vt);
        }

        for (Thread t : threads) {
            t.join();
        }

        return Duration.between(start, Instant.now()).toMillis();
    }

    // ===== 유틸: Thread.ofVirtual() vs Executors 비교 =====

    /**
     * VT-06: 두 가지 VT 생성 방식 비교.
     *
     * Thread.ofVirtual(): 개별 스레드 제어 필요 시
     * Executors.newVirtualThreadPerTaskExecutor(): ExecutorService 패턴
     */
    public static String threadOfVirtualDemo() throws Exception {
        // 방식 1: Thread.ofVirtual()
        var future1 = new CompletableFuture<String>();
        Thread.ofVirtual()
                .name("vt-manual-", 0)
                .start(() -> future1.complete(Thread.currentThread().getName()));
        String name1 = future1.get(5, TimeUnit.SECONDS);

        // 방식 2: Executors
        String name2;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future2 = executor.submit(() -> Thread.currentThread().getName());
            name2 = future2.get(5, TimeUnit.SECONDS);
        }

        return "ofVirtual: " + name1 + ", Executor: " + name2;
    }
}
