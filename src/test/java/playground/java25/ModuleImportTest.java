package playground.java25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module Import Declarations (JEP 494) 학습 테스트
 */
class ModuleImportTest {

    // ========================================================================
    // MI-01: groupByPrefix
    // ========================================================================

    @Nested
    @DisplayName("MI-01: groupByPrefix (문자열 그룹핑)")
    class GroupByPrefixTest {

        @Test
        @DisplayName("첫 글자 기준으로 그룹핑된다")
        void groupsByFirstLetter() {
            var result = ModuleImportExample.groupByPrefix(
                List.of("apple", "avocado", "banana", "blueberry", "cherry")
            );

            assertThat(result).containsKey("a");
            assertThat(result.get("a")).containsExactly("apple", "avocado");
            assertThat(result.get("b")).containsExactly("banana", "blueberry");
            assertThat(result.get("c")).containsExactly("cherry");
        }

        @Test
        @DisplayName("단일 요소 그룹")
        void singleElementGroups() {
            var result = ModuleImportExample.groupByPrefix(
                List.of("x-ray", "yellow", "zebra")
            );

            assertThat(result).hasSize(3);
            assertThat(result.get("x")).containsExactly("x-ray");
            assertThat(result.get("y")).containsExactly("yellow");
            assertThat(result.get("z")).containsExactly("zebra");
        }

        @Test
        @DisplayName("빈 리스트 -> 빈 맵")
        void emptyList() {
            var result = ModuleImportExample.groupByPrefix(List.of());

            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // MI-02: formatDuration
    // ========================================================================

    @Nested
    @DisplayName("MI-02: formatDuration (시간 포맷)")
    class FormatDurationTest {

        @Test
        @DisplayName("1시간 30분 45초 -> 01:30:45")
        void standardDuration() {
            var duration = Duration.ofHours(1).plusMinutes(30).plusSeconds(45);

            assertThat(ModuleImportExample.formatDuration(duration)).isEqualTo("01:30:45");
        }

        @Test
        @DisplayName("0초 -> 00:00:00")
        void zeroDuration() {
            assertThat(ModuleImportExample.formatDuration(Duration.ZERO)).isEqualTo("00:00:00");
        }

        @Test
        @DisplayName("90초 -> 00:01:30")
        void ninetySeconds() {
            assertThat(ModuleImportExample.formatDuration(Duration.ofSeconds(90))).isEqualTo("00:01:30");
        }

        @Test
        @DisplayName("24시간 이상도 가능")
        void moreThanADay() {
            var duration = Duration.ofHours(25).plusMinutes(5).plusSeconds(10);

            assertThat(ModuleImportExample.formatDuration(duration)).isEqualTo("25:05:10");
        }
    }

    // ========================================================================
    // MI-03: describeModuleImport
    // ========================================================================

    @Nested
    @DisplayName("MI-03: describeModuleImport")
    class DescribeTest {

        @Test
        @DisplayName("module import 설명을 반환한다")
        void description() {
            String desc = ModuleImportExample.describeModuleImport();

            assertThat(desc).contains("import module java.base");
            assertThat(desc).contains("one line");
        }
    }
}
