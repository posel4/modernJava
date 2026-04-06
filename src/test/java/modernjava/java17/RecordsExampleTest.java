package modernjava.java17;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import modernjava.java17.RecordsExample.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecordsExampleTest {

    @Nested
    @DisplayName("기본 Record")
    class BasicRecordTest {

        @Test
        @DisplayName("accessor는 getXxx()가 아니라 xxx() 형태")
        void accessorStyle() {
            var user = new ApiUser(1L, 100L, 3, "req-001");
            assertThat(user.memberId()).isEqualTo(1L);
            assertThat(user.tenantId()).isEqualTo(100L);
            assertThat(user.tenantDbId()).isEqualTo(3);
            assertThat(user.requestId()).isEqualTo("req-001");
        }

        @Test
        @DisplayName("자동 생성된 equals/hashCode")
        void equalsHashCode() {
            var user1 = new ApiUser(1L, 100L, 3, "req-001");
            var user2 = new ApiUser(1L, 100L, 3, "req-001");
            assertThat(user1).isEqualTo(user2);
            assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
        }

        @Test
        @DisplayName("자동 생성된 toString")
        void toStringGenerated() {
            var user = new ApiUser(1L, 100L, 3, "req-001");
            assertThat(user.toString()).contains("ApiUser", "1", "100", "3", "req-001");
        }
    }

    @Nested
    @DisplayName("Compact Constructor 검증")
    class CompactConstructorTest {

        @Test
        @DisplayName("유효한 ID는 정상 생성")
        void validId() {
            var channelId = new ChannelId(42L);
            assertThat(channelId.value()).isEqualTo(42L);
        }

        @Test
        @DisplayName("0 이하 ID는 예외 발생")
        void invalidId() {
            assertThatThrownBy(() -> new ChannelId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

            assertThatThrownBy(() -> new ChannelId(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("중첩 Record + 팩토리 메서드")
    class NestedRecordTest {

        @Test
        @DisplayName("텍스트 메시지 팩토리")
        void textOnly() {
            var sender = new MessageResponse.SenderInfo(1L, "홍길동", "profile.jpg");
            var msg = MessageResponse.textOnly(100L, "안녕하세요", sender, Instant.now());

            assertThat(msg.text()).isEqualTo("안녕하세요");
            assertThat(msg.sender().name()).isEqualTo("홍길동");
            assertThat(msg.hasAttachments()).isFalse();
        }

        @Test
        @DisplayName("첨부파일 포함 메시지")
        void withAttachments() {
            var sender = new MessageResponse.SenderInfo(1L, "김개발", null);
            var attachments = List.of(
                new MessageResponse.AttachmentInfo("doc.pdf", 1024L, "application/pdf")
            );
            var msg = new MessageResponse(101L, "파일 보냅니다", sender, Instant.now(), attachments);

            assertThat(msg.hasAttachments()).isTrue();
            assertThat(msg.attachments()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("불변성 검증 (방어적 복사)")
    class ImmutabilityTest {

        @Test
        @DisplayName("외부 List 수정이 Record 내부에 영향 없음")
        void defensiveCopy() {
            var mutableList = new ArrayList<>(List.of(1L, 2L, 3L));
            var members = new ChannelMembers(100L, mutableList);

            mutableList.add(4L); // 외부에서 수정

            assertThat(members.memberIds()).hasSize(3); // Record 내부는 변경 안 됨
        }

        @Test
        @DisplayName("Record의 List는 unmodifiable")
        void unmodifiableList() {
            var members = new ChannelMembers(100L, List.of(1L, 2L));

            assertThatThrownBy(() -> members.memberIds().add(3L))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("null memberIds는 예외")
        void nullMemberIds() {
            assertThatThrownBy(() -> new ChannelMembers(100L, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Record + Interface")
    class InterfaceTest {

        @Test
        @DisplayName("Identifiable 인터페이스 구현")
        void identifiable() {
            Identifiable channel = new Channel(1L, "일반", "PUBLIC");
            Identifiable member = new Member(2L, "홍길동", "hong@example.com");

            assertThat(channel.id()).isEqualTo(1L);
            assertThat(member.id()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Generic Record")
    class GenericRecordTest {

        @Test
        @DisplayName("성공 응답 래퍼")
        void okResult() {
            var result = ApiResult.ok(new Channel(1L, "일반", "PUBLIC"));

            assertThat(result.success()).isTrue();
            assertThat(result.resultCode()).isEqualTo("SUCCESS");
            assertThat(result.data().name()).isEqualTo("일반");
        }

        @Test
        @DisplayName("실패 응답 래퍼")
        void failResult() {
            ApiResult<Channel> result = ApiResult.fail("CHANNEL_NOT_FOUND");

            assertThat(result.success()).isFalse();
            assertThat(result.data()).isNull();
        }
    }

    @Nested
    @DisplayName("Local Record")
    class LocalRecordTest {

        @Test
        @DisplayName("메서드 내 로컬 record로 통계 계산")
        void summarize() {
            String summary = RecordsExample.summarizeChannel(100L, "개발팀", 500, 10);
            assertThat(summary).contains("개발팀", "500 messages", "10 members", "50.0 msg/member");
        }
    }
}
