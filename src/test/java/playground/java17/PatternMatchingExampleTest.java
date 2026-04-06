package playground.java17;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java17.PatternMatchingExample.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PatternMatchingExampleTest {

    @Nested
    @DisplayName("Before vs After 비교")
    class BeforeAfterTest {

        @Test
        @DisplayName("결과가 동일함을 검증")
        void sameResults() {
            Object[] inputs = {"hello", 42, List.of(1, 2, 3), 3.14};
            for (Object input : inputs) {
                assertThat(PatternMatchingExample.formatNew(input))
                    .isEqualTo(PatternMatchingExample.formatOld(input));
            }
        }

        @Test
        @DisplayName("String 패턴 매칭")
        void stringPattern() {
            assertThat(PatternMatchingExample.formatNew("hello")).isEqualTo("String: HELLO");
        }

        @Test
        @DisplayName("Integer 패턴 매칭")
        void integerPattern() {
            assertThat(PatternMatchingExample.formatNew(21)).isEqualTo("Integer: 42");
        }
    }

    @Nested
    @DisplayName("Guard condition (&&)")
    class GuardConditionTest {

        @Test
        @DisplayName("유효한 텍스트")
        void validText() {
            assertThat(PatternMatchingExample.classifyMessage("Hello"))
                .isEqualTo("유효한 텍스트: 5자");
        }

        @Test
        @DisplayName("빈 텍스트")
        void blankText() {
            assertThat(PatternMatchingExample.classifyMessage("  "))
                .isEqualTo("빈 텍스트");
        }

        @Test
        @DisplayName("바이너리 데이터")
        void binaryData() {
            assertThat(PatternMatchingExample.classifyMessage(new byte[]{1, 2, 3}))
                .isEqualTo("바이너리: 3 bytes");
        }

        @Test
        @DisplayName("양수")
        void positiveNumber() {
            assertThat(PatternMatchingExample.classifyMessage(42.5))
                .isEqualTo("양수: 42.5");
        }
    }

    @Nested
    @DisplayName("채널 이름 검증")
    class ChannelNameTest {

        @Test
        @DisplayName("유효한 채널 이름")
        void validName() {
            assertThat(PatternMatchingExample.isValidChannelName("개발팀")).isTrue();
        }

        @Test
        @DisplayName("너무 짧은 이름")
        void tooShort() {
            assertThat(PatternMatchingExample.isValidChannelName("A")).isFalse();
        }

        @Test
        @DisplayName("String이 아닌 타입")
        void notString() {
            assertThat(PatternMatchingExample.isValidChannelName(42)).isFalse();
        }
    }

    @Nested
    @DisplayName("이벤트 핸들링")
    class EventHandlerTest {

        @Test
        @DisplayName("메시지 전송 이벤트")
        void messageSent() {
            var event = new MessageSentEvent(100L, 1L, "안녕");
            assertThat(PatternMatchingExample.handleEvent(event)).contains("메시지 전송", "100", "1");
        }

        @Test
        @DisplayName("멤버 추방 이벤트 (guard condition)")
        void memberKicked() {
            var event = new MemberLeftEvent(100L, 5L, "kicked");
            assertThat(PatternMatchingExample.handleEvent(event)).contains("멤버 추방");
        }

        @Test
        @DisplayName("멤버 자발적 퇴장 이벤트")
        void memberLeft() {
            var event = new MemberLeftEvent(100L, 5L, "voluntary");
            assertThat(PatternMatchingExample.handleEvent(event)).contains("멤버 퇴장");
        }
    }
}
