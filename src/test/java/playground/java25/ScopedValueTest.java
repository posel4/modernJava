package playground.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java25.ScopedValueExample.ShardContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScopedValue (JEP 506) 학습 테스트
 */
class ScopedValueTest {

    // ========================================================================
    // SV-01: ScopedValue 기본 - where().run() 패턴
    // ========================================================================

    @Nested
    @DisplayName("SV-01: ScopedValue 기본 사용")
    class BasicScopedValueTest {

        @Test
        @DisplayName("runAsUser: 스코프 내에서 사용자가 설정된다")
        void runAsUser() throws Exception {
            String result = ScopedValueExample.runAsUser("alice", () ->
                    ScopedValueExample.getCurrentUser()
            );

            assertThat(result).isEqualTo("alice");
        }

        @Test
        @DisplayName("getCurrentUser: 바인딩되지 않으면 anonymous")
        void getCurrentUserWhenNotBound() {
            String result = ScopedValueExample.getCurrentUser();

            assertThat(result).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("runAsUser: 스코프 종료 후 바인딩 해제")
        void scopeEndsAfterRun() throws Exception {
            ScopedValueExample.runAsUser("bob", () -> "done");

            // 스코프 밖에서는 다시 anonymous
            assertThat(ScopedValueExample.getCurrentUser()).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("runAsUser: 다른 사용자로 연속 호출")
        void consecutiveCalls() throws Exception {
            String first = ScopedValueExample.runAsUser("alice", () ->
                    ScopedValueExample.getCurrentUser()
            );
            String second = ScopedValueExample.runAsUser("bob", () ->
                    ScopedValueExample.getCurrentUser()
            );

            assertThat(first).isEqualTo("alice");
            assertThat(second).isEqualTo("bob");
        }
    }

    // ========================================================================
    // SV-02: ThreadLocal vs ScopedValue 메모리 비교
    // ========================================================================

    @Nested
    @DisplayName("SV-02: ThreadLocal vs ScopedValue 메모리 비교")
    class MemoryComparisonTest {

        @Test
        @DisplayName("ThreadLocal: VT마다 독립적인 메모리 사용")
        void threadLocalMemory() throws Exception {
            long memory = ScopedValueExample.threadLocalMemoryUsage(100);

            // 100개 VT * 1KB = 100KB
            assertThat(memory).isEqualTo(100 * 1024L);
        }

        @Test
        @DisplayName("ScopedValue: 공유 메모리로 효율적")
        void scopedValueMemory() throws Exception {
            long memory = ScopedValueExample.scopedValueMemoryUsage(100);

            // VT 수에 관계없이 1KB
            assertThat(memory).isEqualTo(1024L);
        }

        @Test
        @DisplayName("ScopedValue는 ThreadLocal보다 항상 메모리 효율적")
        void scopedValueAlwaysMoreEfficient() throws Exception {
            int threadCount = 500;
            long tlMemory = ScopedValueExample.threadLocalMemoryUsage(threadCount);
            long svMemory = ScopedValueExample.scopedValueMemoryUsage(threadCount);

            assertThat(svMemory).isLessThan(tlMemory);
            assertThat(tlMemory / svMemory).isEqualTo(threadCount);
        }
    }

    // ========================================================================
    // SV-03: 중첩 scope (rebinding)
    // ========================================================================

    @Nested
    @DisplayName("SV-03: 중첩 scope (rebinding)")
    class NestedScopesTest {

        @Test
        @DisplayName("중첩 스코프에서 rebinding 후 복원된다")
        void nestedScopesRebinding() {
            var results = ScopedValueExample.nestedScopes();

            assertThat(results).containsExactly("outer", "inner", "outer");
        }

        @Test
        @DisplayName("inner scope에서 rebinding이 적용된다")
        void innerScopeRebinding() {
            var results = ScopedValueExample.nestedScopes();

            assertThat(results.get(1)).isEqualTo("inner");
        }

        @Test
        @DisplayName("inner scope 종료 후 outer 값으로 복원된다")
        void outerRestoredAfterInner() {
            var results = ScopedValueExample.nestedScopes();

            assertThat(results.get(0)).isEqualTo(results.get(2)); // 둘 다 "outer"
        }
    }

    // ========================================================================
    // SV-05: isBound() 패턴
    // ========================================================================

    @Nested
    @DisplayName("SV-05: isBound() 안전 패턴")
    class IsBoundTest {

        @Test
        @DisplayName("바인딩되지 않으면 not-bound 반환")
        void notBound() {
            String result = ScopedValueExample.safeGet();

            assertThat(result).isEqualTo("not-bound");
        }

        @Test
        @DisplayName("바인딩된 상태에서는 값을 반환")
        void bound() throws Exception {
            String result = ScopedValueExample.runAsUser("charlie", () ->
                    ScopedValueExample.safeGet()
            );

            assertThat(result).isEqualTo("charlie");
        }
    }

    // ========================================================================
    // SV-07: 메신저 ShardContext를 ScopedValue로 구현
    // ========================================================================

    @Nested
    @DisplayName("SV-07: ShardContext 전파")
    class ShardContextTest {

        @Test
        @DisplayName("executeInShard: shard 컨텍스트가 전파된다")
        void executeInShard() throws Exception {
            ShardContext result = ScopedValueExample.executeInShard(3, "tenant-A", () ->
                    ScopedValueExample.currentShard()
            );

            assertThat(result.shardId()).isEqualTo(3);
            assertThat(result.tenantId()).isEqualTo("tenant-A");
        }

        @Test
        @DisplayName("다른 shard에서 연속 실행")
        void consecutiveShards() throws Exception {
            ShardContext shard1 = ScopedValueExample.executeInShard(1, "tenant-X", () ->
                    ScopedValueExample.currentShard()
            );
            ShardContext shard2 = ScopedValueExample.executeInShard(2, "tenant-Y", () ->
                    ScopedValueExample.currentShard()
            );

            assertThat(shard1.shardId()).isEqualTo(1);
            assertThat(shard1.tenantId()).isEqualTo("tenant-X");
            assertThat(shard2.shardId()).isEqualTo(2);
            assertThat(shard2.tenantId()).isEqualTo("tenant-Y");
        }

        @Test
        @DisplayName("중첩된 shard 실행: 안쪽 shard가 우선")
        void nestedShardExecution() throws Exception {
            ShardContext result = ScopedValueExample.executeInShard(1, "outer", () -> {
                // 중첩 shard 실행
                return ScopedValueExample.executeInShard(2, "inner", () ->
                        ScopedValueExample.currentShard()
                );
            });

            assertThat(result.shardId()).isEqualTo(2);
            assertThat(result.tenantId()).isEqualTo("inner");
        }
    }
}
