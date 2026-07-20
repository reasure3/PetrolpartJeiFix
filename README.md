# Petrolpark JEI 19.39 Compatibility Fix

Minecraft 1.21.1에서 Petrolpark Library 1.4.36과 JEI 19.39.0.368을 함께 로드하기 위한 독립 NeoForge 클라이언트 패치 모드입니다. 모드 ID는 `petrolpapetrolpartjeifix`입니다.

이 프로젝트는 Petrolpark Library 모드 JAR를 수정하거나 포함하지 않습니다. Petrolpark의 호환되지 않는 JEI Mixin annotation 두 개만 MixinSquared로 제거하고, JEI 19.39의 호출 지점에 맞는 대체 Mixin을 적용합니다.

## 필수 설정

각 클라이언트 인스턴스의 `config/fml.toml`에 다음 설정이 필요합니다.

```toml
[dependencyOverrides]
petrolpark = ["-jei"]
```

이미 `[dependencyOverrides]` 표가 있다면 표를 중복 생성하지 말고 `petrolpark = ["-jei"]`만 추가하세요. 개발 실행에서는 `config/fml.toml.example`이 실행 디렉터리에 자동 복사됩니다.

## 지원 버전

| 구성 요소                        | 버전         |
|----------------------------------|--------------|
| Java                             | 21           |
| Minecraft                        | 1.21.1       |
| NeoForge                         | 21.1.235–21.1.238 (기준 21.1.236) |
| Petrolpark Library               | 1.4.36       |
| JEI                              | 19.39.0.368  |
| Create                           | 6.0.10       |
| MixinSquared                     | 0.3.7-beta.3 |
| Petrol's Parts 전체 구성 fixture | 1.2.10       |

NeoForge는 검증된 21.1.235부터 21.1.238까지 지원하며, 개발 및 빌드 기준 버전은 21.1.236입니다. 나머지 런타임 의존성은 정확한 버전으로 고정되어 있습니다.

## 빌드

필수 모드는 빌드 중 공개 Maven 저장소에서 자동으로 내려받습니다. 개발 클라이언트는 항상 Create, Petrolpark, JEI, Petrol's Parts를 포함한 전체 구성을 사용합니다.

```powershell
.\gradlew.bat --no-daemon build
.\gradlew.bat --no-daemon runClient
.\gradlew.bat --no-daemon runServer
```

Linux/macOS에서는 `./gradlew`를 사용하세요. GitHub Actions도 같은 `build` 작업을 실행합니다.

### 의존성 출처

| 모드               | 저장소 및 좌표                                               |
|--------------------|--------------------------------------------------------------|
| Create             | Curse Maven `curse.maven:create-328085:7963363`              |
| Petrolpark Library | Curse Maven `curse.maven:petrolpark-library-1093595:8251879` |
| JEI                | Curse Maven `curse.maven:jei-238222:8438425`                 |
| Petrol's Parts     | Modrinth Maven `maven.modrinth:AN0CZD9P:PFCZOOlN`            |

## 설치

1. CurseForge 프로필에 NeoForge 21.1.235–21.1.238 중 하나와 위 표의 정확한 Minecraft, Petrolpark, JEI, Create 버전을 설치합니다.
2. `build/libs/petrolpapetrolpartjeifix-1.0.1a.jar`를 프로필의 `mods` 폴더에 복사합니다.
3. `config/fml.toml`에 필수 dependency override를 추가합니다.
4. 클라이언트를 실행하고 로그에서 `Removed incompatible Petrolpark @WrapOperation 1/2`와 `2/2`를 확인합니다.

이 모드는 클라이언트 전용이며 전용 서버에 설치할 필요가 없습니다.

## 검증

`build` 작업은 다음 계약을 자동 검증합니다.

- 입력 Petrolpark와 JEI JAR의 SHA-256 및 바이트코드 계약
- 정확히 두 개의 비호환 annotation 제거
- 대체 Mixin target과 `require = 1`
- 유지되어야 하는 `@WrapMethod`
- 최종 JAR의 서비스 등록, JarJar 내용, 메타데이터 및 패키지 격리

바이트코드 근거는 [docs/BYTECODE_EVIDENCE.md](docs/BYTECODE_EVIDENCE.md)에 있습니다.
