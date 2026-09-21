# پردآوا موبایل — pardava-mobile

اپلیکیشن اندروید پلتفرم آموزشی **پردآوا** (برنامه‌نویسی و هوش مصنوعی، دوزبانه فارسی/انگلیسی).
این ریپو مستقل است و فقط با **LMS API** (سرویس FastAPI در ریپوی `pardava`، مسیر `backend/`) کار می‌کند.

> **مرحلهٔ ۴ نقشه راه پلتفرم (v0.4.0)** — نسخهٔ فعلی: `0.4.0`

## امکانات

| بخش | توضیح |
|------|-------|
| ورود | OTP موبایل (نمایش کد در حالت dev سرور) + دکمهٔ گوگل (نیازمند تنظیم Firebase؛ تا آن موقع غیرفعال) |
| دوره‌ها | فهرست دوره‌ها، درخت فصل/جلسه با وضعیت **قفل/تکمیل/قبولی آزمون** (قفل ترتیبی سمت سرور اعمال می‌شود) |
| جلسه | پخش‌کنندهٔ ویدیو (Media3/ExoPlayer) با **زیرنویس WebVTT ساید‌لودشده**، متن جلسه، نمونه کد، تمرین |
| پیشرفت | گزارش خودکار هر ۱۵ ثانیه (ثانیه/درصد تماشا)؛ سرور تصمیم می‌گیرد چه زمانی جلسه «تکمیل» شود |
| آزمون | شروع/ادامهٔ تلاش، تایمر شمارش معکوس با ارسال خودکار در صفر، سه نوع سؤال (چهارگزینه‌ای/صحت‌وخطا/کد کوتاه)، گزارش سؤال‌به‌سؤال، XP و دستاورد جدید |
| بازی‌وارسازی | XP، سطح، استریک، دستاوردها، رتبه‌بندی (هفتگی/ماهانه/کلی) |
| دوزبانه | fa پیش‌فرض (RTL) + en؛ سوییچ داخل اپ؛ هم‌زمان `Accept-Language` و ترجیح سمت سرور تنظیم می‌شود |
| احراز هویت | JWT + refresh چرخشی؛ تمدید خودکار و single-flight در 401؛ خروج خودکار در نشست مسکوک |

## معماری

```
app/src/main/java/ir/pardava/mobile/
├── core/        ApiClient (Retrofit+OkHttp+refresh) · TokenStore (DataStore) · Fmt · QuizTimer
├── data/        PardavaApi (Retrofit) · dto/ (kotlinx.serialization)
├── ui/
│   ├── components/   MainScaffold (تب‌ها) · LanguageSwitchRow
│   ├── nav/          Routes + NavHost
│   └── screens/      login · courses · course · lesson · quiz · profile · leaderboard
└── test/        Fmt · QuizTimer · DTO parsing · auth refresh flow (MockWebServer)
```

- **Kotlin 2.0 + Jetpack Compose (Material 3)**، minSdk 24 / targetSdk 35
- **Media3 ExoPlayer** با SubtitleConfiguration برای زیرنویس VTT امضاشده
- **DataStore** برای توکن‌ها، **AppCompat per-app locales** برای زبان
- بدون DI framework (سیم‌کشی دستی ساده) — مناسب ریپوی کوچک و قابل فهم
- قفل ترتیبی، نمره‌دهی، XP و همهٔ منطق‌های حساس **صرفاً سمت سرور** است؛ اپ فقط نمایش می‌دهد

## ساخت

```bash
# وابستگی‌ها فقط از Maven Central و Google — پروژه به‌صورت پیش‌فرض بدون Firebase هم build می‌شود
./gradlew assembleDebug

# تست‌های واحد JVM
./gradlew testDebugUnitTest
```

اتصال به بک‌اند لوکال (emulator): سرور FastAPI روی پورت 8100 بالا بیاید؛
پیش‌فرض `DEFAULT_BASE_URL` برابر `http://10.0.2.2:8100/` است (10.0.2.2 = loopback میزبان در emulator).
برای دستگاه واقعی یا سرور واقعی:

```bash
./gradlew assembleDebug -PpardavaBaseUrl=https://lms.pardava.ir/
```

(`10.0.2.2` و `localhost` در `network_security_config.xml` مجاز به HTTP هستند؛ بقیهٔ دامنه‌ها HTTPS.)

### فعال‌سازی ورود با گوگل (اختیاری — Firebase)

1. در کنسول Firebase پروژه بسازید و `SHA-1` دیباگ/ریلیز را اضافه کنید.
2. فایل `google-services.json` را در `app/` بگذارید (در `.gitignore` است و commit نمی‌شود).
   افزونهٔ `google-services` فقط وقتی این فایل موجود باشد اعمال می‌شود.
3. Web Client ID سرور را به سرور بدهید (متغیر `GOOGLE_CLIENT_ID` بک‌اند) — اپ، ID Token را به
   `POST /api/v1/auth/google` می‌فرستد و سرور با JWKS اعتبارسنجی می‌کند.

## CI

`.github/workflows/android-ci.yml` → JDK 17 + Gradle: `assembleDebug` + `testDebugUnitTest`،
و آپلود APK دیباگ به‌صورت artifact.

## مستندات پلتفرم

معماری کامل، ERD و API در ریپوی اصلی: `github.com/ramazanzadehnazaninzahra-del/pardava`
(فایل `docs/pardava-lms.md`).

---

# Pardava Mobile

The Android app of the **Pardava** bilingual (Persian/English) programming & AI education platform.
This is a standalone repository talking to the **LMS API** (FastAPI service in the `pardava` repo).

**Platform stage 4 (v0.4.0)** — current app version: `0.4.0`

Highlights: OTP login (Google button ready for the Firebase step) · course catalog with
**server-enforced sequential lesson locking** · Media3 player with side-loaded WebVTT subtitles ·
automatic progress reporting every 15s · server-graded quizzes with countdown & auto-submit ·
XP / levels / streaks / achievements / leaderboards · full fa/en UI with instant RTL/LTR switching ·
JWT auto-refresh with single-flight rotation and forced re-login on token theft.

Build: `./gradlew assembleDebug testDebugUnitTest` — point the app at a local backend with
`-PpardavaBaseUrl` (default `http://10.0.2.2:8100/` for the emulator). See the Persian section above
for the full feature map and architecture.
