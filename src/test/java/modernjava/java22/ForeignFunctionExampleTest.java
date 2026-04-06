package modernjava.java22;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.*;

class ForeignFunctionExampleTest {

    @Nested
    @DisplayName("FFM-01: 네이티브 메모리 할당")
    class NativeMemoryTest {

        @Test
        @DisplayName("100개 int 배열 합산 (0*10 + 1*10 + ... + 99*10)")
        void nativeMemoryAllocation() {
            long expected = 0;
            for (int i = 0; i < 100; i++) expected += i * 10;

            assertThat(ForeignFunctionExample.nativeMemoryAllocation()).isEqualTo(expected);
        }

        @Test
        @DisplayName("네이티브 메모리 문자열 왕복")
        void nativeStringRoundtrip() {
            assertThat(ForeignFunctionExample.nativeStringRoundtrip("Hello, 메신저!"))
                    .isEqualTo("Hello, 메신저!");
        }
    }

    @Nested
    @DisplayName("FFM-02: C 라이브러리 함수 호출")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    class CLibraryCallTest {

        @Test
        @DisplayName("strlen() 호출")
        void strlen() throws Throwable {
            assertThat(ForeignFunctionExample.callStrlen("Hello")).isEqualTo(5);
            assertThat(ForeignFunctionExample.callStrlen("")).isEqualTo(0);
        }

        @Test
        @DisplayName("getpid() 호출 - 양수 반환")
        void getpid() throws Throwable {
            assertThat(ForeignFunctionExample.callGetpid()).isGreaterThan(0);
        }
    }
}
