package playground.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java25.StructuredConcurrencyExample.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structured Concurrency (JEP 505) 학습 테스트
 */
class StructuredConcurrencyTest {

    // ========================================================================
    // SC-01: 기본 패턴 - fetchUserProfile
    // ========================================================================

    @Nested
    @DisplayName("SC-01: fetchUserProfile (기본 병렬 패턴)")
    class FetchUserProfileTest {

        @Test
        @DisplayName("두 작업이 병렬로 실행되어 프로필이 완성된다")
        void bothFieldsPopulated() throws Exception {
            UserProfile profile = StructuredConcurrencyExample.fetchUserProfile("123");

            assertThat(profile.name()).isEqualTo("User-123");
            assertThat(profile.age()).isEqualTo(25);
        }

        @Test
        @DisplayName("다른 userId로 조회해도 올바른 결과")
        void differentUserId() throws Exception {
            UserProfile profile = StructuredConcurrencyExample.fetchUserProfile("abc");

            assertThat(profile.name()).isEqualTo("User-abc");
            assertThat(profile.age()).isEqualTo(25);
        }
    }

    // ========================================================================
    // SC-02: 경쟁 패턴 - fetchFromFastestSource
    // ========================================================================

    @Nested
    @DisplayName("SC-02: fetchFromFastestSource (경쟁 패턴)")
    class FetchFromFastestSourceTest {

        @Test
        @DisplayName("가장 빠른 소스(from-db, 50ms)의 결과가 반환된다")
        void fastestSourceWins() throws Exception {
            String result = StructuredConcurrencyExample.fetchFromFastestSource();

            assertThat(result).isEqualTo("from-db");
        }
    }

    // ========================================================================
    // SC-03: 타임아웃 - fetchWithTimeout
    // ========================================================================

    @Nested
    @DisplayName("SC-03: fetchWithTimeout (타임아웃 + 자동 취소)")
    class FetchWithTimeoutTest {

        @Test
        @DisplayName("충분한 타임아웃: 빠른 작업의 결과가 반환된다")
        void withinTimeout() throws Exception {
            String result = StructuredConcurrencyExample.fetchWithTimeout(Duration.ofSeconds(2));

            assertThat(result).isEqualTo("fast-result");
        }

        @Test
        @DisplayName("타임아웃이 모든 작업보다 짧으면 예외 발생")
        void timeoutExceeded() {
            assertThatThrownBy(() ->
                    StructuredConcurrencyExample.fetchWithTimeout(Duration.ofMillis(10))
            ).isInstanceOf(Exception.class);
        }
    }

    // ========================================================================
    // SC-04: 에러 전파 - fetchWithErrorPropagation
    // ========================================================================

    @Nested
    @DisplayName("SC-04: fetchWithErrorPropagation (에러 전파)")
    class FetchWithErrorPropagationTest {

        @Test
        @DisplayName("실패한 작업의 예외가 전파된다")
        void errorPropagated() {
            assertThatThrownBy(() ->
                    StructuredConcurrencyExample.fetchWithErrorPropagation()
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB connection failed");
        }
    }

    // ========================================================================
    // SC-05: CompletableFuture vs Structured Concurrency
    // ========================================================================

    @Nested
    @DisplayName("SC-05: CompletableFuture vs Structured Concurrency")
    class CompletableFutureVsStructuredTest {

        @Test
        @DisplayName("CompletableFuture: 동일한 결과를 반환한다")
        void completableFutureResult() throws Exception {
            String result = StructuredConcurrencyExample.withCompletableFuture();

            assertThat(result).isEqualTo("result1+result2");
        }

        @Test
        @DisplayName("Structured Concurrency: 동일한 결과를 반환한다")
        void structuredConcurrencyResult() throws Exception {
            String result = StructuredConcurrencyExample.withStructuredConcurrency();

            assertThat(result).isEqualTo("result1+result2");
        }

        @Test
        @DisplayName("두 방식의 결과가 동일하다")
        void sameResult() throws Exception {
            String cfResult = StructuredConcurrencyExample.withCompletableFuture();
            String scResult = StructuredConcurrencyExample.withStructuredConcurrency();

            assertThat(cfResult).isEqualTo(scResult);
        }
    }

    // ========================================================================
    // SC-06: 메신저 병렬 조회 - fetchChannelDetail
    // ========================================================================

    @Nested
    @DisplayName("SC-06: fetchChannelDetail (메신저 병렬 조회)")
    class FetchChannelDetailTest {

        @Test
        @DisplayName("채널, 멤버, 메시지가 모두 조회된다")
        void allPartsPopulated() throws Exception {
            ChannelDetail detail = StructuredConcurrencyExample.fetchChannelDetail(42L);

            // 채널 정보
            assertThat(detail.channel().id()).isEqualTo(42L);
            assertThat(detail.channel().name()).isEqualTo("general");

            // 멤버 목록
            assertThat(detail.members()).hasSize(2);
            assertThat(detail.members().get(0).displayName()).isEqualTo("Alice");
            assertThat(detail.members().get(1).displayName()).isEqualTo("Bob");

            // 메시지 목록
            assertThat(detail.messages()).hasSize(2);
            assertThat(detail.messages().get(0).content()).isEqualTo("Hello");
            assertThat(detail.messages().get(1).content()).isEqualTo("World");
        }

        @Test
        @DisplayName("채널 ID가 올바르게 전달된다")
        void channelIdPassedCorrectly() throws Exception {
            ChannelDetail detail = StructuredConcurrencyExample.fetchChannelDetail(100L);

            assertThat(detail.channel().id()).isEqualTo(100L);
        }
    }

    // ========================================================================
    // SV-04: ScopedValue + StructuredTaskScope 자동 전파
    // ========================================================================

    @Nested
    @DisplayName("SV-04: ScopedValue 자동 전파")
    class ScopedValueAutoInheritanceTest {

        @Test
        @DisplayName("fork된 VT에서 ScopedValue가 자동으로 보인다")
        void autoInheritance() throws Exception {
            var results = StructuredConcurrencyExample.scopedValueAutoInheritance("req-123");

            assertThat(results).containsExactly(
                    "task1-sees-req-123",
                    "task2-sees-req-123"
            );
        }

        @Test
        @DisplayName("다른 requestId도 올바르게 전파된다")
        void differentRequestId() throws Exception {
            var results = StructuredConcurrencyExample.scopedValueAutoInheritance("req-abc");

            assertThat(results).containsExactlyInAnyOrder(
                    "task1-sees-req-abc",
                    "task2-sees-req-abc"
            );
        }
    }
}
