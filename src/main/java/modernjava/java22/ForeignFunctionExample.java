package modernjava.java22;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Foreign Function & Memory API (JEP 454) - Java 22 정식
 *
 * JNI 없이 네이티브 코드와 상호작용.
 * 안전한 네이티브 메모리 관리.
 *
 * 메신저 서버에서의 활용도는 낮지만, Java의 진화 방향을 보여줌.
 */
public class ForeignFunctionExample {

    // ===== FFM-01: 네이티브 메모리 할당 =====

    /**
     * Arena를 사용한 안전한 네이티브 메모리 관리.
     * try-with-resources로 자동 해제.
     */
    public static long nativeMemoryAllocation() {
        try (Arena arena = Arena.ofConfined()) {
            // 100 int 크기의 네이티브 메모리 할당
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, 100);

            // 값 쓰기
            for (int i = 0; i < 100; i++) {
                segment.setAtIndex(ValueLayout.JAVA_INT, i, i * 10);
            }

            // 값 읽기 + 합산
            long sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += segment.getAtIndex(ValueLayout.JAVA_INT, i);
            }

            return sum;
            // Arena 닫힘 → 메모리 자동 해제
        }
    }

    /**
     * 네이티브 메모리에 문자열 저장/읽기
     */
    public static String nativeStringRoundtrip(String input) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeStr = arena.allocateFrom(input);
            return nativeStr.getString(0);
        }
    }

    // ===== FFM-02: C 라이브러리 함수 호출 =====

    /**
     * C 표준 라이브러리의 strlen 호출.
     * macOS/Linux에서 동작.
     */
    public static long callStrlen(String input) throws Throwable {
        // C 함수 시그니처: size_t strlen(const char *s)
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        MethodHandle strlen = linker.downcallHandle(
                stdlib.find("strlen").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cString = arena.allocateFrom(input);
            return (long) strlen.invoke(cString);
        }
    }

    /**
     * C getpid() 호출 - 현재 프로세스 ID
     */
    public static long callGetpid() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        MethodHandle getpid = linker.downcallHandle(
                stdlib.find("getpid").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );

        return (int) getpid.invoke();
    }
}
