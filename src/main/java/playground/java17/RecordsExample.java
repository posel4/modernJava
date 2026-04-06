package playground.java17;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Records (JEP 395) - Java 17 (Java 16에서 정식)
 *
 * 불변 데이터 클래스를 한 줄로 정의.
 * equals/hashCode/toString/accessor 자동 생성.
 * compact constructor로 검증 로직 추가 가능.
 */
public class RecordsExample {

    // ===== 1. 기본 Record - 메신저 API 사용자 =====

    /**
     * 기존 방식: class + final 필드 + 생성자 + getter + equals + hashCode + toString = ~50줄
     * Record: 1줄
     */
    public record ApiUser(long memberId, Long tenantId, int tenantDbId, String requestId) {
        // accessor는 memberId(), tenantId() 형태 (getXxx가 아님)
    }

    // ===== 2. Compact Constructor - 유효성 검증 =====

    /**
     * compact constructor: 파라미터 목록 생략하고 검증만 작성.
     * 검증 통과 후 필드 자동 할당.
     */
    public record ChannelId(long value) {
        public ChannelId {
            if (value <= 0) {
                throw new IllegalArgumentException("ChannelId must be positive: " + value);
            }
        }
    }

    public record MemberId(long value) {
        public MemberId {
            if (value <= 0) {
                throw new IllegalArgumentException("MemberId must be positive: " + value);
            }
        }
    }

    // ===== 3. 중첩 Record - 메시지 응답 DTO =====

    public record MessageResponse(
            long logId,
            String text,
            SenderInfo sender,
            Instant createdAt,
            List<AttachmentInfo> attachments
    ) {
        /**
         * 중첩 record로 세부 정보 그룹화
         */
        public record SenderInfo(long memberId, String name, String profileImageUrl) {}

        public record AttachmentInfo(String fileName, long fileSize, String mimeType) {}

        /**
         * 편의 팩토리 메서드 - 텍스트 메시지 생성
         */
        public static MessageResponse textOnly(long logId, String text, SenderInfo sender, Instant createdAt) {
            return new MessageResponse(logId, text, sender, createdAt, List.of());
        }

        /**
         * 첨부파일 존재 여부
         */
        public boolean hasAttachments() {
            return attachments != null && !attachments.isEmpty();
        }
    }

    // ===== 4. Record의 불변성 검증 =====

    /**
     * Record의 List 필드는 방어적 복사 필요.
     * 외부에서 전달된 List를 수정해도 Record 내부에 영향 없도록.
     */
    public record ChannelMembers(long channelId, List<Long> memberIds) {
        public ChannelMembers {
            Objects.requireNonNull(memberIds, "memberIds must not be null");
            memberIds = List.copyOf(memberIds); // 방어적 복사 (unmodifiable)
        }
    }

    // ===== 5. Record + 인터페이스 구현 =====

    public interface Identifiable {
        long id();
    }

    public record Channel(long id, String name, String type) implements Identifiable {}

    public record Member(long id, String name, String email) implements Identifiable {}

    // ===== 6. Generic Record =====

    /**
     * API 응답 래퍼. 제네릭 record 가능.
     */
    public record ApiResult<T>(boolean success, String resultCode, T data) {
        public static <T> ApiResult<T> ok(T data) {
            return new ApiResult<>(true, "SUCCESS", data);
        }

        public static <T> ApiResult<T> fail(String code) {
            return new ApiResult<>(false, code, null);
        }
    }

    // ===== 7. Local Record (메서드 내부) =====

    /**
     * 메서드 내부에서 임시 데이터 구조로 사용.
     * 기존에는 Pair나 Map.Entry 등을 억지로 사용했던 패턴.
     */
    public static String summarizeChannel(long channelId, String name, int messageCount, int memberCount) {
        record ChannelStats(int messageCount, int memberCount, double avgMessagesPerMember) {}

        var stats = new ChannelStats(
                messageCount,
                memberCount,
                memberCount > 0 ? (double) messageCount / memberCount : 0
        );

        return "%s(#%d): %d messages, %d members, avg %.1f msg/member".formatted(
                name, channelId, stats.messageCount(), stats.memberCount(), stats.avgMessagesPerMember()
        );
    }
}
