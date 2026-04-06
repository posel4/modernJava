package modernjava.java17;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import modernjava.java17.SealedClassesExample.*;

import static org.assertj.core.api.Assertions.*;

class SealedClassesExampleTest {

    @Nested
    @DisplayName("MessageType sealed hierarchy")
    class MessageTypeTest {

        @Test
        @DisplayName("TextMessage 생성 및 switch 매칭")
        void textMessage() {
            MessageType msg = new TextMessage("안녕하세요", 1L, true);
            String result = SealedClassesExample.describeMessage(msg);
            assertThat(result).isEqualTo("텍스트: 안녕하세요 (마크다운)");
        }

        @Test
        @DisplayName("ImageMessage 생성 및 switch 매칭")
        void imageMessage() {
            MessageType msg = new ImageMessage("photo.jpg", 2L, 1920, 1080, "thumb.jpg");
            String result = SealedClassesExample.describeMessage(msg);
            assertThat(result).isEqualTo("이미지: 1920x1080");
        }

        @Test
        @DisplayName("FileMessage 생성 및 switch 매칭")
        void fileMessage() {
            MessageType msg = new FileMessage("document", 3L, "report.pdf", 1024000L);
            String result = SealedClassesExample.describeMessage(msg);
            assertThat(result).isEqualTo("파일: report.pdf (1024000 bytes)");
        }

        @Test
        @DisplayName("sealed interface의 구현체는 정확히 3개")
        void sealedPermits() {
            Class<?>[] permitted = MessageType.class.getPermittedSubclasses();
            assertThat(permitted).hasSize(3);
            assertThat(permitted).extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("TextMessage", "ImageMessage", "FileMessage");
        }
    }

    @Nested
    @DisplayName("ChannelType sealed hierarchy")
    class ChannelTypeTest {

        @Test
        @DisplayName("DirectMessage exhaustive switch")
        void directMessage() {
            ChannelType ch = new DirectMessage(100L, "DM", 1L, 2L);
            assertThat(SealedClassesExample.describeChannel(ch)).contains("DM", "1", "2");
        }

        @Test
        @DisplayName("PrivateChannel exhaustive switch")
        void privateChannel() {
            ChannelType ch = new PrivateChannel(200L, "프로젝트A", 5);
            assertThat(SealedClassesExample.describeChannel(ch)).contains("비공개", "프로젝트A", "5");
        }

        @Test
        @DisplayName("PublicChannel exhaustive switch")
        void publicChannel() {
            ChannelType ch = new PublicChannel(300L, "공지사항", "전체 공지", 100);
            assertThat(SealedClassesExample.describeChannel(ch)).contains("공개", "공지사항", "전체 공지");
        }
    }

    @Nested
    @DisplayName("Notification sealed class")
    class NotificationTest {

        @Test
        @DisplayName("PushNotification deliver")
        void push() {
            var noti = new PushNotification(1L, "새 메시지", "device-token-abc");
            assertThat(noti.deliver()).isEqualTo("PUSH to device-token-abc: 새 메시지");
        }

        @Test
        @DisplayName("EmailNotification deliver")
        void email() {
            var noti = new EmailNotification(2L, "초대", "user@example.com");
            assertThat(noti.deliver()).isEqualTo("EMAIL to user@example.com: 초대");
        }

        @Test
        @DisplayName("InAppNotification deliver")
        void inApp() {
            var noti = new InAppNotification(3L, "멘션됨");
            assertThat(noti.deliver()).isEqualTo("IN-APP for 3: 멘션됨");
        }

        @Test
        @DisplayName("sealed class도 getPermittedSubclasses() 가능")
        void sealedClassPermits() {
            Class<?>[] permitted = Notification.class.getPermittedSubclasses();
            assertThat(permitted).hasSize(3);
        }
    }
}
