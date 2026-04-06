package playground.java24;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

/**
 * Stream Gatherers (JEP 485) - Java 24에서 정식 도입
 *
 * Gatherers는 Stream의 중간 연산(intermediate operation)을 사용자 정의할 수 있게 해주는 API.
 * 기존 Collectors가 터미널 연산을 커스터마이즈했다면,
 * Gatherers는 중간 단계에서 요소를 변환/필터/그룹화할 수 있다.
 */
public class StreamGatherersExample {

    // ========================================================================
    // SG-01: windowFixed - 고정 크기 배치 처리
    // ========================================================================

    /**
     * 메시지를 3개씩 배치로 나누어 처리한다.
     * 마지막 배치는 3개보다 적을 수 있다.
     *
     * 예: [a, b, c, d, e] -> [[a, b, c], [d, e]]
     */
    public static List<List<String>> batchMessages(List<String> messages) {
        return messages.stream()
                .gather(Gatherers.windowFixed(3))
                .toList();
    }

    // ========================================================================
    // SG-02: windowSliding - 슬라이딩 윈도우
    // ========================================================================

    /**
     * 슬라이딩 윈도우로 연속된 size개의 요소를 추적한다.
     * 각 윈도우는 이전 윈도우에서 1칸씩 이동한다.
     *
     * 예: [1, 2, 3, 4, 5], size=3 -> [[1, 2, 3], [2, 3, 4], [3, 4, 5]]
     */
    public static List<List<Integer>> slidingWindow(List<Integer> data, int size) {
        return data.stream()
                .gather(Gatherers.windowSliding(size))
                .toList();
    }

    // ========================================================================
    // SG-03: scan - 누적 연산 (running total)
    // ========================================================================

    /**
     * 누적 합계를 계산한다. 각 단계에서 현재까지의 합을 출력한다.
     *
     * 예: [10, 20, 30] -> [10, 30, 60]
     */
    public static List<Long> runningTotal(List<Long> values) {
        return values.stream()
                .gather(Gatherers.scan(() -> 0L, Long::sum))
                .toList();
    }

    // ========================================================================
    // SG-04: Custom Gatherer - distinctBy
    // ========================================================================

    /**
     * 특정 키 기준으로 중복을 제거하는 커스텀 Gatherer.
     * 메신저에서 같은 사용자의 중복 메시지를 필터링하는 등의 용도로 활용 가능.
     *
     * HashSet을 상태(state)로 유지하며, 이미 본 키는 건너뛴다.
     */
    public static <T, K> Gatherer<T, ?, T> distinctBy(Function<T, K> keyExtractor) {
        return Gatherer.ofSequential(
                // initializer: 빈 HashSet 생성
                HashSet<K>::new,
                // integrator: 키가 처음 보이면 downstream으로 전달
                (state, element, downstream) -> {
                    K key = keyExtractor.apply(element);
                    if (state.add(key)) {
                        downstream.push(element);
                    }
                    return true;
                }
        );
    }

    /**
     * 알림 레코드: 채널 ID와 내용으로 구성
     */
    public record Notification(long channelId, String content) {}

    /**
     * 같은 channelId의 첫 번째 알림만 남기고 나머지는 제거한다.
     */
    public static List<Notification> deduplicateByChannel(List<Notification> notifications) {
        return notifications.stream()
                .gather(distinctBy(Notification::channelId))
                .toList();
    }

    // ========================================================================
    // SG-05: Collectors vs Gatherers 비교
    // ========================================================================

    /**
     * 기존 방식: collect 후 정렬, limit 등 후처리.
     * Collectors는 터미널 연산이므로 중간 가공이 제한적이다.
     */
    public static List<String> topNWithCollector(List<String> items, int n) {
        return items.stream()
                .sorted()
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Gatherers 방식: 스트림 중간에서 요소를 그룹화.
     * windowFixed를 사용해 고정 크기 그룹으로 나눈다.
     */
    public static List<List<String>> groupedProcessing(List<String> items, int groupSize) {
        return items.stream()
                .gather(Gatherers.windowFixed(groupSize))
                .toList();
    }
}
