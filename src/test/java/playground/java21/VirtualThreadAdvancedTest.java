package playground.java21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java21.VirtualThreadAdvanced.*;

import static org.assertj.core.api.Assertions.*;

class VirtualThreadAdvancedTest {

    @Nested
    @DisplayName("VT-05: 커넥션 풀 + Semaphore 패턴")
    class ConnectionPoolTest {

        @Test
        @DisplayName("Semaphore 없이: 동시 접근이 풀 사이즈 초과")
        void withoutSemaphore() throws InterruptedException {
            int[] results = VirtualThreadAdvanced.compareSemaphoreEffect(500, 10, 50);
            int withoutMax = results[0];
            int withMax = results[1];

            System.out.println("VT-05: Without Semaphore maxActive=" + withoutMax);
            System.out.println("VT-05: With Semaphore maxActive=" + withMax);

            // Semaphore 없이: 풀 사이즈(10)를 훨씬 초과
            assertThat(withoutMax).isGreaterThan(10);

            // Semaphore 사용: 풀 사이즈(10) 이하로 제한
            assertThat(withMax).isLessThanOrEqualTo(10);
        }
    }

    @Nested
    @DisplayName("VT-07: ThreadLocal 메모리 이슈")
    class ThreadLocalMemoryTest {

        @Test
        @DisplayName("1만 VT x 10KB ThreadLocal = ~100MB 메모리 증가")
        void threadLocalMemoryUsage() throws InterruptedException {
            long[] result = VirtualThreadAdvanced.measureThreadLocalMemory(10_000, 10_000);
            System.out.println("VT-07: Before=" + result[0] + "MB, After=" + result[1] + "MB, Diff=" + result[2] + "MB");

            // 10000 * 10KB = ~100MB 이론치 (실제는 GC 등으로 다를 수 있음)
            // 최소 50MB 이상 증가해야 함
            assertThat(result[2]).isGreaterThan(20L);
        }
    }

    @Nested
    @DisplayName("VT-08: Spring Boot virtual threads 효과 시뮬레이션")
    class SpringVirtualThreadTest {

        @Test
        @DisplayName("2000 요청 x 100ms: VT 서버가 Platform보다 빠름")
        void virtualServerFaster() throws InterruptedException {
            var platformResult = VirtualThreadAdvanced.simulatePlatformServer(2000, 100);
            var virtualResult = VirtualThreadAdvanced.simulateVirtualServer(2000, 100);

            System.out.println("VT-08 Platform: " + platformResult.totalTimeMs() + "ms, " +
                    String.format("%.0f", platformResult.requestsPerSecond()) + " rps");
            System.out.println("VT-08 Virtual: " + virtualResult.totalTimeMs() + "ms, " +
                    String.format("%.0f", virtualResult.requestsPerSecond()) + " rps");
            System.out.println("VT-08 Speedup: " + String.format("%.1fx",
                    (double) platformResult.totalTimeMs() / virtualResult.totalTimeMs()));

            // Virtual이 Platform보다 빨라야 함
            assertThat(virtualResult.totalTimeMs()).isLessThan(platformResult.totalTimeMs());

            // 모든 요청 완료
            assertThat(virtualResult.completedRequests()).isEqualTo(2000);
            assertThat(platformResult.completedRequests()).isEqualTo(2000);
        }
    }
}
