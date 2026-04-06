package playground.java25;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module Import Declarations (JEP 494, 2nd Preview in Java 25)
 *
 * Java 25에서는 `import module java.base;` 한 줄로
 * java.base 모듈의 모든 패키지를 한꺼번에 import할 수 있다.
 *
 * 기존 방식:
 *   import java.util.List;
 *   import java.util.Map;
 *   import java.util.Set;
 *   import java.util.stream.Collectors;
 *   import java.time.LocalDateTime;
 *   import java.time.Duration;
 *
 * Java 25 방식:
 *   import module java.base;   // 위의 모든 import를 대체
 *
 * 장점:
 * - 새 파일 작성 시 import 고민 제거
 * - import 선언부가 깔끔해짐
 * - java.base에 속한 모든 패키지 자동 사용 가능
 *   (java.util, java.time, java.io, java.nio, java.math, java.net 등)
 *
 * 주의:
 * - 이 파일에서는 기존 import 방식을 유지 (Gradle 빌드 호환성)
 * - 실제 module import는 module-info.java가 있는 프로젝트에서 더 자연스러움
 */
public class ModuleImportExample {

    // ========================================================================
    // MI-01: 다양한 java.base 타입을 활용하는 예제
    // ========================================================================

    /**
     * 문자열 리스트를 첫 글자 기준으로 그룹핑한다.
     *
     * import module java.base; 를 사용하면
     * List, Map, Collectors 등을 별도 import 없이 사용할 수 있다.
     *
     * @param items 문자열 리스트
     * @return 첫 글자 -> 해당 글자로 시작하는 문자열 리스트
     */
    public static Map<String, List<String>> groupByPrefix(List<String> items) {
        return items.stream()
            .collect(Collectors.groupingBy(
                s -> s.substring(0, Math.min(1, s.length()))
            ));
    }

    // ========================================================================
    // MI-02: java.time 타입 활용
    // ========================================================================

    /**
     * Duration을 HH:MM:SS 형식으로 포맷한다.
     *
     * import module java.base; 한 줄이면
     * Duration, LocalDateTime 등 java.time 패키지도 자동으로 사용 가능하다.
     *
     * @param duration 시간 간격
     * @return "HH:MM:SS" 형식의 문자열
     */
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    // ========================================================================
    // MI-03: Module Import 설명
    // ========================================================================

    /**
     * Module Import의 핵심 가치를 설명한다.
     *
     * @return module import 설명 문자열
     */
    public static String describeModuleImport() {
        return "import module java.base; replaces dozens of individual imports with one line";
    }
}
