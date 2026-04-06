package modernjava.java21;

import java.util.*;

/**
 * Sequenced Collections (JEP 431) - Java 21 정식
 *
 * 순서가 있는 컬렉션에 공통 인터페이스 추가.
 * getFirst(), getLast(), reversed() 메서드 제공.
 *
 * 기존: list.get(0), list.get(list.size()-1) → 직관적이지 않음
 * 신규: list.getFirst(), list.getLast() → 의도가 명확
 */
public class SequencedCollectionsExample {

    // ===== PM-06: Sequenced Collections =====

    /**
     * List의 getFirst(), getLast()
     */
    public static <T> String firstAndLast(List<T> list) {
        if (list.isEmpty()) return "빈 리스트";
        return "첫 번째: " + list.getFirst() + ", 마지막: " + list.getLast();
    }

    /**
     * reversed() 뷰
     */
    public static <T> List<T> reverseView(List<T> list) {
        return list.reversed();
    }

    /**
     * LinkedHashSet의 Sequenced 지원
     */
    public static <T> T firstElement(SequencedSet<T> set) {
        return set.getFirst();
    }

    public static <T> T lastElement(SequencedSet<T> set) {
        return set.getLast();
    }

    /**
     * LinkedHashMap의 Sequenced 지원
     */
    public static <K, V> Map.Entry<K, V> firstEntry(SequencedMap<K, V> map) {
        return map.firstEntry();
    }

    public static <K, V> Map.Entry<K, V> lastEntry(SequencedMap<K, V> map) {
        return map.lastEntry();
    }

    /**
     * 메신저 적용: 최근 메시지 목록에서 첫/마지막 메시지
     */
    public record ChatMessage(long id, String text, String sender) {}

    public static String getLatestAndOldest(List<ChatMessage> messages) {
        if (messages.isEmpty()) return "메시지 없음";

        ChatMessage oldest = messages.getFirst();
        ChatMessage latest = messages.getLast();

        return "가장 오래된: [%s] %s / 가장 최근: [%s] %s".formatted(
                oldest.sender(), oldest.text(),
                latest.sender(), latest.text()
        );
    }

    /**
     * reversed() + stream() 조합: 최근 메시지부터 역순 처리
     */
    public static List<String> recentMessagesReversed(List<ChatMessage> messages, int limit) {
        return messages.reversed().stream()
                .limit(limit)
                .map(m -> m.sender() + ": " + m.text())
                .toList();
    }
}
