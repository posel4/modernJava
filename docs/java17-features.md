# Java 17 주요 기능 복습

> Java 17 LTS에서 확정된 핵심 기능 4가지를 메신저 서버 도메인으로 학습.

---

## 1. Sealed Classes (JEP 409)

### 한줄 요약
상속 가능한 하위 타입을 컴파일 타임에 제한하는 기능.

### 왜 필요한가?

**기존 문제:**
```
- 아무 클래스나 상속 가능 → 예상치 못한 하위 타입 등장
- switch 문에서 default 분기가 필수 (모든 경우 처리할 수 없음)
- 다형성 처리할 때 누락된 케이스를 런타임에만 발견
```

메신저 서버에서 메시지는 Text, Image, File만 존재해야 하는데, sealed 없으면 VideoMessage를 누군가 추가할 수 있다. 그러면 모든 switch 문에서 비상사태가 일어난다.

### Before / After 코드

**Before (sealed 없음):**
```java
// 누가 이런 클래스를 만들 수도 있음
public interface MessageType {
    String content();
}

public record TextMessage(...) implements MessageType {}
public record ImageMessage(...) implements MessageType {}
public record FileMessage(...) implements MessageType {}
public record VideoMessage(...) implements MessageType {}  // 예상 밖!

public static String describe(MessageType msg) {
    return switch (msg) {
        case TextMessage t -> "텍스트";
        case ImageMessage i -> "이미지";
        case FileMessage f -> "파일";
        default -> "뭔가 새로운 타입?";  // default가 필요함
    };
}
```

**After (sealed 적용):**
```java
public sealed interface MessageType permits TextMessage, ImageMessage, FileMessage {
    String content();
    long senderId();
}

public record TextMessage(String content, long senderId, boolean isMarkdown) implements MessageType {}
public record ImageMessage(String content, long senderId, int width, int height, String thumbnailUrl) implements MessageType {}
public record FileMessage(String content, long senderId, String fileName, long fileSize) implements MessageType {}

public static String describeMessage(MessageType msg) {
    return switch (msg) {
        case TextMessage t -> "텍스트: " + t.content() + (t.isMarkdown() ? " (마크다운)" : "");
        case ImageMessage i -> "이미지: " + i.width() + "x" + i.height();
        case FileMessage f -> "파일: " + f.fileName() + " (" + f.fileSize() + " bytes)";
        // default 필요 없음! 컴파일러가 exhaustive 검증
    };
}
```

### 메신저 서버 적용 포인트

- **MessageType**: Text/Image/File sealed hierarchy → exhaustive switch로 모든 타입 처리 보장
  - 새 메시지 타입 추가 시 컴파일 에러로 누락 방지
  - 핸들러 각각(API, 저장소, 푸시, 통계)이 자동으로 에러 감지

- **ChannelType**: DM/Private/Public → sealed interface로 3가지만 허용
  - 채널별 특수 로직(멤버 관리, 공개 범위 등)이 명확

- **Notification**: Push/Email/InApp sealed class
  - 공통 base class(recipientId, title)와 추상 메서드(deliver)
  - 각 알림 방식의 전송 로직을 final 클래스에서 구현

### TC 목록

| TC | 내용 | 기대 결과 |
|----|------|----------|
| SC-01 | TextMessage switch 매칭 | "텍스트: 안녕하세요 (마크다운)" |
| SC-02 | ImageMessage switch 매칭 | "이미지: 1920x1080" |
| SC-03 | FileMessage switch 매칭 | "파일: report.pdf (1024000 bytes)" |
| SC-04 | getPermittedSubclasses() 검증 | 정확히 3개의 구현체 |
| SC-05 | ChannelType exhaustive switch | DM/Private/Public 모두 처리 |
| SC-06 | sealed class (Notification) | PushNotification.deliver() 실행 |

### 팀원에게 한마디

sealed를 쓰면 "이 인터페이스는 앞으로 3가지만 존재할 거다"를 명시할 수 있어서, 새 기능 추가할 때 영향받는 모든 switch 문을 컴파일 타임에 감지할 수 있다. 야밤에 런타임 에러로 고생하지 말고 sealed 써라.

---

## 2. Records (JEP 395)

### 한줄 요약
불변 데이터 클래스를 한 줄로 정의. equals/hashCode/toString/accessor 자동 생성.

### 왜 필요한가?

**기존 문제:**
```java
public class ApiUser {
    private final long memberId;
    private final Long tenantId;
    private final int tenantDbId;
    private final String requestId;

    public ApiUser(long memberId, Long tenantId, int tenantDbId, String requestId) {
        this.memberId = memberId;
        this.tenantId = tenantId;
        this.tenantDbId = tenantDbId;
        this.requestId = requestId;
    }

    public long memberId() { return memberId; }
    public Long tenantId() { return tenantId; }
    public int tenantDbId() { return tenantDbId; }
    public String requestId() { return requestId; }

    @Override
    public boolean equals(Object o) { /* 10줄 */ }

    @Override
    public int hashCode() { /* 5줄 */ }

    @Override
    public String toString() { /* 5줄 */ }
}
// 총 ~50줄
```

메신저 API는 요청 컨텍스트, DTO, 응답 값 객체로 가득한데, 이렇게 쓰면 코드가 터진다.

### Before / After 코드

**Before:**
```java
public class MessageResponse {
    private final long logId;
    private final String text;
    private final SenderInfo sender;
    private final Instant createdAt;
    private final List<AttachmentInfo> attachments;

    public MessageResponse(long logId, String text, SenderInfo sender, Instant createdAt, List<AttachmentInfo> attachments) {
        this.logId = logId;
        this.text = text;
        this.sender = sender;
        this.createdAt = createdAt;
        this.attachments = attachments;
    }

    public long logId() { return logId; }
    public String text() { return text; }
    // ... 더 많은 getter들 ...

    @Override
    public equals/hashCode/toString...
}
```

**After (record):**
```java
public record MessageResponse(
        long logId,
        String text,
        SenderInfo sender,
        Instant createdAt,
        List<AttachmentInfo> attachments
) {
    public record SenderInfo(long memberId, String name, String profileImageUrl) {}
    public record AttachmentInfo(String fileName, long fileSize, String mimeType) {}

    public static MessageResponse textOnly(long logId, String text, SenderInfo sender, Instant createdAt) {
        return new MessageResponse(logId, text, sender, createdAt, List.of());
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }
}
```

### 메신저 서버 적용 포인트

- **ApiUser record**: 요청 컨텍스트 (memberId, tenantId, tenantDbId, requestId)
  - 모든 API 핸들러가 받는 불변 사용자 정보
  - accessor는 `memberId()` 형태 (getXxx 아님)

- **DTO 전부 record화**: MessageResponse, ChannelResponse, UserResponse 등
  - 생성자 → List.copyOf()로 방어적 복사
  - equals/hashCode 자동 생성 → 캐시 키로 사용 가능

- **Compact constructor**: ChannelId, MemberId 같은 값 객체
  ```java
  public record ChannelId(long value) {
      public ChannelId {
          if (value <= 0) throw new IllegalArgumentException("positive required");
      }
  }
  ```
  - 파라미터 목록 생략하고 검증만 작성
  - 검증 통과 후 필드 자동 할당

- **ChannelMembers**: 불변성 보장 (방어적 복사)
  ```java
  public record ChannelMembers(long channelId, List<Long> memberIds) {
      public ChannelMembers {
          memberIds = List.copyOf(memberIds);  // unmodifiable
      }
  }
  ```

- **Generic record**: API 응답 래퍼
  ```java
  public record ApiResult<T>(boolean success, String resultCode, T data) {
      public static <T> ApiResult<T> ok(T data) { /* ... */ }
      public static <T> ApiResult<T> fail(String code) { /* ... */ }
  }
  ```

- **Local record**: 메서드 내 임시 데이터 구조
  ```java
  public static String summarizeChannel(long channelId, String name, int messageCount, int memberCount) {
      record ChannelStats(int messageCount, int memberCount, double avgMessagesPerMember) {}

      var stats = new ChannelStats(
              messageCount,
              memberCount,
              memberCount > 0 ? (double) messageCount / memberCount : 0
      );
      return "%s(#%d): %d messages, %d members, avg %.1f msg/member"
              .formatted(name, channelId, stats.messageCount(), stats.memberCount(), stats.avgMessagesPerMember());
  }
  ```

### TC 목록

| TC | 내용 | 기대 결과 |
|----|------|----------|
| REC-01 | accessor 스타일 (xxx(), not getXxx()) | `user.memberId()` 호출 가능 |
| REC-02 | 자동 생성 equals/hashCode | user1.equals(user2) == true |
| REC-03 | 자동 생성 toString | "ApiUser[memberId=1, tenantId=100, ...]" 포함 |
| REC-04 | compact constructor 유효성 검증 | ChannelId(0) → IllegalArgumentException |
| REC-05 | 방어적 복사 (불변성) | 외부 List 수정 후에도 record 내부 변경 없음 |
| REC-06 | Record + Interface 구현 | Channel/Member가 Identifiable 구현 |
| REC-07 | Generic Record | ApiResult<Channel> ok/fail 팩토리 |
| REC-08 | Local Record | summarizeChannel에서 ChannelStats 사용 |

### 팀원에게 한마디

DTO는 전부 record로 바꿔라. 보일러플레이트를 90% 줄일 수 있고, 불변성도 보장된다. 특히 값 객체(ChannelId, MemberId)는 compact constructor로 입력값 검증까지 할 수 있어서 실수를 줄인다.

---

## 3. Pattern Matching for instanceof (JEP 394)

### 한줄 요약
instanceof 체크와 캐스팅을 한 번에. 패턴 변수로 타입 검사 후 바로 사용 가능.

### 왜 필요한가?

**기존 문제:**
```java
// 보일러플레이트 + 에러 가능성
if (obj instanceof String) {
    String s = (String) obj;  // 캐스팅이 따로 필요
    // s 사용
} else if (obj instanceof Integer) {
    Integer i = (Integer) obj;  // 또 캐스팅
    // i 사용
}
```

이벤트 핸들링, 다형성 처리 등에서 이 패턴이 반복된다. 실수하기 쉽고 코드도 길어진다.

### Before / After 코드

**Before (Java 16 이전):**
```java
public static String formatOld(Object obj) {
    if (obj instanceof String) {
        String s = (String) obj;
        return "String: " + s.toUpperCase();
    } else if (obj instanceof Integer) {
        Integer i = (Integer) obj;
        return "Integer: " + (i * 2);
    } else if (obj instanceof List) {
        List<?> list = (List<?>) obj;
        return "List size: " + list.size();
    }
    return "Unknown: " + obj;
}
```

**After (Java 17 - Pattern Matching):**
```java
public static String formatNew(Object obj) {
    if (obj instanceof String s) {
        return "String: " + s.toUpperCase();
    } else if (obj instanceof Integer i) {
        return "Integer: " + (i * 2);
    } else if (obj instanceof List<?> list) {
        return "List size: " + list.size();
    }
    return "Unknown: " + obj;
}
```

**Guard condition (&&와 조합):**
```java
// 기존: 캐스팅 후 if로 추가 검사
if (obj instanceof String) {
    String s = (String) obj;
    if (!s.isBlank()) {
        // 처리
    }
}

// 새로: 한 줄에
if (obj instanceof String s && !s.isBlank()) {
    // 처리
}
```

### 메신저 서버 적용 포인트

- **객체 타입별 포매팅**: formatNew()처럼 Object를 String, Integer, List 등으로 분류
  - 로깅, 디버깅, 통계에서 다양한 타입 처리

- **이벤트 핸들링**: MessageSentEvent, MessageDeletedEvent, MemberJoinedEvent 등
  ```java
  public static String handleEvent(Event event) {
      if (event instanceof MessageSentEvent e) {
          return "메시지 전송: ch=" + e.channelId() + ", log=" + e.logId();
      } else if (event instanceof MemberLeftEvent e && "kicked".equals(e.reason())) {
          return "멤버 추방: ch=" + e.channelId();
      } else if (event instanceof MemberLeftEvent e) {
          return "멤버 퇴장: ch=" + e.channelId();
      }
      return "알 수 없는 이벤트";
  }
  ```
  - 파라미터 `e`가 각 Event 타입으로 자동 캐스팅
  - guard condition `&& "kicked".equals(e.reason())`로 추가 조건 검사

- **채널 이름 검증**:
  ```java
  public static boolean isValidChannelName(Object obj) {
      return obj instanceof String s && s.length() >= 2 && s.length() <= 50;
  }
  ```
  - 타입 체크 + 길이 검사를 한 번에

### TC 목록

| TC | 내용 | 기대 결과 |
|----|------|----------|
| PM-01 | formatOld vs formatNew 결과 동일 | "String: HELLO", "Integer: 42", "List size: 3" 모두 일치 |
| PM-02 | Guard condition (&&) | "유효한 텍스트: 5자" (공백 문자열은 제외) |
| PM-03 | 채널 이름 길이 검증 | 2~50자만 true |
| PM-04 | MessageSentEvent 핸들링 | "메시지 전송: ch=100, log=1" |
| PM-05 | MemberLeftEvent guard (kicked vs voluntary) | "멤버 추방" vs "멤버 퇴장" |

### 팀원에게 한마디

이벤트나 다양한 타입을 처리할 때 이 패턴을 쓰면 캐스팅 실수가 줄어든다. guard condition (&&)을 적절히 섞으면 단순한 타입 검사도 한 줄에 처리 가능하다.

---

## 4. Text Blocks (JEP 378)

### 한줄 요약
여러 줄 문자열을 `"""` 로 감싸서 가독성 향상. JSON, SQL, HTML 등을 깔끔하게 작성.

### 왜 필요한가?

**기존 문제:**
```java
String json = "{\n" +
              "  \"channelId\": 100,\n" +
              "  \"name\": \"개발팀\",\n" +
              "  \"type\": \"PRIVATE\"\n" +
              "}";

String sql = "SELECT c.channel_id, c.name, c.type, " +
             "COUNT(cm.member_id) as member_count " +
             "FROM channel c " +
             "JOIN channel_member cm ON c.channel_id = cm.channel_id " +
             "WHERE c.tenant_id = ? " +
             "GROUP BY c.channel_id, c.name, c.type " +
             "ORDER BY c.name";
```

- `\n` 직접 입력해야 함
- `"` 연결이 복잡
- 들여쓰기 혼란
- 가독성 떨어짐

### Before / After 코드

**JSON - Before:**
```java
public static String jsonBefore() {
    return "{\n" +
            "  \"channelId\": 100,\n" +
            "  \"name\": \"개발팀\",\n" +
            "  \"type\": \"PRIVATE\",\n" +
            "  \"memberCount\": 5\n" +
            "}";
}
```

**JSON - After:**
```java
public static String jsonAfter() {
    return """
            {
              "channelId": 100,
              "name": "개발팀",
              "type": "PRIVATE",
              "memberCount": 5
            }""";
}
```

**SQL - Before:**
```java
public static String sqlBefore() {
    return "SELECT c.channel_id, c.name, c.type, " +
            "COUNT(cm.member_id) as member_count " +
            "FROM channel c " +
            "JOIN channel_member cm ON c.channel_id = cm.channel_id " +
            "WHERE c.tenant_id = ? " +
            "GROUP BY c.channel_id, c.name, c.type " +
            "ORDER BY c.name";
}
```

**SQL - After:**
```java
public static String sqlAfter() {
    return """
            SELECT c.channel_id, c.name, c.type,
                   COUNT(cm.member_id) as member_count
            FROM channel c
            JOIN channel_member cm ON c.channel_id = cm.channel_id
            WHERE c.tenant_id = ?
            GROUP BY c.channel_id, c.name, c.type
            ORDER BY c.name""";
}
```

### 메신저 서버 적용 포인트

- **JSON 쿼리 / API 문서**:
  ```java
  String graphqlQuery = """
          query GetChannelMessages {
            channel(id: "%d") {
              name
              messages(limit: 50) {
                id
                text
                createdAt
              }
            }
          }""".formatted(channelId);
  ```

- **SQL (특히 복잡한 쿼리)**:
  - formatted()로 변수 삽입
  ```java
  public record ChannelStats(int messageCount, int memberCount, double avgMessagesPerMember) {}
  ```

- **HTML 템플릿 (알림 이메일, 웹 푸시)**:
  ```java
  public static String notificationHtml(String userName, String channelName, String message) {
      return """
              <div class="notification">
                <h3>%s님이 %s에 메시지를 보냈습니다</h3>
                <p class="message">%s</p>
              </div>""".formatted(userName, channelName, message);
  }
  ```

- **formatted() - 변수 삽입**:
  ```java
  public static String createChannelJson(long channelId, String name, String type) {
      return """
              {
                "channelId": %d,
                "name": "%s",
                "type": "%s"
              }""".formatted(channelId, name, type);
  }
  ```

- **들여쓰기 제어**:
  ```java
  // 닫는 """의 위치로 들여쓰기 기준점 결정
  // 공통 선행 공백은 자동 제거됨
  return """
          {
            "indented": true
          }
          """;
  ```

- **이스케이프 시퀀스**:
  - `\s`: 후행 공백 유지 (trailing space)
  - `\`: 줄 바꿈 방지 (line continuation)
  ```java
  String escaped = """
          줄 끝에 공백 유지:\s
          이 줄과 \
          다음 줄은 하나로 합쳐짐""";
  ```

### TC 목록

| TC | 내용 | 기대 결과 |
|----|------|----------|
| TB-01 | JSON Before vs After 동일 | jsonBefore() == jsonAfter() |
| TB-02 | SQL Before vs After 동일 | sqlBefore()과 sqlAfter() 공백 정규화 후 동일 |
| TB-03 | formatted() 변수 삽입 | `"channelId": 100, "name": "개발팀"` 포함 |
| TB-04 | \s 후행 공백 유지 | "줄 끝에 공백 유지: " (공백 유지됨) |
| TB-05 | \ 줄 바꿈 방지 | "이 줄과 다음 줄은 하나로 합쳐짐" (한 줄) |
| TB-06 | HTML 템플릿 | "<h3>홍길동님이 개발팀에 메시지를 보냈습니다</h3>" 포함 |

### 팀원에게 한마디

JSON, SQL, HTML 같은 여러 줄 문자열을 작성할 때 text block을 쓰면 소스 코드 가독성이 훨씬 좋아진다. formatted()와 함께 쓰면 변수 삽입도 깔끔하게 가능하다.

---

## 종합 정리

| 기능 | 핵심 가치 | 메신저 서버 주요 적용 |
|------|----------|---------------------|
| **Sealed Classes** | 타입 안전한 계층 구조 | MessageType/ChannelType/Notification: 유한한 하위 타입으로 exhaustive switch 보장 |
| **Records** | 불변 DTO, 보일러플레이트 제거 | ApiUser, MessageResponse, DTO 전부 한 줄로 정의, ChannelId/MemberId 값 객체 검증 |
| **Pattern Matching** | 안전한 타입 검사+캐스팅 | Event 핸들링, guard condition으로 추가 조건 검사, 다형성 처리 안전성 |
| **Text Blocks** | 가독성 좋은 여러 줄 문자열 | JSON 쿼리, SQL, HTML 템플릿을 소스 코드처럼 작성 |

### 조합 활용 예시

메신저 서버에서 이 4가지를 모두 활용하는 장면:

```java
// 1. Sealed + Record: 타입 안전 + 불변 DTO
public sealed interface Event permits MessageSentEvent, MemberJoinedEvent { }
public record MessageSentEvent(long channelId, long logId, String text) implements Event { }

// 2. Pattern Matching + Guard: 안전한 이벤트 분류
public static void handleEvent(Event event) {
    if (event instanceof MessageSentEvent e && !e.text().isBlank()) {
        // 유효한 메시지만 처리
    }
}

// 3. Text Blocks: 복잡한 JSON 쿼리를 깔끔하게
String analyticsQuery = """
        {
          "eventType": "MESSAGE_SENT",
          "count": %d
        }""".formatted(count);

// 4. Record: API 응답을 간단하게
public record ApiResult<T>(boolean success, T data) { }
```

이 4가지를 함께 쓰면:
- **컴파일 타임 안전성**: sealed class로 모든 경우 검사
- **코드 간결성**: record로 보일러플레이트 90% 제거
- **타입 안전성**: pattern matching으로 캐스팅 실수 방지
- **가독성**: text block으로 문자열 템플릿이 눈에 들어옴
