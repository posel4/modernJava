# Java 22 주요 기능

> Unnamed Variables로 의도 명확화, FFM API로 JNI 없는 네이티브 상호작용.

---

## 1. Unnamed Variables & Patterns (JEP 456)

### 한줄 요약
**사용하지 않는 변수를 `_`로 선언하여 "이 값은 의도적으로 무시한다"를 코드로 표현.**

### 왜 필요한가?

IDE 경고를 피하기 위해 불필요한 변수명을 만들어야 했던 과거:
```java
// 이전: key는 필요 없지만 함수 시그니처 때문에 변수를 만들어야 함
map.forEach((unusedKey, value) -> total += value);

// 경고 대시보드
// ⚠️ Variable 'unusedKey' is never used
// ⚠️ variable name 'unused*' suggests unused variable
```

개발자의 **명확한 의도 표현**:
- "이 변수는 의도적으로 사용 안 함"
- IDE 경고 없음
- 코드 리뷰 시 "왜 이 변수가 없지?" 같은 질문 불필요

### Before / After 코드

#### Before (Java 21 이전)
```java
// 1. Map.forEach에서 불필요한 변수명
map.forEach((key, value) -> {
    // key는 안 쓰는데 어쩔 수 없이 변수로 만들어야 함
    total += value;
});

// 2. catch에서 사용 안 하는 예외
try {
    Integer.parseInt(s);
    return true;
} catch (NumberFormatException e) {  // e는 안 씀
    return false;
}

// 3. for-each에서 요소 무시 (횟수만 필요)
int count = 0;
for (var item : list) {  // item은 안 씀
    count++;
}

// 4. sealed type에서 세부 필드 무시
public static String shapeType(Shape shape) {
    return switch (shape) {
        case Circle c -> "원";           // c는 안 씀
        case Rectangle r -> "직사각형";   // r은 안 씀
        case Triangle t -> "삼각형";      // t는 안 씀
    };
}
```

#### After (Java 22)
```java
// 1. Map.forEach - key 무시
map.forEach((_, value) -> {
    total += value;  // 의도 명확
});

// 2. catch - 예외 무시
try {
    Integer.parseInt(s);
    return true;
} catch (NumberFormatException _) {  // 의도적으로 무시
    return false;
}

// 3. for-each - 요소 무시
int count = 0;
for (var _ : list) {  // 요소를 사용 안 함을 명시
    count++;
}

// 4. sealed type - 패턴 무시
public static String shapeType(Shape shape) {
    return switch (shape) {
        case Circle _ -> "원";
        case Rectangle _ -> "직사각형";
        case Triangle _ -> "삼각형";
    };
}
```

### 실제 코드 예제 (프로젝트에서)

#### UV-01: 다양한 컨텍스트에서 _ 사용

**Map value 합산 (key 무시)**
```java
public static int sumValues(Map<String, Integer> map) {
    var ref = new Object() { int total = 0; };
    map.forEach((_, value) -> ref.total += value);
    return ref.total;
}
```

**List 횟수 카운트 (요소 무시)**
```java
public static int countItems(List<?> list) {
    int count = 0;
    for (var _ : list) {  // 요소 자체는 불필요, 개수만 셈
        count++;
    }
    return count;
}
```

**예외 무시 (try-catch)**
```java
public static boolean tryParseInt(String s) {
    try {
        Integer.parseInt(s);
        return true;
    } catch (NumberFormatException _) {  // 예외 정보 불필요
        return false;
    }
}
```

#### UV-02: Unnamed Patterns in switch (sealed types)

**알림 채널 분류 (세부 정보 무시)**
```java
public sealed interface Notification permits Push, Email, InApp {}
public record Push(String token, String title, String body) implements Notification {}
public record Email(String address, String subject, String body) implements Notification {}
public record InApp(long userId, String message) implements Notification {}

public static String notificationChannel(Notification notification) {
    return switch (notification) {
        case Push _ -> "PUSH";
        case Email _ -> "EMAIL";
        case InApp _ -> "IN_APP";
    };
}
```

**수신자 추출 (일부 필드만 사용)**
```java
public static String recipient(Notification notification) {
    return switch (notification) {
        case Push(var token, _, _) -> "device:" + token;
        case Email(var addr, _, _) -> "email:" + addr;
        case InApp(var userId, _) -> "user:" + userId;
    };
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| UV-01 | 다양한 컨텍스트에서 `_` 사용 | Map.forEach, for-each, catch, try-with-resources |
| UV-02 | Unnamed patterns in switch (sealed types) | 패턴 매칭에서 필드 또는 전체 패턴 무시 |

### 메신저 서버 적용 포인트

1. **이벤트 핸들러 (메시지 발송)**
   ```java
   // 메시지 전송 후 응답 무시할 경우
   messageQueue.process((_, ackFlag) -> {
       // ackFlag만 필요, 메시지 자체는 이미 처리됨
   });
   ```

2. **알림 채널 라우팅**
   ```java
   // 알림 종류만 판단, 세부 필드는 서로 다름
   switch (notification) {
       case PushNotification _ -> sendViaPush();
       case EmailNotification _ -> sendViaEmail();
       case InAppNotification _ -> saveInApp();
   }
   ```

3. **에러 핸들링 (예외 타입만 중요)**
   ```java
   try {
       sendMessage();
   } catch (TimeoutException _) {
       retryWithBackoff();
   } catch (NetworkException _) {
       failover();
   }
   ```

4. **반복문에서 횟수 카운팅**
   ```java
   // 배치 처리에서 몇 개 처리했는지만 필요
   for (var _ : messageBatch) {
       processMessage();
       processedCount++;
   }
   ```

### 팀원에게 한마디

Java 22에서 Unnamed Variables는 작은 기능처럼 보이지만, **코드 의도를 명확히 표현**한다는 점에서 중요합니다.

- IDE 경고 제거: 코드 품질 리포트가 깔끔해짐
- 코드 리뷰 간편: "왜 이 변수를 쓰지 않아?" 같은 질문 불필요
- 가독성 향상: 한눈에 "이건 의도적으로 무시한 값"이 드러남

특히 **sealed types + switch 패턴 매칭**과 함께 쓸 때 위력이 있습니다. 메신저 서버에서 알림 채널 분류나 메시지 상태 처리할 때 자주 마주치는 패턴이므로, 이 기능으로 코드를 더 읽기 좋게 만들 수 있습니다.

---

## 2. Foreign Function & Memory API (JEP 454)

### 한줄 요약
**JNI 없이 Java에서 직접 C/C++ 라이브러리와 상호작용하고, 안전하게 네이티브 메모리를 관리.**

### 왜 필요한가?

#### 문제 1: JNI의 복잡성
```
Java ↔ JNI ↔ C/C++
     (번거로움: .so/.dll 관리, 크래시 위험)
```

- C 함수 호출마다 JNI 래퍼 클래스 작성 필요
- 메모리 관리 어려움 (메모리 누수, 세그멘테이션 폴트)
- 디버깅 어려움 (JVM과 네이티브 코드 간 오류 추적)

#### FFM API의 이점
```
Java ↔ FFM API ↔ C/C++
     (직접 호출, 타입 안전, 자동 메모리 관리)
```

- JNI 래퍼 코드 제거
- `Arena` 기반 자동 메모리 해제
- 컴파일 타임 타입 체크

### Before / After 코드 (JNI vs FFM)

#### Before (JNI 방식)

**C 코드 (native.c)**
```c
#include <string.h>

JNIEXPORT jlong JNICALL Java_com_example_Native_strlen
  (JNIEnv *env, jclass clazz, jstring jstr) {
    const char *str = (*env)->GetStringUTFChars(env, jstr, 0);
    jlong len = (jlong)strlen(str);
    (*env)->ReleaseStringUTFChars(env, jstr, str);
    return len;
}
```

**Java 코드**
```java
public class Native {
    static {
        System.loadLibrary("native");  // native.so 또는 native.dll 필요
    }

    // JNI 메서드 선언
    public native long strlen(String s);
}

// 사용
long len = native.strlen("hello");
```

#### After (FFM API 방식)

```java
public static long callStrlen(String input) throws Throwable {
    // 1. 네이티브 링커 얻기
    Linker linker = Linker.nativeLinker();

    // 2. C 표준 라이브러리에서 strlen 함수 찾기
    SymbolLookup stdlib = linker.defaultLookup();

    // 3. 함수 시그니처 정의: size_t strlen(const char *s)
    MethodHandle strlen = linker.downcallHandle(
        stdlib.find("strlen").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );

    // 4. Arena로 메모리 관리 (try-with-resources로 자동 해제)
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment cString = arena.allocateFrom(input);  // Java String → C String
        return (long) strlen.invoke(cString);  // C strlen 호출
    }
}
```

**차이점:**
| 항목 | JNI | FFM API |
|------|-----|---------|
| C 래퍼 | 별도 C 코드 작성 필요 | 불필요 |
| 메모리 관리 | 수동 | Arena 자동 관리 |
| 타입 체크 | 런타임 | 컴파일 타임 (FunctionDescriptor) |
| 라이브러리 로드 | System.loadLibrary | Linker.nativeLinker |

### 실제 코드 예제 (프로젝트에서)

#### FFM-01: 네이티브 메모리 할당

**정수 배열 할당 및 처리**
```java
public static long nativeMemoryAllocation() {
    try (Arena arena = Arena.ofConfined()) {
        // 100 int 크기의 네이티브 메모리 할당
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, 100);

        // 값 쓰기
        for (int i = 0; i < 100; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT, i, i * 10);
        }

        // 값 읽기 + 합산
        long sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += segment.getAtIndex(ValueLayout.JAVA_INT, i);
        }

        return sum;
        // Arena 닫힘 → 메모리 자동 해제 (메모리 누수 없음)
    }
}
```

**문자열 저장/읽기**
```java
public static String nativeStringRoundtrip(String input) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment nativeStr = arena.allocateFrom(input);  // Java → C
        return nativeStr.getString(0);  // C → Java
    }
}
```

#### FFM-02: C 라이브러리 함수 호출

**C strlen 호출**
```java
public static long callStrlen(String input) throws Throwable {
    // C 함수 시그니처: size_t strlen(const char *s)
    Linker linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();

    MethodHandle strlen = linker.downcallHandle(
        stdlib.find("strlen").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );

    try (Arena arena = Arena.ofConfined()) {
        MemorySegment cString = arena.allocateFrom(input);
        return (long) strlen.invoke(cString);
    }
}
```

**C getpid() 호출**
```java
public static long callGetpid() throws Throwable {
    Linker linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();

    MethodHandle getpid = linker.downcallHandle(
        stdlib.find("getpid").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );

    return (int) getpid.invoke();
}
```

### TC 목록

| TC | 설명 | 예제 |
|----|------|------|
| FFM-01 | Arena를 사용한 안전한 네이티브 메모리 할당/해제 | 정수 배열 할당, 문자열 round-trip |
| FFM-02 | C 표준 라이브러리 함수 호출 (JNI 없음) | strlen, getpid 호출 |

### 메신저 서버 적용 포인트 (활용도 낮음, but 참고용)

메신저 서버에서 FFM API를 직접 쓸 일은 거의 없지만, 다음 시나리오에서 고려할 수 있습니다:

1. **성능 최적화 (C 라이브러리 활용)**
   ```java
   // 고속 암호화 라이브러리 (OpenSSL 등)를 JNI 대신 FFM으로 호출
   // (JNI 오버헤드 제거, 타입 안전)
   ```

2. **시스템 레벨 API 호출**
   ```java
   // 파일 디스크립터, 소켓 옵션 등 저수준 OS API
   // POSIX 함수 직접 호출 가능
   ```

3. **레거시 C 라이브러리 통합**
   ```java
   // 기존 .so/.dll 파일을 JNI 대신 FFM으로 호출
   // (JNI 래퍼 유지보수 부담 감소)
   ```

**현실적으로** 메신저 서버는 Java/Spring Boot 생태계를 충분히 활용하므로, FFM API는 "Java의 진화 방향"을 이해하는 수준에서 참고하면 됩니다.

### 팀원에게 한마디

FFM API는 **Java가 C/C++ 세계로 손을 뻗는 신호**입니다.

- **JNI의 쇠퇴**: Java 진영에서 JNI의 번거로움을 인정하고 더 나은 방법 제공
- **메모리 안전성**: Arena 기반 자동 메모리 관리로 세그멘테이션 폴트 예방
- **타입 안전성**: FunctionDescriptor로 C 함수 호출을 컴파일 타임에 검증

메신저 서버에서는 거의 쓸 일 없겠지만, **"Java가 고성능 네이티브 코드와 안전하게 협력할 수 있다"**는 점은 Java의 미래를 보여줍니다. 특히 성능이 중요한 금융, 게임, 머신러닝 분야에서 Java 도입을 가능하게 해줍니다.

---

## 종합 정리

### Java 22의 두 기능 요약

| 기능 | 핵심 가치 | 메신저 서버 활용 |
|------|----------|-----------------|
| **Unnamed Variables** | 코드 의도 명확화, IDE 경고 제거 | 높음 (알림/메시지 처리에서 패턴 매칭) |
| **FFM API** | JNI 없는 네이티브 상호작용 | 낮음 (참고용, 미래 대비) |

### 각 기능의 학습 포인트

**Unnamed Variables (UV)**
- [x] `_`를 단순 변수명이 아닌 **의도 표현**으로 이해
- [x] sealed types + switch 패턴 매칭과의 조합
- [x] 실무에서 즉시 활용 가능 (경고 제거, 코드 명확화)

**Foreign Function & Memory API (FFM)**
- [x] JNI의 한계와 FFM API의 이점 이해
- [x] Arena 기반 메모리 관리 원리
- [x] C 함수 호출 방식 (downcallHandle, FunctionDescriptor)
- [x] 현재는 활용도 낮지만, Java의 진화 방향 인식

### 실전 체크리스트

- [ ] Unnamed Variables를 이용해 sealed interface 기반 알림 분류 리팩토링
- [ ] 메시지 배치 처리에서 불필요한 변수명 제거
- [ ] catch 블록에서 `_` 사용으로 의도 명확화
- [ ] FFM API 샘플 코드 읽고 메모리 안전성 이해
