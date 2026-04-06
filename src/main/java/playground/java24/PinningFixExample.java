package playground.java24;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Virtual Thread Pinning Fix (JEP 491) - Java 24에서 정식 도입
 *
 * Java 21~23에서는 Virtual Thread가 synchronized 블록 안에서 블로킹 호출을 하면
 * carrier thread에 "pin"되어 다른 VT가 해당 carrier를 사용할 수 없었다.
 *
 * Java 24부터 이 제한이 해제되어, synchronized 블록 내에서도
 * Virtual Thread가 자유롭게 carrier thread를 양보(unmount)할 수 있다.
 */
public class PinningFixExample {

    // ========================================================================
    // PIN-01: synchronized + Virtual Thread = no pinning (Java 24+)
    // ========================================================================

    /**
     * Java 24 이후: synchronized 블록에서 VT가 carrier thread를 pin하지 않는다.
     *
     * 이전 Java 버전에서는 synchronized + sleep 조합이 VT 성능을 심하게 저하시켰지만,
     * Java 24+에서는 VT가 sleep 중에 carrier를 양보하므로 빠르게 완료된다.
     *
     * @param taskCount 실행할 VT 작업 수
     * @return 전체 실행 시간 (밀리초)
     */
    public static long synchronizedWithVirtualThreads(int taskCount) {
        Object lock = new Object();
        AtomicInteger counter = new AtomicInteger();

        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    synchronized (lock) {
                        try {
                            Thread.sleep(Duration.ofMillis(10));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter.incrementAndGet();
                    }
                });
            }
        }

        assert counter.get() == taskCount
                : "Expected " + taskCount + " but got " + counter.get();

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    // ========================================================================
    // PIN-02: synchronized vs ReentrantLock 성능 비교
    // ========================================================================

    /**
     * Java 24에서는 synchronized와 ReentrantLock의 VT 성능 차이가 미미하다.
     *
     * 이전에는 VT에서 ReentrantLock이 권장되었지만 (pinning 회피),
     * Java 24+에서는 두 방식 모두 비슷한 성능을 보인다.
     *
     * @param taskCount 각 방식별 실행할 VT 작업 수
     * @return "synchronized" -> 시간(ms), "reentrantLock" -> 시간(ms) 맵
     */
    public static Map<String, Long> compareLocksPerformance(int taskCount) {
        long syncTime = measureSynchronized(taskCount);
        long lockTime = measureReentrantLock(taskCount);

        return Map.of(
                "synchronized", syncTime,
                "reentrantLock", lockTime
        );
    }

    private static long measureSynchronized(int taskCount) {
        Object lock = new Object();
        AtomicInteger counter = new AtomicInteger();

        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    synchronized (lock) {
                        try {
                            Thread.sleep(Duration.ofMillis(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter.incrementAndGet();
                    }
                });
            }
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static long measureReentrantLock(int taskCount) {
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger counter = new AtomicInteger();

        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    lock.lock();
                    try {
                        try {
                            Thread.sleep(Duration.ofMillis(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                });
            }
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    // ========================================================================
    // PIN-03: 스타일 권장사항
    // ========================================================================

    /**
     * Java 24+에서의 동기화 스타일 권장사항.
     *
     * Java 24 이후에는 synchronized가 다시 1순위 추천이다.
     * ReentrantLock은 다음과 같은 고급 기능이 필요할 때만 사용한다:
     * - tryLock(): 논블로킹 락 획득 시도
     * - timed lock: 타임아웃이 있는 락 획득
     * - multiple conditions: 여러 Condition 객체 사용
     *
     * @param needsTryLock         tryLock 기능이 필요한가
     * @param needsTimedLock       타임아웃 락이 필요한가
     * @param needsMultipleConditions 다중 Condition이 필요한가
     * @return "synchronized" 또는 "ReentrantLock" 권장 문자열
     */
    public static String lockStyleRecommendation(
            boolean needsTryLock,
            boolean needsTimedLock,
            boolean needsMultipleConditions) {
        if (needsTryLock || needsTimedLock || needsMultipleConditions) {
            return "ReentrantLock";
        }
        return "synchronized";
    }
}
