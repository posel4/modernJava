package modernjava.java25;

/**
 * Flexible Constructor Bodies (JEP 492, 3rd Preview in Java 25)
 *
 * Java 25 이전에는 생성자에서 super()나 this() 호출이 반드시 첫 번째 문장이어야 했다.
 * 이 제약으로 인해 super() 호출 전에 인자 검증이나 변환이 불가능했다.
 *
 * Java 25에서는 super()/this() 전에 문장을 작성할 수 있다:
 * - 인자 유효성 검사 (validation)
 * - 인자 변환 (normalization)
 * - 로깅이나 디버깅
 *
 * 단, super()/this() 호출 전에는 this의 필드에 접근할 수 없다.
 * (아직 초기화되지 않은 상태이므로)
 */
public class FlexibleConstructorExample {

    // ========================================================================
    // FC-01: Base class
    // ========================================================================

    /**
     * 채널 기본 클래스.
     * id와 name을 갖는 단순한 엔티티.
     */
    public static class Channel {
        private final long id;
        private final String name;

        public Channel(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() { return id; }
        public String getName() { return name; }
    }

    // ========================================================================
    // FC-02: Before Java 25 - super()가 반드시 첫 줄
    // ========================================================================

    /**
     * Java 25 이전 스타일: super()가 반드시 첫 번째 문장이어야 했다.
     * maxMembers 검증은 super() 이후에만 가능했다.
     * 즉, 유효하지 않은 인자로 부모 생성자가 먼저 실행되는 문제가 있었다.
     */
    public static class OldStyleChannel extends Channel {
        private final int maxMembers;

        public OldStyleChannel(long id, String name, int maxMembers) {
            super(id, name); // must be first line
            if (maxMembers <= 0) throw new IllegalArgumentException("maxMembers must be positive");
            this.maxMembers = maxMembers;
        }

        public int getMaxMembers() { return maxMembers; }
    }

    // ========================================================================
    // FC-03: After Java 25 - super() 전에 validation, 변환 가능
    // ========================================================================

    /**
     * Java 25 Flexible Constructor: super() 전에 validation과 변환이 가능하다.
     *
     * 장점:
     * 1. 유효하지 않은 인자로 부모 생성자가 실행되는 것을 방지
     * 2. super()에 전달할 인자를 미리 가공 가능
     * 3. 생성자 로직이 더 자연스럽고 읽기 쉬움
     */
    public static class FlexibleChannel extends Channel {
        private final int maxMembers;

        public FlexibleChannel(long id, String name, int maxMembers) {
            // super() 전에 validation!
            if (id <= 0) throw new IllegalArgumentException("id must be positive");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (maxMembers <= 0) throw new IllegalArgumentException("maxMembers must be positive");

            // super() 전에 변환!
            var normalizedName = name.strip().toLowerCase();

            super(id, normalizedName);
            this.maxMembers = maxMembers;
        }

        public int getMaxMembers() { return maxMembers; }
    }

    // ========================================================================
    // FC-04: this() delegating constructor에서도 동일하게 작동
    // ========================================================================

    /**
     * this() 위임 생성자에서도 Flexible Constructor가 작동한다.
     * this() 호출 전에 인자를 검증하고 변환할 수 있다.
     */
    public static class Message {
        private final long id;
        private final String content;
        private final String sender;

        public Message(long id, String content, String sender) {
            this.id = id;
            this.content = content;
            this.sender = sender;
        }

        // this() 전에도 validation 가능
        public Message(String content, String sender) {
            // this() 전에 validation!
            if (content == null) throw new IllegalArgumentException("content must not be null");

            // this() 전에 변환!
            var trimmedContent = content.strip();

            this(System.nanoTime(), trimmedContent, sender);
        }

        public long getId() { return id; }
        public String getContent() { return content; }
        public String getSender() { return sender; }
    }
}
