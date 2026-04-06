package modernjava.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import modernjava.java25.FlexibleConstructorExample.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flexible Constructor Bodies (JEP 492) 학습 테스트
 */
class FlexibleConstructorTest {

    // ========================================================================
    // FC-02: OldStyleChannel (기존 방식)
    // ========================================================================

    @Nested
    @DisplayName("FC-02: OldStyleChannel (기존 방식)")
    class OldStyleChannelTest {

        @Test
        @DisplayName("유효한 인자로 생성 성공")
        void validCreation() {
            var channel = new OldStyleChannel(1L, "general", 100);

            assertThat(channel.getId()).isEqualTo(1L);
            assertThat(channel.getName()).isEqualTo("general");
            assertThat(channel.getMaxMembers()).isEqualTo(100);
        }

        @Test
        @DisplayName("maxMembers가 0이면 예외 (super() 후 검증)")
        void invalidMaxMembers() {
            assertThatThrownBy(() -> new OldStyleChannel(1L, "general", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxMembers must be positive");
        }

        @Test
        @DisplayName("maxMembers가 음수면 예외")
        void negativeMaxMembers() {
            assertThatThrownBy(() -> new OldStyleChannel(1L, "general", -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxMembers must be positive");
        }
    }

    // ========================================================================
    // FC-03: FlexibleChannel (Java 25 스타일)
    // ========================================================================

    @Nested
    @DisplayName("FC-03: FlexibleChannel (super() 전 validation)")
    class FlexibleChannelTest {

        @Test
        @DisplayName("유효한 인자로 생성 성공")
        void validCreation() {
            var channel = new FlexibleChannel(1L, "General", 100);

            assertThat(channel.getId()).isEqualTo(1L);
            assertThat(channel.getMaxMembers()).isEqualTo(100);
        }

        @Test
        @DisplayName("name이 strip().toLowerCase()로 정규화된다")
        void nameNormalized() {
            var channel = new FlexibleChannel(1L, "  General Chat  ", 50);

            assertThat(channel.getName()).isEqualTo("general chat");
        }

        @Test
        @DisplayName("id가 0이면 super() 전에 예외 발생")
        void invalidIdZero() {
            assertThatThrownBy(() -> new FlexibleChannel(0L, "test", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must be positive");
        }

        @Test
        @DisplayName("id가 음수면 super() 전에 예외 발생")
        void invalidIdNegative() {
            assertThatThrownBy(() -> new FlexibleChannel(-1L, "test", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must be positive");
        }

        @Test
        @DisplayName("name이 null이면 super() 전에 예외 발생")
        void nullName() {
            assertThatThrownBy(() -> new FlexibleChannel(1L, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("name이 blank이면 super() 전에 예외 발생")
        void blankName() {
            assertThatThrownBy(() -> new FlexibleChannel(1L, "   ", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("maxMembers가 0이면 super() 전에 예외 발생")
        void invalidMaxMembers() {
            assertThatThrownBy(() -> new FlexibleChannel(1L, "test", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxMembers must be positive");
        }
    }

    // ========================================================================
    // FC-04: Message (this() delegating constructor)
    // ========================================================================

    @Nested
    @DisplayName("FC-04: Message (this() 전 validation)")
    class MessageTest {

        @Test
        @DisplayName("3-arg 생성자: 직접 생성")
        void threeArgConstructor() {
            var msg = new Message(42L, "Hello", "Alice");

            assertThat(msg.getId()).isEqualTo(42L);
            assertThat(msg.getContent()).isEqualTo("Hello");
            assertThat(msg.getSender()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("2-arg 생성자: this() 위임으로 id 자동 생성")
        void twoArgConstructor() {
            var msg = new Message("Hello World", "Bob");

            assertThat(msg.getId()).isGreaterThan(0);
            assertThat(msg.getContent()).isEqualTo("Hello World");
            assertThat(msg.getSender()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("2-arg 생성자: content가 strip()으로 정리된다")
        void contentTrimmed() {
            var msg = new Message("  Hello World  ", "Bob");

            assertThat(msg.getContent()).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("2-arg 생성자: null content이면 this() 전에 예외 발생")
        void nullContent() {
            assertThatThrownBy(() -> new Message(null, "Bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content must not be null");
        }

        @Test
        @DisplayName("2-arg 생성자: 각 호출마다 고유한 id 생성")
        void uniqueIds() {
            var msg1 = new Message("msg1", "Alice");
            var msg2 = new Message("msg2", "Bob");

            assertThat(msg1.getId()).isNotEqualTo(msg2.getId());
        }
    }
}
