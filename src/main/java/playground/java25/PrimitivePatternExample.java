package playground.java25;

/**
 * Primitive Types in Patterns (JEP 488, 2nd Preview in Java 25)
 *
 * Java 25에서는 switch 표현식과 패턴 매칭에서 primitive 타입을 직접 사용할 수 있다.
 *
 * 기존에는 int, long, boolean 등의 primitive를 switch에서 사용할 때
 * 상수 값만 매칭할 수 있었다. Java 25에서는:
 * - primitive 타입 패턴: case int a -> ...
 * - guard clause: case int a when a > 0 -> ...
 * - boolean switch: case true -> ... / case false -> ...
 * - float/double switch with guard
 *
 * 이를 통해 if-else 체인을 깔끔한 switch 표현식으로 대체할 수 있다.
 */
public class PrimitivePatternExample {

    // ========================================================================
    // PP-01: int switch - HTTP 상태 코드 분류
    // ========================================================================

    /**
     * HTTP 상태 코드를 문자열로 분류한다.
     * 기존 int switch는 상수 매칭만 가능했지만,
     * Java 25에서는 guard clause를 추가할 수도 있다.
     *
     * @param status HTTP 상태 코드
     * @return 상태 코드의 설명
     */
    public static String classifyHttpStatus(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown (" + status + ")";
        };
    }

    // ========================================================================
    // PP-02: int primitive pattern with guard clause - 나이 분류
    // ========================================================================

    /**
     * 나이를 카테고리로 분류한다.
     * primitive 타입 패턴과 guard clause(when)를 사용한다.
     *
     * case int a when a < 0 → "Invalid"
     * 이전에는 이런 범위 체크를 switch로 표현할 수 없었다.
     *
     * @param age 나이
     * @return 나이 카테고리
     */
    public static String classifyAge(int age) {
        return switch (age) {
            case int a when a < 0 -> "Invalid";
            case int a when a < 13 -> "Child";
            case int a when a < 20 -> "Teenager";
            case int a when a < 65 -> "Adult";
            default -> "Senior";
        };
    }

    // ========================================================================
    // PP-03: long switch with guard clause - 메시지 ID 분류
    // ========================================================================

    /**
     * 메시지 ID를 범위별로 분류한다.
     * long 타입에서도 primitive 패턴이 작동한다.
     *
     * @param messageId 메시지 ID
     * @return ID 범위 카테고리
     */
    public static String classifyMessageId(long messageId) {
        return switch (messageId) {
            case long id when id <= 0 -> "Invalid ID";
            case long id when id < 1_000_000 -> "Legacy ID";
            case long id when id < 1_000_000_000L -> "Standard ID";
            default -> "Snowflake ID";
        };
    }

    // ========================================================================
    // PP-04: boolean switch - 새로운 기능
    // ========================================================================

    /**
     * boolean switch: true/false를 패턴으로 매칭한다.
     * 기존에는 boolean을 switch에 사용할 수 없었다.
     * Java 25에서는 가능하다.
     *
     * @param flag boolean 값
     * @return "Enabled" 또는 "Disabled"
     */
    public static String booleanSwitch(boolean flag) {
        return switch (flag) {
            case true -> "Enabled";
            case false -> "Disabled";
        };
    }

    // ========================================================================
    // PP-05: double switch with guard - 온도 분류
    // ========================================================================

    /**
     * 온도를 카테고리로 분류한다.
     * double 타입에서도 primitive 패턴이 작동한다.
     *
     * @param temp 온도 (섭씨)
     * @return 온도 카테고리
     */
    public static String classifyTemperature(double temp) {
        return switch (temp) {
            case double t when t < 0.0 -> "Freezing";
            case double t when t < 20.0 -> "Cold";
            case double t when t < 30.0 -> "Comfortable";
            default -> "Hot";
        };
    }

    // ========================================================================
    // PP-06: 메신저 서버 활용 - 에러 코드 분류
    // ========================================================================

    /**
     * 메신저 서버 에러 코드를 도메인별로 분류한다.
     * 범위 기반 분류를 switch로 깔끔하게 표현할 수 있다.
     *
     * 기존에는 if-else 체인이 필요했던 패턴:
     * if (code >= 1000 && code < 2000) return "Channel Error";
     * else if ...
     *
     * @param errorCode 에러 코드
     * @return 에러 도메인
     */
    public static String classifyErrorCode(int errorCode) {
        return switch (errorCode) {
            case int code when code >= 1000 && code < 2000 -> "Channel Error";
            case int code when code >= 2000 && code < 3000 -> "Message Error";
            case int code when code >= 3000 && code < 4000 -> "Member Error";
            case int code when code >= 4000 && code < 5000 -> "File Error";
            default -> "Unknown Error";
        };
    }
}
