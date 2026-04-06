package playground.java21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java21.SequencedCollectionsExample.ChatMessage;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SequencedCollectionsExampleTest {

    @Nested
    @DisplayName("PM-06: Sequenced Collections")
    class SequencedCollectionsTest {

        @Test
        @DisplayName("List getFirst(), getLast()")
        void listFirstLast() {
            var list = List.of("A", "B", "C");
            assertThat(SequencedCollectionsExample.firstAndLast(list))
                    .isEqualTo("첫 번째: A, 마지막: C");
        }

        @Test
        @DisplayName("빈 리스트 처리")
        void emptyList() {
            assertThat(SequencedCollectionsExample.firstAndLast(List.of()))
                    .isEqualTo("빈 리스트");
        }

        @Test
        @DisplayName("reversed() 뷰")
        void reversed() {
            var list = List.of(1, 2, 3, 4, 5);
            assertThat(SequencedCollectionsExample.reverseView(list))
                    .containsExactly(5, 4, 3, 2, 1);
        }

        @Test
        @DisplayName("LinkedHashSet의 getFirst(), getLast()")
        void sequencedSet() {
            var set = new LinkedHashSet<>(List.of("first", "second", "third"));
            assertThat(SequencedCollectionsExample.firstElement(set)).isEqualTo("first");
            assertThat(SequencedCollectionsExample.lastElement(set)).isEqualTo("third");
        }

        @Test
        @DisplayName("LinkedHashMap의 firstEntry(), lastEntry()")
        void sequencedMap() {
            var map = new LinkedHashMap<String, Integer>();
            map.put("채널A", 10);
            map.put("채널B", 20);
            map.put("채널C", 30);

            assertThat(SequencedCollectionsExample.firstEntry(map).getKey()).isEqualTo("채널A");
            assertThat(SequencedCollectionsExample.lastEntry(map).getKey()).isEqualTo("채널C");
        }

        @Test
        @DisplayName("메시지 목록에서 첫/마지막 조회")
        void chatMessages() {
            var messages = List.of(
                    new ChatMessage(1L, "안녕하세요", "홍길동"),
                    new ChatMessage(2L, "반갑습니다", "김개발"),
                    new ChatMessage(3L, "오늘 배포합니다", "이기획")
            );

            String result = SequencedCollectionsExample.getLatestAndOldest(messages);
            assertThat(result).contains("홍길동", "안녕하세요", "이기획", "오늘 배포합니다");
        }

        @Test
        @DisplayName("reversed() + stream: 최근 메시지 역순")
        void recentReversed() {
            var messages = List.of(
                    new ChatMessage(1L, "첫번째", "A"),
                    new ChatMessage(2L, "두번째", "B"),
                    new ChatMessage(3L, "세번째", "C"),
                    new ChatMessage(4L, "네번째", "D")
            );

            var recent = SequencedCollectionsExample.recentMessagesReversed(messages, 2);
            assertThat(recent).containsExactly("D: 네번째", "C: 세번째");
        }
    }
}
