package playground.java25;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Structured Concurrency (JEP 505, 5th Preview in Java 25)
 *
 * Structured Concurrency는 동시 작업의 생명주기를 구조적으로 관리한다.
 * try-with-resources로 스코프를 열고, fork()로 작업을 시작하고,
 * join()으로 완료를 기다린다. 스코프 종료 시 모든 작업이 자동 정리된다.
 *
 * Java 25 API 변경사항:
 * - StructuredTaskScope.open() 사용 (생성자 대신)
 * - Joiner를 통한 결과 수집 전략 지정
 * - Config function으로 타임아웃 등 설정
 */
public class StructuredConcurrencyExample {

    // ========================================================================
    // SC-01: 기본 패턴 - 병렬 작업 후 결합
    // ========================================================================

    /**
     * 사용자 프로필 레코드
     */
    public static record UserProfile(String name, int age) {}

    /**
     * 두 작업을 병렬로 실행하여 사용자 프로필을 조합한다.
     * 이름 조회와 나이 조회를 동시에 수행한다.
     *
     * @param userId 사용자 ID
     * @return 조합된 UserProfile
     */
    public static UserProfile fetchUserProfile(String userId) throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<String> name = scope.fork(() -> {
                Thread.sleep(100);
                return "User-" + userId;
            });
            Subtask<Integer> age = scope.fork(() -> {
                Thread.sleep(150);
                return 25;
            });
            scope.join();
            return new UserProfile(name.get(), age.get());
        }
    }

    // ========================================================================
    // SC-02: 경쟁 패턴 (가장 빠른 결과 사용)
    // ========================================================================

    /**
     * 여러 소스에서 동시에 데이터를 조회하고, 가장 빨리 응답한 결과를 사용한다.
     * anySuccessfulResultOrThrow()는 첫 번째 성공 결과를 반환하고
     * 나머지 작업을 자동으로 취소한다.
     *
     * @return 가장 빨리 응답한 소스의 결과
     */
    public static String fetchFromFastestSource() throws Exception {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> {
                Thread.sleep(200);
                return "from-cache";
            });
            scope.fork(() -> {
                Thread.sleep(50);
                return "from-db";
            });
            scope.fork(() -> {
                Thread.sleep(300);
                return "from-remote";
            });
            return scope.join();
        }
    }

    // ========================================================================
    // SC-03: 타임아웃 + 자동 취소
    // ========================================================================

    /**
     * 타임아웃이 설정된 경쟁 패턴.
     * 지정된 시간 내에 완료된 가장 빠른 결과를 반환한다.
     *
     * @param timeout 최대 대기 시간
     * @return 타임아웃 내에 완료된 결과
     */
    public static String fetchWithTimeout(Duration timeout) throws Exception {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>anySuccessfulResultOrThrow(),
                cf -> cf.withTimeout(timeout))) {
            scope.fork(() -> {
                Thread.sleep(5000); // 5초 걸리는 작업
                return "slow-result";
            });
            scope.fork(() -> {
                Thread.sleep(100); // 100ms 걸리는 작업
                return "fast-result";
            });
            return scope.join();
        }
    }

    // ========================================================================
    // SC-04: 에러 전파 (한 작업 실패 시 상태 확인)
    // ========================================================================

    /**
     * 병렬 작업 중 하나가 실패한 경우의 처리를 시연한다.
     * join() 후 각 Subtask의 state()를 확인하여 실패를 감지한다.
     *
     * @return 성공 시 결과 문자열
     * @throws RuntimeException 작업 실패 시
     */
    public static String fetchWithErrorPropagation() throws Exception {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>awaitAll())) {
            Subtask<String> task1 = scope.fork(() -> {
                Thread.sleep(100);
                return "success";
            });
            Subtask<String> task2 = scope.fork(() -> {
                Thread.sleep(50);
                throw new RuntimeException("DB connection failed");
            });
            scope.join();
            // Subtask 상태 확인: awaitAll()은 실패해도 join()이 예외를 던지지 않음
            if (task2.state() == Subtask.State.FAILED) {
                Throwable ex = task2.exception();
                if (ex instanceof RuntimeException re) throw re;
                if (ex instanceof Exception e) throw e;
                throw new RuntimeException(ex);
            }
            return task1.get();
        }
    }

    // ========================================================================
    // SC-05: CompletableFuture.allOf() vs Structured Concurrency 비교
    // ========================================================================

    /**
     * Before: CompletableFuture 방식.
     * 에러 처리가 복잡하고, 스레드 누수 가능성이 있다.
     * 하나가 실패해도 나머지가 계속 실행될 수 있다.
     */
    public static String withCompletableFuture() throws Exception {
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result1";
        });
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result2";
        });
        CompletableFuture.allOf(f1, f2).join();
        return f1.join() + "+" + f2.join();
    }

    /**
     * After: Structured Concurrency 방식.
     * 명확한 생명주기, 자동 취소, 구조화된 에러 전파.
     * try-with-resources가 모든 작업의 정리를 보장한다.
     */
    public static String withStructuredConcurrency() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            var f1 = scope.fork(() -> {
                Thread.sleep(100);
                return "result1";
            });
            var f2 = scope.fork(() -> {
                Thread.sleep(100);
                return "result2";
            });
            scope.join();
            return f1.get() + "+" + f2.get();
        }
    }

    // ========================================================================
    // SC-06: 메신저 병렬 조회 - 채널 + 멤버 + 메시지 동시 조회
    // ========================================================================

    public record Channel(long id, String name) {}
    public record Member(long id, String displayName) {}
    public record Message(long id, String content) {}
    public record ChannelDetail(Channel channel, List<Member> members, List<Message> messages) {}

    /**
     * 채널 상세 정보를 병렬로 조회한다.
     * 채널 정보, 멤버 목록, 메시지 목록을 동시에 가져온다.
     * 실제 메신저에서는 각각 다른 DB 테이블/서비스에서 조회할 수 있다.
     *
     * @param channelId 채널 ID
     * @return 채널 상세 정보 (채널 + 멤버 + 메시지)
     */
    public static ChannelDetail fetchChannelDetail(long channelId) throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<Channel> channelTask = scope.fork(() -> {
                Thread.sleep(50); // DB 조회 시뮬레이션
                return new Channel(channelId, "general");
            });
            Subtask<List<Member>> membersTask = scope.fork(() -> {
                Thread.sleep(80);
                return List.of(new Member(1, "Alice"), new Member(2, "Bob"));
            });
            Subtask<List<Message>> messagesTask = scope.fork(() -> {
                Thread.sleep(100);
                return List.of(new Message(101, "Hello"), new Message(102, "World"));
            });
            scope.join();
            return new ChannelDetail(
                    channelTask.get(),
                    membersTask.get(),
                    messagesTask.get()
            );
        }
    }

    // ========================================================================
    // SV-04: StructuredTaskScope와 ScopedValue 자동 전파
    // ========================================================================

    /**
     * Request ID를 담는 ScopedValue.
     * fork된 Virtual Thread에 자동으로 전파된다.
     */
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    /**
     * ScopedValue가 fork된 VT에 자동으로 전파되는 것을 시연한다.
     * 부모 스코프에서 바인딩된 REQUEST_ID가 자식 작업에서도 접근 가능하다.
     *
     * @param requestId 요청 ID
     * @return 각 작업에서 읽은 REQUEST_ID 목록
     */
    public static List<String> scopedValueAutoInheritance(String requestId) throws Exception {
        return ScopedValue.where(REQUEST_ID, requestId).call(() -> {
            try (var scope = StructuredTaskScope.open()) {
                Subtask<String> task1 = scope.fork(() ->
                        "task1-sees-" + REQUEST_ID.get()
                );
                Subtask<String> task2 = scope.fork(() ->
                        "task2-sees-" + REQUEST_ID.get()
                );
                scope.join();
                return List.of(task1.get(), task2.get());
            }
        });
    }
}
