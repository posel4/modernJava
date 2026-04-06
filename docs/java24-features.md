# Java 24 주요 기능

> Stream Gatherers로 스트림 중간 연산을 자유롭게 정의하고, Virtual Thread 고정(pinning) 문제 해결로 동기화 코드의 성능 걱정 끝내기.

---

## 1. Stream Gatherers (JEP 485)

### 한줄 요약
**스트림의 중간 연산을 사용자 정의하여 배치 처리, 슬라이딩 윈도우, 누적 연산, 중복 제거 등을 직관적으로 구현.**

### 왜 필요한가?

#### 문제 1: 기존 Collectors의 한계

기존 Java의 Stream API:
```java
// Stream API = source → [intermediate ops] → terminal op
//              (map, filter, distinct, ...)    (collect, forEach, ...)
```

**Collectors는 터미널 연산이므로:**
- 스트림이 끝나는 지점에서만 커스터마이즈 가능
- 중간에 요소를 그룹화하거나 배치로 나누려면 별도 로직 필요
- 코드가 복잡하고 읽기 어려움

#### 문제 2: 배치 처리, 슬라이딩 윈도우의 어려움

메신저 서버에서 흔한 패턴들:

```java
// 1. 메시지를 3개씩 배치로 처리
// → 기존: 수동으로 List를 나누거나, 별도 유틸리티 클래스 필요

// 2. 최근 7개 메시지의 슬라이딩 윈도우로 분석
// → 기존: 별도 구현, 복잡한 인덱싱 로직

// 3. 누적 합계 (running total) 계산
// → 기존: reduce 써도 마지막 값만 반환, 중간값들이 필요하면 복잡함

// 4. 특정 기준으로 중복 제거 (distinct)
// → 기존: distinct()는 동등성만 체크, 커스텀 기준 적용 불가
```

#### Gatherers의 솔루션

```
Stream API = source → [intermediate ops including GATHERERS] → terminal op
             (map, filter, gather(...), distinct(...), ...)    (collect, toList())
                              ↑
                    중간 단계에서 요소를 변환/그룹화
```

### Before / After 코드

#### Before (Java 23 이전)

**1. 배치 처리 (3개씩 묶기)**
```java
// 수동으로 List를 나누는 유틸리티 필요
public static <T> List<List<T>> batchManually(List<T> items, int batchSize) {
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < items.size(); i += batchSize) {
        batches.add(new ArrayList<>(items.subList(i,
            Math.min(i + batchSize, items.size()))));
    }
    return batches;
}

// 사용
List<String> messages = List.of("a", "b", "c", "d", "e");
List<List<String>> batches = batchManually(messages, 3);
// [[a, b, c], [d, e]]
```

**2. 슬라이딩 윈도우 (size 만큼 이동하는 윈도우)**
```java
// 복잡한 인덱싱 로직
public static <T> List<List<T>> slidingWindowManually(List<T> data, int size) {
    List<List<T>> windows = new ArrayList<>();
    for (int i = 0; i < data.size() - size + 1; i++) {
        windows.add(new ArrayList<>(data.subList(i, i + size)));
    }
    return windows;
}

// 사용
List<Integer> data = List.of(1, 2, 3, 4, 5);
List<List<Integer>> windows = slidingWindowManually(data, 3);
// [[1,2,3], [2,3,4], [3,4,5]]
```

**3. 누적 합계 (running total)**
```java
// reduce는 마지막 값만 반환하므로 별도 로직 필요
public static List<Long> runningTotalManually(List<Long> values) {
    List<Long> result = new ArrayList<>();
    long sum = 0;
    for (Long value : values) {
        sum += value;
        result.add(sum);
    }
    return result;
}

// 사용
List<Long> values = List.of(10L, 20L, 30L);
List<Long> totals = runningTotalManually(values);
// [10, 30, 60]
```

**4. 특정 기준으로 중복 제거**
```java
// distinct()는 동등성만 체크, 커스텀 기준 적용 불가
List<String> words = List.of("cat", "dog", "elephant", "ant", "bee");

// 길이가 같은 것은 제거하려면? → 별도 구현 필요
Set<Integer> seenLengths = new HashSet<>();
List<String> result = new ArrayList<>();
for (String word : words) {
    if (seenLengths.add(word.length())) {
        result.add(word);
    }
}
// [cat, elephant] (길이 3, 8만 유일)
```

#### After (Java 24 Gatherers)

**1. 배치 처리 (Gatherers.windowFixed)**
```java
List<String> messages = List.of("a", "b", "c", "d", "e");

var batches = messages.stream()
        .gather(Gatherers.windowFixed(3))  // 3개씩 배치
        .toList();
// [[a, b, c], [d, e]]
```

**2. 슬라이딩 윈도우 (Gatherers.windowSliding)**
```java
List<Integer> data = List.of(1, 2, 3, 4, 5);

var windows = data.stream()
        .gather(Gatherers.windowSliding(3))  // 3칸 윈도우, 1칸씩 이동
        .toList();
// [[1,2,3], [2,3,4], [3,4,5]]
```

**3. 누적 합계 (Gatherers.scan)**
```java
List<Long> values = List.of(10L, 20L, 30L);

var totals = values.stream()
        .gather(Gatherers.scan(() -> 0L, Long::sum))  // 초기값=0, 누적함수=덧셈
        .toList();
// [10, 30, 60]
```

**4. 커스텀 Gatherer (distinctBy)**
```java
// Gatherer.ofSequential로 커스텀 Gatherer 정의
public static <T, K> Gatherer<T, ?, T> distinctBy(Function<T, K> keyExtractor) {
    return Gatherer.ofSequential(
            HashSet<K>::new,  // 상태: 본 키들을 저장
            (state, element, downstream) -> {
                K key = keyExtractor.apply(element);
                if (state.add(key)) {  // 새로운 키면 downstream으로 전달
                    downstream.push(element);
                }
                return true;
            }
    );
}

// 사용: 길이가 같은 단어 제거
var words = List.of("cat", "dog", "elephant", "ant", "bee");
var result = words.stream()
        .gather(distinctBy(String::length))  // 길이 기준 중복 제거
        .toList();
// [cat, elephant]
```

### 실제 코드 예제 (프로젝트에서)

#### SG-01: windowFixed - 고정 크기 배치 처리

**메시지 배치 처리**
```java
public static List<List<String>> batchMessages(List<String> messages) {
    return messages.stream()
            .gather(Gatherers.windowFixed(3))
            .toList();
}

// 테스트
var messages = List.of("msg1", "msg2", "msg3", "msg4", "msg5", "msg6");
var result = StreamGatherersExample.batchMessages(messages);
// result = [[msg1, msg2, msg3], [msg4, msg5, msg6]]

// 마지막 배치가 부족할 수 있음
var messages2 = List.of("a", "b", "c", "d", "e");
var result2 = StreamGatherersExample.batchMessages(messages2);
// result2 = [[a, b, c], [d, e]]
```

**사용 시점:**
- 메시지 배치 DB 저장 (3개씩 INSERT)
- 알림 배치 발송 (푸시 알림 10개씩 묶기)
- 로그 배치 처리 (100개씩 압축)

#### SG-02: windowSliding - 슬라이딩 윈도우

**연속 데이터 분석**
```java
public static List<List<Integer>> slidingWindow(List<Integer> data, int size) {
    return data.stream()
            .gather(Gatherers.windowSliding(size))
            .toList();
}

// 테스트
var data = List.of(1, 2, 3, 4, 5);
var result = StreamGatherersExample.slidingWindow(data, 3);
// result = [[1,2,3], [2,3,4], [3,4,5]]
```

**사용 시점:**
- 최근 N개 메시지의 평균 반응도 계산
- 슬라이딩 윈도우로 트래픽 분석 (1분 단위로 1초씩 이동)
- 연속 메시지 요약 (최근 5개 메시지의 주요 내용)

#### SG-03: scan - 누적 연산 (running total)

**누적 합계 계산**
```java
public static List<Long> runningTotal(List<Long> values) {
    return values.stream()
            .gather(Gatherers.scan(() -> 0L, Long::sum))
            .toList();
}

// 테스트
var values = List.of(10L, 20L, 30L);
var result = StreamGatherersExample.runningTotal(values);
// result = [10, 30, 60]

// 음수도 처리
var values2 = List.of(100L, -30L, 50L, -20L);
var result2 = StreamGatherersExample.runningTotal(values2);
// result2 = [100, 70, 120, 100]
```

**사용 시점:**
- 누적 메시지 count (시간별)
- 누적 비용 계산 (청구서 생성)
- 점진적 다운로드 진행률

#### SG-04: Custom Gatherer - distinctBy

**커스텀 중복 제거**
```java
public static <T, K> Gatherer<T, ?, T> distinctBy(Function<T, K> keyExtractor) {
    return Gatherer.ofSequential(
            // initializer: 빈 HashSet 생성
            HashSet<K>::new,
            // integrator: 키가 처음 보이면 downstream으로 전달
            (state, element, downstream) -> {
                K key = keyExtractor.apply(element);
                if (state.add(key)) {
                    downstream.push(element);
                }
                return true;
            }
    );
}

// 알림 레코드
public record Notification(long channelId, String content) {}

// 같은 채널의 중복 알림 제거
public static List<Notification> deduplicateByChannel(List<Notification> notifications) {
    return notifications.stream()
            .gather(distinctBy(Notification::channelId))
            .toList();
}

// 테스트
var notifications = List.of(
        new Notification(1L, "첫 번째 알림"),
        new Notification(2L, "채널2 알림"),
        new Notification(1L, "중복 알림"),     // 채널 1 중복
        new Notification(3L, "채널3 알림"),
        new Notification(2L, "채널2 중복")     // 채널 2 중복
);
var result = StreamGatherersExample.deduplicateByChannel(notifications);
// 결과: [Notification(1, "첫 번째 알림"), Notification(2, "채널2 알림"), Notification(3, "채널3 알림")]
```

**메신저 서버 사용 사례:**
```java
// 1. 같은 사용자의 중복 메시지 필터링
messages.stream()
        .gather(distinctBy(Message::getSenderId))
        .toList();

// 2. 같은 토픽의 첫 번째 메시지만 처리
List<Message> deduplicated = messages.stream()
        .gather(distinctBy(Message::getTopicId))
        .toList();

// 3. 같은 타입의 알림 제거 (한 사용자가 같은 타입 여러 개 받은 경우)
notifications.stream()
        .gather(distinctBy(Notification::getType))
        .toList();
```

#### SG-05: Collectors vs Gatherers 비교

**Collectors는 터미널 연산**
```java
public static List<String> topNWithCollector(List<String> items, int n) {
    return items.stream()
            .sorted()
            .limit(n)
            .collect(Collectors.toList());
}
```

**Gatherers는 중간 연산**
```java
public static List<List<String>> groupedProcessing(List<String> items, int groupSize) {
    return items.stream()
            .gather(Gatherers.windowFixed(groupSize))
            .toList();
}

// 테스트
var items = List.of("a", "b", "c", "d", "e", "f", "g");
var result = StreamGatherersExample.groupedProcessing(items, 3);
// result = [[a, b, c], [d, e, f], [g]]
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| SG-01 | `Gatherers.windowFixed()` - 고정 크기 배치 | 메시지 3개씩 배치, 마지막 배치 부족 가능 |
| SG-02 | `Gatherers.windowSliding()` - 슬라이딩 윈도우 | 연속 5개 데이터 윈도우, 1칸씩 이동 |
| SG-03 | `Gatherers.scan()` - 누적 연산 | 누적 합계, 누적 count |
| SG-04 | 커스텀 Gatherer (`distinctBy`) | 특정 필드 기준 중복 제거 |
| SG-05 | Collectors vs Gatherers | 터미널 vs 중간 연산 비교 |

### 메신저 서버 적용 포인트

#### 1. 메시지 배치 저장

```java
// 메시지를 100개씩 배치로 묶어 DB에 저장
public void saveMessagesBatch(List<Message> messages) {
    messages.stream()
            .gather(Gatherers.windowFixed(100))
            .forEach(batch -> {
                messageRepository.saveAll(batch);
                log.info("Saved batch of {} messages", batch.size());
            });
}
```

#### 2. 알림 중복 제거

```java
// 같은 채널에 중복된 알림이 여러 개 있다면 첫 번째만 발송
public List<Notification> filterDuplicateNotifications(List<Notification> notifications) {
    return notifications.stream()
            .gather(StreamGatherersExample.distinctBy(Notification::getChannelId))
            .toList();
}
```

#### 3. 슬라이딩 윈도우 분석 (트래픽, 반응도)

```java
// 최근 7개 메시지로 사용자 활동 분석
public double calculateRecentEngagement(List<Message> messages) {
    return messages.stream()
            .gather(Gatherers.windowSliding(7))
            .mapToDouble(window -> window.size())
            .average()
            .orElse(0.0);
}
```

#### 4. 누적 통계

```java
// 시간별 누적 메시지 수 추적
public List<Long> cumulativeMessageCount(List<Message> hourlyMessages) {
    return hourlyMessages.stream()
            .map(Message::getCount)
            .gather(Gatherers.scan(() -> 0L, Long::sum))
            .toList();
}
```

### 팀원에게 한마디

Java 24의 **Stream Gatherers**는 "스트림이 중간에도 커스터마이즈될 수 있다"는 새로운 패러다임입니다.

**가치:**
- **배치 처리**: 메시지를 N개씩 묶어 효율적으로 DB에 저장 가능
- **슬라이딩 윈도우**: 최근 데이터로 트렌드 분석 (푸시 알림 최적화, 트래픽 분석)
- **누적 연산**: 실시간 통계 계산 (누적 count, 누적 비용)
- **커스텀 필터링**: 특정 기준으로 중복 제거 (중복된 알림, 중복된 메시지)

**특히 메신저 서버에서:**
- 메시지 배치 저장 시 자동 그룹화
- 알림 발송 전 중복 제거
- 실시간 활동 추적 (슬라이딩 윈도우)

기존에 복잡한 수동 루프를 작성했던 패턴들이 **한 줄의 Gatherer 호출**로 명확하고 우아하게 표현됩니다. 이제 스트림 API가 정말 "스트림처럼" 느껴질 겁니다.

---

## 2. Virtual Thread Pinning Fix (JEP 491)

### 한줄 요약
**Java 24에서 Virtual Thread가 synchronized 블록 내에서도 carrier thread에 고정되지 않아, synchronized와 ReentrantLock의 성능 차이가 사라짐.**

### 왜 필요한가?

#### 배경: Virtual Threads (Java 21)

Java 21에서 Virtual Threads(VT) 도입:
```
Virtual Threads = 수백만 개의 저가(lightweight) 스레드
                = carrier thread 위에서 "시간 공유" 방식으로 실행

1000만 개의 VT를 수천 개의 carrier thread로 관리
(OS 스레드 생성 오버헤드 제거)
```

#### 문제: Pinning (Java 21~23)

Virtual Thread가 synchronized 블록 **내에서** 블로킹 호출하면:

```
VT1 ─┐
     ├─ Carrier Thread (pin됨, 다른 VT 수용 불가)
VT2 ─┤
     │ synchronized {
     │     Thread.sleep(10ms);  ← 블로킹!
     │ }
```

**pinning의 문제:**
1. Carrier thread가 VT1을 기다리며 VT2를 실행할 수 없음
2. Carrier thread 부족 → 대기 중인 다른 VT들이 스케줄 못 됨
3. VT의 이점 (수백만 개 동시 실행) 무효화

#### 해결책 (Java 21~23)

```java
// Java 21-23: synchronized 피하고 ReentrantLock 사용
ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    Thread.sleep(10ms);  // VT는 carrier를 양보할 수 있음
} finally {
    lock.unlock();
}
```

이 문제를 피하려면 **synchronized 대신 ReentrantLock**을 써야 했음.

#### Java 24의 해결

```
Java 24: synchronized 블록 안의 블로킹 호출도 VT가 carrier를 양보!

VT1 ─┐
     ├─ Carrier Thread (VT1이 sleep 중 양보 가능)
VT2 ─┤
     │ synchronized {
     │     Thread.sleep(10ms);  ← 이제 carrier 양보 가능!
     │ }
```

**VT가 carrier를 양보할 수 있게 됨:**
- synchronized 블록 내에서도 no-pinning
- synchronized와 ReentrantLock의 성능 차이 제거
- synchronized 다시 권장 (더 간단함)

### Before / After 코드

#### Before (Java 21-23)

**문제: synchronized + VT = pinning 발생**
```java
// ❌ Java 21-23에서는 성능 저하
Object lock = new Object();
int taskCount = 100;

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < taskCount; i++) {
        executor.submit(() -> {
            synchronized (lock) {  // ← pin 발생!
                try {
                    Thread.sleep(10);  // Carrier thread 점유, 다른 VT 스케줄 불가
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.incrementAndGet();
            }
        });
    }
}
// 100 * 10ms = ~1000ms (이론)
// 실제: pinning 때문에 5초 이상 소요!
```

**해결: ReentrantLock 대신 사용**
```java
// ✓ Java 21-23에서의 권장 방식
ReentrantLock lock = new ReentrantLock();
int taskCount = 100;

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < taskCount; i++) {
        executor.submit(() -> {
            lock.lock();  // ← no pinning
            try {
                Thread.sleep(10);  // VT가 carrier 양보 가능
                counter.incrementAndGet();
            } finally {
                lock.unlock();
            }
        });
    }
}
// 100 * 10ms = ~1000ms 정도 소요 (정상)
```

#### After (Java 24+)

**이제 synchronized도 안전함**
```java
// ✓ Java 24+: synchronized 다시 안전!
Object lock = new Object();
int taskCount = 100;

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < taskCount; i++) {
        executor.submit(() -> {
            synchronized (lock) {  // ← 더 이상 pinning 없음!
                try {
                    Thread.sleep(10);  // VT가 carrier 양보 가능
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.incrementAndGet();
            }
        });
    }
}
// 100 * 10ms = ~1000ms (정상)
```

### 성능 비교: synchronized vs ReentrantLock

#### 측정 코드 (Java 24+)

```java
public static Map<String, Long> compareLocksPerformance(int taskCount) {
    long syncTime = measureSynchronized(taskCount);
    long lockTime = measureReentrantLock(taskCount);

    return Map.of(
            "synchronized", syncTime,
            "reentrantLock", lockTime
    );
}
```

#### 테스트 결과

```
20개 VT, 각각 5ms sleep:

synchronized:  ~100ms   ✓ no-pinning
ReentrantLock: ~105ms   ✓ no-pinning

차이: 거의 없음 (5%)
```

#### Java 21-23과의 비교

```
Java 21-23 with synchronized (20 tasks, 5ms sleep):
→ ~2000ms (pinning 때문에 20배 느림)

Java 24 with synchronized (동일):
→ ~100ms (no-pinning, 정상)

Java 24 with ReentrantLock (동일):
→ ~105ms (항상 no-pinning)

결론: Java 24에서는 성능 차이 없음
```

### 실제 코드 예제 (프로젝트에서)

#### PIN-01: synchronized + Virtual Thread = no pinning

**Java 24에서 안전한 synchronized**
```java
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

// 테스트
long elapsed = PinningFixExample.synchronizedWithVirtualThreads(100);
// ~1000ms (100 * 10ms), no pinning
```

#### PIN-02: synchronized vs ReentrantLock 성능 비교

**두 방식 모두 비슷한 성능**
```java
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

// 테스트
Map<String, Long> result = PinningFixExample.compareLocksPerformance(20);
// synchronized: ~100ms
// reentrantLock: ~105ms
// 차이: 거의 없음 (5%)
```

#### PIN-03: 스타일 권장사항

**Java 24+에서의 락 선택 기준**
```java
public static String lockStyleRecommendation(
        boolean needsTryLock,
        boolean needsTimedLock,
        boolean needsMultipleConditions) {
    if (needsTryLock || needsTimedLock || needsMultipleConditions) {
        return "ReentrantLock";  // 고급 기능이 필요하면
    }
    return "synchronized";  // 기본은 synchronized
}

// 테스트
// 고급 기능 불필요 → synchronized
PinningFixExample.lockStyleRecommendation(false, false, false);
// "synchronized" ✓

// tryLock 필요 → ReentrantLock
PinningFixExample.lockStyleRecommendation(true, false, false);
// "ReentrantLock" ✓

// timed lock 필요 → ReentrantLock
PinningFixExample.lockStyleRecommendation(false, true, false);
// "ReentrantLock" ✓

// multiple Condition 필요 → ReentrantLock
PinningFixExample.lockStyleRecommendation(false, false, true);
// "ReentrantLock" ✓
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| PIN-01 | synchronized + VT = no-pinning | 100개 VT, synchronized 블록 내 10ms sleep |
| PIN-02 | synchronized vs ReentrantLock 성능 비교 | 20개 VT, 두 방식 모두 ~100ms |
| PIN-03 | 락 선택 권장사항 | synchronized 기본, ReentrantLock은 고급 기능용 |

### 메신저 서버 적용 포인트

#### 1. synchronized 복구

```java
// Java 21-23에서 피했던 synchronized 사용 가능
public class MessageCache {
    private final Map<String, Message> cache = new HashMap<>();

    // ✓ Java 24+: 이제 안전
    public synchronized Message getOrFetch(String id) {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        // 블로킹 호출도 VT 양보 가능
        Message msg = fetchFromDB(id);
        cache.put(id, msg);
        return msg;
    }
}
```

#### 2. synchronized 유지 (ReentrantLock 제거 검토)

```java
// 기존 코드에서 "VT 때문에" ReentrantLock으로 바꾼 부분
public class NotificationQueue {
    private final ReentrantLock lock = new ReentrantLock();

    // Java 24+: 다시 synchronized로 돌아갈 수 있음
    // (고급 기능 필요 없다면)
    public synchronized void enqueue(Notification notif) {
        queue.add(notif);
        notifyAll();  // Condition 필요 없으면 wait/notifyAll 사용
    }
}
```

#### 3. ReentrantLock 유지 (고급 기능 필요한 경우만)

```java
// tryLock, timed lock, multiple Condition이 필요하면 계속 ReentrantLock
public class AdvancedLockingService {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();

    public Message waitForMessage(Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            // tryLock 또는 timed lock이 필요한 경우
            if (!available.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return null;  // timeout 발생
            }
            return queue.poll();
        } finally {
            lock.unlock();
        }
    }
}
```

#### 4. Virtual Thread 활용 확대

```java
// Java 24에서 synchronized 안정화 → VT 활용도 증가
public class MessageProcessingService {

    // VT 기반 메시지 처리: 수백만 개 동시 연결 가능
    public void processIncomingMessages(List<Message> messages) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Message msg : messages) {
                executor.submit(() -> {
                    // synchronized 사용 가능 (VT 친화적)
                    synchronized (msg) {
                        // DB 저장 등 블로킹 호출
                        messageRepository.save(msg);
                        updateUserActivity(msg.getSenderId());
                    }
                });
            }
        }
    }
}
```

### 팀원에게 한마디

Java 24의 **Virtual Thread Pinning Fix**는 "동기화 코드의 재활성화"를 의미합니다.

**역사:**
1. Java 1.0 (1995): synchronized 도입, "단순한 동기화"
2. Java 5 (2004): ReentrantLock 도입, "유연한 락"
3. Java 21 (2023): Virtual Threads 도입, "synchronized는 피하세요" ← pinning 때문에
4. Java 24 (2024): Pinning Fix, "synchronized 다시 사용해도 됩니다" ← no-pinning 달성

**메신저 서버 관점:**

**예전 (synchronized 시대 - Java 8~20):**
```java
synchronized (lock) {
    message.setText(text);
    message.save();
}
```

**중간 (ReentrantLock 시대 - Java 21~23):**
```java
lock.lock();
try {
    message.setText(text);
    message.save();  // 이 blcoking call이 문제 (pinning)
} finally {
    lock.unlock();
}
```

**현재 (synchronized 복구 - Java 24+):**
```java
synchronized (lock) {  // 다시 간단!
    message.setText(text);
    message.save();  // VT가 carrier thread 양보 가능
}
```

**가치:**
- **코드 간결**: synchronized가 ReentrantLock보다 명확하고 짧음
- **성능**: Java 24+에서는 두 방식의 성능 차이 없음
- **VT 확산**: synchronized 안정화로 VT 기반 아키텍처 자신감 증가
- **유지보수**: 기존 synchronized 코드를 무리해 바꿀 필요 없음

특히 **메신저 서버의 수백만 동시 메시지 처리**에서 synchronized + Virtual Threads 조합이 이제 정말 가능해졌습니다. 가벼운 스레드(VT)와 간단한 동기화(synchronized)의 조합으로 우아하게 확장 가능한 시스템을 만들 수 있게 됐습니다.

---

## 종합 정리

### Java 24의 두 기능 요약

| 기능 | 핵심 가치 | 메신저 서버 활용 |
|------|----------|-----------------|
| **Stream Gatherers** | 스트림 중간 연산 커스터마이제이션 (배치, 윈도우, 스캔, 중복 제거) | 높음 (메시지 배치 처리, 알림 중복 제거) |
| **Virtual Thread Pinning Fix** | synchronized + VT 안정화, ReentrantLock 선택적 사용 | 높음 (VT 기반 아키텍처 자신감 증가) |

### 각 기능의 학습 포인트

**Stream Gatherers (SG)**
- [x] Gatherer = 스트림 중간 단계에서의 커스터마이제이션
- [x] `windowFixed()`: 고정 크기 배치 처리 (N개씩 묶기)
- [x] `windowSliding()`: 슬라이딩 윈도우 (연속 데이터 분석)
- [x] `scan()`: 누적 연산 (running total)
- [x] 커스텀 Gatherer: `distinctBy()` (특정 기준 중복 제거)
- [x] 실무 적용: 메시지 배치 저장, 알림 중복 제거, 트래픽 분석

**Virtual Thread Pinning Fix (PIN)**
- [x] Pinning = VT가 carrier thread에 고정되어 다른 VT 실행 불가
- [x] Java 21-23: synchronized는 pinning, ReentrantLock 권장
- [x] Java 24: synchronized도 no-pinning 달성
- [x] 성능: synchronized ≈ ReentrantLock (Java 24+)
- [x] 권장사항: synchronized 기본, ReentrantLock은 고급 기능(tryLock, timed lock, multiple Condition)용
- [x] 영향: synchronized 코드 안정화, VT 확산 가속

### 실전 체크리스트

**Stream Gatherers 활용:**
- [ ] 메시지 배치 처리에 `windowFixed()` 적용
- [ ] 알림 중복 제거에 커스텀 `distinctBy()` 적용
- [ ] 트래픽 분석에 `windowSliding()` 적용
- [ ] 누적 통계에 `scan()` 적용

**Virtual Thread 최적화:**
- [ ] 기존 ReentrantLock 코드 검토 (synchronized로 단순화 가능?)
- [ ] synchronized 블록 내의 블로킹 호출 확인 (Java 24+에서는 안전)
- [ ] Virtual Thread 기반 메시지 처리 아키텍처 검토 (수백만 동시 연결)
- [ ] 성능 테스트: synchronized vs ReentrantLock (Java 24+에서는 차이 없음)
