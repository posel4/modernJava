# Java 25 나머지 주요 기능

> super()/this() 전 검증으로 안전한 생성자, primitive 패턴 매칭으로 깔끔한 분기, 모듈 단위 import로 간결한 헤더.

---

## 1. Flexible Constructor Bodies (JEP 492) - 3rd Preview

### 한줄 요약
**생성자에서 super() 또는 this() 호출 전에 인자 검증과 변환을 수행할 수 있어 부모 클래스 초기화 전 안정성 보장.**

### 왜 필요한가?

Java 25 이전에는 생성자에서 `super()` 또는 `this()` 호출이 **반드시 첫 번째 문장**이어야 했습니다.

```
문제점:
❌ super() 호출 전 유효성 검사 불가능
❌ 유효하지 않은 인자로도 부모 생성자 실행됨
❌ 인자 변환(normalization) 후 전달 불가능
```

예를 들어, 채널 ID 검증 없이 부모 생성자가 먼저 초기화되었고, 이후에야 검증하면:
- 부분적으로 초기화된 객체가 메모리에 남음
- 생성자 실패 시 정리(cleanup)가 복잡해짐

Java 25에서는 `super()/this()` 전에 **검증 및 변환**을 먼저 수행하고, 안전한 상태에서만 부모를 초기화합니다.

### Before / After 코드

#### Before (Java 24 이전)
```java
public class OldStyleChannel extends Channel {
    private final int maxMembers;

    public OldStyleChannel(long id, String name, int maxMembers) {
        super(id, name);  // ← 반드시 첫 줄! 검증 없이 부모 초기화

        // 이 시점에 검증 (너무 늦음)
        if (maxMembers <= 0) throw new IllegalArgumentException("maxMembers must be positive");
        this.maxMembers = maxMembers;
    }
}
```

**문제:**
- `super()` 실행 → 부모 필드 초기화됨 → `maxMembers` 검증 실패 → 예외
- 부분적으로 초기화된 객체가 남음

#### After (Java 25)
```java
public class FlexibleChannel extends Channel {
    private final int maxMembers;

    public FlexibleChannel(long id, String name, int maxMembers) {
        // super() 전에 모든 검증!
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (maxMembers <= 0) throw new IllegalArgumentException("maxMembers must be positive");

        // super() 전에 변환!
        var normalizedName = name.strip().toLowerCase();

        super(id, normalizedName);  // 안전한 인자로 부모 초기화
        this.maxMembers = maxMembers;
    }
}
```

**장점:**
- ✅ 모든 인자 검증 후 `super()` 호출
- ✅ 완전히 유효한 상태에서만 부모 초기화
- ✅ 인자 정규화(strip, toLowerCase) 후 부모에 전달
- ✅ 예외 발생 시 객체 생성 자체가 실패 (부분 초기화 없음)

#### this() delegating constructor에서도 동작
```java
public class Message {
    private final long id;
    private final String content;

    public Message(long id, String content, String sender) {
        this.id = id;
        this.content = content;
    }

    // this() 전 검증
    public Message(String content, String sender) {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        var trimmedContent = content.strip();

        this(System.nanoTime(), trimmedContent, sender);  // 안전한 상태로 위임
    }
}
```

### 실제 코드 예제

#### FC-01: 채널 생성자에서 ID/이름 검증

```java
public class DmChannel extends BaseChannel {
    private final Set<Long> memberIds;

    public DmChannel(long channelId, String name, Set<Long> memberIds) {
        // super() 전 검증
        if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (memberIds == null || memberIds.isEmpty())
            throw new IllegalArgumentException("memberIds must not be empty");

        // super() 전 변환
        var trimmedName = name.strip();
        var immutableMembers = Set.copyOf(memberIds);  // 불변화

        super(channelId, trimmedName);
        this.memberIds = immutableMembers;
    }
}
```

#### FC-02: 메시지 빌더에서 required 필드 검증

```java
public class MessageBuilder {
    private final long messageId;
    private final String content;
    private final long senderId;

    public MessageBuilder(long msgId, String text, long sId) {
        // build() 전에 모든 필드 검증
        if (msgId <= 0) throw new IllegalArgumentException("messageId must be positive");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("content required");
        if (sId <= 0) throw new IllegalArgumentException("senderId must be positive");

        // 정규화 후 이 객체 초기화
        this.messageId = msgId;
        this.content = text.strip();
        this.senderId = sId;
    }
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| FC-01 | super() 전 인자 검증 | 채널 ID, 이름 null/blank 체크 |
| FC-02 | super() 전 인자 변환 | 문자열 strip/toLowerCase, 컬렉션 불변화 |
| FC-03 | this() 전 검증 (delegating constructor) | 내용 검증 후 주 생성자로 위임 |

### 메신저 서버 적용 포인트

1. **Channel/User/Message 엔티티 생성자**
   ```java
   // 채널 생성 시 ID, 이름, 멤버 모두 검증 후 초기화
   public Channel(long id, String name, int capacity) {
       if (id <= 0 || name.isBlank() || capacity <= 0)
           throw new IllegalArgumentException();
       this.name = name.strip().toLowerCase();
       // ... 안전한 상태에서 불변 필드 설정
   }
   ```

2. **DM/그룹 채널 생성자**
   ```java
   // 선택적 필드도 this() 전에 정규화
   public DmChannel(long channelId, String name) {
       if (channelId <= 0) throw new IllegalArgumentException();
       var cleaned = name == null ? "Untitled DM" : name.strip();

       this(channelId, cleaned, LocalDateTime.now());
   }
   ```

3. **에러 핸들링 전문화**
   ```java
   // 생성자 실패 = 객체 생성 안 함 (부분 초기화 없음)
   // → 데이터베이스 저장 시 일관성 보장
   ```

### 팀원에게 한마디

Flexible Constructor는 **방어적 프로그래밍을 더 자연스럽게** 만듭니다.

- **Java의 철학**: "실패는 빨리, 완전하게" (fail-fast, all-or-nothing)
- **이전**: 부모 초기화 후 자식 검증 → 부분 초기화 위험
- **지금**: 모든 검증 → 부모 초기화 → 안전 보장

메신저 서버에서 **Channel, User, Message 같은 핵심 엔티티 생성자**가 훨씬 안전해집니다. "이 객체가 생성되었다 = 모든 불변식이 만족된다"는 계약을 지킬 수 있습니다.

---

## 2. Primitive Types in Patterns (JEP 488) - 2nd Preview

### 한줄 요약
**switch 표현식과 패턴 매칭에서 int, long, boolean, double 같은 primitive 타입을 직접 패턴으로 사용하고 guard clause로 범위 검사.**

### 왜 필요한가?

Java 24까지는 switch에서 primitive 타입을 사용할 때 **상수만 매칭**할 수 있었습니다.

```
문제점:
❌ int/long의 범위 검사 (e.g., 0~100, 100~1000)를 switch로 표현 불가
❌ HTTP 상태 코드 분류, 에러 코드 매핑은 if-else 체인 필수
❌ 범위 기반 분류가 번거로움
```

Java 25에서는 primitive 타입을 패턴으로 사용하고, **guard clause(when)**로 범위를 표현합니다:
```java
case int code when code >= 1000 && code < 2000 -> "Channel Error"
case int code when code >= 2000 && code < 3000 -> "Message Error"
```

### Before / After 코드

#### Before (Java 24)
```java
// HTTP 상태 코드 분류 - 상수만 가능
public static String classifyStatus(int status) {
    return switch (status) {
        case 200 -> "OK";
        case 201 -> "Created";
        case 400 -> "Bad Request";
        case 404 -> "Not Found";
        case 500 -> "Internal Server Error";
        default -> "Unknown";
    };
}

// 나이 범위 분류 - if-else 강제
public static String classifyAge(int age) {
    if (age < 0) return "Invalid";
    if (age < 13) return "Child";
    if (age < 20) return "Teenager";
    if (age < 65) return "Adult";
    return "Senior";
}
```

#### After (Java 25)
```java
// 동일하게 상수 매칭 (기존 코드 호환)
public static String classifyStatus(int status) {
    return switch (status) {
        case 200 -> "OK";
        case 201 -> "Created";
        case 400 -> "Bad Request";
        case 404 -> "Not Found";
        case 500 -> "Internal Server Error";
        default -> "Unknown";
    };
}

// 범위 기반 분류 - switch로 표현 가능!
public static String classifyAge(int age) {
    return switch (age) {
        case int a when a < 0 -> "Invalid";
        case int a when a < 13 -> "Child";
        case int a when a < 20 -> "Teenager";
        case int a when a < 65 -> "Adult";
        default -> "Senior";
    };
}

// boolean도 패턴 매칭
public static String booleanSwitch(boolean flag) {
    return switch (flag) {
        case true -> "Enabled";
        case false -> "Disabled";
    };
}
```

### 실제 코드 예제

#### PP-01: HTTP 상태 코드 분류

```java
public static String classifyHttpStatus(int status) {
    return switch (status) {
        case 200, 201, 204 -> "Success";
        case 301, 302, 304 -> "Redirect";
        case 400, 401, 403, 404 -> "Client Error";
        case 500, 502, 503 -> "Server Error";
        default -> "Unknown (" + status + ")";
    };
}
```

#### PP-02: 메신저 서버 에러 코드 분류

```java
public static String classifyErrorCode(int errorCode) {
    return switch (errorCode) {
        case int code when code >= 1000 && code < 2000 -> "Channel Error";
        case int code when code >= 2000 && code < 3000 -> "Message Error";
        case int code when code >= 3000 && code < 4000 -> "Member Error";
        case int code when code >= 4000 && code < 5000 -> "File Error";
        default -> "Unknown Error";
    };
}
```

#### PP-03: 메시지 ID 범위 분류

```java
public static String classifyMessageId(long messageId) {
    return switch (messageId) {
        case long id when id <= 0 -> "Invalid ID";
        case long id when id < 1_000_000L -> "Legacy ID";
        case long id when id < 1_000_000_000L -> "Standard ID";
        default -> "Snowflake ID";
    };
}
```

#### PP-04: boolean과 double 패턴

```java
// boolean 패턴 매칭
public static String notificationStatus(boolean enabled) {
    return switch (enabled) {
        case true -> "Notifications ON";
        case false -> "Notifications OFF";
    };
}

// double 범위 분류 (온도 예제)
public static String classifyTemperature(double temp) {
    return switch (temp) {
        case double t when t < 0.0 -> "Freezing";
        case double t when t < 20.0 -> "Cold";
        case double t when t < 30.0 -> "Comfortable";
        default -> "Hot";
    };
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| PP-01 | int 상수 매칭 (기존 기능) | HTTP 상태 코드 분류 |
| PP-02 | int 범위 패턴 with guard | 에러 코드 도메인 분류 |
| PP-03 | long 범위 패턴 with guard | 메시지 ID 범위 분류 |
| PP-04 | boolean, double 패턴 | 활성화 상태, 온도 범위 |

### 메신저 서버 적용 포인트

1. **에러 코드 분류 (대역폭 절약)**
   ```java
   // 기존: if-else 체인 5줄
   // 새로움: switch 표현식 1줄
   String domain = switch (errorCode) {
       case int c when c >= 1000 && c < 2000 -> "CHANNEL";
       case int c when c >= 2000 && c < 3000 -> "MESSAGE";
       default -> "UNKNOWN";
   };
   ```

2. **API 응답 코드 매핑**
   ```java
   public ResponseStatus mapStatus(int httpCode) {
       return switch (httpCode) {
           case 200, 201 -> ResponseStatus.SUCCESS;
           case 400, 401, 403 -> ResponseStatus.CLIENT_ERROR;
           case 500, 502, 503 -> ResponseStatus.SERVER_ERROR;
           default -> ResponseStatus.UNKNOWN;
       };
   }
   ```

3. **메시지 상태 분류**
   ```java
   public String classifyMessageState(long stateCode) {
       return switch (stateCode) {
           case long s when s == 0 -> "DRAFT";
           case long s when s == 1 -> "SENT";
           case long s when s == 2 -> "DELIVERED";
           case long s when s == 3 -> "READ";
           default -> "UNKNOWN";
       };
   }
   ```

4. **조절 기능 (Rate Limiting, Timeout)**
   ```java
   public String rateLimit(long requestsPerSecond) {
       return switch (requestsPerSecond) {
           case long r when r < 10 -> "ALLOW";
           case long r when r < 100 -> "WARN";
           case long r when r >= 100 -> "BLOCK";
           default -> "UNKNOWN";
       };
   }
   ```

### 팀원에게 한마디

Primitive Pattern Matching은 **분기 로직을 더 명확하고 안전하게** 만듭니다.

- **가독성**: if-else 체인 대신 switch 표현식으로 한눈에 분류 기준이 보임
- **유지보수**: 범위를 변경해도 한 곳에서만 수정 (guard clause)
- **성능**: switch는 테이블 점프(jump table)로 최적화됨 (if-else보다 빠름)

메신저 서버에서 **에러 코드, HTTP 상태, 메시지 ID, 활성화 플래그** 같은 숫자 기반 분류가 자주 나타나므로, 이 기능으로 코드를 훨씬 깔끔하게 작성할 수 있습니다.

---

## 3. Module Import Declarations (JEP 494) - 2nd Preview

### 한줄 요약
**`import module java.base;` 한 줄로 모듈의 모든 공개 패키지를 한꺼번에 import하여 import 선언부를 간결하게.**

### 왜 필요한가?

Java는 JPMS(Java Platform Module System)를 통해 모듈을 관리합니다.

```
문제점:
❌ java.base 모듈에는 수십 개 패키지가 있음
❌ java.util, java.time, java.io 등을 일일이 import 필수
❌ 새 파일 작성 시 어떤 import가 필요한지 매번 고민
```

Java 25에서는 **모듈 단위 import**로 해결:
```java
import module java.base;  // java.util, java.time, java.io 모두 포함
```

### Before / After 코드

#### Before
```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.Duration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.net.URI;
// ... 더 필요할 때마다 추가

public class UserRepository {
    // ...
}
```

#### After
```java
import module java.base;  // 위의 모든 import를 대체!

public class UserRepository {
    // List, Map, Collectors, LocalDateTime, Duration, Files 등
    // 모두 사용 가능
}
```

### 실제 코드 예제

#### MI-01: java.base 모듈 전체 사용

```java
import module java.base;

public class DataProcessor {
    // List, Map, Collectors
    public Map<String, List<String>> groupByPrefix(List<String> items) {
        return items.stream()
            .collect(Collectors.groupingBy(s -> s.substring(0, 1)));
    }

    // Duration, LocalDateTime 등 java.time 타입
    public String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return String.format("%02d:%02d", hours, minutes);
    }

    // java.nio 타입
    public List<String> readLines(Path filePath) throws IOException {
        return Files.readAllLines(filePath);
    }
}
```

#### MI-02: 선택적 모듈 import (현재는 java.base만 지원)

```java
import module java.base;      // 표준 라이브러리
import java.util.logging.Logger;  // 필요한 것만 개별 import

public class ServiceWithLogging {
    private static final Logger logger = Logger.getLogger("Service");

    public List<Integer> processNumbers(List<Integer> numbers) {
        logger.info("Processing " + numbers.size() + " numbers");
        return numbers.stream()
            .filter(n -> n > 0)
            .collect(Collectors.toList());
    }
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| MI-01 | java.base 모듈 전체 import | List, Map, Duration, Files 등 한꺼번에 |
| MI-02 | 모듈 import + 개별 import 혼합 | 필요하면 추가 import도 가능 |

### 메신저 서버 적용 포인트

1. **Repository 클래스에서 Collection 활용**
   ```java
   import module java.base;

   public class ChannelRepository {
       // List, Map, Set, Stream API 자유로움
       public List<Channel> findAll() { ... }
       public Map<Long, Channel> findByIds(Collection<Long> ids) { ... }
   }
   ```

2. **Service 클래스에서 시간/날짜 처리**
   ```java
   import module java.base;

   public class MessageService {
       // LocalDateTime, Duration, Instant 자유로움
       public Message createMessage(String content) {
           return new Message(content, LocalDateTime.now());
       }
   }
   ```

3. **유틸리티 클래스에서 IO/NIO 활용**
   ```java
   import module java.base;

   public class FileUtil {
       // Files, Path, InputStream 자유로움
       public static String readFile(Path path) throws IOException {
           return new String(Files.readAllBytes(path));
       }
   }
   ```

### 팀원에게 한마디

Module Import는 **import 선언부의 보일러플레이트를 제거**합니다.

- **간결성**: 새 파일 작성 시 `import module java.base;` 한 줄로 시작
- **버전 무관**: 모듈에 새 패키지가 추가되어도 자동 포함
- **IDE 지원**: 어떤 타입을 쓸 수 있는지 한눈에 알아짐

메신저 서버에서는 **모든 Java 파일이 java.base를 사용**하므로 (List, LocalDateTime, etc), 이 기능으로 import 관리가 훨씬 간단해집니다.

---

## 4. Compact Source Files (JEP 495) - 4th Preview

### 한줄 요약
**클래스 선언 없이 main() 메서드만 작성하여 단순한 프로그램을 빠르게 프로토타이핑하고 실행.**

### 왜 필요한가?

Java 입문자나 스크립트 작성 시 보일러플레이트가 과하다는 비판:

```
기존 방식:
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

Python, JavaScript처럼 간단하게:
```python
print("Hello")
```

Java 25는 **중간 지점**을 제공합니다:
```java
void main() {
    println("Hello");
}
```

### Before / After 코드

#### Before (전통적 Java)
```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}

public class Calculator {
    public static void main(String[] args) {
        int sum = 5 + 3;
        System.out.println("Sum: " + sum);
    }
}
```

#### After (Compact Source File)
```java
// HelloWorld.java 전체 내용 (클래스 선언 없음!)
void main() {
    println("Hello, World!");
}

// Calculator.java 전체 내용
void main() {
    int sum = 5 + 3;
    println("Sum: " + sum);
}
```

**자동으로 제공되는 기능:**
- 클래스 이름 = 파일명 (자동 생성)
- `static` 불필요 (instance main)
- `String[] args` 불필요 (varargs 자동)
- `println()` 직접 호출 (System.out 자동 import)
- `import module java.base;` 자동 적용

### 실제 코드 예제

#### CS-01: 간단한 스크립트

```java
// Script.java (패키지 없음, 클래스 선언 없음)
void main() {
    println("Welcome to Messenger Playground");
    println("Java 25 Compact Source Sample");
}
```

실행:
```bash
javac Script.java
java Script
# 출력:
# Welcome to Messenger Playground
# Java 25 Compact Source Sample
```

#### CS-02: 데이터 처리 스크립트

```java
// DataProcessor.java
void main() {
    var numbers = List.of(1, 2, 3, 4, 5);
    var sum = numbers.stream()
        .mapToInt(Integer::intValue)
        .sum();
    println("Sum: " + sum);

    var doubled = numbers.stream()
        .map(n -> n * 2)
        .toList();
    println("Doubled: " + doubled);
}
```

실행:
```
Sum: 15
Doubled: [2, 4, 6, 8, 10]
```

#### CS-03: 빠른 프로토타이핑 (API 테스트)

```java
// QuickApiTest.java
void main() {
    var client = new HttpClient.Builder().build();
    var request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/users"))
        .GET()
        .build();

    try {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        println("Status: " + response.statusCode());
        println("Body: " + response.body());
    } catch (Exception e) {
        println("Error: " + e.getMessage());
    }
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| CS-01 | 클래스 선언 없는 main | 패키지 선언 없이 void main() 작성 |
| CS-02 | 자동 import (java.base) | List, Stream, println() 사용 |
| CS-03 | 빠른 프로토타이핑 | API 테스트, 데이터 처리 스크립트 |

### 메신저 서버 적용 포인트

**직접 활용도: 낮음** (Spring Boot 애플리케이션이므로 @SpringBootApplication 필수)

하지만 다음 시나리오에서는 유용합니다:

1. **개발 환경 설정 스크립트**
   ```java
   // InitDb.java
   void main() {
       var datasource = new HikariDataSource();
       datasource.setJdbcUrl("jdbc:mysql://localhost:3306/messenger");
       println("Database initialized");
   }
   ```

2. **마이그레이션 도구**
   ```java
   // MigrateErrorCodes.java
   void main() {
       var oldCodes = loadOldErrorCodes();
       var newCodes = transformErrorCodes(oldCodes);
       saveNewErrorCodes(newCodes);
       println("Migration complete");
   }
   ```

3. **배치 작업 프로토타입**
   ```java
   // QuickBatchTest.java
   void main() {
       var messages = loadMessages();
       messages.forEach(msg -> println(msg.getId() + ": " + msg.getContent()));
   }
   ```

4. **단위 테스트 헬퍼**
   ```java
   // GenerateTestData.java
   void main() {
       var channels = IntStream.range(0, 10)
           .mapToObj(i -> new Channel(i, "Channel-" + i))
           .toList();
       println("Generated " + channels.size() + " test channels");
   }
   ```

### 팀원에게 한마디

Compact Source Files는 **Java의 접근성을 높이는 실험**입니다.

- **입문자 친화적**: "public static void main(String[] args)" 없이 시작
- **프로토타이핑 빠름**: 스크립트 언어처럼 빠르게 코드 작성
- **학습 곡선 완화**: Java 문법 학습 전에 로직 구현에 집중

메신저 서버 **프로덕션 코드**에서는 거의 쓰지 않을 것 같지만, **마이그레이션 도구, 배치 작업, 빠른 테스트** 같은 스크립트성 작업에서 유용합니다. Java를 "진정한 멀티퍼퍼즈 언어"로 만드는 신호입니다.

---

## 종합 정리

### Java 25 나머지 기능 요약

| 기능 | 핵심 가치 | 메신저 서버 활용 |
|------|----------|-----------------|
| **Flexible Constructor** | super()/this() 전 검증으로 안정성 | 높음 (엔티티 생성자) |
| **Primitive Patterns** | 범위 기반 분류를 switch로 표현 | 높음 (에러 코드, 상태 분류) |
| **Module Import** | 모듈 단위 import로 선언부 간결화 | 높음 (모든 파일이 java.base 사용) |
| **Compact Source** | 클래스 없이 main() 작성 | 낮음 (스크립트/프로토타이핑용) |

### 각 기능별 학습 포인트

**Flexible Constructor Bodies (FC)**
- [x] super() 호출이 더 이상 첫 줄일 필요 없음
- [x] 부모 초기화 전 유효성 검사로 부분 초기화 방지
- [x] 생성자 실패 = 객체 생성 안 함 (all-or-nothing)

**Primitive Types in Patterns (PP)**
- [x] switch에서 int/long/boolean/double을 직접 패턴으로 사용
- [x] guard clause (when)로 범위 검사
- [x] if-else 체인을 깔끔한 switch 표현식으로 대체

**Module Import Declarations (MI)**
- [x] `import module java.base;` 한 줄로 모든 표준 패키지 사용 가능
- [x] import 선언부 최소화
- [x] 새 파일 작성 시 import 고민 제거

**Compact Source Files (CS)**
- [x] 클래스 선언 없이 void main() 작성
- [x] static, String[] args 불필요
- [x] 프로토타이핑, 스크립트, 마이그레이션 도구에 유용

### 실전 체크리스트

- [ ] 엔티티 생성자에서 Flexible Constructor 적용 (Channel, User, Message)
- [ ] 에러 코드 분류를 Primitive Pattern switch로 리팩토링
- [ ] 모든 Java 파일 헤더에 `import module java.base;` 검토
- [ ] 개발 스크립트/마이그레이션 도구를 Compact Source로 작성
- [ ] guard clause를 사용해 범위 기반 분류 명확화

### 추가 학습 자료

- [JEP 492: Flexible Constructor Bodies](https://openjdk.org/jeps/492)
- [JEP 488: Primitive Types in Patterns](https://openjdk.org/jeps/488)
- [JEP 494: Module Import Declarations](https://openjdk.org/jeps/494)
- [JEP 495: Compact Source Files](https://openjdk.org/jeps/495)
