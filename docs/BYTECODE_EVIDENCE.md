# 바이트코드 및 계약 근거

검사는 클래스 디컴파일 소스를 복사하지 않고 ASM, `javap`, ZIP 메타데이터 및 공개 API 시그니처로 수행했습니다.

## 입력 식별

| 입력                                  | SHA-256                                                            |
|---------------------------------------|--------------------------------------------------------------------|
| `petrolpark-1.21.1-1.4.36.jar`        | `C28C46CA83879D000CBBB770B2554B888B4A761AE773B7BB61DA2CAA3294C4F1` |
| `jei-1.21.1-neoforge-19.39.0.368.jar` | `AC27623A1E425A05F95F170EEA94974D26EA68CA2037E5695176FB66A090DE84` |

## MixinSquared가 제거하는 두 annotation

### 1. ModIdHelperMixin

- mixin: `com.petrolpark.mixin.compat.jei.client.ModIdHelperMixin`
- target class: `mezz.jei.library.helpers.ModIdHelper`
- handler: `petrolpark$getSharedFeatureModIds`
- handler descriptor:

```text
(Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/lang/Object;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;
```

- annotation: `Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;`
- injected method:

```text
Lmezz/jei/library/helpers/ModIdHelper;getModNameForTooltip(Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/Optional;
```

- 제거되는 단일 `INVOKE @At` target:

```text
Lmezz/jei/api/ingredients/IIngredientHelper;getDisplayModId(Ljava/lang/Object;)Ljava/lang/String;
```

### 2. RecipeCategoryTabMixin

- mixin: `com.petrolpark.mixin.compat.jei.client.RecipeCategoryTabMixin`
- target class: `mezz.jei.gui.recipes.RecipeCategoryTab`
- handler: `petrolpark$getSharedFeatureModIds`
- handler descriptor:

```text
(Lmezz/jei/api/helpers/IModIdHelper;Ljava/lang/String;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;
```

- annotation: `Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;`
- injected method:

```text
Lmezz/jei/gui/recipes/RecipeCategoryTab;getTooltip()Lmezz/jei/common/gui/JeiTooltip;
```

- 제거되는 단일 `INVOKE @At` target:

```text
Lmezz/jei/api/helpers/IModIdHelper;getFormattedModNameForModId(Ljava/lang/String;)Ljava/lang/String;
```

Adjuster는 위 모든 필드와 `targetClassNames`의 정확한 단일 원소 목록이 일치해야만 `null`을 반환합니다. handler descriptor 또는 `@At` target을 변경한 near-miss 테스트에서는 annotation이 유지되며 제거 수는 증가하지 않습니다.

## 유지되는 Petrolpark 주입

다음 정상 `@WrapMethod`는 실제 Petrolpark JAR에서 확인했고 Adjuster 정적 테스트 및 런타임 export 모두에서 유지됐습니다.

```text
com.petrolpark.mixin.compat.jei.client.ModIdHelperMixin
#petrolpark$formatSharedFeatureModIds(
  Ljava/lang/String;
  Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;
)Ljava/lang/String;
```

대상은 `ModIdHelper#getModNameForModId(Ljava/lang/String;)Ljava/lang/String;`입니다.

## JEI 19.39 replacement 대상

`ModIdHelper#getModNameForTooltip`에는 아래 호출이 정확히 한 번, bytecode offset 46에 있습니다.

```text
Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;
```

replacement handler descriptor:

```text
(Ljava/util/function/Function;Ljava/lang/Object;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/Object;
```

`RecipeCategoryTab#getTooltip`에는 아래 Component helper 호출이 정확히 한 번, bytecode offset 75에 있습니다.

```text
Lmezz/jei/api/helpers/IModIdHelper;getFormattedModNameComponentForModId(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;
```

replacement handler descriptor:

```text
(Lmezz/jei/api/helpers/IModIdHelper;Ljava/lang/String;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lnet/minecraft/network/chat/Component;
```

두 주입은 모두 `ordinal = 0`, `require = 1`입니다. `@Overwrite`는 없습니다.

JEI의 `getFormattedModNameComponentForModId(String)`는 `getModNameForModId(String)`를 호출하므로 marker는 살아 있는 Petrolpark `@WrapMethod`를 거쳐 최종 모드 표시명으로 변환됩니다.

## marker와 Petrol's Parts 대표 기능

공개 계약에서 marker 형식은 다음과 같습니다.

```text
petrolparkshared,<comma-separated mod ids>
```

공유 기능 사용자는 `SharedFeatureFlag#streamUsers()`와 `Mods#getId()`로 얻습니다.

Petrol's Parts 1.2.10의 공개 feature provider는 `SharedFeatureFlag.REDSTONE_PROGRAMMER` 한 개를 활성화합니다. 실제 `ISharedFeature` 구현 아이템은 `petrolpark:redstone_programmer`이고, 이 fixture의 marker와 최종 표시는 다음과 같습니다.

```text
marker: petrolparkshared,petrolsparts
display: Petrol's Parts
```

Petrol's Parts가 기능과 레시피를 제공하지만 공용 구현과 registry namespace가 Petrolpark에 있는 구조입니다.

## 런타임 export 확인

`-Dmixin.debug.export=true`로 생성한 전체 구성의 런타임 Mixin export에서 다음을 확인했습니다.

- 변환된 `ModIdHelper#getModNameForTooltip`가 patch replacement handler를 정확히 한 번 호출함.
- 변환된 `RecipeCategoryTab#getTooltip`가 patch replacement handler를 정확히 한 번 호출함.
- 변환된 `ModIdHelper`에 Petrolpark `wrapMethod$...$formatSharedFeatureModIds`가 남아 있음.
- 제거된 두 Petrolpark handler의 깨진 invocation 주입은 적용되지 않음.
