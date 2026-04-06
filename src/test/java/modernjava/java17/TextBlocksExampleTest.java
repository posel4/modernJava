package modernjava.java17;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TextBlocksExampleTest {

    @Nested
    @DisplayName("JSON Before vs After")
    class JsonTest {

        @Test
        @DisplayName("Before와 After 결과 동일")
        void sameResult() {
            assertThat(TextBlocksExample.jsonAfter())
                .isEqualTo(TextBlocksExample.jsonBefore());
        }

        @Test
        @DisplayName("JSON 내용 검증")
        void jsonContent() {
            String json = TextBlocksExample.jsonAfter();
            assertThat(json).contains("\"channelId\": 100");
            assertThat(json).contains("\"name\": \"개발팀\"");
        }
    }

    @Nested
    @DisplayName("SQL Before vs After")
    class SqlTest {

        @Test
        @DisplayName("Before와 After 결과 동일")
        void sameResult() {
            // 공백 정규화 후 비교 (text block은 줄바꿈이 다를 수 있음)
            String before = TextBlocksExample.sqlBefore().replaceAll("\\s+", " ").trim();
            String after = TextBlocksExample.sqlAfter().replaceAll("\\s+", " ").trim();
            assertThat(after).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("formatted() 변수 삽입")
    class FormattedTest {

        @Test
        @DisplayName("채널 JSON 생성")
        void createChannelJson() {
            String json = TextBlocksExample.createChannelJson(100L, "개발팀", "PRIVATE");
            assertThat(json).contains("\"channelId\": 100");
            assertThat(json).contains("\"name\": \"개발팀\"");
            assertThat(json).contains("\"type\": \"PRIVATE\"");
        }
    }

    @Nested
    @DisplayName("이스케이프 시퀀스")
    class EscapeTest {

        @Test
        @DisplayName("\\s는 후행 공백 유지")
        void trailingSpace() {
            String result = TextBlocksExample.escapeDemo();
            assertThat(result).contains("줄 끝에 공백 유지: ");
        }

        @Test
        @DisplayName("\\ 줄 바꿈 방지")
        void lineContinuation() {
            String result = TextBlocksExample.escapeDemo();
            assertThat(result).contains("이 줄과 다음 줄은 하나로 합쳐짐");
        }
    }

    @Nested
    @DisplayName("HTML 템플릿")
    class HtmlTest {

        @Test
        @DisplayName("알림 HTML 생성")
        void notificationHtml() {
            String html = TextBlocksExample.notificationHtml("홍길동", "개발팀", "배포 완료!");
            assertThat(html).contains("<h3>홍길동님이 개발팀에 메시지를 보냈습니다</h3>");
            assertThat(html).contains("<p class=\"message\">배포 완료!</p>");
        }
    }
}
