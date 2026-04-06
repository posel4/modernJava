package modernjava.java25;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ScopedValue (JEP 506, 5th Preview in Java 25)
 *
 * ScopedValue는 ThreadLocal의 대안으로, Virtual Thread 환경에서
 * 불변(immutable)하고 스코프가 명확한 컨텍스트 전달을 제공한다.
 *
 * ThreadLocal과의 핵심 차이:
 * - 불변: 한번 바인딩하면 해당 스코프 내에서 변경 불가
 * - 자동 해제: 스코프 종료 시 자동으로 unbind
 * - 상속 효율: fork된 VT에 자동 전파 (복사 아닌 공유)
 * - 메모리 효율: VT마다 복사본을 갖지 않음
 */
public class ScopedValueExample {

    // ========================================================================
    // SV-01: ScopedValue 기본 - where().run() 패턴
    // ========================================================================

    /**
     * 현재 사용자 정보를 담는 ScopedValue.
     * static final로 선언하여 전역에서 접근 가능하되,
     * 값은 스코프 내에서만 바인딩된다.
     */
    public static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

    /**
     * 현재 바인딩된 사용자를 반환한다.
     * 바인딩되지 않은 경우 "anonymous"를 반환한다.
     */
    public static String getCurrentUser() {
        return CURRENT_USER.isBound() ? CURRENT_USER.get() : "anonymous";
    }

    /**
     * 지정된 사용자로 스코프를 열고 작업을 실행한다.
     * where()로 값을 바인딩하고, call()로 Callable을 실행한다.
     *
     * @param user 스코프 내에서 사용할 사용자 이름
     * @param task 실행할 작업
     * @return 작업의 결과
     */
    public static String runAsUser(String user, ScopedValue.CallableOp<String, Exception> task) throws Exception {
        return ScopedValue.where(CURRENT_USER, user).call(task);
    }

    // ========================================================================
    // SV-02: ThreadLocal vs ScopedValue 메모리 비교
    // ========================================================================

    /**
     * ThreadLocal: 각 VT마다 독립적인 복사본을 저장한다.
     * threadCount개의 Virtual Thread를 생성하고 각각 1KB 데이터를 ThreadLocal에 저장.
     *
     * @param threadCount 생성할 Virtual Thread 수
     * @return 대략적인 메모리 사용량 (bytes)
     */
    public static long threadLocalMemoryUsage(int threadCount) throws Exception {
        ThreadLocal<byte[]> local = new ThreadLocal<>();
        AtomicLong totalSize = new AtomicLong(0);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                byte[] data = new byte[1024]; // 1KB per VT
                local.set(data);
                totalSize.addAndGet(data.length);
                try {
                    Thread.sleep(10); // 잠시 유지
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    local.remove();
                }
            });
            threads.add(vt);
        }
        for (Thread t : threads) {
            t.join();
        }
        return totalSize.get(); // threadCount * 1024
    }

    /**
     * ScopedValue: 하나의 값을 모든 VT가 공유한다.
     * 복사본을 만들지 않으므로 메모리 효율적이다.
     *
     * @param threadCount 생성할 Virtual Thread 수
     * @return 대략적인 메모리 사용량 (bytes) - 공유이므로 1024 고정
     */
    public static final ScopedValue<byte[]> SHARED_DATA = ScopedValue.newInstance();

    public static long scopedValueMemoryUsage(int threadCount) throws Exception {
        byte[] sharedData = new byte[1024]; // 1KB, 모든 VT가 공유
        AtomicLong readCount = new AtomicLong(0);

        ScopedValue.where(SHARED_DATA, sharedData).run(() -> {
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                Thread vt = Thread.ofVirtual().start(() -> {
                    // 읽기만 함 - 복사본 없음
                    byte[] data = SHARED_DATA.get();
                    readCount.addAndGet(data.length);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(vt);
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        // 실제 메모리 사용량은 1KB (공유)
        return sharedData.length; // 1024, threadCount와 무관
    }

    // ========================================================================
    // SV-03: 중첩 scope (rebinding)
    // ========================================================================

    /**
     * 범용 컨텍스트 ScopedValue.
     */
    public static final ScopedValue<String> CONTEXT = ScopedValue.newInstance();

    /**
     * 중첩된 스코프에서 ScopedValue의 rebinding을 시연한다.
     *
     * outer scope에서 "outer" → inner scope에서 "inner"로 rebinding →
     * inner scope 종료 후 다시 "outer"로 복원된다.
     *
     * @return ["outer", "inner", "outer"] 순서의 결과 리스트
     */
    public static List<String> nestedScopes() {
        List<String> results = new ArrayList<>();
        ScopedValue.where(CONTEXT, "outer").run(() -> {
            results.add(CONTEXT.get()); // "outer"
            ScopedValue.where(CONTEXT, "inner").run(() -> {
                results.add(CONTEXT.get()); // "inner" (rebinding)
            });
            results.add(CONTEXT.get()); // "outer" (복원됨)
        });
        return results;
    }

    // ========================================================================
    // SV-05: isBound() 패턴
    // ========================================================================

    /**
     * ScopedValue가 바인딩되어 있는지 안전하게 확인한다.
     * 바인딩되지 않은 상태에서 get()을 호출하면 NoSuchElementException이 발생하므로,
     * isBound()로 먼저 확인하는 것이 안전한 패턴이다.
     *
     * @return 바인딩된 값 또는 "not-bound"
     */
    public static String safeGet() {
        if (CURRENT_USER.isBound()) {
            return CURRENT_USER.get();
        }
        return "not-bound";
    }

    // ========================================================================
    // SV-07: 메신저 ShardContext를 ScopedValue로 구현
    // ========================================================================

    /**
     * Shard ID를 담는 ScopedValue.
     */
    public static final ScopedValue<Integer> SHARD_ID = ScopedValue.newInstance();

    /**
     * Shard 컨텍스트 레코드: shardId와 tenantId를 포함한다.
     * 메신저에서 @ShardBy 어노테이션 기반 DB 라우팅에 사용될 수 있는 구조.
     */
    public record ShardContext(int shardId, String tenantId) {}

    /**
     * Shard 컨텍스트를 담는 ScopedValue.
     */
    public static final ScopedValue<ShardContext> SHARD_CONTEXT = ScopedValue.newInstance();

    /**
     * 특정 shard에서 작업을 실행한다.
     * @ShardBy AOP가 호출하는 메서드를 시뮬레이션한다.
     *
     * @param shardId  샤드 번호
     * @param tenantId 테넌트 ID
     * @param task     실행할 작업
     * @return 작업의 결과
     */
    public static <T> T executeInShard(int shardId, String tenantId, ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return ScopedValue.where(SHARD_CONTEXT, new ShardContext(shardId, tenantId)).call(task);
    }

    /**
     * 현재 shard 컨텍스트를 조회한다.
     * Repository 레이어에서 현재 연결할 DB shard를 결정할 때 사용.
     *
     * @return 현재 ShardContext
     * @throws java.util.NoSuchElementException 바인딩되지 않은 경우
     */
    public static ShardContext currentShard() {
        return SHARD_CONTEXT.get();
    }
}
