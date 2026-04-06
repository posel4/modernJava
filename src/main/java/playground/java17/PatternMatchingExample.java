package playground.java17;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pattern Matching for instanceof (JEP 394) - Java 17 (Java 16에서 정식)
 *
 * instanceof 체크와 캐스팅을 한 번에.
 * 기존: if (obj instanceof String) { String s = (String) obj; }
 * 새로: if (obj instanceof String s) { // s 바로 사용 }
 */
public class PatternMatchingExample {

    // ===== 1. Before vs After =====

    /**
     * Before (Java 16 이전): instanceof + 명시적 캐스팅
     */
    public static String formatOld(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            return "String: " + s.toUpperCase();
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "Integer: " + (i * 2);
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return "List size: " + list.size();
        }
        return "Unknown: " + obj;
    }

    /**
     * After (Java 17): 패턴 매칭 instanceof
     */
    public static String formatNew(Object obj) {
        if (obj instanceof String s) {
            return "String: " + s.toUpperCase();
        } else if (obj instanceof Integer i) {
            return "Integer: " + (i * 2);
        } else if (obj instanceof List<?> list) {
            return "List size: " + list.size();
        }
        return "Unknown: " + obj;
    }

    // ===== 2. Guard condition (&&와 조합) =====

    /**
     * 패턴 매칭 + 조건을 함께 사용.
     * 기존에는 캐스팅 후 if문을 추가해야 했음.
     */
    public static String classifyMessage(Object payload) {
        if (payload instanceof String text && !text.isBlank()) {
            return "유효한 텍스트: " + text.length() + "자";
        } else if (payload instanceof String) {
            return "빈 텍스트";
        } else if (payload instanceof byte[] data && data.length > 0) {
            return "바이너리: " + data.length + " bytes";
        } else if (payload instanceof Number n && n.doubleValue() > 0) {
            return "양수: " + n;
        }
        return "처리 불가";
    }

    // ===== 3. 논리 연산자와 스코프 =====

    /**
     * 패턴 변수의 스코프는 해당 분기에서만 유효.
     * &&는 가능하지만 ||는 불가 (어떤 타입인지 확정 못하므로).
     */
    public static boolean isValidChannelName(Object obj) {
        // && 사용 가능: obj가 String이면 s가 바인딩되고, 추가 조건 체크
        return obj instanceof String s && s.length() >= 2 && s.length() <= 50;
    }

    // ===== 4. 메신저 도메인 예제 =====

    /**
     * 다양한 이벤트 타입을 처리하는 핸들러.
     * sealed type이 없던 시절의 패턴.
     */
    public interface Event {}
    public record MessageSentEvent(long channelId, long logId, String text) implements Event {}
    public record MessageDeletedEvent(long channelId, long logId) implements Event {}
    public record MemberJoinedEvent(long channelId, long memberId) implements Event {}
    public record MemberLeftEvent(long channelId, long memberId, String reason) implements Event {}

    public static String handleEvent(Event event) {
        if (event instanceof MessageSentEvent e) {
            return "메시지 전송: ch=" + e.channelId() + ", log=" + e.logId();
        } else if (event instanceof MessageDeletedEvent e) {
            return "메시지 삭제: ch=" + e.channelId() + ", log=" + e.logId();
        } else if (event instanceof MemberJoinedEvent e) {
            return "멤버 입장: ch=" + e.channelId() + ", member=" + e.memberId();
        } else if (event instanceof MemberLeftEvent e && "kicked".equals(e.reason())) {
            return "멤버 추방: ch=" + e.channelId() + ", member=" + e.memberId();
        } else if (event instanceof MemberLeftEvent e) {
            return "멤버 퇴장: ch=" + e.channelId() + ", member=" + e.memberId();
        }
        return "알 수 없는 이벤트";
    }
}
