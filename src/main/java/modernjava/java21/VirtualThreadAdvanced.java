package modernjava.java21;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Virtual Threads 고급 주제 - VT-05, VT-07, VT-08
 *
 * 커넥션 풀 사이즈 이슈, ThreadLocal 메모리 문제,
 * Spring Boot Virtual Thread 설정 효과.
 */
public class VirtualThreadAdvanced {

    // ===== VT-05: VT + 커넥션 풀 사이즈 이슈 (Semaphore 패턴) =====

    /**
     * 문제: Virtual Thread는 수만 개 동시 실행 가능.
     *       하지만 DB 커넥션 풀은 보통 50개.
     *       → 5만 VT가 동시에 DB 접근하면 커넥션 고갈.
     *
     * 해결: Semaphore로 동시 접근 수 제한.
     *       HikariCP의 maximumPoolSize와 동일하게 설정.
     */
    public static class ConnectionPoolSimulator {
        private final Semaphore semaphore;
        private final int poolSize;
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicInteger maxActiveConnections = new AtomicInteger(0);
        private final AtomicInteger totalQueries = new AtomicInteger(0);
        private final AtomicInteger rejectedQueries = new AtomicInteger(0);

        public ConnectionPoolSimulator(int poolSize) {
            this.poolSize = poolSize;
            this.semaphore = new Semaphore(poolSize);
        }

        /**
         * Semaphore 없이 직접 접근 (커넥션 풀 초과 가능)
         */
        public String queryWithoutSemaphore(String sql, int latencyMs) throws InterruptedException {
            int active = activeConnections.incrementAndGet();
            maxActiveConnections.updateAndGet(max -> Math.max(max, active));
            totalQueries.incrementAndGet();

            if (active > poolSize) {
                rejectedQueries.incrementAndGet();
            }

            try {
                Thread.sleep(Duration.ofMillis(latencyMs)); // DB 쿼리 시뮬레이션
                return "Result of: " + sql;
            } finally {
                activeConnections.decrementAndGet();
            }
        }

        /**
         * Semaphore로 동시 접근 수 제한 (권장 패턴)
         */
        public String queryWithSemaphore(String sql, int latencyMs) throws InterruptedException {
            semaphore.acquire(); // 커넥션 풀에 여유가 있을 때까지 대기
            int active = activeConnections.incrementAndGet();
            maxActiveConnections.updateAndGet(max -> Math.max(max, active));
            totalQueries.incrementAndGet();

            try {
                Thread.sleep(Duration.ofMillis(latencyMs));
                return "Result of: " + sql;
            } finally {
                activeConnections.decrementAndGet();
                semaphore.release(); // 커넥션 반환
            }
        }

        public int getMaxActiveConnections() { return maxActiveConnections.get(); }
        public int getTotalQueries() { return totalQueries.get(); }
        public int getRejectedQueries() { return rejectedQueries.get(); }

        public void reset() {
            activeConnections.set(0);
            maxActiveConnections.set(0);
            totalQueries.set(0);
            rejectedQueries.set(0);
        }
    }

    /**
     * VT-05 실행: Semaphore 유/무에 따른 동시 접근 수 비교
     *
     * @return [withoutSemaphoreMaxActive, withSemaphoreMaxActive]
     */
    public static int[] compareSemaphoreEffect(int vtCount, int poolSize, int queryLatencyMs)
            throws InterruptedException {
        var pool = new ConnectionPoolSimulator(poolSize);

        // 1) Semaphore 없이
        pool.reset();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < vtCount; i++) {
                futures.add(executor.submit(() -> {
                    pool.queryWithoutSemaphore("SELECT 1", queryLatencyMs);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { /* ignore */ }
            }
        }
        int withoutMax = pool.getMaxActiveConnections();

        // 2) Semaphore 사용
        pool.reset();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < vtCount; i++) {
                futures.add(executor.submit(() -> {
                    pool.queryWithSemaphore("SELECT 1", queryLatencyMs);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { /* ignore */ }
            }
        }
        int withMax = pool.getMaxActiveConnections();

        return new int[]{withoutMax, withMax};
    }

    // ===== VT-07: VT에서 ThreadLocal 사용 시 메모리 이슈 =====

    /**
     * 문제: ThreadLocal은 스레드당 값을 저장.
     *       Platform Thread: ~200개 → 메모리 적음.
     *       Virtual Thread: ~100만개 → 각각 ThreadLocal 복사 → 메모리 폭증.
     *
     * 특히 InheritableThreadLocal은 부모→자식 VT 생성 시 값 복사.
     * VT를 대량으로 spawn하면 O(N) 복사 비용.
     */
    private static final ThreadLocal<byte[]> THREAD_LOCAL_DATA = new ThreadLocal<>();

    /**
     * ThreadLocal에 데이터를 저장하는 VT 생성.
     * 메모리 사용량 추적.
     *
     * @param vtCount VT 개수
     * @param dataSizeBytes ThreadLocal에 저장할 데이터 크기
     * @return [beforeMemoryMB, afterMemoryMB, diffMB]
     */
    public static long[] measureThreadLocalMemory(int vtCount, int dataSizeBytes) throws InterruptedException {
        System.gc();
        Thread.sleep(100);
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        CountDownLatch allStarted = new CountDownLatch(vtCount);
        CountDownLatch allDone = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < vtCount; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                THREAD_LOCAL_DATA.set(new byte[dataSizeBytes]);
                allStarted.countDown();
                try {
                    allDone.await(); // 모든 VT가 동시에 ThreadLocal을 보유하도록 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    THREAD_LOCAL_DATA.remove();
                }
            });
            threads.add(vt);
        }

        allStarted.await(30, TimeUnit.SECONDS);

        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        allDone.countDown(); // 모든 VT 해제

        for (Thread t : threads) {
            t.join(10_000);
        }

        long beforeMB = beforeMemory / (1024 * 1024);
        long afterMB = afterMemory / (1024 * 1024);
        long diffMB = afterMB - beforeMB;

        return new long[]{beforeMB, afterMB, diffMB};
    }

    // ===== VT-08: Spring Boot virtual threads 설정 효과 시뮬레이션 =====

    /**
     * spring.threads.virtual.enabled=true 의 효과를 시뮬레이션.
     *
     * Spring Boot가 하는 것:
     * 1. Tomcat의 스레드 풀을 VirtualThreadPerTask로 교체
     * 2. 모든 HTTP 요청이 Virtual Thread에서 실행
     * 3. @Async 작업도 Virtual Thread에서 실행
     *
     * 시뮬레이션: 동일한 블로킹 작업을 Platform/Virtual 방식으로 처리
     */
    public record ServerSimulationResult(
            long totalTimeMs,
            int completedRequests,
            double requestsPerSecond
    ) {}

    /**
     * Platform Thread 기반 서버 시뮬레이션 (Tomcat 기본: 200 threads)
     */
    public static ServerSimulationResult simulatePlatformServer(int requestCount, int handlerLatencyMs)
            throws InterruptedException {
        Instant start = Instant.now();
        AtomicInteger completed = new AtomicInteger(0);

        try (var executor = Executors.newFixedThreadPool(200)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(Duration.ofMillis(handlerLatencyMs)); // DB + 외부 API
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    completed.incrementAndGet();
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(60, TimeUnit.SECONDS); } catch (Exception e) { /* ignore */ }
            }
        }

        long totalMs = Duration.between(start, Instant.now()).toMillis();
        double rps = (double) completed.get() / totalMs * 1000;
        return new ServerSimulationResult(totalMs, completed.get(), rps);
    }

    /**
     * Virtual Thread 기반 서버 시뮬레이션 (spring.threads.virtual.enabled=true)
     */
    public static ServerSimulationResult simulateVirtualServer(int requestCount, int handlerLatencyMs)
            throws InterruptedException {
        Instant start = Instant.now();
        AtomicInteger completed = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(Duration.ofMillis(handlerLatencyMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    completed.incrementAndGet();
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(60, TimeUnit.SECONDS); } catch (Exception e) { /* ignore */ }
            }
        }

        long totalMs = Duration.between(start, Instant.now()).toMillis();
        double rps = (double) completed.get() / totalMs * 1000;
        return new ServerSimulationResult(totalMs, completed.get(), rps);
    }
}
