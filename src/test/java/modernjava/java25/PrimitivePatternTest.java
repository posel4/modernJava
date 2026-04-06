package modernjava.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primitive Types in Patterns (JEP 488) 학습 테스트
 */
class PrimitivePatternTest {

    // ========================================================================
    // PP-01: classifyHttpStatus
    // ========================================================================

    @Nested
    @DisplayName("PP-01: classifyHttpStatus (HTTP 상태 코드)")
    class HttpStatusTest {

        @Test
        @DisplayName("200 -> OK")
        void ok() {
            assertThat(PrimitivePatternExample.classifyHttpStatus(200)).isEqualTo("OK");
        }

        @Test
        @DisplayName("201 -> Created")
        void created() {
            assertThat(PrimitivePatternExample.classifyHttpStatus(201)).isEqualTo("Created");
        }

        @Test
        @DisplayName("404 -> Not Found")
        void notFound() {
            assertThat(PrimitivePatternExample.classifyHttpStatus(404)).isEqualTo("Not Found");
        }

        @Test
        @DisplayName("500 -> Internal Server Error")
        void serverError() {
            assertThat(PrimitivePatternExample.classifyHttpStatus(500)).isEqualTo("Internal Server Error");
        }

        @Test
        @DisplayName("알 수 없는 코드 -> Unknown")
        void unknown() {
            assertThat(PrimitivePatternExample.classifyHttpStatus(418)).isEqualTo("Unknown (418)");
        }
    }

    // ========================================================================
    // PP-02: classifyAge (primitive pattern + guard)
    // ========================================================================

    @Nested
    @DisplayName("PP-02: classifyAge (나이 분류)")
    class AgeTest {

        @Test
        @DisplayName("음수 -> Invalid")
        void negative() {
            assertThat(PrimitivePatternExample.classifyAge(-1)).isEqualTo("Invalid");
        }

        @Test
        @DisplayName("0 -> Child")
        void zero() {
            assertThat(PrimitivePatternExample.classifyAge(0)).isEqualTo("Child");
        }

        @Test
        @DisplayName("12 -> Child (경계값)")
        void childBoundary() {
            assertThat(PrimitivePatternExample.classifyAge(12)).isEqualTo("Child");
        }

        @Test
        @DisplayName("13 -> Teenager (경계값)")
        void teenagerBoundary() {
            assertThat(PrimitivePatternExample.classifyAge(13)).isEqualTo("Teenager");
        }

        @Test
        @DisplayName("19 -> Teenager")
        void teenager() {
            assertThat(PrimitivePatternExample.classifyAge(19)).isEqualTo("Teenager");
        }

        @Test
        @DisplayName("20 -> Adult (경계값)")
        void adultBoundary() {
            assertThat(PrimitivePatternExample.classifyAge(20)).isEqualTo("Adult");
        }

        @Test
        @DisplayName("64 -> Adult")
        void adult() {
            assertThat(PrimitivePatternExample.classifyAge(64)).isEqualTo("Adult");
        }

        @Test
        @DisplayName("65 -> Senior (경계값)")
        void seniorBoundary() {
            assertThat(PrimitivePatternExample.classifyAge(65)).isEqualTo("Senior");
        }

        @Test
        @DisplayName("100 -> Senior")
        void senior() {
            assertThat(PrimitivePatternExample.classifyAge(100)).isEqualTo("Senior");
        }
    }

    // ========================================================================
    // PP-03: classifyMessageId (long primitive pattern)
    // ========================================================================

    @Nested
    @DisplayName("PP-03: classifyMessageId (long 패턴)")
    class MessageIdTest {

        @Test
        @DisplayName("0 -> Invalid ID")
        void zero() {
            assertThat(PrimitivePatternExample.classifyMessageId(0)).isEqualTo("Invalid ID");
        }

        @Test
        @DisplayName("음수 -> Invalid ID")
        void negative() {
            assertThat(PrimitivePatternExample.classifyMessageId(-100)).isEqualTo("Invalid ID");
        }

        @Test
        @DisplayName("1 -> Legacy ID")
        void legacyId() {
            assertThat(PrimitivePatternExample.classifyMessageId(1)).isEqualTo("Legacy ID");
        }

        @Test
        @DisplayName("999_999 -> Legacy ID (경계값)")
        void legacyBoundary() {
            assertThat(PrimitivePatternExample.classifyMessageId(999_999)).isEqualTo("Legacy ID");
        }

        @Test
        @DisplayName("1_000_000 -> Standard ID (경계값)")
        void standardBoundary() {
            assertThat(PrimitivePatternExample.classifyMessageId(1_000_000)).isEqualTo("Standard ID");
        }

        @Test
        @DisplayName("999_999_999 -> Standard ID")
        void standardId() {
            assertThat(PrimitivePatternExample.classifyMessageId(999_999_999)).isEqualTo("Standard ID");
        }

        @Test
        @DisplayName("1_000_000_000 -> Snowflake ID (경계값)")
        void snowflakeBoundary() {
            assertThat(PrimitivePatternExample.classifyMessageId(1_000_000_000L)).isEqualTo("Snowflake ID");
        }

        @Test
        @DisplayName("Long.MAX_VALUE -> Snowflake ID")
        void maxLong() {
            assertThat(PrimitivePatternExample.classifyMessageId(Long.MAX_VALUE)).isEqualTo("Snowflake ID");
        }
    }

    // ========================================================================
    // PP-04: booleanSwitch
    // ========================================================================

    @Nested
    @DisplayName("PP-04: booleanSwitch")
    class BooleanSwitchTest {

        @Test
        @DisplayName("true -> Enabled")
        void trueCase() {
            assertThat(PrimitivePatternExample.booleanSwitch(true)).isEqualTo("Enabled");
        }

        @Test
        @DisplayName("false -> Disabled")
        void falseCase() {
            assertThat(PrimitivePatternExample.booleanSwitch(false)).isEqualTo("Disabled");
        }
    }

    // ========================================================================
    // PP-05: classifyTemperature (double primitive pattern)
    // ========================================================================

    @Nested
    @DisplayName("PP-05: classifyTemperature (double 패턴)")
    class TemperatureTest {

        @Test
        @DisplayName("-10.0 -> Freezing")
        void freezing() {
            assertThat(PrimitivePatternExample.classifyTemperature(-10.0)).isEqualTo("Freezing");
        }

        @Test
        @DisplayName("0.0 -> Cold (경계값: 0.0 >= 0)")
        void coldBoundary() {
            assertThat(PrimitivePatternExample.classifyTemperature(0.0)).isEqualTo("Cold");
        }

        @Test
        @DisplayName("15.0 -> Cold")
        void cold() {
            assertThat(PrimitivePatternExample.classifyTemperature(15.0)).isEqualTo("Cold");
        }

        @Test
        @DisplayName("20.0 -> Comfortable (경계값)")
        void comfortableBoundary() {
            assertThat(PrimitivePatternExample.classifyTemperature(20.0)).isEqualTo("Comfortable");
        }

        @Test
        @DisplayName("25.0 -> Comfortable")
        void comfortable() {
            assertThat(PrimitivePatternExample.classifyTemperature(25.0)).isEqualTo("Comfortable");
        }

        @Test
        @DisplayName("30.0 -> Hot (경계값)")
        void hotBoundary() {
            assertThat(PrimitivePatternExample.classifyTemperature(30.0)).isEqualTo("Hot");
        }

        @Test
        @DisplayName("40.0 -> Hot")
        void hot() {
            assertThat(PrimitivePatternExample.classifyTemperature(40.0)).isEqualTo("Hot");
        }
    }

    // ========================================================================
    // PP-06: classifyErrorCode (에러 코드 분류)
    // ========================================================================

    @Nested
    @DisplayName("PP-06: classifyErrorCode (에러 코드)")
    class ErrorCodeTest {

        @Test
        @DisplayName("1000~1999 -> Channel Error")
        void channelError() {
            assertThat(PrimitivePatternExample.classifyErrorCode(1000)).isEqualTo("Channel Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(1500)).isEqualTo("Channel Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(1999)).isEqualTo("Channel Error");
        }

        @Test
        @DisplayName("2000~2999 -> Message Error")
        void messageError() {
            assertThat(PrimitivePatternExample.classifyErrorCode(2000)).isEqualTo("Message Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(2500)).isEqualTo("Message Error");
        }

        @Test
        @DisplayName("3000~3999 -> Member Error")
        void memberError() {
            assertThat(PrimitivePatternExample.classifyErrorCode(3000)).isEqualTo("Member Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(3999)).isEqualTo("Member Error");
        }

        @Test
        @DisplayName("4000~4999 -> File Error")
        void fileError() {
            assertThat(PrimitivePatternExample.classifyErrorCode(4000)).isEqualTo("File Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(4999)).isEqualTo("File Error");
        }

        @Test
        @DisplayName("범위 밖 -> Unknown Error")
        void unknownError() {
            assertThat(PrimitivePatternExample.classifyErrorCode(0)).isEqualTo("Unknown Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(999)).isEqualTo("Unknown Error");
            assertThat(PrimitivePatternExample.classifyErrorCode(5000)).isEqualTo("Unknown Error");
        }
    }
}
