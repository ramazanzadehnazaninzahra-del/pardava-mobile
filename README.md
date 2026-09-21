# پردآوا موبایل — pardava-mobile

اپلیکیشن اندروید پلتفرم آموزشی **پردآوا** (برنامه‌نویسی و هوش مصنوعی، دوزبانه فارسی/انگلیسی).
این ریپو مستقل است و مستقیماً با **Courses API سایت** (`https://pardava.ir/api/courses`) کار می‌کند.

> نسخهٔ فعلی: `0.5.0` — «اول مرور رایگان، ورود فقط وقت نیاز»

## امکانات

| بخش | توضیح |
|------|-------|
| **مرور بدون حساب** | فهرست دوره‌ها، جزئیات و پیش‌نمایش درس‌های رایگان، جدول لیگ — همه بدون لاگین |
| ورود (۴ روش) | رمز عبور سایت · کد پیامکی OTP (شماره‌های ایران) · **جیمیل** (Credential Manager) · **توکن اپ** از پروفایل سایت |
| ثبت‌نام | ثبت‌نام رایگان با یک کلیک؛ دوره‌های پولی: «درخواست خرید» و پیگیری وضعیت تأیید ادمین |
| درس | متن درس، فایل پیوست (دانلود با توکن + باز کردن از حافظه)، تکمیل درس و دریافت امتیاز، درس قبلی/بعدی |
| لیگ | جدول امتیازات کل سایت + فیلتر هر دوره |
| پروفایل | نام، امتیاز، تعداد دوره‌های فعال، خروج، تغییر زبان، تغییر آدرس سرور |
| دوزبانه | fa پیش‌فرض (RTL) + en؛ سوییچ داخل اپ + ارسال `Accept-Language` |

نقشهٔ دسترسی درس‌ها (سمت سرور تعیین می‌شود): `open` آماده · `preview` پیش‌نمایش رایگان (حتی بی‌حساب) · `done` تکمیل‌شده · `enroll` نیاز به ثبت‌نام · `locked` قفل (ترتیبی).

## معماری

```
app/src/main/java/ir/pardava/mobile/
├── core/        ApiClient (Retrofit+OkHttp، بازسازی روی تغییر سرور) · SessionManager · TokenStore (DataStore) · Fmt
├── data/        PardavaApi (Retrofit) · dto/ (kotlinx.serialization + envelope {ok,…})
├── ui/
│   ├── components/   MainScaffold (تب‌ها) · LanguageSwitchRow · ServerSettingsSection
│   ├── nav/          Routes + NavHost (شروع همیشه عمومی، لاگین فقط push وقت نیاز)
│   └── screens/      courses · course · lesson · league · profile · login
└── test/        قرارداد API سایت روی MockWebServer (envelope، خطاها، مسیرها) + Fmt
```

- **Kotlin 2.0 + Jetpack Compose (Material 3)**، minSdk 24 / targetSdk 35
- خطاها: پیام فارسی سرور عیناً نمایش داده می‌شود؛ `action` سرور (`login/enroll/purchase/…`) هدایت‌گر UI است
- **Credential Manager** برای جیمیل؛ توکن نهایی: `Authorization: Bearer pdv_…` (+ `X-Api-Token`)

## نصب نسخهٔ آماده (APK)

فایل `Pardava-v0.5.0-release.apk` امضاشده است و روی اندروید ۷ به بالا نصب می‌شود:

1. فایل را به گوشی بدهید و بازش کنید؛ در پیام «نصب از منابع ناشناس» اجازه بدهید.
2. اپ بدون حساب باز می‌شود و مستقیم به `https://pardava.ir/api/courses` وصل است — دوره‌ها را ببینید.
3. برای ثبت‌نام: دکمهٔ **ورود** → یکی از ۴ روش. کاربران جیمیل فعلاً راحت‌ترین راه: ورود در سایت pardava.ir → پروفایل → «توکن اپ» → تب «توکن» در اپ.
4. آدرس سرور از پایین صفحهٔ ورود یا پروفایل قابل تغییر است (پیش‌فرض همان pardava.ir).

### رفع ارورها و هشدارهای نصب

| پیام | علت | راه‌حل |
|------|-----|--------|
| «App not installed» بعد از به‌روزرسانی | نسخهٔ قبلی با امضای متفاوت نصب بوده | نسخهٔ قبلی را **حذف** کنید و دوباره نصب کنید (امضای v0.5.0 با نسخه‌های 0.4.x یکسان است، ولی اگر APK جعلی/دیگری نصب کرده‌اید حذف لازم است) |
| «App not installed» | فضای کم / APK ناقص دانلود شده | حداقل ~۲۰۰MB فضا خالی کنید؛ فایل را دوباره بگیرید (حجم سالم ≈ ۲.۸MB) |
| «Install blocked / منابع ناشناس» | نصب خارج از Play | Settings → اجازهٔ نصب برای همان مرورگر/فایل‌منیجر |
| «Play Protect hasn't scanned» | اسکن ابری نشده | **More details → Install anyway**؛ اپ فقط مجوز `INTERNET` دارد |
| «Blocked by Play Protect» | سیاست سخت‌گیرانه | Play Protect → Settings → اسکن را موقتاً خاموش/خنثی کنید (با احتیاط) |
| هشدار نمی‌خواهم | ماهیت sideload | فقط انتشار در Google Play (حتی Internal testing) هشدارها را حذف می‌کند |

> **امضای ریلیز**: `pardava-release.keystore` (alias `pardava`) + `keystore.properties` (gitignored).
> SHA-1 امضا: `ac011be60913cfc58d99ae5533fc95ef99fda960`

### فعال‌سازی ورود جیمیل در اپ (یک‌بار، برای مالک سایت)

اپ از **Credential Manager** استفاده می‌کند و ID Token گوگل را به `POST /api/courses/auth/google` می‌فرستد
(سرور باید همان client را قبول کند). قدم‌ها:

1. در پروژهٔ Google Cloud **همان پروژه‌ای که pardava.ir استفاده می‌کند**، یک **OAuth 2.0 Client ID** از نوع *Web application* (اگر نیست) بگیرید.
2. یک **Android client** بسازید با:
   - Package name: `ir.pardava.mobile`
   - SHA-1: `ac011be60913cfc58d99ae5533fc95ef99fda960`
3. مقدار **Web client ID** را به build بدهید:

```bash
./gradlew assembleRelease -PpardavaGoogleClientId=XXXXXXXX.apps.googleusercontent.com
```

تا وقتی این کار انجام نشود، تب «جیمیل» راهنما نشان می‌دهد و مسیر «توکن اپ» کار می‌کند.

## ساخت

```bash
./gradlew assembleDebug            # بیلد بدون هیچ رازی (keystore اختیاری است)
./gradlew assembleRelease          # اگر keystore.properties باشد، امضاشده
./gradlew testDebugUnitTest        # ۱۸ تست JVM (قرارداد API + Fmt)

# تغییر سرور پیش‌فرض:
./gradlew assembleRelease -PpardavaBaseUrl=https://example.com/api/courses/
```

پیش‌فرض `DEFAULT_BASE_URL` = `https://pardava.ir/api/courses/`؛ برای سرور آزمایشی HTTP محلی،
`network_security_config.xml` کلیرتکست را مجاز می‌داند (برای تولید HTTPS توصیهٔ اکید).

## CI

`.github/workflows/android-ci.yml` → JDK 17 + Gradle: `assembleDebug` + `testDebugUnitTest`
و آپلود APK دیباگ به‌صورت artifact.

## مستندات پلتفرم

معماری کامل، ERD و API در ریپوی اصلی: `github.com/ramazanzadehnazaninzahra-del/pardava`
(فایل `docs/pardava-lms.md`) + مستند زندهٔ Courses API: `https://pardava.ir/api/courses/docs`

---

# Pardava Mobile (English summary)

Android app of the **Pardava** bilingual (Persian/English) programming & AI education platform.
Standalone repository talking directly to the **site Courses API** (`https://pardava.ir/api/courses`).

**v0.5.0 — “browse freely, sign in only when it matters”**: courses, course details, free-preview
lessons and the league table are public. Four sign-in methods — site password, Iranian mobile OTP,
**Gmail (Credential Manager)**, and the **app token** from the pardava.ir profile page. One-tap
enrollment for free courses, purchase requests (admin approval) for paid ones, lesson text +
token-authorized file downloads, mark-complete with points, prev/next navigation, and fa/en UI.

Build: `./gradlew assembleDebug testDebugUnitTest`. Default API base is the production site;
override with `-PpardavaBaseUrl`. Gmail sign-in needs a one-time Google Cloud setup — create an
Android OAuth client for `ir.pardava.mobile` with SHA-1 `ac011be60913cfc58d99ae5533fc95ef99fda960`
in the pardava.ir project and pass the Web client ID via `-PpardavaGoogleClientId`.
