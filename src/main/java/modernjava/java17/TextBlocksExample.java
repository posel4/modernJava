package modernjava.java17;

/**
 * Text Blocks (JEP 378) - Java 17 (Java 15에서 정식)
 *
 * 여러 줄 문자열을 """ 로 감싸서 가독성 향상.
 * JSON, SQL, HTML 등을 깔끔하게 작성 가능.
 */
public class TextBlocksExample {

    // ===== 1. JSON - Before vs After =====

    public static String jsonBefore() {
        return "{\n" +
                "  \"channelId\": 100,\n" +
                "  \"name\": \"개발팀\",\n" +
                "  \"type\": \"PRIVATE\",\n" +
                "  \"memberCount\": 5\n" +
                "}";
    }

    public static String jsonAfter() {
        return """
                {
                  "channelId": 100,
                  "name": "개발팀",
                  "type": "PRIVATE",
                  "memberCount": 5
                }""";
    }

    // ===== 2. SQL =====

    public static String sqlBefore() {
        return "SELECT c.channel_id, c.name, c.type, " +
                "COUNT(cm.member_id) as member_count " +
                "FROM channel c " +
                "JOIN channel_member cm ON c.channel_id = cm.channel_id " +
                "WHERE c.tenant_id = ? " +
                "GROUP BY c.channel_id, c.name, c.type " +
                "ORDER BY c.name";
    }

    public static String sqlAfter() {
        return """
                SELECT c.channel_id, c.name, c.type,
                       COUNT(cm.member_id) as member_count
                FROM channel c
                JOIN channel_member cm ON c.channel_id = cm.channel_id
                WHERE c.tenant_id = ?
                GROUP BY c.channel_id, c.name, c.type
                ORDER BY c.name""";
    }

    // ===== 3. formatted() - 변수 삽입 =====

    public static String createChannelJson(long channelId, String name, String type) {
        return """
                {
                  "channelId": %d,
                  "name": "%s",
                  "type": "%s"
                }""".formatted(channelId, name, type);
    }

    // ===== 4. 들여쓰기 제어 =====

    /**
     * 닫는 """ 의 위치로 들여쓰기 기준점 결정.
     * 공통 선행 공백은 자동 제거됨.
     */
    public static String indentationDemo() {
        // 닫는 """를 왼쪽으로 이동하면 들여쓰기 유지됨
        return """
            {
              "indented": true
            }
            """;
    }

    // ===== 5. 이스케이프 시퀀스 =====

    /**
     * \s - 후행 공백 유지 (trailing space)
     * \ - 줄 바꿈 방지 (line continuation)
     */
    public static String escapeDemo() {
        return """
                줄 끝에 공백 유지:\s
                이 줄과 \
                다음 줄은 하나로 합쳐짐""";
    }

    // ===== 6. HTML 템플릿 =====

    public static String notificationHtml(String userName, String channelName, String message) {
        return """
                <div class="notification">
                  <h3>%s님이 %s에 메시지를 보냈습니다</h3>
                  <p class="message">%s</p>
                </div>""".formatted(userName, channelName, message);
    }
}
