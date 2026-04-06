package modernjava.java24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import modernjava.java24.StreamGatherersExample.Notification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stream Gatherers (JEP 485) 학습 테스트
 */
class StreamGatherersTest {

    // ========================================================================
    // SG-01: windowFixed - batchMessages
    // ========================================================================

    @Nested
    @DisplayName("SG-01: batchMessages (windowFixed)")
    class BatchMessagesTest {

        @Test
        @DisplayName("6개 메시지를 3개씩 배치하면 2개 그룹")
        void exactBatches() {
            var messages = List.of("msg1", "msg2", "msg3", "msg4", "msg5", "msg6");

            var result = StreamGatherersExample.batchMessages(messages);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("msg1", "msg2", "msg3");
            assertThat(result.get(1)).containsExactly("msg4", "msg5", "msg6");
        }

        @Test
        @DisplayName("마지막 배치는 3개보다 적을 수 있다")
        void lastBatchCanBeSmaller() {
            var messages = List.of("a", "b", "c", "d", "e");

            var result = StreamGatherersExample.batchMessages(messages);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("a", "b", "c");
            assertThat(result.get(1)).containsExactly("d", "e"); // 2개만
        }

        @Test
        @DisplayName("빈 리스트는 빈 결과")
        void emptyList() {
            var result = StreamGatherersExample.batchMessages(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("3개 미만이면 하나의 배치")
        void lessThanBatchSize() {
            var result = StreamGatherersExample.batchMessages(List.of("only", "two"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("only", "two");
        }
    }

    // ========================================================================
    // SG-02: windowSliding - slidingWindow
    // ========================================================================

    @Nested
    @DisplayName("SG-02: slidingWindow")
    class SlidingWindowTest {

        @Test
        @DisplayName("슬라이딩 윈도우가 1칸씩 이동하며 겹침")
        void basicSliding() {
            var data = List.of(1, 2, 3, 4, 5);

            var result = StreamGatherersExample.slidingWindow(data, 3);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsExactly(1, 2, 3);
            assertThat(result.get(1)).containsExactly(2, 3, 4);
            assertThat(result.get(2)).containsExactly(3, 4, 5);
        }

        @Test
        @DisplayName("윈도우 크기가 데이터와 같으면 1개 윈도우")
        void windowSizeEqualsDataSize() {
            var data = List.of(10, 20, 30);

            var result = StreamGatherersExample.slidingWindow(data, 3);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(10, 20, 30);
        }

        @Test
        @DisplayName("윈도우 크기가 데이터보다 크면 가용 요소로 하나의 윈도우 생성")
        void windowSizeLargerThanData() {
            var data = List.of(1, 2);

            var result = StreamGatherersExample.slidingWindow(data, 5);

            // windowSliding은 데이터가 윈도우 크기보다 작아도 가용 요소로 윈도우 하나를 생성
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(1, 2);
        }

        @Test
        @DisplayName("윈도우 크기 1이면 각 요소가 개별 윈도우")
        void windowSizeOne() {
            var data = List.of(1, 2, 3);

            var result = StreamGatherersExample.slidingWindow(data, 1);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsExactly(1);
            assertThat(result.get(1)).containsExactly(2);
            assertThat(result.get(2)).containsExactly(3);
        }
    }

    // ========================================================================
    // SG-03: scan - runningTotal
    // ========================================================================

    @Nested
    @DisplayName("SG-03: runningTotal (scan)")
    class RunningTotalTest {

        @Test
        @DisplayName("누적 합계가 올바르게 계산된다")
        void basicAccumulation() {
            var values = List.of(10L, 20L, 30L);

            var result = StreamGatherersExample.runningTotal(values);

            assertThat(result).containsExactly(10L, 30L, 60L);
        }

        @Test
        @DisplayName("단일 값이면 그 값 자체")
        void singleValue() {
            var result = StreamGatherersExample.runningTotal(List.of(42L));

            assertThat(result).containsExactly(42L);
        }

        @Test
        @DisplayName("빈 리스트는 빈 결과")
        void emptyList() {
            var result = StreamGatherersExample.runningTotal(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("음수 포함 누적 합계")
        void withNegativeValues() {
            var values = List.of(100L, -30L, 50L, -20L);

            var result = StreamGatherersExample.runningTotal(values);

            assertThat(result).containsExactly(100L, 70L, 120L, 100L);
        }
    }

    // ========================================================================
    // SG-04: Custom Gatherer - distinctBy / deduplicateByChannel
    // ========================================================================

    @Nested
    @DisplayName("SG-04: distinctBy / deduplicateByChannel")
    class DistinctByTest {

        @Test
        @DisplayName("같은 channelId의 첫 번째 알림만 남긴다")
        void deduplicateByChannel() {
            var notifications = List.of(
                    new Notification(1L, "첫 번째 알림"),
                    new Notification(2L, "채널2 알림"),
                    new Notification(1L, "중복 알림"),     // channelId 1 중복
                    new Notification(3L, "채널3 알림"),
                    new Notification(2L, "채널2 중복")     // channelId 2 중복
            );

            var result = StreamGatherersExample.deduplicateByChannel(notifications);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(new Notification(1L, "첫 번째 알림"));
            assertThat(result.get(1)).isEqualTo(new Notification(2L, "채널2 알림"));
            assertThat(result.get(2)).isEqualTo(new Notification(3L, "채널3 알림"));
        }

        @Test
        @DisplayName("중복이 없으면 모두 통과")
        void noDuplicates() {
            var notifications = List.of(
                    new Notification(1L, "알림1"),
                    new Notification(2L, "알림2"),
                    new Notification(3L, "알림3")
            );

            var result = StreamGatherersExample.deduplicateByChannel(notifications);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("모두 같은 channelId면 첫 번째만 남긴다")
        void allSameChannel() {
            var notifications = List.of(
                    new Notification(1L, "첫 번째"),
                    new Notification(1L, "두 번째"),
                    new Notification(1L, "세 번째")
            );

            var result = StreamGatherersExample.deduplicateByChannel(notifications);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).content()).isEqualTo("첫 번째");
        }

        @Test
        @DisplayName("distinctBy를 문자열 길이 기준으로 사용")
        void distinctByStringLength() {
            var words = List.of("cat", "dog", "elephant", "ant", "bee");

            var result = words.stream()
                    .gather(StreamGatherersExample.distinctBy(String::length))
                    .toList();

            // "cat"(3), "elephant"(8) 만 남음 - "dog"(3), "ant"(3), "bee"(3) 은 길이 중복
            assertThat(result).containsExactly("cat", "elephant");
        }
    }

    // ========================================================================
    // SG-05: Collectors vs Gatherers 비교
    // ========================================================================

    @Nested
    @DisplayName("SG-05: topNWithCollector / groupedProcessing")
    class CollectorsVsGatherersTest {

        @Test
        @DisplayName("topNWithCollector: 정렬 후 상위 N개")
        void topNWithCollector() {
            var items = List.of("banana", "apple", "cherry", "date", "elderberry");

            var result = StreamGatherersExample.topNWithCollector(items, 3);

            assertThat(result).containsExactly("apple", "banana", "cherry");
        }

        @Test
        @DisplayName("groupedProcessing: 고정 크기로 그룹화")
        void groupedProcessing() {
            var items = List.of("a", "b", "c", "d", "e", "f", "g");

            var result = StreamGatherersExample.groupedProcessing(items, 3);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsExactly("a", "b", "c");
            assertThat(result.get(1)).containsExactly("d", "e", "f");
            assertThat(result.get(2)).containsExactly("g"); // 마지막 그룹은 1개
        }

        @Test
        @DisplayName("그룹 크기가 전체보다 크면 하나의 그룹")
        void groupSizeLargerThanList() {
            var items = List.of("x", "y");

            var result = StreamGatherersExample.groupedProcessing(items, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("x", "y");
        }
    }
}
