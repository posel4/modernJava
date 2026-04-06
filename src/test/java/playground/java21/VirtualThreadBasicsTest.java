package playground.java21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class VirtualThreadBasicsTest {

    @Nested
    @DisplayName("VT-01: 10만 Virtual Thread 생성")
    class Create100kTest {

        @Test
        @DisplayName("10만 VT가 합리적인 시간 내 완료 (< 10초)")
        void create100kVirtualThreads() throws InterruptedException {
            long ms = VirtualThreadBasics.create100kVirtualThreads();
            System.out.println("VT-01: 100K virtual threads completed in " + ms + "ms");
            assertThat(ms).isLessThan(10_000L);
        }

        @Test
        @DisplayName("Virtual Thread 확인")
        void isVirtualThread() throws Exception {
            assertThat(VirtualThreadBasics.isVirtualThread()).isTrue();
        }

        @Test
        @DisplayName("메인 스레드는 Virtual Thread가 아님")
        void mainIsNotVirtual() {
            assertThat(Thread.currentThread().isVirtual()).isFalse();
        }
    }

    @Nested
    @DisplayName("VT-02: FixedThreadPool vs VirtualThreadPerTask 처리량 비교")
    class ThroughputComparisonTest {

        @Test
        @DisplayName("1000 작업 x 50ms: VT가 Platform보다 빠름")
        void vtFasterThanPlatform() throws InterruptedException {
            long[] results = VirtualThreadBasics.compareThroughput(1000, 50);
            long platformMs = results[0];
            long virtualMs = results[1];

            System.out.println("VT-02: Platform(200)=" + platformMs + "ms, Virtual=" + virtualMs + "ms");
            System.out.println("VT-02: Speedup=" + String.format("%.1fx", (double) platformMs / virtualMs));

            // VT가 최소 Platform의 절반 이상 빠를 것으로 기대
            // (1000 tasks / 200 threads = 5 batches * 50ms = ~250ms for platform)
            // (1000 tasks all concurrent * 50ms = ~50ms for virtual)
            assertThat(virtualMs).isLessThan(platformMs);
        }
    }

    @Nested
    @DisplayName("VT-03: 동시 HTTP 요청 시뮬레이션")
    class ConcurrentHttpTest {

        @Test
        @DisplayName("1000 동시 요청 (각 50ms latency): 전체 < 5초")
        void concurrentRequests() throws InterruptedException {
            long ms = VirtualThreadBasics.simulateConcurrentHttpRequests(1000, 50);
            System.out.println("VT-03: 1000 concurrent requests in " + ms + "ms");
            assertThat(ms).isLessThan(5_000L);
        }
    }

    @Nested
    @DisplayName("VT-04: synchronized + VT pinning (Java 25에서 해결)")
    class PinningTest {

        @Test
        @DisplayName("synchronized + VT: Java 25에서는 pinning 없이 정상 동작")
        void synchronizedNoPinning() throws InterruptedException {
            // Java 25 (JEP 491)에서는 synchronized에서도 pinning 없음
            // 10개 VT x 10ms = 순차라면 100ms, 병렬이면 ~10ms
            long ms = VirtualThreadBasics.synchronizedWithVirtualThreads(10, 10);
            System.out.println("VT-04: synchronized + VT = " + ms + "ms");
            // synchronized이므로 순차 실행됨 (lock 자체가 순차) → ~100ms 이상
            assertThat(ms).isGreaterThanOrEqualTo(90L);
        }

        @Test
        @DisplayName("ReentrantLock + VT: 동일하게 순차 실행 (lock이므로)")
        void reentrantLockAlsoSequential() throws InterruptedException {
            long ms = VirtualThreadBasics.reentrantLockWithVirtualThreads(10, 10);
            System.out.println("VT-04: ReentrantLock + VT = " + ms + "ms");
            assertThat(ms).isGreaterThanOrEqualTo(90L);
        }
    }

    @Nested
    @DisplayName("VT-06: Thread.ofVirtual() vs Executors")
    class CreationStyleTest {

        @Test
        @DisplayName("두 가지 VT 생성 방식 모두 동작")
        void bothStylesWork() throws Exception {
            String result = VirtualThreadBasics.threadOfVirtualDemo();
            System.out.println("VT-06: " + result);
            assertThat(result).contains("ofVirtual:", "Executor:");
        }
    }
}
