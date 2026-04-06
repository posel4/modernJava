package modernjava.java22;

import java.util.List;
import java.util.Map;

/**
 * Unnamed Variables & Patterns (JEP 456) - Java 22 정식
 *
 * 사용하지 않는 변수를 `_`로 선언하여 의도를 명확히 표현.
 * "이 값은 의도적으로 무시한다"를 코드로 표현.
 */
public class UnnamedVariablesExample {

    // ===== UV-01: 다양한 컨텍스트에서 _ 사용 =====

    /**
     * Map.forEach에서 key 무시
     */
    public static int sumValues(Map<String, Integer> map) {
        // key를 무시하고 value만 사용
        var ref = new Object() { int total = 0; };
        map.forEach((_, value) -> ref.total += value);
        return ref.total;
    }

    /**
     * for-each에서 요소 무시 (횟수만 중요할 때)
     */
    public static int countItems(List<?> list) {
        int count = 0;
        for (var _ : list) {
            count++;
        }
        return count;
    }

    /**
     * catch에서 예외 변수 무시
     */
    public static boolean tryParseInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    /**
     * try-with-resources에서 변수 무시
     * (리소스 자체를 사용하지 않고 생성/해제만 필요할 때)
     */
    public static void demonstrateTryWithResources() {
        try (var _ = new AutoCloseable() {
            @Override
            public void close() {
                // cleanup
            }
        }) {
            // do work
        } catch (Exception _) {
            // ignore
        }
    }

    // ===== UV-02: Unnamed Patterns in switch =====

    /**
     * sealed type에서 일부 타입의 세부 정보를 무시
     */
    public sealed interface Shape permits Circle, Rectangle, Triangle {}
    public record Circle(double radius) implements Shape {}
    public record Rectangle(double width, double height) implements Shape {}
    public record Triangle(double a, double b, double c) implements Shape {}

    /**
     * 면적만 필요하고 각 도형의 세부 필드 이름이 중요하지 않을 때
     */
    public static double area(Shape shape) {
        return switch (shape) {
            case Circle(var r) -> Math.PI * r * r;
            case Rectangle(var w, var h) -> w * h;
            case Triangle(var a, var b, var c) -> {
                double s = (a + b + c) / 2;
                yield Math.sqrt(s * (s - a) * (s - b) * (s - c));
            }
        };
    }

    /**
     * 도형 종류만 알면 되고 세부 값은 불필요할 때
     */
    public static String shapeType(Shape shape) {
        return switch (shape) {
            case Circle _ -> "원";
            case Rectangle _ -> "직사각형";
            case Triangle _ -> "삼각형";
        };
    }

    // ===== 메신저 도메인 예제 =====

    public sealed interface Notification permits Push, Email, InApp {}
    public record Push(String token, String title, String body) implements Notification {}
    public record Email(String address, String subject, String body) implements Notification {}
    public record InApp(long userId, String message) implements Notification {}

    /**
     * 알림 채널만 분류 (세부 내용 무시)
     */
    public static String notificationChannel(Notification notification) {
        return switch (notification) {
            case Push _ -> "PUSH";
            case Email _ -> "EMAIL";
            case InApp _ -> "IN_APP";
        };
    }

    /**
     * 알림 수신자만 추출 (나머지 필드 무시)
     */
    public static String recipient(Notification notification) {
        return switch (notification) {
            case Push(var token, _, _) -> "device:" + token;
            case Email(var addr, _, _) -> "email:" + addr;
            case InApp(var userId, _) -> "user:" + userId;
        };
    }
}
