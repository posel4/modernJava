# Java 21 Pattern Matching for switch + Record Patterns + Sequenced Collections

> switch 표현식의 완전한 패턴 매칭 지원. sealed type + record + guard 조합으로 타입 안전한 분기 처리.

---

## 1. Pattern Matching for switch (JEP 441)

### 한줄 요약
switch에서 타입 패턴, guard 절(when), null 처리를 지원. sealed type과 조합하면 exhaustive 보장으로 모든 경우가 처리되었음을 컴파일 타임에 검증.

### 왜 필요한가?

**Java 17 이전:**
```java
// instanceof 패턴 매칭만 가능, switch는 상수만 지원
public String handleResponse(Object response) {
    if (response instanceof Success) {
        Success s = (Success) response;
        return "성공: " + s.statusCode();
    } else if (response instanceof ClientError) {
        ClientError e = (ClientError) response;
        return "에러: " + e.statusCode();
    } else if (response instanceof ServerError) {
        ServerError e = (ServerError) response;
        return "서버 에러: " + e.statusCode();
    } else if (response instanceof Timeout) {
        Timeout t = (Timeout) response;
        return "타임아웃: " + t.endpoint();
    } else {
        return "알 수 없음";
    }
}
```

**Java 21:**
```java
// switch에서 직접 타입 패턴 매칭 + guard + null 처리
public String handleResponse(ApiResponse response) {
    return switch (response) {
        case Success s -> "성공: " + s.statusCode();
        case ClientError e -> "에러: " + e.statusCode();
        case ServerError e -> "서버 에러: " + e.statusCode();
        case Timeout t -> "타임아웃: " + t.endpoint();
    };
}
```

**개선점:**
- 명확한 의도: switch는 "분기"임을 직관적으로 표현
- 변수 바인딩: 타입 체크 후 자동으로 변수 선언
- exhaustive: sealed type이면 모든 경우 처리 검증 (default 불필요)
- 가독성: if-else 체인보다 구조적

---

## 2. Guard Clause (`when` 키워드)

### 한줄 요약
같은 타입의 값도 추가 조건(guard)으로 세분화. 조건이 거짓이면 다음 case로 진행.

### Before / After

**Before (Java 17):**
```java
public String classifyResponse(ApiResponse response) {
    if (response instanceof Success s) {
        if (s.statusCode() == 200) {
            return "OK";
        } else if (s.statusCode() == 201) {
            return "Created";
        } else {
            return "기타 성공";
        }
    } else if (response instanceof ClientError e) {
        if (e.statusCode() == 400) {
            return "잘못된 요청";
        } else if (e.statusCode() == 401) {
            return "인증 필요";
        } else {
            return "기타 클라이언트 에러";
        }
    }
    return "알 수 없음";
}
```

**After (Java 21):**
```java
public String classifyResponse(ApiResponse response) {
    return switch (response) {
        case Success s when s.statusCode() == 200 -> "OK";
        case Success s when s.statusCode() == 201 -> "Created";
        case Success s when s.statusCode() == 204 -> "No Content";
        case Success s -> "기타 성공: " + s.statusCode();
        case ClientError e when e.statusCode() == 400 -> "잘못된 요청";
        case ClientError e when e.statusCode() == 401 -> "인증 필요";
        case ClientError e when e.statusCode() == 403 -> "권한 없음";
        case ClientError e when e.statusCode() == 404 -> "리소스 없음";
        case ClientError e -> "기타 클라이언트 에러: " + e.statusCode();
        case ServerError e when e.statusCode() == 503 -> "서비스 점검 중";
        case ServerError e -> "서버 내부 에러";
        case Timeout t when t.durationMs() > 5000 -> "심각한 타임아웃";
        case Timeout t -> "일반 타임아웃";
    };
}
```

**핵심 차이:**
- `when` 절로 조건 표현 → 들여쓰기 줄어들고 흐름 명확
- 상태코드 분기가 구조적 → 매뉴얼 순서 관리 불필요
- exhaustive 검증: 모든 경우의 수 처리 여부를 컴파일러가 확인

---

## 3. Test Cases: PM-01, PM-02, PM-03

### PM-01: Sealed Type + Exhaustive switch

**정의:**
sealed interface의 모든 구현체를 switch에서 처리. 하나라도 빠지면 컴파일 에러.

```java
public sealed interface ApiResponse permits
    Success, ClientError, ServerError, Timeout {}

public record Success(int statusCode, Object body) implements ApiResponse {}
public record ClientError(int statusCode, String message) implements ApiResponse {}
public record ServerError(int statusCode, String message, String traceId)
    implements ApiResponse {}
public record Timeout(long durationMs, String endpoint) implements ApiResponse {}

// exhaustive switch (default 없어도 컴파일 OK)
public String handleResponse(ApiResponse response) {
    return switch (response) {
        case Success s -> "성공 (" + s.statusCode() + "): " + s.body();
        case ClientError e -> "클라이언트 에러 (" + e.statusCode() + "): " + e.message();
        case ServerError e -> "서버 에러 (" + e.statusCode() + "): " + e.message() + " [" + e.traceId() + "]";
        case Timeout t -> "타임아웃: " + t.endpoint() + " (" + t.durationMs() + "ms)";
    };
}
```

**장점:**
- 새로운 ApiResponse 구현체 추가 시 → switch에 추가하지 않으면 컴파일 에러
- 안전성: 누락된 타입 처리를 compile-time에 발견
- 유지보수성: sealed interface로 변경 영향도 명확

---

### PM-02: Guard Clause (Conditional Patterns)

**정의:**
같은 타입 패턴에 `when` 조건 추가. 조건이 거짓이면 다음 case로 진행.

```java
public String classifyResponse(ApiResponse response) {
    return switch (response) {
        // Success의 statusCode별 분류
        case Success s when s.statusCode() == 200 -> "OK";
        case Success s when s.statusCode() == 201 -> "Created";
        case Success s when s.statusCode() == 204 -> "No Content";
        case Success s -> "기타 성공: " + s.statusCode();

        // ClientError의 statusCode별 분류
        case ClientError e when e.statusCode() == 400 -> "잘못된 요청";
        case ClientError e when e.statusCode() == 401 -> "인증 필요";
        case ClientError e when e.statusCode() == 403 -> "권한 없음";
        case ClientError e when e.statusCode() == 404 -> "리소스 없음";
        case ClientError e -> "기타 클라이언트 에러: " + e.statusCode();

        // ServerError는 503만 특별 처리
        case ServerError e when e.statusCode() == 503 -> "서비스 점검 중";
        case ServerError e -> "서버 내부 에러";

        // Timeout은 duration으로 분류
        case Timeout t when t.durationMs() > 5000 -> "심각한 타임아웃";
        case Timeout t -> "일반 타임아웃";
    };
}
```

**핵심:**
- 패턴 일치 후 추가 조건 검증
- guard 조건이 거짓이면 다음 case 시도 (fall-through 아님)
- 복잡한 분기 로직을 선언적으로 표현

---

### PM-03: null 처리 in switch

**정의:**
Java 21 switch는 null을 case로 처리 가능. 기존: switch 전에 null 체크 필수 → NullPointerException 위험.

**Before:**
```java
public String safeHandle(ApiResponse response) {
    if (response == null) {
        return "응답 없음";
    }

    // 기존 switch는 null을 처리할 수 없음
    if (response instanceof Success s) {
        return "성공: " + s.body();
    } else if (response instanceof ClientError e) {
        return "에러: " + e.message();
    }
    // ...
    return "알 수 없음";
}
```

**After (Java 21):**
```java
public String safeHandle(ApiResponse response) {
    return switch (response) {
        case null -> "응답 없음 (null)";
        case Success s -> "성공: " + s.body();
        case ClientError e -> "에러: " + e.message();
        case ServerError e -> "서버 에러: " + e.traceId();
        case Timeout t -> "타임아웃: " + t.endpoint();
    };
}
```

**장점:**
- null을 정상적으로 처리 → NullPointerException 방지
- 단일 switch에서 모든 경우 처리
- 의도가 명확: null은 유효한 입력값이라는 표현

---

## 4. Record Patterns (JEP 440)

### 한줄 요약
Record의 컴포넌트를 switch에서 직접 분해. 중첩 record도 한 번에 분해 가능. getter 호출 없이 직관적으로 필드 접근.

### Before / After

**Before (Java 17):**
```java
public record ChannelInfo(long channelId, ChannelMeta meta) {}
public record ChannelMeta(String name, ChannelStatus status, int memberCount) {}

public String describeChannel(ChannelInfo info) {
    if (info != null && info.meta() != null) {
        String name = info.meta().name();
        ChannelStatus status = info.meta().status();
        int count = info.meta().memberCount();
        long id = info.channelId();

        if (status == ChannelStatus.DELETED) {
            return "삭제된 채널: " + name;
        } else if (status == ChannelStatus.ARCHIVED) {
            return "아카이브 채널: " + name + " (" + count + "명)";
        } else if (status == ChannelStatus.ACTIVE && count > 100) {
            return "대규모 활성 채널: " + name;
        } else {
            return "활성 채널: " + name + " (" + count + "명)";
        }
    }
    return "채널 정보 없음";
}
```

**After (Java 21):**
```java
public String describeChannel(ChannelInfo info) {
    return switch (info) {
        // 중첩 분해: ChannelInfo → ChannelMeta → name, status, memberCount
        case ChannelInfo(var id, ChannelMeta(var name, ChannelStatus.DELETED, var ignored))
            -> "삭제된 채널: " + name + " (id=" + id + ")";

        case ChannelInfo(var id, ChannelMeta(var name, ChannelStatus.ARCHIVED, var count))
            -> "아카이브 채널: " + name + " (" + count + "명, id=" + id + ")";

        case ChannelInfo(var id, ChannelMeta(var name, ChannelStatus.ACTIVE, var count))
            when count > 100
            -> "대규모 활성 채널: " + name + " (" + count + "명)";

        case ChannelInfo(var id, ChannelMeta(var name, var status, var count))
            -> "활성 채널: " + name + " (" + count + "명)";
    };
}
```

**개선점:**
- 중첩 분해: `ChannelInfo(var id, ChannelMeta(var name, ...))` 한 번에
- getter 호출 불필요: 변수 자동 바인딩
- 패턴 매칭과 guard 조합: 상태별 조건 명확
- 타입 안전성: 잘못된 필드 접근은 컴파일 에러

### Test Case: PM-04

```java
public enum ChannelStatus { ACTIVE, ARCHIVED, DELETED }

public record ChannelInfo(long channelId, ChannelMeta meta) {}
public record ChannelMeta(String name, ChannelStatus status, int memberCount) {}

// Record Pattern으로 중첩 분해 + enum 상태 + guard clause 조합
public String describeChannel(ChannelInfo info) {
    return switch (info) {
        case ChannelInfo(var id, ChannelMeta(var name, var status, var ignored))
                when status == ChannelStatus.DELETED ->
                "삭제된 채널: " + name + " (id=" + id + ")";

        case ChannelInfo(var id, ChannelMeta(var name, var status, var count))
                when status == ChannelStatus.ARCHIVED ->
                "아카이브 채널: " + name + " (" + count + "명, id=" + id + ")";

        case ChannelInfo(var id, ChannelMeta(var name, var status, var count))
                when status == ChannelStatus.ACTIVE && count > 100 ->
                "대규모 활성 채널: " + name + " (" + count + "명)";

        case ChannelInfo(var id, ChannelMeta(var name, var status, var count)) ->
                "활성 채널: " + name + " (" + count + "명)";
    };
}
```

---

## 5. Pattern + Sealed + Record 조합 (PM-05): 실전 예제

### 메신저 이벤트 디스패처

sealed interface + record pattern + guard clause를 조합한 실전 예제.

**도메인 설계: 3단계 sealed hierarchy**

```java
// Level 1: Root interface
public sealed interface MessengerEvent permits
    MessageEvent, ChannelEvent, MemberEvent {}

// Level 2: Category interfaces
public sealed interface MessageEvent extends MessengerEvent permits
    MessageSent, MessageEdited, MessageDeleted {}

public sealed interface ChannelEvent extends MessengerEvent permits
    ChannelCreated, ChannelArchived {}

public sealed interface MemberEvent extends MessengerEvent permits
    MemberJoined, MemberLeft {}

// Level 3: Concrete records
public record MessageSent(long channelId, long logId, String text, boolean isBot)
    implements MessageEvent {}
public record MessageEdited(long channelId, long logId, String oldText, String newText)
    implements MessageEvent {}
public record MessageDeleted(long channelId, long logId, long deletedBy)
    implements MessageEvent {}

public record ChannelCreated(long channelId, String name, String type)
    implements ChannelEvent {}
public record ChannelArchived(long channelId, String reason)
    implements ChannelEvent {}

public record MemberJoined(long channelId, long memberId, String invitedBy)
    implements MemberEvent {}
public record MemberLeft(long channelId, long memberId, boolean kicked)
    implements MemberEvent {}
```

**exhaustive switch + record pattern + guard:**

```java
public static String dispatchEvent(MessengerEvent event) {
    return switch (event) {
        // ===== MessageEvent 처리 =====
        // MessageSent: isBot guard로 봇/사용자 구분
        case MessageSent(var ch, var log, var text, var isBot) when isBot ->
                "봇 메시지: ch=" + ch + " text=" + text;
        case MessageSent(var ch, var log, var text, var ignored) ->
                "사용자 메시지: ch=" + ch + " log=" + log;

        // MessageEdited
        case MessageEdited(var ch, var log, var oldText, var newText) ->
                "메시지 수정: ch=" + ch + " [" + oldText + " → " + newText + "]";

        // MessageDeleted
        case MessageDeleted(var ch, var log, var deletedBy) ->
                "메시지 삭제: ch=" + ch + " log=" + log + " by=" + deletedBy;

        // ===== ChannelEvent 처리 =====
        // ChannelCreated: type guard로 DM/일반채널 구분
        case ChannelCreated(var ch, var name, var type) when "DM".equals(type) ->
                "DM 생성: " + name;
        case ChannelCreated(var ch, var name, var type) ->
                "채널 생성: " + name + " (" + type + ")";

        // ChannelArchived
        case ChannelArchived(var ch, var reason) ->
                "채널 아카이브: ch=" + ch + " 이유=" + reason;

        // ===== MemberEvent 처리 =====
        // MemberJoined
        case MemberJoined(var ch, var member, var inviter) ->
                "멤버 입장: ch=" + ch + " member=" + member + " (초대: " + inviter + ")";

        // MemberLeft: kicked guard로 추방/자발 퇴장 구분
        case MemberLeft(var ch, var member, var kicked) when kicked ->
                "멤버 추방: ch=" + ch + " member=" + member;
        case MemberLeft(var ch, var member, var ignored) ->
                "멤버 퇴장: ch=" + ch + " member=" + member;
    };
}
```

**특징:**
- **exhaustive:** MessengerEvent의 모든 가능한 타입 처리 → 컴파일 타임 검증
- **구조화:** 3단계 sealed hierarchy로 이벤트 카테고리 명확
- **record pattern:** 각 이벤트의 필드 직접 바인딩 (getter 호출 없음)
- **guard clause:** isBot, type, kicked 조건으로 세분화된 처리
- **가독성:** 이벤트 종류와 처리 로직이 한눈에 파악

---

## 6. Sequenced Collections (JEP 431)

### 한줄 요약
순서가 있는 컬렉션(List, LinkedHashSet, LinkedHashMap)에 `getFirst()`, `getLast()`, `reversed()` 공통 인터페이스 추가. 명확하고 타입 안전한 인터페이스.

### Before / After

**Before (Java 20):**
```java
List<Message> messages = fetchMessages();

// 첫 메시지: get(0) → 직관적이지 않음
Message first = messages.get(0);

// 마지막 메시지: get(size()-1) → 복잡하고 실수하기 쉬움
Message last = messages.get(messages.size() - 1);

// 역순 처리: Collections.reverse() → 원본 수정, 또는 수동 루프
for (int i = messages.size() - 1; i >= 0; i--) {
    Message msg = messages.get(i);
    // 처리
}

// LinkedHashSet의 첫/마지막 요소: 복잡한 로직 필요
LinkedHashSet<String> set = new LinkedHashSet<>();
String first = set.stream().findFirst().orElse(null);
String last = set.stream().reduce((a, b) -> b).orElse(null);
```

**After (Java 21):**
```java
List<Message> messages = fetchMessages();

// 첫 메시지
Message first = messages.getFirst();

// 마지막 메시지
Message last = messages.getLast();

// 역순 처리 (뷰, 원본 수정 안함)
for (Message msg : messages.reversed()) {
    // 처리
}

// stream과 조합
messages.reversed().stream()
    .limit(10)
    .forEach(System.out::println);

// LinkedHashSet의 첫/마지막 요소
SequencedSet<String> set = new LinkedHashSet<>();
String first = set.getFirst();
String last = set.getLast();

// LinkedHashMap의 첫/마지막 엔트리
SequencedMap<String, Integer> map = new LinkedHashMap<>();
Map.Entry<String, Integer> firstEntry = map.firstEntry();
Map.Entry<String, Integer> lastEntry = map.lastEntry();
```

**개선점:**
- **명확성:** `get(0)`보다 `getFirst()` → 의도가 명확
- **타입 안전:** `reversed()` 반환이 `SequencedCollection` → 같은 인터페이스 제공
- **효율성:** `reversed()` 뷰는 원본 수정하지 않음 (copy-free)
- **일관성:** List, LinkedHashSet, LinkedHashMap이 같은 인터페이스 제공

### Test Case: PM-06

**메신저 적용: 메시지 목록 처리**

```java
public record ChatMessage(long id, String text, String sender) {}

// 첫/마지막 메시지 조회
public static String getLatestAndOldest(List<ChatMessage> messages) {
    if (messages.isEmpty()) return "메시지 없음";

    ChatMessage oldest = messages.getFirst();
    ChatMessage latest = messages.getLast();

    return "가장 오래된: [%s] %s / 가장 최근: [%s] %s".formatted(
            oldest.sender(), oldest.text(),
            latest.sender(), latest.text()
    );
}

// 최근 메시지부터 역순 처리 (최근 10개)
public static List<String> recentMessagesReversed(List<ChatMessage> messages, int limit) {
    return messages.reversed().stream()
            .limit(limit)
            .map(m -> m.sender() + ": " + m.text())
            .toList();
}

// LinkedHashSet으로 메시지 스레드 순서 유지
public static String getThreadBoundary(SequencedSet<ChatMessage> thread) {
    if (thread.isEmpty()) return "스레드 없음";

    ChatMessage first = thread.getFirst();
    ChatMessage last = thread.getLast();

    return "처음: " + first.text() + " / 끝: " + last.text();
}
```

**메신저 서버 적용:**
- 메시지 페이징: `messages.getFirst()`, `messages.getLast()`로 경계 판단
- 최신순 로드: `messages.reversed().stream()`로 최근 메시지부터 처리
- 스레드 뷰: `LinkedHashSet<ChatMessage>`로 순서 보장

---

## 7. 종합 정리

| 기능 | 핵심 개념 | 코드 예제 | 메신저 적용 |
|------|---------|---------|-----------|
| **Pattern Matching for switch** | Exhaustive + sealed type | `case Success s ->` | 응답/에러 분류 (PM-01) |
| **Guard Clause (when)** | 조건부 패턴 매칭 | `case Success s when s.statusCode() == 200` | 상태코드별 처리 (PM-02) |
| **null 처리** | case null → 안전성 | `case null -> "응답 없음"` | 네트워크 오류 대응 (PM-03) |
| **Record Patterns** | 중첩 분해 + 변수 바인딩 | `case ChannelInfo(var id, ChannelMeta(...))` | 채널 상태 분해 (PM-04) |
| **Pattern + Sealed + Record** | 복합 패턴 + exhaustive | 3단계 sealed hierarchy + guard | 이벤트 디스패처 (PM-05) |
| **Sequenced Collections** | getFirst/getLast/reversed | `messages.getFirst()`, `messages.reversed()` | 메시지 목록 처리 (PM-06) |

---

## 8. 메신저 서버 적용 포인트

### 응답 처리 계층 (API Layer)

```java
// src/main/java/.../api/response/
public sealed interface ApiResponse permits Success, ClientError, ServerError, Timeout {}

// 네트워크 호출 후 응답 분류
public String handleResponse(ApiResponse response) {
    return switch (response) {
        case null -> "응답 없음";
        case Success s when s.statusCode() == 200 -> handleSuccess(s);
        case ClientError e when e.statusCode() == 401 -> handleUnauthorized(e);
        case ClientError e when e.statusCode() == 403 -> handleForbidden(e);
        case ServerError e when e.statusCode() == 503 -> handleMaintenance(e);
        case Timeout t when t.durationMs() > 5000 -> handleLongTimeout(t);
        default -> "예상 밖의 응답";
    };
}
```

**적용 위치:**
- `src/main/java/.../exception/ErrorCodes.java` → sealed interface로 개선
- `src/main/java/.../api/controller/` → guard clause로 상태코드 분기
- `src/main/java/.../client/HttpClient.java` → 응답 처리

### 이벤트 디스패처 (Event System)

```java
// src/main/java/.../event/
public sealed interface MessengerEvent permits
    MessageEvent, ChannelEvent, MemberEvent, ReactionEvent {}

// 이벤트 리스너에서 이벤트 타입별 처리
@EventListener
public void onMessengerEvent(MessengerEvent event) {
    String result = switch (event) {
        // 봇 메시지 특별 처리
        case MessageSent(var ch, var log, var text, var isBot) when isBot ->
            processBotMessage(ch, text);

        // DM 생성 알림
        case ChannelCreated(var ch, var name, "DM") ->
            notifyDmCreation(ch, name);

        // 멤버 추방
        case MemberLeft(var ch, var member, true) ->
            logMemberKick(ch, member);

        // 기타 처리
        default -> processDefaultEvent(event);
    };
}
```

**적용 위치:**
- `src/main/java/.../event/listener/` → sealed event hierarchy
- `src/main/java/.../service/` → 비즈니스 로직에서 guard clause로 상태 분기
- ApplicationEvent 리스너

### 메시지 조회 계층 (Repository)

```java
// src/main/java/.../repository/ChatMessageRepository.java
public List<ChatMessage> findRecentMessages(long channelId, int limit) {
    List<ChatMessage> messages = queryDatabase(...);

    if (messages.isEmpty()) {
        return List.of();
    }

    // getFirst/getLast로 경계 판단
    ChatMessage oldest = messages.getFirst();
    ChatMessage latest = messages.getLast();

    // reversed()로 최신순 처리
    return messages.reversed().stream()
            .limit(limit)
            .toList();
}
```

**적용 위치:**
- `src/main/java/.../repository/` → `List<ChatMessage>` 처리
- `src/main/java/.../service/ChatService.java` → reversed() + stream 조합

---

## 9. 팀원에게 한마디

**Pattern Matching for switch는 단순히 코드 줄 수 줄이기가 아니라, 타입 안전성과 exhaustive 검증을 컴파일 타임에 보장하는 기능입니다.** sealed type과 조합하면 새로운 타입 추가 시 모든 switch를 자동으로 찾아낼 수 있어, 누락으로 인한 버그를 사전에 방지합니다. 특히 API 응답 처리나 이벤트 디스패처처럼 타입 분기가 많은 곳에서 큰 가치를 발휘합니다.

**Record Pattern은 불필요한 getter 호출과 변수 할당을 제거하고, 중첩 구조도 한 번에 분해하므로, 복잡한 도메인 모델을 다룰 때 코드 복잡도를 크게 낮춉니다.**

**Sequenced Collections의 `getFirst()`, `getLast()`, `reversed()`는 메시지 목록, 채팅 히스토리처럼 순서가 중요한 컬렉션에서 직관적이고 효율적인 처리를 가능하게 합니다.** 특히 `reversed()`는 복사 없이 역순 뷰를 제공하므로 메모리 효율도 우수합니다.

---

## 참고 자료

- **JEP 441:** Pattern Matching for switch
  https://openjdk.org/jeps/441

- **JEP 440:** Record Patterns
  https://openjdk.org/jeps/440

- **JEP 431:** Sequenced Collections
  https://openjdk.org/jeps/431

- **소스 코드:**
  - `/modernjava/java21/PatternMatchingSwitchExample.java`
  - `/modernjava/java21/SequencedCollectionsExample.java`
