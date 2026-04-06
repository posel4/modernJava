package playground.java24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Virtual Thread Pinning Fix (JEP 491) 학습 테스트
 */
class PinningFixTest {

    // ========================================================================
    // PIN-01: synchronized + Virtual Thread = no pinning
    // ========================================================================

    @Nested
    @DisplayName("PIN-01: synchronizedWithVirtualThreads")
    class SynchronizedWithVirtualThreadsTest {

        @Test
        @DisplayName("100개 VT가 synchronized 블록에서 합리적 시간 내 완료 (pinning 없음)")
        void completesInReasonableTime() {
            // 100 tasks x 10ms sleep (순차이므로 최소 ~1000ms)
            // pinning이 없으면 carrier thread 양보가 잘 되어 정상 완료
            // pinning이 있으면 carrier thread 부족으로 매우 오래 걸림
            long elapsed = PinningFixExample.synchronizedWithVirtualThreads(100);

            // synchronized lock은 하나이므로 순차 실행: 100 * 10ms = ~1000ms
            // 넉넉하게 10초 이내면 OK (pinning 있으면 수십 초 이상 소요)
            assertThat(elapsed)
                    .as("100개 VT synchronized 블록 실행 시간 (ms)")
                    .isLessThan(10_000L);
        }

        @Test
        @DisplayName("소수 VT도 정상 동작")
        void smallTaskCount() {
            long elapsed = PinningFixExample.synchronizedWithVirtualThreads(5);

            // 5 * 10ms = ~50ms, 넉넉하게 2초 이내
            assertThat(elapsed).isLessThan(2_000L);
        }
    }

    // ========================================================================
    // PIN-02: synchronized vs ReentrantLock 성능 비교
    // ========================================================================

    @Nested
    @DisplayName("PIN-02: compareLocksPerformance")
    class CompareLocksPerformanceTest {

        @Test
        @DisplayName("두 방식 모두 완료되고 시간이 기록된다")
        void bothComplete() {
            Map<String, Long> result = PinningFixExample.compareLocksPerformance(20);

            assertThat(result).containsKeys("synchronized", "reentrantLock");
            assertThat(result.get("synchronized")).isPositive();
            assertThat(result.get("reentrantLock")).isPositive();
        }

        @Test
        @DisplayName("Java 24+ 에서는 두 방식의 성능 차이가 크지 않다")
        void similarPerformance() {
            Map<String, Long> result = PinningFixExample.compareLocksPerformance(20);

            long syncTime = result.get("synchronized");
            long lockTime = result.get("reentrantLock");

            // 두 시간의 비율이 5배 이내 (순차 실행이라 비슷해야 함)
            double ratio = (double) Math.max(syncTime, lockTime) / Math.max(1, Math.min(syncTime, lockTime));
            assertThat(ratio)
                    .as("synchronized/ReentrantLock 성능 비율 (%.1f배)", ratio)
                    .isLessThan(5.0);
        }
    }

    // ========================================================================
    // PIN-03: 스타일 권장사항
    // ========================================================================

    @Nested
    @DisplayName("PIN-03: lockStyleRecommendation")
    class LockStyleRecommendationTest {

        @Test
        @DisplayName("고급 기능이 불필요하면 synchronized 권장")
        void recommendSynchronized() {
            String result = PinningFixExample.lockStyleRecommendation(false, false, false);

            assertThat(result).isEqualTo("synchronized");
        }

        @Test
        @DisplayName("tryLock 필요 시 ReentrantLock 권장")
        void recommendReentrantLockForTryLock() {
            String result = PinningFixExample.lockStyleRecommendation(true, false, false);

            assertThat(result).isEqualTo("ReentrantLock");
        }

        @Test
        @DisplayName("timed lock 필요 시 ReentrantLock 권장")
        void recommendReentrantLockForTimedLock() {
            String result = PinningFixExample.lockStyleRecommendation(false, true, false);

            assertThat(result).isEqualTo("ReentrantLock");
        }

        @Test
        @DisplayName("multiple conditions 필요 시 ReentrantLock 권장")
        void recommendReentrantLockForMultipleConditions() {
            String result = PinningFixExample.lockStyleRecommendation(false, false, true);

            assertThat(result).isEqualTo("ReentrantLock");
        }

        @Test
        @DisplayName("모든 고급 기능이 필요하면 당연히 ReentrantLock")
        void recommendReentrantLockForAll() {
            String result = PinningFixExample.lockStyleRecommendation(true, true, true);

            assertThat(result).isEqualTo("ReentrantLock");
        }
    }
}
