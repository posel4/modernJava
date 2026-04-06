package playground.java21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import playground.java21.PatternMatchingSwitchExample.*;

import static org.assertj.core.api.Assertions.*;

class PatternMatchingSwitchExampleTest {

    @Nested
    @DisplayName("PM-01: switch + sealed type exhaustive 매칭")
    class ExhaustiveSwitchTest {

        @Test
        @DisplayName("Success 응답 처리")
        void success() {
            var response = new Success(200, "채널 목록");
            assertThat(PatternMatchingSwitchExample.handleResponse(response))
                    .contains("성공", "200", "채널 목록");
        }

        @Test
        @DisplayName("ServerError 응답 처리")
        void serverError() {
            var response = new ServerError(500, "내부 오류", "trace-abc");
            assertThat(PatternMatchingSwitchExample.handleResponse(response))
                    .contains("서버 에러", "500", "trace-abc");
        }

        @Test
        @DisplayName("Timeout 응답 처리")
        void timeout() {
            var response = new Timeout(3000L, "/api/channels");
            assertThat(PatternMatchingSwitchExample.handleResponse(response))
                    .contains("타임아웃", "/api/channels", "3000");
        }
    }

    @Nested
    @DisplayName("PM-02: guard clause (when)")
    class GuardClauseTest {

        @Test
        @DisplayName("200 OK")
        void ok() {
            assertThat(PatternMatchingSwitchExample.classifyResponse(new Success(200, "ok")))
                    .isEqualTo("OK");
        }

        @Test
        @DisplayName("201 Created")
        void created() {
            assertThat(PatternMatchingSwitchExample.classifyResponse(new Success(201, null)))
                    .isEqualTo("Created");
        }

        @Test
        @DisplayName("404 Not Found")
        void notFound() {
            assertThat(PatternMatchingSwitchExample.classifyResponse(new ClientError(404, "not found")))
                    .isEqualTo("리소스 없음");
        }

        @Test
        @DisplayName("심각한 타임아웃 (> 5초)")
        void severeTimeout() {
            assertThat(PatternMatchingSwitchExample.classifyResponse(new Timeout(10000L, "/api/slow")))
                    .isEqualTo("심각한 타임아웃");
        }

        @Test
        @DisplayName("일반 타임아웃 (<= 5초)")
        void normalTimeout() {
            assertThat(PatternMatchingSwitchExample.classifyResponse(new Timeout(3000L, "/api/normal")))
                    .isEqualTo("일반 타임아웃");
        }
    }

    @Nested
    @DisplayName("PM-03: null 처리 in switch")
    class NullHandlingTest {

        @Test
        @DisplayName("null 응답을 안전하게 처리")
        void nullResponse() {
            assertThat(PatternMatchingSwitchExample.safeHandle(null))
                    .isEqualTo("응답 없음 (null)");
        }

        @Test
        @DisplayName("정상 응답도 동일하게 처리")
        void nonNullResponse() {
            assertThat(PatternMatchingSwitchExample.safeHandle(new Success(200, "ok")))
                    .contains("성공");
        }
    }

    @Nested
    @DisplayName("PM-04: Record Pattern 분해")
    class RecordPatternTest {

        @Test
        @DisplayName("삭제된 채널 (중첩 분해)")
        void deletedChannel() {
            var info = new ChannelInfo(1L, new ChannelMeta("일반", ChannelStatus.DELETED, 0));
            assertThat(PatternMatchingSwitchExample.describeChannel(info))
                    .contains("삭제된 채널", "일반", "1");
        }

        @Test
        @DisplayName("아카이브 채널")
        void archivedChannel() {
            var info = new ChannelInfo(2L, new ChannelMeta("프로젝트A", ChannelStatus.ARCHIVED, 10));
            assertThat(PatternMatchingSwitchExample.describeChannel(info))
                    .contains("아카이브 채널", "프로젝트A", "10명");
        }

        @Test
        @DisplayName("대규모 활성 채널 (guard + record pattern)")
        void largeActiveChannel() {
            var info = new ChannelInfo(3L, new ChannelMeta("전사공지", ChannelStatus.ACTIVE, 500));
            assertThat(PatternMatchingSwitchExample.describeChannel(info))
                    .contains("대규모 활성 채널", "전사공지", "500명");
        }

        @Test
        @DisplayName("일반 활성 채널")
        void normalActiveChannel() {
            var info = new ChannelInfo(4L, new ChannelMeta("개발팀", ChannelStatus.ACTIVE, 10));
            assertThat(PatternMatchingSwitchExample.describeChannel(info))
                    .contains("활성 채널", "개발팀", "10명");
        }
    }

    @Nested
    @DisplayName("PM-05: Pattern + Sealed + Record 조합")
    class CombinedPatternTest {

        @Test
        @DisplayName("봇 메시지 (guard: isBot)")
        void botMessage() {
            var event = new MessageSent(1L, 100L, "/remind 회의", true);
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("봇 메시지", "/remind 회의");
        }

        @Test
        @DisplayName("사용자 메시지")
        void userMessage() {
            var event = new MessageSent(1L, 101L, "안녕하세요", false);
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("사용자 메시지", "101");
        }

        @Test
        @DisplayName("메시지 수정")
        void messageEdited() {
            var event = new MessageEdited(1L, 100L, "오타", "수정됨");
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("메시지 수정", "오타", "수정됨");
        }

        @Test
        @DisplayName("DM 채널 생성 (guard: type=DM)")
        void dmCreated() {
            var event = new ChannelCreated(10L, "홍길동", "DM");
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("DM 생성", "홍길동");
        }

        @Test
        @DisplayName("멤버 추방 (guard: kicked=true)")
        void memberKicked() {
            var event = new MemberLeft(1L, 5L, true);
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("멤버 추방");
        }

        @Test
        @DisplayName("멤버 자발적 퇴장")
        void memberLeft() {
            var event = new MemberLeft(1L, 5L, false);
            assertThat(PatternMatchingSwitchExample.dispatchEvent(event))
                    .contains("멤버 퇴장");
        }
    }
}
