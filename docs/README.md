# Modern Java

Java 17→25 변천사 학습 프로젝트.

## 학습 로드맵

| Step | Java 버전 | 기능 | 기간 |
|------|----------|------|------|
| 1 | Java 17 | Sealed Classes, Records, Pattern Matching instanceof, Text Blocks | 1일 |
| 2 | Java 21 | Virtual Threads (가장 중요) | 2일 |
| 3 | Java 21 | Pattern Matching + Record Patterns, Sequenced Collections | 1일 |
| 4 | Java 22 | Unnamed Variables, FFM API | 0.5일 |
| 5 | Java 24 | Stream Gatherers, Pinning Fix | 1일 |
| 6 | Java 25 | Scoped Values, Structured Concurrency (핵심) | 2일 |
| 7 | Java 25 | Flexible Constructor Bodies, Primitive Patterns, Module Import | 0.5일 |
| 8 | 종합 | comparison-summary.md 작성, 최종 정리 | 1일 |

## 학습 문서 템플릿

각 기능별 학습 문서는 다음 형식:

```
# [기능명] (JEP XXX) - Java XX
## 한줄 요약
## 왜 필요한가? (기존 문제)
## Before / After 코드
## 메신저 서버 적용 포인트
## TC 목록
## 팀원에게 한마디
```

## 빌드 & 실행

```bash
./gradlew build
./gradlew test
```

Java 25 + `--enable-preview` 자동 적용.
