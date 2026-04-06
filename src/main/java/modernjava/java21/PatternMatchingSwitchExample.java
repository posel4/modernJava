package modernjava.java21;

import java.time.Instant;
import java.util.List;

/**
 * Pattern Matching for switch (JEP 441) + Record Patterns (JEP 440) - Java 21 정식
 *
 * Java 17: instanceof 패턴 매칭만 가능
 * Java 21: switch 표현식에서 패턴 매칭 + record 분해 + guard 절 + null 처리
 */
public class PatternMatchingSwitchExample {

    // ===== 도메인 타입 정의 =====

    public sealed interface ApiResponse permits Success, ClientError, ServerError, Timeout {}

    public record Success(int statusCode, Object body) implements ApiResponse {}
    public record ClientError(int statusCode, String message) implements ApiResponse {}
    public record ServerError(int statusCode, String message, String traceId) implements ApiResponse {}
    public record Timeout(long durationMs, String endpoint) implements ApiResponse {}

    // 중첩 Record (Record Pattern 분해용)
    public record ChannelInfo(long channelId, ChannelMeta meta) {}
    public record ChannelMeta(String name, ChannelStatus status, int memberCount) {}
    public enum ChannelStatus { ACTIVE, ARCHIVED, DELETED }

    // ===== PM-01: switch + sealed type exhaustive 매칭 =====

    /**
     * sealed type의 모든 하위 타입을 switch에서 처리.
     * 하나라도 빠지면 컴파일 에러.
     * default 불필요 (exhaustive).
     */
    public static String handleResponse(ApiResponse response) {
        return switch (response) {
            case Success s -> "성공 (" + s.statusCode() + "): " + s.body();
            case ClientError e -> "클라이언트 에러 (" + e.statusCode() + "): " + e.message();
            case ServerError e -> "서버 에러 (" + e.statusCode() + "): " + e.message() + " [" + e.traceId() + "]";
            case Timeout t -> "타임아웃: " + t.endpoint() + " (" + t.durationMs() + "ms)";
        };
    }

    // ===== PM-02: guard clause (`when` 키워드) =====

    /**
     * when 키워드로 패턴에 조건 추가.
     * 같은 타입이라도 조건에 따라 다른 분기.
     */
    public static String classifyResponse(ApiResponse response) {
        return switch (response) {
            case Success s when s.statusCode() == 200 -> "OK";
            case Success s when s.statusCode() == 201 -> "Created";
            case Success s when s.statusCode() == 204 -> "No Content";
            case Success s -> "기타 성공: " + s.statusCode();
            case ClientError e when e.statusCode() == 400 -> "잘못된 요청";
            case ClientError e when e.statusCode() == 401 -> "인증 필요";
            case ClientError e when e.statusCode() == 403 -> "권한 없음";
            case ClientError e when e.statusCode() == 404 -> "리소스 없음";
            case ClientError e -> "기타 클라이언트 에러: " + e.statusCode();
            case ServerError e when e.statusCode() == 503 -> "서비스 점검 중";
            case ServerError e -> "서버 내부 에러";
            case Timeout t when t.durationMs() > 5000 -> "심각한 타임아웃";
            case Timeout t -> "일반 타임아웃";
        };
    }

    // ===== PM-03: null 처리 in switch =====

    /**
     * Java 21 switch는 null을 case로 처리 가능.
     * 기존: switch 전에 null 체크 필요 → NullPointerException 위험.
     */
    public static String safeHandle(ApiResponse response) {
        return switch (response) {
            case null -> "응답 없음 (null)";
            case Success s -> "성공: " + s.body();
            case ClientError e -> "에러: " + e.message();
            case ServerError e -> "서버 에러: " + e.traceId();
            case Timeout t -> "타임아웃: " + t.endpoint();
        };
    }

    // ===== PM-04: Record Pattern 분해 (중첩 record) =====

    /**
     * Record Pattern: record의 컴포넌트를 switch에서 직접 분해.
     * 중첩된 record도 한 번에 분해 가능.
     */
    public static String describeChannel(ChannelInfo info) {
        return switch (info) {
            // 중첩 분해: ChannelInfo → ChannelMeta → name, status, memberCount
            // guard로 enum 상태 구분 (record pattern 안에서 enum constant는 직접 매칭 불가)
            case ChannelInfo(var id, ChannelMeta(var name, var status, var ignored))
                    when status == ChannelStatus.DELETED ->
                    "삭제된 채널: " + name + " (id=" + id + ")";
            case ChannelInfo(var id, ChannelMeta(var name, var status, var count))
                    when status == ChannelStatus.ARCHIVED ->
                    "아카이브 채널: " + name + " (" + count + "명, id=" + id + ")";
            case ChannelInfo(var id, ChannelMeta(var name, var status, var count))
                    when status == ChannelStatus.ACTIVE && count > 100 ->
                    "대규모 활성 채널: " + name + " (" + count + "명)";
            case ChannelInfo(var id, ChannelMeta(var name, var status, var count)) ->
                    "활성 채널: " + name + " (" + count + "명)";
        };
    }

    // ===== PM-05: Pattern + Sealed + Record 조합 실전 예제 =====

    /**
     * 메신저 이벤트 디스패처.
     * sealed interface + record + pattern matching + guard 조합.
     */
    public sealed interface MessengerEvent permits
            MessageEvent, ChannelEvent, MemberEvent {}

    public sealed interface MessageEvent extends MessengerEvent permits
            MessageSent, MessageEdited, MessageDeleted {}

    public record MessageSent(long channelId, long logId, String text, boolean isBot) implements MessageEvent {}
    public record MessageEdited(long channelId, long logId, String oldText, String newText) implements MessageEvent {}
    public record MessageDeleted(long channelId, long logId, long deletedBy) implements MessageEvent {}

    public sealed interface ChannelEvent extends MessengerEvent permits
            ChannelCreated, ChannelArchived {}

    public record ChannelCreated(long channelId, String name, String type) implements ChannelEvent {}
    public record ChannelArchived(long channelId, String reason) implements ChannelEvent {}

    public sealed interface MemberEvent extends MessengerEvent permits
            MemberJoined, MemberLeft {}

    public record MemberJoined(long channelId, long memberId, String invitedBy) implements MemberEvent {}
    public record MemberLeft(long channelId, long memberId, boolean kicked) implements MemberEvent {}

    /**
     * 모든 이벤트 타입을 exhaustive하게 처리.
     * guard clause로 세부 분류.
     */
    public static String dispatchEvent(MessengerEvent event) {
        return switch (event) {
            // MessageEvent
            case MessageSent(var ch, var log, var text, var isBot) when isBot ->
                    "봇 메시지: ch=" + ch + " text=" + text;
            case MessageSent(var ch, var log, var text, var ignored) ->
                    "사용자 메시지: ch=" + ch + " log=" + log;
            case MessageEdited(var ch, var log, var oldText, var newText) ->
                    "메시지 수정: ch=" + ch + " [" + oldText + " → " + newText + "]";
            case MessageDeleted(var ch, var log, var deletedBy) ->
                    "메시지 삭제: ch=" + ch + " log=" + log + " by=" + deletedBy;

            // ChannelEvent
            case ChannelCreated(var ch, var name, var type) when "DM".equals(type) ->
                    "DM 생성: " + name;
            case ChannelCreated(var ch, var name, var type) ->
                    "채널 생성: " + name + " (" + type + ")";
            case ChannelArchived(var ch, var reason) ->
                    "채널 아카이브: ch=" + ch + " 이유=" + reason;

            // MemberEvent
            case MemberJoined(var ch, var member, var inviter) ->
                    "멤버 입장: ch=" + ch + " member=" + member + " (초대: " + inviter + ")";
            case MemberLeft(var ch, var member, var kicked) when kicked ->
                    "멤버 추방: ch=" + ch + " member=" + member;
            case MemberLeft(var ch, var member, var ignored) ->
                    "멤버 퇴장: ch=" + ch + " member=" + member;
        };
    }
}
