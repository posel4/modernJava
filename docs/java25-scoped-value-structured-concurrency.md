# Java 25 ScopedValue + Structured Concurrency (5th Preview)

> Virtual Thread 친화적인 불변 컨텍스트 전파(ScopedValue)와 구조화된 병렬 작업 관리(Structured Concurrency)로 멀티스레딩 코드를 우아하고 안전하게 작성하기.

---

## 1. ScopedValue (JEP 506) - 5th Preview

### 한줄 요약
**ThreadLocal을 대체하는 불변(immutable) 컨텍스트 전파 메커니즘. Virtual Thread 환경에서 메모리 효율적이고 명시적인 스코프를 제공.**

### 왜 필요한가?

#### 문제 1: ThreadLocal의 메모리 낭비

ThreadLocal의 원리:
```
ThreadLocal = 각 스레드마다 독립적인 복사본을 저장

1000개의 Virtual Thread 생성
│
└─ ThreadLocal<byte[1024]> data;
   ├─ VT-1 → copy of 1KB
   ├─ VT-2 → copy of 1KB
   ├─ VT-3 → copy of 1KB
   └─ ... (1000개)

   총 메모리: 1000 * 1024 = 1MB (복사)
```

**Virtual Thread 환경에서의 문제:**
- VT는 수백만 개 동시 실행 가능
- 각 VT마다 복사본 생성 → 메모리 폭증
- ThreadLocal이 설계되지 않은 환경에서 무리하게 사용

#### 문제 2: ThreadLocal의 가변성

```java
ThreadLocal<StringBuilder> sb = new ThreadLocal<>();
sb.set(new StringBuilder("Alice"));
sb.get().append(" corrupted!");  // 값을 변경할 수 있음
```

**ThreadLocal의 단점:**
- 값이 변경 가능 (mutable) → 버그 가능성
- 자식 스레드 상속 시 복사 비용
- 스코프가 명확하지 않음 (set/remove 수동 관리)

#### ScopedValue의 솔루션

```
ScopedValue = 불변(immutable) 값을 명시적 스코프 내에서 공유

ScopedValue.where(CURRENT_USER, "alice").run(() -> {
    // ← 여기서만 "alice"에 접근 가능
    String user = CURRENT_USER.get();  // "alice"
});  // ← 스코프 종료, 자동 unbound
```

**ScopedValue의 장점:**
- **불변**: 한번 바인딩하면 스코프 내에서 변경 불가
- **명시적 스코프**: where().run() 또는 where().call()로 시작/종료 명확
- **메모리 효율**: fork된 VT에 자동 전파 (복사 아닌 공유)
- **자동 정리**: 스코프 종료 시 자동으로 unbind

### Before / After 코드

#### Before (ThreadLocal)

```java
// ThreadLocal: 각 VT마다 복사본 생성
ThreadLocal<String> currentUser = new ThreadLocal<>();

public void processUser(String userId) {
    currentUser.set(userId);
    try {
        // 작업 수행
        String user = currentUser.get();
        saveToDatabase(user);
    } finally {
        currentUser.remove();  // 수동으로 정리 필요!
    }
}

// 문제점:
// 1. remove() 누락 → 메모리 누수
// 2. set()/remove() 전후 처리 필요
// 3. VT 환경에서 메모리 폭증
// 4. 값이 가변 → 동기화 버그 가능성
```

#### After (ScopedValue)

```java
// ScopedValue: 불변 값을 명시적 스코프로 관리
ScopedValue<String> currentUser = ScopedValue.newInstance();

public void processUser(String userId) {
    ScopedValue.where(currentUser, userId).run(() -> {
        // 작업 수행
        String user = currentUser.get();
        saveToDatabase(user);
    });
    // 스코프 종료 → 자동 unbound, remove() 불필요!
}

// 장점:
// 1. 자동 정리 (try-finally 불필요)
// 2. 불변값 → 동기화 버그 불가능
// 3. VT 환경에서 메모리 효율적 (공유)
// 4. 스코프가 명확함
```

### 실제 코드 예제

#### SV-01: ScopedValue 기본 - where().run() 패턴

```java
// SourceFile: ScopedValueExample.java

public static final ScopedValue<String> CURRENT_USER =
    ScopedValue.newInstance();

public static String getCurrentUser() {
    return CURRENT_USER.isBound() ? CURRENT_USER.get() : "anonymous";
}

public static String runAsUser(String user, ScopedValue.CallableOp<String, Exception> task)
    throws Exception {
    return ScopedValue.where(CURRENT_USER, user).call(task);
}

// 사용
String result = runAsUser("alice", () -> getCurrentUser());
// result = "alice"
```

**테스트 (ScopedValueTest.java):**
```java
@Test
void runAsUser() throws Exception {
    String result = ScopedValueExample.runAsUser("alice", () ->
            ScopedValueExample.getCurrentUser()
    );
    assertThat(result).isEqualTo("alice");
}

@Test
void scopeEndsAfterRun() throws Exception {
    ScopedValueExample.runAsUser("bob", () -> "done");
    // 스코프 종료 후
    assertThat(ScopedValueExample.getCurrentUser()).isEqualTo("anonymous");
}
```

**주요 학습 포인트:**
- `ScopedValue.where(SV, value)` - 값을 바인딩
- `.run(Runnable)` - 값을 반환하지 않는 작업 실행
- `.call(Callable)` - 값을 반환하는 작업 실행
- 스코프 종료 시 자동으로 unbind

#### SV-02: ThreadLocal vs ScopedValue 메모리 비교

```java
// SourceFile: ScopedValueExample.java

// ThreadLocal: VT마다 복사본 생성
public static long threadLocalMemoryUsage(int threadCount) throws Exception {
    ThreadLocal<byte[]> local = new ThreadLocal<>();
    AtomicLong totalSize = new AtomicLong(0);

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
        Thread vt = Thread.ofVirtual().start(() -> {
            byte[] data = new byte[1024];  // 1KB per VT
            local.set(data);
            totalSize.addAndGet(data.length);
            try {
                Thread.sleep(10);
            } finally {
                local.remove();
            }
        });
        threads.add(vt);
    }
    for (Thread t : threads) {
        t.join();
    }
    return totalSize.get();  // threadCount * 1024
}

// ScopedValue: 모든 VT가 공유
public static final ScopedValue<byte[]> SHARED_DATA =
    ScopedValue.newInstance();

public static long scopedValueMemoryUsage(int threadCount) throws Exception {
    byte[] sharedData = new byte[1024];  // 1KB, 모든 VT가 공유
    AtomicLong readCount = new AtomicLong(0);

    ScopedValue.where(SHARED_DATA, sharedData).run(() -> {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread vt = Thread.ofVirtual().start(() -> {
                byte[] data = SHARED_DATA.get();  // 복사본 없음!
                readCount.addAndGet(data.length);
                try {
                    Thread.sleep(10);
                } finally {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(vt);
        }
        for (Thread t : threads) {
            t.join();
        }
    });
    return sharedData.length;  // 1024 (공유이므로 threadCount와 무관)
}
```

**테스트 (ScopedValueTest.java):**
```java
@Test
void threadLocalMemory() throws Exception {
    long memory = ScopedValueExample.threadLocalMemoryUsage(100);
    // 100개 VT * 1KB = 100KB
    assertThat(memory).isEqualTo(100 * 1024L);
}

@Test
void scopedValueMemory() throws Exception {
    long memory = ScopedValueExample.scopedValueMemoryUsage(100);
    // VT 수에 관계없이 1KB (공유)
    assertThat(memory).isEqualTo(1024L);
}

@Test
void scopedValueAlwaysMoreEfficient() throws Exception {
    int threadCount = 500;
    long tlMemory = ScopedValueExample.threadLocalMemoryUsage(threadCount);
    long svMemory = ScopedValueExample.scopedValueMemoryUsage(threadCount);

    assertThat(svMemory).isLessThan(tlMemory);
    assertThat(tlMemory / svMemory).isEqualTo(threadCount);
}
```

**메모리 효과:**
```
100개의 VT:
- ThreadLocal: 100 * 1024 = 100KB
- ScopedValue: 1024 = 1KB

500배 이상의 VT:
- ThreadLocal: 500 * 1024 = 512KB
- ScopedValue: 1024 = 1KB

메모리 절감: 500배!
```

#### SV-03: 중첩 Scope (Rebinding)

```java
// SourceFile: ScopedValueExample.java

public static final ScopedValue<String> CONTEXT =
    ScopedValue.newInstance();

public static List<String> nestedScopes() {
    List<String> results = new ArrayList<>();
    ScopedValue.where(CONTEXT, "outer").run(() -> {
        results.add(CONTEXT.get());  // "outer"

        ScopedValue.where(CONTEXT, "inner").run(() -> {
            results.add(CONTEXT.get());  // "inner" (rebinding)
        });

        results.add(CONTEXT.get());  // "outer" (복원됨)
    });
    return results;
}
```

**테스트 (ScopedValueTest.java):**
```java
@Test
void nestedScopesRebinding() {
    var results = ScopedValueExample.nestedScopes();
    assertThat(results).containsExactly("outer", "inner", "outer");
}
```

**실행 흐름:**
```
1. ScopedValue.where(CONTEXT, "outer").run(() -> {
   ┌─ CONTEXT = "outer" 바인딩
   │
   ├─ results.add(CONTEXT.get()) // → "outer"
   │
   ├─ ScopedValue.where(CONTEXT, "inner").run(() -> {
   │  ┌─ CONTEXT rebinding → "inner"
   │  │
   │  ├─ results.add(CONTEXT.get()) // → "inner"
   │  │
   │  └─ rebinding 종료 → "outer"로 복원
   │
   ├─ results.add(CONTEXT.get()) // → "outer"
   │
   └─ 스코프 종료 → unbound

결과: ["outer", "inner", "outer"]
```

#### SV-04: StructuredTaskScope에서 ScopedValue 자동 전파 (중요!)

```java
// SourceFile: StructuredConcurrencyExample.java

public static final ScopedValue<String> REQUEST_ID =
    ScopedValue.newInstance();

public static List<String> scopedValueAutoInheritance(String requestId)
    throws Exception {
    return ScopedValue.where(REQUEST_ID, requestId).call(() -> {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<String> task1 = scope.fork(() ->
                    "task1-sees-" + REQUEST_ID.get()  // ← fork된 VT에서 자동 접근 가능!
            );
            Subtask<String> task2 = scope.fork(() ->
                    "task2-sees-" + REQUEST_ID.get()  // ← fork된 VT에서 자동 접근 가능!
            );
            scope.join();
            return List.of(task1.get(), task2.get());
        }
    });
}
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void autoInheritance() throws Exception {
    var results = StructuredConcurrencyExample.scopedValueAutoInheritance("req-123");

    assertThat(results).containsExactly(
            "task1-sees-req-123",
            "task2-sees-req-123"
    );
}
```

**중요한 동작:**
```
부모 VT (REQUEST_ID = "req-123")
│
├─ ScopedValue.where() ← "req-123" 바인딩
│
└─ scope.fork()
   ├─ fork된 VT-1 (REQUEST_ID 자동 전파!)
   │  └─ REQUEST_ID.get() = "req-123" ✓
   │
   └─ fork된 VT-2 (REQUEST_ID 자동 전파!)
      └─ REQUEST_ID.get() = "req-123" ✓

ThreadLocal과의 차이:
- ThreadLocal: 자식 스레드에 복사 (비용 높음)
- ScopedValue: 자식 스레드에서 부모 값 공유 (비용 없음)
```

#### SV-05: isBound() 안전 접근 패턴

```java
// SourceFile: ScopedValueExample.java

public static String safeGet() {
    if (CURRENT_USER.isBound()) {
        return CURRENT_USER.get();
    }
    return "not-bound";
}
```

**테스트 (ScopedValueTest.java):**
```java
@Test
void notBound() {
    String result = ScopedValueExample.safeGet();
    assertThat(result).isEqualTo("not-bound");
}

@Test
void bound() throws Exception {
    String result = ScopedValueExample.runAsUser("charlie", () ->
            ScopedValueExample.safeGet()
    );
    assertThat(result).isEqualTo("charlie");
}
```

**중요한 패턴:**
```java
// ❌ 위험: NoSuchElementException 발생 가능
String user = CURRENT_USER.get();

// ✓ 안전: 먼저 확인
if (CURRENT_USER.isBound()) {
    String user = CURRENT_USER.get();
}
```

#### SV-07: 메신저 ShardContext를 ScopedValue로 구현

```java
// SourceFile: ScopedValueExample.java

public record ShardContext(int shardId, String tenantId) {}

public static final ScopedValue<ShardContext> SHARD_CONTEXT =
    ScopedValue.newInstance();

public static <T> T executeInShard(int shardId, String tenantId,
        ScopedValue.CallableOp<T, Exception> task) throws Exception {
    return ScopedValue.where(SHARD_CONTEXT, new ShardContext(shardId, tenantId))
            .call(task);
}

public static ShardContext currentShard() {
    return SHARD_CONTEXT.get();
}
```

**사용 예시 (메신저에서의 @ShardBy AOP):**
```java
// Repository 레이어
public class MessageRepository {
    public void save(Message msg) {
        ShardContext shard = ScopedValueExample.currentShard();
        // shard.shardId() 기반으로 올바른 DB 연결 선택
        getShardConnection(shard.shardId()).insert(msg);
    }
}

// 사용
public void sendMessage(long channelId, String text) throws Exception {
    ShardContext shard = getShardForChannel(channelId);
    ScopedValueExample.executeInShard(shard.shardId(), shard.tenantId(), () -> {
        Message msg = new Message(text);
        messageRepository.save(msg);  // ← currentShard() 접근 가능
        return null;
    });
}
```

**테스트 (ScopedValueTest.java):**
```java
@Test
void executeInShard() throws Exception {
    ShardContext result = ScopedValueExample.executeInShard(3, "tenant-A", () ->
            ScopedValueExample.currentShard()
    );

    assertThat(result.shardId()).isEqualTo(3);
    assertThat(result.tenantId()).isEqualTo("tenant-A");
}

@Test
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
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| SV-01 | `where().run()` / `where().call()` 기본 패턴 | 사용자 컨텍스트 바인딩 및 작업 실행 |
| SV-02 | ThreadLocal vs ScopedValue 메모리 비교 | 100개~500개 VT, 500배 메모리 절감 |
| SV-03 | 중첩 Scope (rebinding) | outer → inner → outer로 복원 |
| SV-04 | StructuredTaskScope에서 자동 전파 (중요!) | fork된 VT에서 부모 ScopedValue 자동 접근 |
| SV-05 | `isBound()` 안전 접근 | unbind된 상태에서 NoSuchElementException 방지 |
| SV-07 | 메신저 ShardContext (DB 라우팅) | tenantId + shardId 기반 DB 선택 |

### 메신저 서버 적용 포인트

#### 1. ShardContext 전파

```java
// 메신저의 기존 @ShardBy 어노테이션 구현을 ScopedValue로 개선

// Before (ThreadLocal)
public class ShardContextHolder {
    private static ThreadLocal<ShardContext> holder = new ThreadLocal<>();

    public static void setContext(ShardContext ctx) {
        holder.set(ctx);
    }

    public static ShardContext getContext() {
        return holder.get();  // null 가능성
    }
}

// After (ScopedValue)
public class ShardContextHolder {
    public static final ScopedValue<ShardContext> CONTEXT =
        ScopedValue.newInstance();

    public static <T> T executeInShard(ShardContext ctx,
            ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return ScopedValue.where(CONTEXT, ctx).call(task);
    }

    public static ShardContext getContext() {
        return CONTEXT.get();  // unbind되면 명확한 예외
    }
}
```

#### 2. RequestContext 및 TenantId 전파

```java
// HTTP 요청별 컨텍스트

public record RequestContext(
    String requestId,
    String tenantId,
    long userId,
    long timestamp
) {}

public static final ScopedValue<RequestContext> REQUEST_CONTEXT =
    ScopedValue.newInstance();

// Interceptor에서 설정
public class RequestContextInterceptor {
    public void postHandle(HttpServletRequest request) throws Exception {
        RequestContext context = extractContext(request);

        // VT 기반 비동기 처리도 자동으로 context 전파
        ScopedValue.where(REQUEST_CONTEXT, context).run(() -> {
            processRequest();
        });
    }
}

// 메시지 저장 시
public class MessageService {
    public void saveMessage(Message msg) throws Exception {
        RequestContext ctx = REQUEST_CONTEXT.get();
        msg.setTenantId(ctx.tenantId());
        msg.setUserId(ctx.userId());

        // ShardContext와 함께 사용
        ShardContext shard = getShard(ctx.tenantId());
        ScopedValueExample.executeInShard(shard.shardId(), ctx.tenantId(),
            () -> messageRepository.save(msg)
        );
    }
}
```

#### 3. Structured Concurrency와의 시너지

```java
// 병렬 조회 시 자동으로 context 전파

public ChannelDetail fetchChannelDetail(long channelId) throws Exception {
    RequestContext reqCtx = REQUEST_CONTEXT.get();

    // REQUEST_CONTEXT가 fork된 VT에 자동 전파
    try (var scope = StructuredTaskScope.open()) {
        Subtask<Channel> channelTask = scope.fork(() -> {
            // REQUEST_CONTEXT 자동 접근 가능
            log.info("Fetching channel for tenant: {}", REQUEST_CONTEXT.get().tenantId());
            return channelRepository.findById(channelId);
        });

        Subtask<List<Member>> membersTask = scope.fork(() -> {
            // REQUEST_CONTEXT 자동 접근 가능
            return memberRepository.findByChannel(channelId);
        });

        Subtask<List<Message>> messagesTask = scope.fork(() -> {
            // REQUEST_CONTEXT 자동 접근 가능
            return messageRepository.findByChannel(channelId,
                REQUEST_CONTEXT.get().tenantId());
        });

        scope.join();
        return new ChannelDetail(
            channelTask.get(),
            membersTask.get(),
            messagesTask.get()
        );
    }
}
```

### 팀원에게 한마디

**ScopedValue**는 "ThreadLocal의 정신적 후계자"입니다.

**역사:**
1. Java 1.0 (1995): ThreadLocal 도입, "스레드별 저장소"
2. Java 5~20: ThreadLocal 주용, 하지만 누수와 복잡성 증가
3. Java 21: Virtual Thread 도입, "ThreadLocal은 VT에 맞지 않음"
4. Java 25: ScopedValue 5th Preview, "불변, 명시적, 효율적"

**메신저 서버 관점:**

**기존 (ThreadLocal - Spring Boot):**
```java
@Aspect
@Component
public class ShardByAspect {
    private static final ThreadLocal<ShardContext> contextHolder = new ThreadLocal<>();

    @Around("@annotation(ShardBy)")
    public Object sharding(ProceedingJoinPoint pjp, ShardBy shardBy) throws Throwable {
        ShardContext ctx = getContext();
        contextHolder.set(ctx);
        try {
            return pjp.proceed();
        } finally {
            contextHolder.remove();  // 누락 가능한 수동 정리
        }
    }
}
```

**미래 (ScopedValue - Virtual Thread 시대):**
```java
@Aspect
@Component
public class ShardByAspect {
    public static final ScopedValue<ShardContext> SHARD = ScopedValue.newInstance();

    @Around("@annotation(ShardBy)")
    public Object sharding(ProceedingJoinPoint pjp, ShardBy shardBy) throws Exception {
        ShardContext ctx = getContext();
        return ScopedValue.where(SHARD, ctx).call(() -> {
            try {
                return pjp.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        // 자동 정리, fork된 VT에도 자동 전파!
    }
}
```

**가치:**
- **메모리**: VT 수백만 개 환경에서 500배 이상 절감
- **안전성**: 불변값으로 동기화 버그 제거
- **명확성**: where().run() / where().call()로 스코프 명확
- **자동화**: Structured Concurrency와 통합으로 자동 전파

특히 메신저의 **멀티테넌트 + 샤딩 구조**에서 ScopedValue는 TenantId와 ShardId를 우아하게 전파하면서도 메모리 걱정 없이 VT 기반 확장을 가능하게 합니다.

---

## 2. Structured Concurrency (JEP 505) - 5th Preview

### 한줄 요약
**병렬 작업의 생명주기를 구조적으로 관리. try-with-resources로 fork/join을 명확하게 하고, 타임아웃/취소/에러 전파를 자동으로 처리.**

### 왜 필요한가?

#### 문제 1: CompletableFuture의 복잡성

CompletableFuture의 원리:
```java
// ❌ CompletableFuture: 생명주기 관리가 복잡
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
    return "result1";
});
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
    return "result2";
});

// 모두 완료 대기
CompletableFuture.allOf(f1, f2).join();

// 결과 얻기
String result = f1.join() + "+" + f2.join();

// 문제점:
// 1. allOf().join()과 개별 join()이 둘 다 필요
// 2. f1 실패 시에도 f2는 계속 실행됨
// 3. 타임아웃 설정 복잡
// 4. 명시적 취소 어려움
// 5. 에러 전파가 까다로움
```

#### 문제 2: 스레드 누수 가능성

```
CompletableFuture.supplyAsync()
│
└─ ForkJoinPool.commonPool() (기본 스레드풀)
   ├─ 스레드-1 (작업 실행)
   ├─ 스레드-2
   └─ ...

문제: 예외 발생 시 스레드가 풀에 반환되지 않음
     → 스레드 누수 → OOM
```

#### 문제 3: 명확하지 않은 생명주기

```java
// 언제 시작? 언제 완료? 언제 취소?
CompletableFuture<String> future =
    CompletableFuture.supplyAsync(() -> heavyTask());

// 3초 후 취소?
ScheduledExecutorService scheduler = ...;
scheduler.schedule(() -> future.cancel(true), 3, TimeUnit.SECONDS);
// 하지만 실제로 cancel 확인 불가

// 결과 얻기?
try {
    String result = future.get(1, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // 타임아웃, 하지만 내부 작업은 계속 실행 중?
}
```

#### Structured Concurrency의 솔루션

```
Structured Concurrency = 병렬 작업을 구조화

try (var scope = StructuredTaskScope.open()) {
    Subtask<String> task1 = scope.fork(() -> operation1());
    Subtask<String> task2 = scope.fork(() -> operation2());

    scope.join();  // 모든 작업 완료 대기

    return new Result(task1.get(), task2.get());
}
// ← 스코프 종료: 모든 작업 자동 정리, 취소, 메모리 해제
```

**구조화된 동시성의 장점:**
- **명확한 생명주기**: fork → join → 스코프 종료
- **자동 정리**: try-with-resources로 누수 불가능
- **타임아웃**: config 함수로 간단히 설정
- **자동 취소**: 한 작업 실패 → 나머지 자동 취소
- **에러 전파**: 예외 처리가 구조적

### Before / After 코드

#### Before (CompletableFuture)

```java
// Java 8~20: CompletableFuture

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

// 문제:
// 1. allOf().join()과 f1.join() 둘 다 필요
// 2. 예외 처리 복잡
// 3. 타임아웃 설정 어려움
// 4. 취소 명시적 필요
// 5. 실패 시 나머지 작업 계속 실행 가능
```

#### After (Structured Concurrency)

```java
// Java 21+: Structured Concurrency

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

// 장점:
// 1. 명확한 fork/join
// 2. 스코프 종료 시 모든 작업 정리
// 3. 타임아웃은 config에서 설정
// 4. 한 작업 실패 → 나머지 자동 취소
// 5. try-with-resources로 누수 불가능
```

### 실제 코드 예제

#### SC-01: 기본 패턴 - 병렬 작업 후 결합

```java
// SourceFile: StructuredConcurrencyExample.java

public record UserProfile(String name, int age) {}

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
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void bothFieldsPopulated() throws Exception {
    UserProfile profile = StructuredConcurrencyExample.fetchUserProfile("123");

    assertThat(profile.name()).isEqualTo("User-123");
    assertThat(profile.age()).isEqualTo(25);
}
```

**실행 흐름:**
```
scope.open()
│
├─ fork(name task)     ┐
├─ fork(age task)      ├─ 병렬 실행!
│                      │ name: 100ms
│                      │ age:  150ms
│                      │ 총 시간: ~150ms (병렬)
│
├─ scope.join()        ┘ 모두 완료 대기
│
├─ name.get() = "User-123"
├─ age.get() = 25
│
└─ 스코프 종료 → 모든 작업 정리
   (혹시 실패했다면 자동 취소)
```

#### SC-02: 경쟁 패턴 (가장 빠른 결과 사용)

```java
// SourceFile: StructuredConcurrencyExample.java

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
        return scope.join();  // 가장 빠른 결과 반환, 나머지 취소
    }
}
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void fastestSourceWins() throws Exception {
    String result = StructuredConcurrencyExample.fetchFromFastestSource();

    assertThat(result).isEqualTo("from-db");  // 50ms가 가장 빠름
}
```

**경쟁의 의미:**
```
세 개의 소스 동시 조회:
├─ Cache (200ms)
├─ DB     (50ms)   ← 가장 빠름!
└─ Remote (300ms)

anySuccessfulResultOrThrow() Joiner:
- DB에서 50ms 후 "from-db" 반환
- 나머지 Cache, Remote 자동 취소
- 불필요한 작업 최소화
```

#### SC-03: 타임아웃 + 자동 취소

```java
// SourceFile: StructuredConcurrencyExample.java

public static String fetchWithTimeout(Duration timeout) throws Exception {
    try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.<String>anySuccessfulResultOrThrow(),
            cf -> cf.withTimeout(timeout))) {  // 타임아웃 설정
        scope.fork(() -> {
            Thread.sleep(5000);  // 5초 (타임아웃 초과)
            return "slow-result";
        });
        scope.fork(() -> {
            Thread.sleep(100);   // 100ms (충분함)
            return "fast-result";
        });
        return scope.join();
    }
}
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void withinTimeout() throws Exception {
    String result = StructuredConcurrencyExample.fetchWithTimeout(
        Duration.ofSeconds(2)
    );

    assertThat(result).isEqualTo("fast-result");
}

@Test
void timeoutExceeded() {
    assertThatThrownBy(() ->
            StructuredConcurrencyExample.fetchWithTimeout(
                Duration.ofMillis(10)
            )
    ).isInstanceOf(Exception.class);
}
```

**타임아웃의 동작:**
```
config: Duration = 2초

fork: slow-result (5초)
fork: fast-result (100ms)

timeline:
0ms   ──── 100ms ──── 2000ms
      ↑             ↑
      fast 완료     timeout!

결과:
- 100ms: fast-result 완료
- 2000ms: timeout 도달 → slow-result 자동 취소
- 반환: "fast-result" (또는 timeout exception)
```

#### SC-04: 에러 전파 (한 작업 실패 → 상태 확인)

```java
// SourceFile: StructuredConcurrencyExample.java

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

        // awaitAll()은 실패해도 join()이 예외 던지지 않음
        // 각 Subtask의 state() 확인 필요
        if (task2.state() == Subtask.State.FAILED) {
            Throwable ex = task2.exception();
            if (ex instanceof RuntimeException re) throw re;
            if (ex instanceof Exception e) throw e;
            throw new RuntimeException(ex);
        }
        return task1.get();
    }
}
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void errorPropagated() {
    assertThatThrownBy(() ->
            StructuredConcurrencyExample.fetchWithErrorPropagation()
    ).isInstanceOf(RuntimeException.class)
            .hasMessage("DB connection failed");
}
```

**Subtask 상태:**
```
Subtask.State:
- SUCCESS: 정상 완료
- FAILED:  예외 발생
- CANCELLED: 취소됨

사용:
if (subtask.state() == Subtask.State.FAILED) {
    Throwable ex = subtask.exception();
    // 예외 처리
}

if (subtask.state() == Subtask.State.SUCCESS) {
    T result = subtask.get();
    // 결과 사용
}
```

#### SC-05: CompletableFuture vs Structured Concurrency 비교

```java
// SourceFile: StructuredConcurrencyExample.java

// CompletableFuture
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

// Structured Concurrency
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
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void completableFutureResult() throws Exception {
    String result = StructuredConcurrencyExample.withCompletableFuture();
    assertThat(result).isEqualTo("result1+result2");
}

@Test
void structuredConcurrencyResult() throws Exception {
    String result = StructuredConcurrencyExample.withStructuredConcurrency();
    assertThat(result).isEqualTo("result1+result2");
}

@Test
void sameResult() throws Exception {
    String cfResult = StructuredConcurrencyExample.withCompletableFuture();
    String scResult = StructuredConcurrencyExample.withStructuredConcurrency();

    assertThat(cfResult).isEqualTo(scResult);
}
```

**비교 표:**
| 항목 | CompletableFuture | Structured Concurrency |
|------|-------------------|------------------------|
| 생명주기 | 부정확 (allOf + 개별 join) | 명확 (try-with-resources) |
| 타임아웃 | 복잡 (get(timeout)) | 간단 (config) |
| 취소 | 명시적 필요 (cancel()) | 자동 (스코프 종료 시) |
| 에러 처리 | 복잡 (여러 예외 처리) | 구조적 (state() 확인) |
| 스레드 누수 | 가능 (스레드풀 관리 필요) | 불가능 (try-with-resources) |
| 메모리 정리 | 불명확 | 보장 |

#### SC-06: 메신저 병렬 조회 - 채널 + 멤버 + 메시지

```java
// SourceFile: StructuredConcurrencyExample.java

public record Channel(long id, String name) {}
public record Member(long id, String displayName) {}
public record Message(long id, String content) {}
public record ChannelDetail(Channel channel, List<Member> members,
                          List<Message> messages) {}

public static ChannelDetail fetchChannelDetail(long channelId) throws Exception {
    try (var scope = StructuredTaskScope.open()) {
        Subtask<Channel> channelTask = scope.fork(() -> {
            Thread.sleep(50);  // DB 조회 시뮬레이션
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
```

**테스트 (StructuredConcurrencyTest.java):**
```java
@Test
void allPartsPopulated() throws Exception {
    ChannelDetail detail = StructuredConcurrencyExample.fetchChannelDetail(42L);

    assertThat(detail.channel().id()).isEqualTo(42L);
    assertThat(detail.channel().name()).isEqualTo("general");

    assertThat(detail.members()).hasSize(2);
    assertThat(detail.members().get(0).displayName()).isEqualTo("Alice");

    assertThat(detail.messages()).hasSize(2);
    assertThat(detail.messages().get(0).content()).isEqualTo("Hello");
}
```

**메신저에서의 성능:**
```
기존 (순차 조회):
├─ 채널 조회: 50ms
├─ 멤버 조회: 80ms
├─ 메시지 조회: 100ms
└─ 총 시간: 50 + 80 + 100 = 230ms

구조화된 동시성 (병렬 조회):
┌─ 채널 조회: 50ms
├─ 멤버 조회: 80ms
├─ 메시지 조회: 100ms (가장 오래 걸림)
└─ 총 시간: ~100ms

성능 향상: 230ms → 100ms (2.3배)
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| SC-01 | 기본 fork/join 패턴 | UserProfile: 이름 + 나이 병렬 조회 |
| SC-02 | anySuccessfulResultOrThrow() - 경쟁 패턴 | 가장 빠른 소스 결과 사용 |
| SC-03 | 타임아웃 + 자동 취소 | 2초 이내의 결과만 허용 |
| SC-04 | 에러 전파 (Subtask.State 확인) | 한 작업 실패 → 상태 확인 |
| SC-05 | CompletableFuture vs Structured Concurrency | 두 방식 비교 |
| SC-06 | 메신저 병렬 조회 | 채널 + 멤버 + 메시지 동시 조회 |

### 메신저 서버 적용 포인트

#### 1. API 응답 시간 단축

```java
// 기존 (순차):
@GetMapping("/channels/{id}")
public ChannelDto getChannel(@PathVariable long id) {
    Channel channel = channelService.getChannel(id);        // 50ms
    List<Member> members = memberService.getMembers(id);    // 80ms
    List<Message> messages = messageService.getMessages(id); // 100ms
    // 총 230ms
    return new ChannelDto(channel, members, messages);
}

// 개선 (병렬):
@GetMapping("/channels/{id}")
public ChannelDto getChannel(@PathVariable long id) throws Exception {
    try (var scope = StructuredTaskScope.open()) {
        Subtask<Channel> ch = scope.fork(() ->
            channelService.getChannel(id)
        );
        Subtask<List<Member>> mbrs = scope.fork(() ->
            memberService.getMembers(id)
        );
        Subtask<List<Message>> msgs = scope.fork(() ->
            messageService.getMessages(id)
        );
        scope.join();
        // 총 100ms (2.3배 향상!)
        return new ChannelDto(ch.get(), mbrs.get(), msgs.get());
    }
}
```

#### 2. 검색 결과 병렬 수집

```java
// 여러 검색 소스에서 병렬로 데이터 수집
public SearchResult search(String query) throws Exception {
    try (var scope = StructuredTaskScope.open()) {
        Subtask<List<Message>> messages = scope.fork(() ->
            messageSearchService.search(query)
        );
        Subtask<List<User>> users = scope.fork(() ->
            userSearchService.search(query)
        );
        Subtask<List<Channel>> channels = scope.fork(() ->
            channelSearchService.search(query)
        );
        scope.join();
        return new SearchResult(messages.get(), users.get(), channels.get());
    }
}
```

#### 3. 푸시 알림과 메시지 저장의 병렬화

```java
// 메시지 저장과 푸시 알림을 병렬로 처리
public void sendMessage(Message msg) throws Exception {
    try (var scope = StructuredTaskScope.open()) {
        Subtask<Void> save = scope.fork(() -> {
            messageRepository.save(msg);
            return null;
        });
        Subtask<Void> notify = scope.fork(() -> {
            pushService.sendNotification(msg);
            return null;
        });
        scope.join();
        // 둘 다 완료될 때까지 대기
    }
}
```

#### 4. 선택적 데이터 병렬 로딩

```java
// 필수 데이터는 무조건 로드, 부가 데이터는 타임아웃으로 제한
public ChannelDetail getChannelWithOptional(long channelId) throws Exception {
    try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.allSuccessfulOrThrow(),
            cf -> cf.withTimeout(Duration.ofSeconds(2)))) {

        Subtask<Channel> channel = scope.fork(() ->
            channelService.getChannel(channelId)  // 필수
        );
        Subtask<List<Message>> messages = scope.fork(() ->
            messageService.getMessages(channelId)  // 필수
        );
        Subtask<ChannelStats> stats = scope.fork(() ->
            statsService.getStats(channelId)  // 부가 (타임아웃 적용)
        );

        scope.join();
        return new ChannelDetail(
            channel.get(),
            messages.get(),
            stats.isSuccess() ? stats.get() : null  // 타임아웃 시 null
        );
    }
}
```

### 팀원에게 한마디

**Structured Concurrency**는 "동시성 코드의 구조화"입니다.

**역사:**
1. Java 8 이전: Thread 직접 생성, ExecutorService 복잡
2. Java 5-20: CompletableFuture (유연하지만 복잡)
3. Java 21+: Structured Concurrency (명확하고 안전)

**메신저 서버의 성능 문제:**

**시나리오: 채널 상세 조회**
```
현재 (순차):
├─ 채널 정보 DB 조회: 50ms
├─ 멤버 목록 DB 조회: 80ms
├─ 최근 메시지 조회: 100ms
├─ 채널 통계 조회: 120ms
└─ 총 API 응답 시간: 350ms

개선 (Structured Concurrency):
┌─ 채널 정보: 50ms
├─ 멤버 목록: 80ms
├─ 최근 메시지: 100ms
├─ 채널 통계: 120ms (모두 병렬)
└─ 총 API 응답 시간: 120ms

개선 효과: 350ms → 120ms (3배 향상!)
```

**코드 간결성:**

```java
// CompletableFuture (2015~2024):
CompletableFuture.allOf(f1, f2, f3).join();
String r1 = f1.join();
String r2 = f2.join();
String r3 = f3.join();

// Structured Concurrency (2025+):
try (var scope = StructuredTaskScope.open()) {
    var t1 = scope.fork(() -> op1());
    var t2 = scope.fork(() -> op2());
    var t3 = scope.fork(() -> op3());
    scope.join();
    return new Result(t1.get(), t2.get(), t3.get());
}
// 더 짧고 명확함!
```

**가치:**
- **성능**: 순차 → 병렬로 2-5배 응답 시간 단축
- **안전성**: try-with-resources로 누수 불가능
- **명확성**: fork/join이 명시적, 생명주기 명확
- **유지보수**: CompletableFuture의 콜백 헬 제거

특히 메신저의 **복합 데이터 조회** (채널 + 멤버 + 메시지 + 통계 + 활동 기록) 시나리오에서 Structured Concurrency는 500ms → 150ms 정도의 응답 시간 개선을 가져올 수 있습니다.

---

## 3. ScopedValue + Structured Concurrency 시너지

### 두 기능이 함께 사용되는 패턴

#### 패턴 1: ScopedValue가 fork된 VT에 자동 전파

```
부모 스코프:
│
├─ ScopedValue.where(REQUEST_ID, "req-123").call(() -> {
│  ┌────────────────────────────────────────────
│  │ (REQUEST_ID = "req-123" 바인딩됨)
│  │
│  ├─ try (var scope = StructuredTaskScope.open()) {
│  │  │
│  │  ├─ fork(() -> {
│  │  │     REQUEST_ID.get()  // "req-123" 자동 접근 가능!
│  │  │ })
│  │  │
│  │  ├─ fork(() -> {
│  │  │     REQUEST_ID.get()  // "req-123" 자동 접근 가능!
│  │  │ })
│  │  │
│  │  └─ scope.join()
│  │
│  └────────────────────────────────────────────
└─ })
   (REQUEST_ID unbound)
```

**코드:**
```java
public List<String> scopedValueAutoInheritance(String requestId)
    throws Exception {
    return ScopedValue.where(REQUEST_ID, requestId).call(() -> {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<String> task1 = scope.fork(() ->
                    "task1-sees-" + REQUEST_ID.get()  // 자동 전파!
            );
            Subtask<String> task2 = scope.fork(() ->
                    "task2-sees-" + REQUEST_ID.get()  // 자동 전파!
            );
            scope.join();
            return List.of(task1.get(), task2.get());
        }
    });
}
```

**테스트:**
```java
var results = scopedValueAutoInheritance("req-123");
// results = ["task1-sees-req-123", "task2-sees-req-123"]
```

#### 패턴 2: 메신저의 ShardContext + 병렬 조회

```java
// 메신저 서버에서의 실제 시나리오

public ChannelDetail fetchChannelDetail(long channelId) throws Exception {
    // RequestContext에서 ShardContext 추출
    RequestContext reqCtx = REQUEST_CONTEXT.get();
    ShardContext shardCtx = getShardContext(reqCtx.tenantId());

    // ScopedValue로 ShardContext 바인딩
    return ScopedValue.where(SHARD_CONTEXT, shardCtx).call(() -> {
        // fork된 VT에서 SHARD_CONTEXT 자동 접근 가능!
        try (var scope = StructuredTaskScope.open()) {
            Subtask<Channel> channelTask = scope.fork(() -> {
                // SHARD_CONTEXT.get() 자동 접근 가능
                // → 올바른 shard에서 조회
                return channelRepository.findById(channelId);
            });

            Subtask<List<Member>> membersTask = scope.fork(() -> {
                // SHARD_CONTEXT.get() 자동 접근 가능
                return memberRepository.findByChannel(channelId);
            });

            Subtask<List<Message>> messagesTask = scope.fork(() -> {
                // SHARD_CONTEXT.get() 자동 접근 가능
                return messageRepository.findByChannel(channelId);
            });

            scope.join();
            return new ChannelDetail(
                channelTask.get(),
                membersTask.get(),
                messagesTask.get()
            );
        }
    });
}
```

**시너지의 가치:**
```
1. ScopedValue: ShardContext를 명시적으로 관리
   ├─ 어느 shard에서 조회할지 명확
   └─ 각 fork된 VT가 자동으로 같은 shard 사용

2. Structured Concurrency: 채널/멤버/메시지 병렬 조회
   ├─ 같은 shard에서 3개 조회 병렬화
   └─ 응답 시간 3배 향상

결합: 멀티테넌트 + 병렬 처리 + 자동 컨텍스트 전파
```

#### 패턴 3: 중첩된 Structured Concurrency와 ScopedValue Rebinding

```java
public List<ChannelDetail> fetchMultipleChannels(List<Long> channelIds)
    throws Exception {

    return ScopedValue.where(REQUEST_ID, generateRequestId()).call(() -> {
        try (var scope = StructuredTaskScope.open()) {
            // 각 채널을 병렬로 조회
            List<Subtask<ChannelDetail>> tasks = new ArrayList<>();

            for (long channelId : channelIds) {
                Subtask<ChannelDetail> task = scope.fork(() -> {
                    // REQUEST_ID 자동 전파 ("req-123")

                    ShardContext shard = getShardForChannel(channelId);

                    // 내부 scope에서 SHARD_CONTEXT rebinding
                    return ScopedValue.where(SHARD_CONTEXT, shard).call(() -> {
                        try (var innerScope = StructuredTaskScope.open()) {
                            // 같은 shard에서 3개 병렬 조회
                            var ch = innerScope.fork(() ->
                                channelRepository.findById(channelId)
                            );
                            var mbrs = innerScope.fork(() ->
                                memberRepository.findByChannel(channelId)
                            );
                            var msgs = innerScope.fork(() ->
                                messageRepository.findByChannel(channelId)
                            );
                            innerScope.join();
                            return new ChannelDetail(ch.get(), mbrs.get(), msgs.get());
                        }
                    });
                });
                tasks.add(task);
            }

            scope.join();
            return tasks.stream()
                .map(Subtask::get)
                .toList();
        }
    });
}
```

**두 단계의 병렬화:**
```
레벨 1 (외부 scope):
├─ 채널-1 병렬 조회
├─ 채널-2 병렬 조회
└─ 채널-3 병렬 조회

레벨 2 (내부 scope, 각 채널마다):
├─ 채널 정보 병렬 조회
├─ 멤버 목록 병렬 조회
└─ 메시지 목록 병렬 조회

결과:
- 3개 채널 × (채널 정보 + 멤버 + 메시지) = 9개 작업
- 모두 병렬 실행
- 응답 시간: 개별 작업 × 1 (완벽한 병렬화)
```

### ScopedValue가 fork된 VT에 자동 전파되는 동작

**JVM 내부 동작:**

```
1. 부모 스코프에서 ScopedValue 바인딩
   ScopedValue.where(SV, value).call(() -> {
       ↓
       Thread-Local 저장소에 (SV, value) 저장

2. Structured Concurrency fork()
   scope.fork(() -> {
       ↓
       JVM이 fork된 VT의 ScopedValue 저장소 준비
       부모의 ScopedValue 바인딩을 "복사"하지 않고
       "공유 참조" 제공

3. fork된 VT에서 접근
   SV.get()
   ↓
   공유 참조를 통해 부모의 값에 직접 접근 (비용 0)

이것이 ThreadLocal과의 핵심 차이!
- ThreadLocal: 복사 (비용 있음)
- ScopedValue: 공유 참조 (비용 없음)
```

### 메신저 서버에서의 결합 시나리오

```java
// 전체 흐름

// 1. HTTP 요청 진입
@PostMapping("/messages")
public MessageResponse sendMessage(@RequestBody SendMessageRequest req)
    throws Exception {

    // 2. RequestContext 바인딩
    RequestContext reqCtx = new RequestContext(
        generateRequestId(),
        getTenantFromAuth(),
        getUserIdFromAuth(),
        System.currentTimeMillis()
    );

    return ScopedValue.where(REQUEST_CONTEXT, reqCtx).call(() -> {
        // 3. ShardContext 결정
        ShardContext shard = getShardForTenant(reqCtx.tenantId());

        // 4. ShardContext 바인딩
        return ScopedValue.where(SHARD_CONTEXT, shard).call(() -> {
            // 5. 메시지 저장 및 병렬 처리
            Message msg = new Message(req.text(), reqCtx.userId());
            messageRepository.save(msg);  // ← SHARD_CONTEXT 자동 적용

            // 6. 병렬 후처리
            try (var scope = StructuredTaskScope.open()) {
                Subtask<Void> pushTask = scope.fork(() -> {
                    // REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파
                    pushService.notifyChannelMembers(req.channelId(), msg);
                    return null;
                });

                Subtask<Void> searchTask = scope.fork(() -> {
                    // REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파
                    searchService.indexMessage(msg);
                    return null;
                });

                Subtask<Void> wsTask = scope.fork(() -> {
                    // REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파
                    websocketService.broadcastMessage(msg);
                    return null;
                });

                scope.join();
            }

            // 7. 응답
            return new MessageResponse(msg.getId(), "success");
        });
    });
}

// 요청 흐름:
// HTTP 진입
// ├─ REQUEST_CONTEXT 바인딩
// ├─ SHARD_CONTEXT 바인딩
// │
// ├─ messageRepository.save() ← SHARD_CONTEXT 자동 사용
// │
// ├─ 병렬 처리 (3개 작업)
// │ ├─ pushTask (REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파)
// │ ├─ searchTask (REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파)
// │ └─ wsTask (REQUEST_CONTEXT, SHARD_CONTEXT 자동 전파)
// │
// └─ 응답 반환
//    (스코프 종료 → 모든 context 자동 해제)
```

---

## 종합 정리

### Java 25의 두 기능 요약

| 기능 | 핵심 가치 | 메신저 서버 활용 |
|------|----------|-----------------|
| **ScopedValue** | 불변 컨텍스트를 명시적 스코프로 관리. VT 환경에서 메모리 효율적. fork된 VT에 자동 전파. | 높음 (ShardContext, TenantId, RequestId 전파) |
| **Structured Concurrency** | 병렬 작업의 생명주기를 구조화. fork/join/타임아웃/취소/에러 처리 자동화. | 높음 (API 응답 시간 2-3배 향상, 검색/푸시/WS 병렬) |
| **두 기능의 시너지** | ScopedValue가 fork된 VT에 자동 전파되어 멀티테넌트 + 병렬 처리 가능 | 매우 높음 (샤딩 + 병렬화 + 자동 컨텍스트 전파) |

### 각 기능의 학습 포인트

**ScopedValue (SV) - 5가지 핵심**
- [x] SV-01: `where().run()` / `where().call()` 기본 패턴
- [x] SV-02: ThreadLocal vs ScopedValue (메모리: 500배 절감)
- [x] SV-03: 중첩 scope와 rebinding (outer → inner → outer)
- [x] SV-04: StructuredTaskScope에서 자동 전파 (중요!)
- [x] SV-05: `isBound()` 안전 접근 패턴
- [x] SV-07: 메신저 ShardContext (DB 라우팅 구현)

**Structured Concurrency (SC) - 6가지 핵심**
- [x] SC-01: 기본 fork/join 패턴 (UserProfile 병렬 조회)
- [x] SC-02: `anySuccessfulResultOrThrow()` - 경쟁 패턴
- [x] SC-03: 타임아웃 + 자동 취소
- [x] SC-04: 에러 전파 (Subtask.State 확인)
- [x] SC-05: CompletableFuture 대비 우월성
- [x] SC-06: 메신저 병렬 조회 (채널 + 멤버 + 메시지)

**시너지 (SV + SC)**
- [x] fork된 VT에 ScopedValue 자동 전파
- [x] 멀티테넌트 + 병렬 처리 결합
- [x] ShardContext + 병렬 조회 시나리오

### 실전 체크리스트

**ScopedValue 활용:**
- [ ] 기존 ThreadLocal<ShardContext> → ScopedValue로 마이그레이션
- [ ] 기존 ThreadLocal<RequestContext> → ScopedValue로 마이그레이션
- [ ] TenantId 전파에 ScopedValue 적용
- [ ] VT 환경에서 메모리 절감 검증

**Structured Concurrency 활용:**
- [ ] 순차 API 조회 → 병렬 패턴으로 개선 (응답 시간 측정)
- [ ] CompletableFuture 사용 부분 → StructuredTaskScope로 마이그레이션
- [ ] 검색 서비스 병렬화 (메시지/유저/채널 동시 검색)
- [ ] 푸시 알림 + 메시지 저장 병렬화

**시너지 검증:**
- [ ] fork된 VT에서 ScopedValue 자동 접근 테스트
- [ ] 중첩 ScopedValue + Structured Concurrency 작동 확인
- [ ] 멀티테넌트 샤딩 + 병렬 조회 성능 테스트

### Preview 상태 주의사항

```
Java 25: ScopedValue, Structured Concurrency = 5th Preview
         (2개 모두 JEP 506, JEP 505)

주의사항:
1. --enable-preview 플래그 필수
   javac --enable-preview --source 25 *.java
   java --enable-preview Main

2. API가 변경될 수 있음
   - StructuredTaskScope.open() (Java 25에서 생성자 대신 사용)
   - Joiner 메서드 확장 가능
   - ScopedValue 바인딩 API 추가 가능

3. 프로덕션 사용
   - Java 25 정식 버전 출시 전까지는 테스트 환경에만 권장
   - Java 26 이상에서 정식 추가 예상

4. 마이그레이션 계획
   - ThreadLocal → ScopedValue (API 안정화 후 진행)
   - CompletableFuture → StructuredTaskScope (API 안정화 후 진행)
```

### 프로젝트 셋업

```bash
# 컴파일
javac --enable-preview --source 25 -d target/classes src/**/*.java

# 실행
java --enable-preview -cp target/classes modernjava.java25.Main

# 테스트 (Maven)
mvn clean test -DargLine="--enable-preview"

# IDE 설정 (IntelliJ IDEA)
Settings → Project Structure → Project
├─ SDK: Java 25
├─ Language Level: 25 (Preview)
└─ Enable preview features in source code
```

---

## 마지막 당부

**Java 25의 ScopedValue + Structured Concurrency는:**

1. **Virtual Thread 시대의 필수 도구**
   - VT는 수백만 개 동시 실행 가능
   - ScopedValue는 메모리 효율적으로 컨텍스트 전파
   - Structured Concurrency는 병렬 작업을 안전하게 관리

2. **메신저 서버의 성능 혁신**
   - 순차 조회 (350ms) → 병렬 조회 (100ms)
   - ThreadLocal 메모리 (100KB) → ScopedValue 메모리 (1KB)
   - CompletableFuture 복잡성 → StructuredTaskScope 명확성

3. **코드 품질 향상**
   - 명시적 스코프 관리 (버그 감소)
   - 자동 리소스 정리 (누수 방지)
   - 구조적 에러 처리 (예측 가능)

**다음 단계:**
- 테스트 환경에서 실험 (프로젝트 내 학습용)
- API 안정화 모니터링 (Java 26 계획)
- 마이그레이션 로드맵 수립 (ThreadLocal → ScopedValue)

이 두 기능이 정식 추가되면, 메신저 서버의 병렬 처리와 컨텍스트 관리는 완전히 새로운 수준으로 진화할 것입니다.
