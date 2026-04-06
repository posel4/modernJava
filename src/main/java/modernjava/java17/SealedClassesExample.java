package modernjava.java17;

/**
 * Sealed Classes (JEP 409) - Java 17
 *
 * sealed 키워드로 상속 가능한 하위 클래스를 제한한다.
 * permits 절에 명시된 클래스만 상속 가능.
 * 하위 클래스는 final, sealed, non-sealed 중 하나를 선택해야 한다.
 */
public class SealedClassesExample {

    // ===== 1. 메시지 타입 계층 =====

    /**
     * 메신저의 메시지는 Text, Image, File 세 가지만 존재.
     * sealed로 제한하면 새로운 타입 추가 시 컴파일 타임에 감지 가능.
     */
    public sealed interface MessageType permits TextMessage, ImageMessage, FileMessage {
        String content();
        long senderId();
    }

    public record TextMessage(String content, long senderId, boolean isMarkdown) implements MessageType {}

    public record ImageMessage(String content, long senderId, int width, int height, String thumbnailUrl) implements MessageType {}

    public record FileMessage(String content, long senderId, String fileName, long fileSize) implements MessageType {}

    // ===== 2. 채널 타입 계층 =====

    /**
     * 채널은 DM(1:1), Private(비공개), Public(공개) 세 가지.
     */
    public sealed interface ChannelType permits DirectMessage, PrivateChannel, PublicChannel {
        long channelId();
        String name();
    }

    public record DirectMessage(long channelId, String name, long member1Id, long member2Id) implements ChannelType {}

    public record PrivateChannel(long channelId, String name, int memberCount) implements ChannelType {}

    public record PublicChannel(long channelId, String name, String description, int memberCount) implements ChannelType {}

    // ===== 3. Exhaustive switch (Java 21 preview → 정식) =====

    /**
     * sealed type + switch = exhaustive 매칭.
     * 모든 하위 타입을 처리하지 않으면 컴파일 에러.
     */
    public static String describeMessage(MessageType msg) {
        return switch (msg) {
            case TextMessage t -> "텍스트: " + t.content() + (t.isMarkdown() ? " (마크다운)" : "");
            case ImageMessage i -> "이미지: " + i.width() + "x" + i.height();
            case FileMessage f -> "파일: " + f.fileName() + " (" + f.fileSize() + " bytes)";
        };
    }

    public static String describeChannel(ChannelType ch) {
        return switch (ch) {
            case DirectMessage dm -> "DM: " + dm.member1Id() + " ↔ " + dm.member2Id();
            case PrivateChannel pc -> "비공개 채널: " + pc.name() + " (" + pc.memberCount() + "명)";
            case PublicChannel pub -> "공개 채널: " + pub.name() + " - " + pub.description();
        };
    }

    // ===== 4. sealed class with abstract method =====

    /**
     * sealed class도 가능 (interface뿐 아니라).
     * 공통 로직을 가질 수 있다.
     */
    public static sealed abstract class Notification permits PushNotification, EmailNotification, InAppNotification {
        private final long recipientId;
        private final String title;

        protected Notification(long recipientId, String title) {
            this.recipientId = recipientId;
            this.title = title;
        }

        public long recipientId() { return recipientId; }
        public String title() { return title; }

        public abstract String deliver();
    }

    public static final class PushNotification extends Notification {
        private final String deviceToken;

        public PushNotification(long recipientId, String title, String deviceToken) {
            super(recipientId, title);
            this.deviceToken = deviceToken;
        }

        public String deviceToken() { return deviceToken; }

        @Override
        public String deliver() {
            return "PUSH to " + deviceToken + ": " + title();
        }
    }

    public static final class EmailNotification extends Notification {
        private final String email;

        public EmailNotification(long recipientId, String title, String email) {
            super(recipientId, title);
            this.email = email;
        }

        @Override
        public String deliver() {
            return "EMAIL to " + email + ": " + title();
        }
    }

    public static final class InAppNotification extends Notification {
        public InAppNotification(long recipientId, String title) {
            super(recipientId, title);
        }

        @Override
        public String deliver() {
            return "IN-APP for " + recipientId() + ": " + title();
        }
    }
}
