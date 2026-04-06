package modernjava.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Compact Source Files / Implicitly Declared Classes (JEP 495) 학습 테스트
 */
class CompactSourceTest {

    // ========================================================================
    // CS-01: traditionalMain
    // ========================================================================

    @Nested
    @DisplayName("CS-01: traditionalMain")
    class TraditionalMainTest {

        @Test
        @DisplayName("예외 없이 실행된다")
        void noException() {
            assertThatCode(() -> CompactSourceExample.traditionalMain(new String[]{}))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null args도 처리 가능")
        void nullArgs() {
            assertThatCode(() -> CompactSourceExample.traditionalMain(null))
                .doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // CS-02: describeCompactSource
    // ========================================================================

    @Nested
    @DisplayName("CS-02: describeCompactSource")
    class DescribeTest {

        @Test
        @DisplayName("void main() 언급")
        void mentionsVoidMain() {
            String desc = CompactSourceExample.describeCompactSource();

            assertThat(desc).contains("void main()");
        }

        @Test
        @DisplayName("class declaration 불필요 언급")
        void mentionsNoClass() {
            String desc = CompactSourceExample.describeCompactSource();

            assertThat(desc).contains("without class declaration");
        }

        @Test
        @DisplayName("auto-import 언급")
        void mentionsAutoImport() {
            String desc = CompactSourceExample.describeCompactSource();

            assertThat(desc).contains("auto-import");
        }

        @Test
        @DisplayName("println() 언급")
        void mentionsPrintln() {
            String desc = CompactSourceExample.describeCompactSource();

            assertThat(desc).contains("println()");
        }
    }

    // ========================================================================
    // CS-03: messengerServerRelevance
    // ========================================================================

    @Nested
    @DisplayName("CS-03: messengerServerRelevance")
    class RelevanceTest {

        @Test
        @DisplayName("Spring Boot 앱에서는 활용도가 낮음을 명시")
        void lowRelevanceForSpringBoot() {
            String relevance = CompactSourceExample.messengerServerRelevance();

            assertThat(relevance).containsIgnoringCase("low relevance");
            assertThat(relevance).containsIgnoringCase("Spring Boot");
        }

        @Test
        @DisplayName("프로토타이핑에 유용함을 명시")
        void usefulForPrototyping() {
            String relevance = CompactSourceExample.messengerServerRelevance();

            assertThat(relevance).containsIgnoringCase("prototyping");
        }
    }
}
