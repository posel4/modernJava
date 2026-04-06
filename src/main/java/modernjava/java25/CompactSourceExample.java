package modernjava.java25;

/**
 * Compact Source Files / Implicitly Declared Classes (JEP 495, 4th Preview in Java 25)
 *
 * Java 25에서는 클래스 선언 없이 바로 main() 메서드를 작성할 수 있다.
 * "Implicitly Declared Class"라고 하며, 초보자와 프로토타이핑에 유용하다.
 *
 * Compact Source File 특징:
 * 1. 클래스 선언 불필요 - 파일에 바로 메서드 작성
 * 2. static 불필요 - void main() { ... } 만으로 충분
 * 3. String[] args 불필요 - 인자 없는 main() 허용
 * 4. import module java.base; 자동 적용
 * 5. println() 직접 호출 가능 (java.io.IO 자동 import)
 *
 * 예시 (Compact Source File):
 * <pre>
 * // Hello.java - 이것이 파일의 전체 내용
 * void main() {
 *     println("Hello, World!");
 * }
 * </pre>
 *
 * 이전 방식:
 * <pre>
 * public class Hello {
 *     public static void main(String[] args) {
 *         System.out.println("Hello, World!");
 *     }
 * }
 * </pre>
 *
 * 주의: 이 파일은 패키지 내 일반 프로젝트이므로 클래스 형태를 유지한다.
 * Compact Source File은 패키지 선언 없이 단독 파일로 실행할 때 사용한다.
 */
public class CompactSourceExample {

    // ========================================================================
    // CS-01: 전통적인 main vs Compact Source main
    // ========================================================================

    /**
     * 전통적인 main 메서드.
     * public static void main(String[] args) - 모든 키워드가 필요했다.
     */
    public static void traditionalMain(String[] args) {
        System.out.println("Hello, World!");
    }

    /**
     * Java 25 스타일: instance main() 메서드.
     * - static 불필요
     * - String[] args 불필요
     * - Compact Source File에서는 클래스 선언도 불필요
     *
     * 실제 Compact Source File에서는 이렇게만 작성:
     * void main() {
     *     println("Hello, World!");  // System.out 불필요
     * }
     */
    void main() {
        System.out.println("Compact source: no class declaration needed, no static, no String[] args");
    }

    // ========================================================================
    // CS-02: Compact Source File 설명
    // ========================================================================

    /**
     * Compact Source File의 핵심 특징을 설명한다.
     *
     * @return 기능 설명
     */
    public static String describeCompactSource() {
        return "Compact Source Files: write void main() without class declaration, " +
               "auto-import java.base module, use println() directly";
    }

    // ========================================================================
    // CS-03: 메신저 서버에서의 활용도
    // ========================================================================

    /**
     * Spring Boot 기반 메신저 서버에서의 활용도를 평가한다.
     *
     * Spring Boot 앱은 이미 @SpringBootApplication과 SpringApplication.run()을 사용하므로
     * Compact Source File의 직접적인 활용도는 낮다.
     * 하지만 스크립트성 유틸리티나 빠른 프로토타이핑에는 유용하다.
     *
     * @return 활용도 평가
     */
    public static String messengerServerRelevance() {
        return "Low relevance for Spring Boot apps, useful for quick scripts and prototyping";
    }
}
