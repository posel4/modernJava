package modernjava.java22;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import modernjava.java22.UnnamedVariablesExample.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class UnnamedVariablesExampleTest {

    @Nested
    @DisplayName("UV-01: Unnamed Variables (_)")
    class UnnamedVariablesTest {

        @Test
        @DisplayName("Map.forEach에서 key 무시, value만 합산")
        void sumValues() {
            var map = Map.of("a", 1, "b", 2, "c", 3);
            assertThat(UnnamedVariablesExample.sumValues(map)).isEqualTo(6);
        }

        @Test
        @DisplayName("for-each에서 요소 무시 (카운팅)")
        void countItems() {
            assertThat(UnnamedVariablesExample.countItems(List.of("a", "b", "c"))).isEqualTo(3);
        }

        @Test
        @DisplayName("catch에서 예외 변수 무시")
        void tryParseInt() {
            assertThat(UnnamedVariablesExample.tryParseInt("42")).isTrue();
            assertThat(UnnamedVariablesExample.tryParseInt("abc")).isFalse();
        }
    }

    @Nested
    @DisplayName("UV-02: Unnamed Patterns in switch")
    class UnnamedPatternsTest {

        @Test
        @DisplayName("도형 면적 계산")
        void area() {
            assertThat(UnnamedVariablesExample.area(new Circle(5.0)))
                    .isCloseTo(Math.PI * 25, within(0.001));
            assertThat(UnnamedVariablesExample.area(new Rectangle(3.0, 4.0)))
                    .isEqualTo(12.0);
        }

        @Test
        @DisplayName("도형 종류 분류 (세부 값 무시)")
        void shapeType() {
            assertThat(UnnamedVariablesExample.shapeType(new Circle(1.0))).isEqualTo("원");
            assertThat(UnnamedVariablesExample.shapeType(new Rectangle(1.0, 2.0))).isEqualTo("직사각형");
            assertThat(UnnamedVariablesExample.shapeType(new Triangle(3, 4, 5))).isEqualTo("삼각형");
        }

        @Test
        @DisplayName("알림 채널 분류 (세부 내용 무시)")
        void notificationChannel() {
            assertThat(UnnamedVariablesExample.notificationChannel(new Push("t", "title", "body")))
                    .isEqualTo("PUSH");
            assertThat(UnnamedVariablesExample.notificationChannel(new Email("a@b.c", "sub", "body")))
                    .isEqualTo("EMAIL");
            assertThat(UnnamedVariablesExample.notificationChannel(new InApp(1L, "msg")))
                    .isEqualTo("IN_APP");
        }

        @Test
        @DisplayName("알림 수신자 추출 (나머지 필드 무시)")
        void recipient() {
            assertThat(UnnamedVariablesExample.recipient(new Push("abc-token", "t", "b")))
                    .isEqualTo("device:abc-token");
            assertThat(UnnamedVariablesExample.recipient(new Email("user@test.com", "s", "b")))
                    .isEqualTo("email:user@test.com");
            assertThat(UnnamedVariablesExample.recipient(new InApp(42L, "m")))
                    .isEqualTo("user:42");
        }
    }
}
