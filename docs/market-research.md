# Aura — погодное приложение с 3D-дизайном
## Отчёт об исследовании рынка

*Дата: 22 октября 2026 · Рабочее название продукта: **Aura** (можно сменить)*

---

## 1. Коротко

- **Рынок небольшой, но растёт и хорошо монетизируется.** Мировой рынок погодных приложений — около **$1,2 млрд в 2026 г.**, прогноз **$2,27 млрд к 2034 г.** (CAGR ≈ 8,3 %). Быстрее всего растёт сегмент **подписок** ([Straits Research](https://straitsresearch.com/press-release/global-weather-app-market-size)).
- **Лидеры по охвату (The Weather Channel, AccuWeather) — это реклама и устаревший UX.** Пользователи чаще всего жалуются на точность (~28 % негативных отзывов), обилие рекламы (~23 %) и «ползучие» подписки (~19 %) ([Unstar](https://unstar.app/ar/blog/weather-apps-ranked-by-user-complaints-2026)).
- **Ни одно приложение не сочетает всё сразу.** Есть красивое (*(Not Boring) Weather*), есть глубокое по данным (*Carrot*, *Windy*), есть точное локально (*Яндекс Погода* с Meteum). Приложения, которое объединяет все три качества, нет. Это и есть наша ниша.
- **Позиционирование Aura:** *«самая красивая погода на телефоне, которая к тому же точнее всех говорит, когда начнётся дождь»*. 3D-сцена, которая живёт вместе с реальной погодой, плюс минутный прогноз осадков, ИИ-советы и никакой рекламы.

---

## 2. Объём и динамика рынка

| Показатель | Значение | Источник |
|---|---|---|
| Объём рынка 2025 | ≈ $1,10 млрд | Straits Research |
| Объём рынка 2026 | ≈ $1,20 млрд | Straits Research |
| Прогноз 2034 | ≈ $2,27 млрд | Straits Research |
| CAGR 2026–2034 | 8,32 % | Straits Research |
| Самый быстрорастущий сегмент | подписки | Straits Research (вторичный пересказ) |
| Крупнейший регион | Северная Америка | Straits Research |

**Что важно для нас:**
- Ещё в 2021 г. Appfigures называл погодные приложения «денежными машинами»: выручка категории в US App Store выросла примерно в 8 раз к уровню 2018 г., хотя сама категория одна из самых маленьких по числу приложений ([Appfigures](https://appfigures.com/resources/insights/20210806/1-weather-apps-=-cash-machines)). **Мало конкурентов, высокая готовность платить.**
- Спрос сезонный и событийный: всплески приходятся на ураганы, жару и снегопады ([Appfigures](https://analytics.appfigures.com/resources/insights/20220930?f=1)). Маркетинг и пуши стоит привязывать к погодным событиям.
- По рынку РФ публичной статистики аудитории за 2025–2026 гг. мы не нашли. Ориентир: у сайта gismeteo.ru около 16 млн визитов в месяц, 62,8 % из них из России (оценка Ahrefs, июнь 2025) ([Ahrefs](https://ahrefs.com/es/top/gismeteo.ru)). **Перед запуском нужно купить данные Sensor Tower / Similarweb / Mediascope.**

> ⚠️ Оценки рынка взяты из пресс-релизов аналитических агентств и у разных издателей расходятся. Это ориентир, а не точная цифра.

---

## 3. Конкуренты

### 3.1 Сравнительная таблица

| Приложение | Сильные стороны | Слабые стороны | Модель / цена |
|---|---|---|---|
| **Apple Weather** | Бесплатно, встроено в iOS, хорошие карты и уведомления об осадках | Мало настроек | Бесплатно |
| **The Weather Channel** | Лидер по загрузкам, сильные алерты и радар | Тяжёлая реклама (баннер «на полэкрана») | Реклама + Premium |
| **AccuWeather** | MinuteCast — дождь по минутам на ближайший час | Реклама, редизайн 2025 г. «спрятал функции» | Premium $1,99/мес или $12,99/год; Premium+ $4,99/мес или $29,99/год |
| **CARROT Weather** | Максимум кастомизации, выбор источника данных, «характер» | Сложно, ключевые функции за подпиской | Premium Ultra $9,99/мес или $39,99/год |
| **Windy** | Сравнение моделей, профессиональные слои (ветер, авиация, море) | Слишком сложно на каждый день | Freemium |
| **(Not Boring) Weather** | **3D-интерфейс, физика, звуки, как в игре**; финалист Apple Design Award 2021; 4,8★; Apple показывала его в подборках 339 раз за I пол. 2025 г. | Мало метеоданных, «красиво, но неглубоко»; платный бандл | Подписка-бандл |
| **Яндекс Погода** | Meteum (ML-смешивание моделей), осадки для точки с шагом 10 мин, ~2 млн пользовательских сообщений в день, тематические прогнозы | Только экосистема Яндекса, дизайн утилитарный | Бесплатно |
| **Gismeteo** | Узнаваемость в РФ/СНГ | Реклама, устаревший UX | Реклама + подписка |
| **RP5** | Детальные данные метеостанций для энтузиастов | Интерфейс для специалистов | Бесплатно |

Источники: [Eskimo](https://eskimo.travel/en/blog/best-weather-apps-2026), [iTechGuides](https://itechguides.com/the-best-weather-apps-for-2026-compared-by-use-case), [TWiT](https://twit.tv/posts/tech/best-weather-apps-iphone-ipad-2026), [Digital Trends](https://www.digitaltrends.com/phones/best-weather-apps-for-iphone/), [Carrot press kit](https://www.meetcarrot.com/weather/presskit.html), [App Store — (Not Boring)](https://apps.apple.com/us/app-bundle/not-boring-apps-the-essentials-made-fun/id1553834540), [Screens Design](https://screensdesign.com/showcase/not-boring-weather), [NeoAds](https://neoads.substack.com/p/design-bundles-apple-features-how), [Яндекс — справка](https://yandex.ru/support/weather/faq.html), [Rozetked — Meteum 2.0](https://rozetked.me/news/17853-meteum-2-0-novaya-tehnologiya-prognoza-pogody-ot-yandeksa), [Mel.fm](https://mel.fm/novosti/4083526-yandex), [Mobile-Review](https://mobile-review.com/all/press_releases/yandeks-pogoda-nachala-delat-tematicheskie-prognozy/), [Overclockers](https://overclockers.ru/lab/show/81978/vybiraem-meteoservis-dlya-android-ustrojstv-gismeteo-yandeks-pogoda-i-rp5-raspisanie-pogody).

> Цены взяты из обзоров и пресс-кита, в разных регионах они отличаются. Перед установкой нашей цены нужно сверить их в App Store / Google Play.

### 3.2 Карта позиционирования

```
              Глубина данных ↑
                    │
      Windy ●       │        ● CARROT
      RP5 ●         │
                    │           ★ AURA (цель)
  ──────────────────┼──────────────────────→ Качество дизайна / эмоция
  Gismeteo ●        │
  Weather Channel ● │        ● Apple Weather
  AccuWeather ●     │
                    │              ● (Not Boring) Weather
```

**Вывод:** правый верхний квадрант («красиво и глубоко») почти пуст. CARROT туда частично попадает, но его эстетика — юмор и кастомизация, а не 3D и «вау-эффект».

### 3.3 Чему учиться у лидеров

- **У (Not Boring):** 3D, физика, звук и хаптика превращают проверку погоды в удовольствие. Apple охотно продвигает такие приложения, и это даёт бесплатный органический рост.
- **У Яндекса:** прогноз «дождь начнётся через 12 минут» — самая ценная функция на каждый день. Пользовательские отчёты («у меня идёт снег») улучшают модель.
- **У CARROT:** выбор источника данных и гибкие виджеты удерживают продвинутых пользователей.
- **У AccuWeather — анти-урок:** нельзя прятать функции ради рекламы и менять условия подписки задним числом.

---

## 4. Боли пользователей

По анализу негативных отзывов (1–3★) в 8 популярных приложениях ([Unstar](https://unstar.app/ar/blog/weather-apps-ranked-by-user-complaints-2026)) и отзывам на [Trustpilot](https://www.trustpilot.com/review/accuweather.com), [unitQ](https://unitq.com/unitq-scorecards/accuweather) и [Kimola](https://kimola.com/reports/unlock-insights-weather-radar-app-user-feedback-report-google-play-156369):

| # | Боль | Доля жалоб | Как решает Aura |
|---|---|---|---|
| 1 | Неточный прогноз, особенно дождя | ~28 % | Мультимодельный ансамбль + ИИ-nowcast + индикатор уверенности прогноза («87 % уверенности») |
| 2 | Реклама | ~23 % | **Ноль рекламы**, в бесплатной версии тоже |
| 3 | Подписка «съедает» функции | ~19 % | Честный бесплатный тариф. Купленное навсегда не отбирается, есть пожизненная лицензия |
| 4 | Слишком много уведомлений | — | Умные алерты: только то, что меняет планы, плюс тихие часы |
| 5 | Редизайны прячут функции | — | Стабильная структура: 4 вкладки, настраиваемые карточки |

---

## 5. Технологические тренды 2025–2026

1. **ИИ-модели прогноза стали стандартом.** ECMWF запустил ИИ-модель **AIFS** в операционную работу 25.02.2025. Расчёт занимает ~3 мин вместо ~30, а энергозатраты ниже примерно в 1000 раз ([Gizmodo](https://gizmodo.com/europes-new-ai-weather-model-is-faster-smarter-and-free-heres-what-to-know-2000567775), [ECMWF](https://events.ecmwf.int/event/495/contributions/5751/attachments/3479/5853/Data-driven-weather-forecasting-ml-training.pdf)). DeepMind **GenCast** (вероятностный ансамбль) обходит ENS ECMWF по 97,2 % целевых показателей, а Microsoft **Aurora** работает с разрешением 0,1° ([EU Space](https://eu-space.europa.eu/sites/default/files/2026-06/2_AIRevolution_EarthSystem_Chantry_March2026.pdf)). *Для нас это значит:* открытые ИИ-модели снижают стоимость собственного прогноза.
2. **ИИ-nowcasting на 0–6 часов.** В январе 2026 г. NVIDIA выпустила Earth-2 Nowcasting (StormScope) с километровым разрешением. По заявлению разработчика, это первая ИИ-модель, которая точнее физических моделей предсказывает краткосрочные осадки. Ключевой дифференциатор Aura — «дождь по минутам».
3. **iOS 26 Liquid Glass.** Новый язык Apple: полупрозрачное «стекло» с преломлением, многослойные виджеты с глубиной ([Apple](https://www.apple.com/os/pdf/All_New_Features_iOS_26_Sept_2025.pdf), [MacRumors](https://www.macrumors.com/2025/06/09/apple-announces-ios-26)). Наш 3D-стиль органично ложится на эту эстетику и повышает шансы попасть в подборки App Store.
4. **Виджеты, Live Activities, Dynamic Island, Apple Watch.** Пользователь всё чаще смотрит погоду, не открывая приложение. Виджеты — это продукт, а не дополнение.
5. **Персонализация и ИИ-ассистент.** Тематические прогнозы (для садоводов, бегунов, рыбаков) уже делает Яндекс. Следующий шаг — диалог: «Можно ли завтра в 7 утра на пробежку?»

---

## 6. Продукт: что делает Aura лучшей

### 6.1 Ценностное предложение
> **Aura — погода, которую хочется открывать.** Живая 3D-сцена показывает погоду за окном и через час, точный минутный прогноз дождя, ИИ-советы. Без рекламы.

### 6.2 Дизайн-концепция «Живая диорама»
- **Центральный 3D-объект**: парящая «капсула» с небом (солнце, облака, дождь, снег, гроза, туман, северное сияние). Её состояние вычисляется из реальных данных: облачности, интенсивности осадков, ветра, высоты солнца.
- **Параллакс от гироскопа**: сцена наклоняется вслед за телефоном.
- **Время суток**: палитра неба меняется плавно (рассвет → день → закат → ночь) по реальному положению солнца.
- **Скраббинг времени**: проводишь пальцем по часовой ленте, и сцена «проигрывает» будущую погоду.
- **Хаптика и звук**: дождь «стучит» вибрацией при прокрутке, гром отзывается хаптикой. Звук по умолчанию выключен, и мы уважаем беззвучный режим (у (Not Boring) пользователи на это жалуются).
- **Стеклянные карточки** (glassmorphism / Liquid Glass) с глубиной и мягкими тенями.
- **Доступность**: режим «уменьшить движение», крупный шрифт, контраст текста ≥ 4,5:1, озвучка VoiceOver/TalkBack.

### 6.3 Функции по релизам

| Функция | MVP (v1.0) | v1.5 | v2.0 |
|---|:-:|:-:|:-:|
| 3D-сцена текущей погоды, 8 состояний × 4 времени суток | ✅ | | |
| Почасовой (48 ч) и 10-дневный прогноз | ✅ | | |
| **Минутный прогноз осадков (2 ч)** с пушем «дождь через N мин» | ✅ | | |
| Детали: ощущается как, УФ, влажность, ветер, давление, видимость, восход/закат, фаза Луны | ✅ | | |
| Качество воздуха (AQI) и пыльца | ✅ | | |
| Виджеты (Home / Lock Screen), тёмная и светлая тема | ✅ | | |
| Индикатор уверенности прогноза | ✅ | | |
| Анимированная 3D-карта: радар осадков, облака, температура, ветер | | ✅ | |
| Live Activity + Dynamic Island (дождь, гроза) | | ✅ | |
| Apple Watch / Wear OS | | ✅ | |
| Пользовательские отчёты о погоде (краудсорсинг) | | ✅ | |
| ИИ-ассистент: «что надеть», «когда выйти», планирование по календарю | | | ✅ |
| Профили активностей: бег, вело, рыбалка, сад, лыжи, фото («золотой час») | | | ✅ |
| Сравнение моделей (ECMWF / GFS / ICON / ИИ) для энтузиастов | | | ✅ |
| Семейный доступ, алерты для близких в других городах | | | ✅ |

### 6.4 Монетизация

Ниша между AccuWeather Premium+ ($29,99/год) и CARROT Ultra ($39,99/год):

| Тариф | Что входит | Цена (гипотеза, проверить A/B) |
|---|---|---|
| **Free** | 3D-сцена, прогноз на 10 дней, минутные осадки для 1 города, 2 виджета. **Без рекламы** | 0 |
| **Aura+** | Все города, 3D-карты, Live Activity, все виджеты и темы сцены, ИИ-ассистент, сравнение моделей | [ЦЕНА/мес] · [ЦЕНА/год] — ориентир $2,99 / $19,99 |
| **Lifetime** | Всё из Aura+ навсегда | [ЦЕНА] — ориентир $59,99 |
| **Семейный** | До 6 человек | [ЦЕНА/год] |

Дополнительно: платные «скины» сцены (зимний город, космос, аниме) как разовые покупки.

---

## 7. Технологический стек (рекомендация)

| Слой | Рекомендация | Почему |
|---|---|---|
| iOS (первым) | Swift + SwiftUI, 3D на **RealityKit / Metal-шейдерах** | Максимальная плавность (120 Гц ProMotion), Liquid Glass, виджеты, Live Activities, шанс на фичеринг Apple |
| Android (вторым) | Kotlin + Jetpack Compose, 3D на **Filament** (glTF) | Нативное качество, Material You |
| Общие 3D-ассеты | Blender → glTF / USDZ, процедурные шейдеры облаков и осадков | Один пайплайн на обе платформы |
| 2D-микроанимации | Rive | Лёгкие интерактивные анимации иконок и переходов |
| Альтернатива для быстрого старта | Flutter (Impeller + фрагментные шейдеры) | Одна кодовая база. Компромисс: сложнее с виджетами и 3D |
| Данные погоды | Агрегация: Apple WeatherKit, Open-Meteo (ECMWF / ICON / GFS), коммерческий nowcast (Tomorrow.io и аналоги); для РФ — Meteum API Яндекса | Ансамбль повышает точность. Условия и лицензии каждого источника нужно проверить |
| Бэкенд | Прокси-кеш прогнозов, сервис пушей, ML-смешивание моделей | Контроль стоимости запросов и качества |

---

## 8. Метрики успеха (KPI)

| Метрика | Цель первого года |
|---|---|
| Рейтинг в сторах | ≥ 4,8★ |
| D1 / D30 retention | ≥ 45 % / ≥ 20 % [проверить против бенчмарков категории] |
| Конверсия Free → Aura+ | [ЦЕЛЬ] % |
| Точность «дождь через N мин» (hit-rate) | ≥ [ЦЕЛЬ] %, публиковать открыто |
| Фичеринг App Store / Google Play | ≥ 1 «App of the Day» |
| Доля пользователей с виджетом | ≥ 50 % |

---

## 9. Риски

| Риск | Митигация |
|---|---|
| 3D сажает батарею и греет телефон | Рендер только при открытом экране, 30 fps в фоне, режим энергосбережения, LOD-модели |
| Стоимость API погоды растёт с аудиторией | Кеш по геосетке, собственное смешивание открытых моделей |
| Встроенные Apple / Google Weather бесплатны и «достаточно хороши» | Конкурировать эмоцией, nowcast и виджетами, а не базовым прогнозом |
| Копирование дизайна конкурентами | Скорость итераций, бренд, авторские 3D-сцены, сообщество |
| Точность хуже обещанной | Честный индикатор уверенности, публичная статистика точности |

---

## 10. Следующие шаги

1. Утвердить название, бренд и концепцию по макетам (`design/`).
2. Провести 10–15 интервью с пользователями и протестировать прототип на кликабельных макетах.
3. Сделать технический спайк: 3D-сцена на RealityKit (60/120 fps, расход батареи) и сравнить точность источников данных для 3 городов.
4. Купить данные Sensor Tower или AppMagic по категории Weather (РФ + США + ЕС).
5. Выпустить MVP на iOS: срок [СРОК].

---

## Источники

- Straits Research — [Weather App Market](https://straitsresearch.com/press-release/global-weather-app-market-size)
- Appfigures — [Weather apps = cash machines](https://appfigures.com/resources/insights/20210806/1-weather-apps-=-cash-machines), [Hurricane season](https://analytics.appfigures.com/resources/insights/20220930?f=1)
- Sensor Tower — [Top weather apps 2018](https://sensortower.com/blog/top-weather-apps-2018)
- Statista — [Top weather apps Canada by revenue](https://statista.com/statistics/1536513/top-weather-apps-canada-by-revenue)
- Обзоры: [Eskimo](https://eskimo.travel/en/blog/best-weather-apps-2026), [iTechGuides](https://itechguides.com/the-best-weather-apps-for-2026-compared-by-use-case), [TWiT](https://twit.tv/posts/tech/best-weather-apps-iphone-ipad-2026), [Softpicker](https://softpicker.com/best-weather-apps/), [Tom's Guide](https://www.tomsguide.com/uk/round-up/best-weather-apps), [Digital Trends](https://www.digitaltrends.com/phones/best-weather-apps-for-iphone/), [MacMyths](https://macmyths.com/best-apple-watch-weather-apps-in-2026/)
- CARROT — [Press kit](https://www.meetcarrot.com/weather/presskit.html)
- (Not Boring) — [App Store](https://apps.apple.com/us/app-bundle/not-boring-apps-the-essentials-made-fun/id1553834540), [Screens Design](https://screensdesign.com/showcase/not-boring-weather), [NeoAds](https://neoads.substack.com/p/design-bundles-apple-features-how), [36Kr](https://eu.36kr.com/de/p/3990951063600130)
- Отзывы: [Unstar](https://unstar.app/ar/blog/weather-apps-ranked-by-user-complaints-2026), [Trustpilot AccuWeather](https://www.trustpilot.com/review/accuweather.com), [unitQ](https://unitq.com/unitq-scorecards/accuweather), [Kimola](https://kimola.com/reports/unlock-insights-weather-radar-app-user-feedback-report-google-play-156369)
- Яндекс Погода — [Справка](https://yandex.ru/support/weather/faq.html), [Rozetked](https://rozetked.me/news/17853-meteum-2-0-novaya-tehnologiya-prognoza-pogody-ot-yandeksa), [Mel.fm](https://mel.fm/novosti/4083526-yandex), [Mobile-Review](https://mobile-review.com/all/press_releases/yandeks-pogoda-nachala-delat-tematicheskie-prognozy/), [Kod.ru](https://kod.ru/neuro-prognozy-yandex)
- Gismeteo / РФ — [Ahrefs](https://ahrefs.com/es/top/gismeteo.ru), [iPhones.ru](https://www.iphones.ru/iNotes/apple-yandeks-ili-gismeteo-kto-luchshe-vsego-predskazyvaet-pogodu-v-rossii-testy-na-dozhd-i-solnce), [Overclockers](https://overclockers.ru/lab/show/81978/vybiraem-meteoservis-dlya-android-ustrojstv-gismeteo-yandeks-pogoda-i-rp5-raspisanie-pogody), [Хабр](https://habr.com/ru/post/724686)
- ИИ-прогнозирование — [ECMWF](https://events.ecmwf.int/event/495/contributions/5751/attachments/3479/5853/Data-driven-weather-forecasting-ml-training.pdf), [EU Space](https://eu-space.europa.eu/sites/default/files/2026-06/2_AIRevolution_EarthSystem_Chantry_March2026.pdf), [Gizmodo](https://gizmodo.com/europes-new-ai-weather-model-is-faster-smarter-and-free-heres-what-to-know-2000567775)
- Apple iOS 26 — [Apple](https://www.apple.com/os/pdf/All_New_Features_iOS_26_Sept_2025.pdf), [MacRumors](https://www.macrumors.com/2025/06/09/apple-announces-ios-26)
