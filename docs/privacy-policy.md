# Nour Time – Privacy Policy (draft)

*Draft for review. Replace the bracketed placeholders before publishing.*

**Effective date:** [date]
**Developer:** [developer name], [contact email]

Nour Time helps parents limit how long their child uses the apps they choose. This policy explains
what the app does with information. In short: **unless a parent connects their own phone, everything
stays on the device. Nour Time never sells data, shows ads or uses analytics.**

## What the app uses, and why

- **Which apps are on the screen.** Through Android's Accessibility service (and, as a backup, Usage
  access), Nour Time sees the package name of the apps that are open, including picture-in-picture
  and split-screen windows. This is used only to count time on the apps the parent selected, to
  show the "Time's up" screen, to show the parent-PIN screen over the phone's Settings, the app
  installer and security apps (so the child can't switch Nour Time off or uninstall it), and to go back
  to the home screen when the time is up. Nour Time never reads the text or content of any screen, or
  anything typed.
- **Background audio of the limited apps.** From the media notifications of the apps the parent limits,
  the Accessibility service takes only the control that plays and pauses them. During a lock period,
  bedtime, or while protection is weakened, Nour Time pauses their music or videos (also when played
  again from the notification shade); while the screen is on, their playback counts toward the time
  budget. The text of notifications is never read, and other apps' notifications are ignored. This
  stays on the device.
- **Time used per app per day.** Stored on the device to show the parent today's usage.
- **Apps installed later.** The list of apps already on the phone, so that a newly installed app can
  be added to the limited apps (the parent can turn this off).
- **Settings.** The selected apps, time budget, lock period, schedule, bedtime, the child's age group
  and whether messages should address a boy or a girl.
- **Parent PIN and security question.** The PIN and the answer are stored only as salted one-way
  hashes. The question text is stored as written.
- **Learning Hub.** Game progress (stars per level) and the minutes the child earned, stored on the
  device.

## If a parent connects their own phone (optional)

A parent can install Nour Time on their own phone, sign in with their Google account and connect it to
the child's phone. Connecting needs the parent to hold both phones: the child's phone shows a code, and
the parent confirms on the child's phone (behind the parent PIN). Only then, and only while connected,
the child's phone sends the following to Google Firebase (Cloud Firestore), where only that parent's
account can read it:

- the phone's make and model (to name it in the parent's list);
- the timer status: time left, whether the apps are locked and until when, and whether protection is
  working;
- the minutes used today on each limited app;
- the names and package names of the apps that can be opened from the launcher (so the parent can
  choose limited and allowed apps);
- protection alerts: when the phone restarted without Nour Time (for example in safe mode), when it
  switched to another user, whether USB debugging is on, whether Nour Time's notifications are turned
  off, and when the PIN was reset with the security question and when that new PIN starts working (the
  time of the event, no other details);
- crash reports, if Nour Time crashes: where in Nour Time's code it failed, the Android version and
  phone model, sent to Google Firebase Crashlytics so the crash can be fixed. Not linked to the parent's
  account or the child. The parent's phone sends them too once the parent signs in. Without a connected
  parent's phone nothing is sent, and crash reports saved before connecting are deleted, not sent;
- the Nour Time settings the parent can change remotely (time budget, lock period, lock type, bedtime,
  daily reset, limited apps and apps allowed during the lock, Learning Hub and voice settings);
- the Learning Hub minutes: the child's banked minutes, the minutes earned today, and a lock that is
  paused during a learning break.
- "Ask for more time" requests the child sends from the "Time's up" screen: only when the request
  was made and the parent's answer (approved with how many minutes, or not now), no message text.

The parent's Google account name and email are stored with the connection in Cloud Firestore and on the
child's phone (so the child's phone can show who wants to connect and who is connected); the parent's phone
stores them too, with and the commands the parent sends (extra time, lock now, end the lock, approving a new PIN). The child's
phone uses an anonymous Firebase account that contains no personal information.

**Notifications on the parent's phone.** While the parent is signed in, the parent's phone registers a
Firebase Cloud Messaging token (a random identifier for that app installation) so Nour Time's server can
tell it when the child asks for more time, when a protection alert appears, and when a new PIN waits for
approval. A notification carries only the kind of event, the name and Nour Time's id of the child's
phone, the parent's account id (so a phone that changed hands shows only the signed-in parent's
notifications) and, for a waiting PIN, when it starts working; the text is written on the parent's phone.
The token is deleted when the parent signs out (if the phone is offline then, it ignores any notification
for an account that is no longer signed in) or deletes the account. The child's phone receives no
notifications from the server and registers no token.

**"Stopped checking in" notifications.** Nour Time's server checks when each connected child's phone last
updated its timer status (the time already sent above). If a phone hasn't for 12 hours, the parent gets one
notification for that silence. No new information is collected for this.

Not sent, ever: the child's name, age group or gender, the PIN or the security answer, what is shown on
the screen or typed, location, contacts, photos or messages.

Data is encrypted in transit (HTTPS). It is kept only while the phones are connected, and per-day
usage is kept for the last 14 days (for the parent's weekly report); see "Deleting data". On the child's
phone, per-day usage is kept for 30 days.

## What the app does not do

- Without a connected parent's phone, it sends nothing off the device.
- It contains no ads and no analytics or tracking (crash reports from connected phones, described
  above, are only used to fix crashes).
- It does not collect the child's name, photos, location, contacts or messages.
- Data is excluded from cloud backup and device-to-device transfer.

## Permissions

The app explains each permission before asking for it: Accessibility service, Usage access, Display
over other apps, Device admin and Notifications. All of them serve the time limit and protecting it
from being turned off. Each can be revoked in the phone's settings. Setup also asks the parent to take
Nour Time off the phone's battery optimization list, in the phone's own settings (no permission is
requested for this).

## Deleting data

Uninstalling Nour Time deletes all of its data on that phone. The parent can uninstall from Nour Time's
Settings → Uninstall Nour Time (after the security question).

When a parent's phone is connected, the child's phone's copy in Firebase (status, usage, app list,
settings and commands) is deleted, together with its anonymous account, when:

- the child's phone is disconnected (Settings → Parent's phone → Disconnect);
- the parent removes the phone from their list, or deletes their account (the child's phone deletes
  its data the next time it is online);
- Nour Time is uninstalled from Settings → Uninstall Nour Time while the phone is online.

**Deleting the parent's account:** on the parent's phone, open Nour Time → *Delete my account*. This
deletes the parent's account and the commands they sent, and disconnects every child's phone (which
then deletes its own data as above). Without the app, ask for deletion at
[account-deletion page URL] or by email to [contact email]; requests are handled within 30 days.

If a phone was offline or uninstalled without the app's own uninstall button, its copy may stay in
Firebase; ask for its deletion as above.

## Children

Nour Time is meant to be installed and set up by a parent or guardian. The child sees the "Time's up"
screens and can play the Learning Hub games on them. The child's name, age, photos, location,
contacts and messages are never collected.

Some information about the child's use of the phone is collected only if the parent connects their own
phone: the usage, timer, app list, settings and Learning Hub minutes listed above. Under children's
privacy laws (such as COPPA in the US and the GDPR in Europe) this counts as personal information about
the child. It is collected only with the parent's consent: the parent connects the phones themselves and
confirms the connection on the child's phone behind the parent PIN. The parent can stop it at any time
by disconnecting the phone, and can have it deleted as described in "Deleting data". It is used only to
show the parent their child's screen time and to let the parent change the settings, never for ads,
analytics or any other purpose, and it is never sold or shared with anyone else.

## Changes

If a future version changes what is sent off the device, this policy will be updated before that
version is released, and the app will ask for consent where required.

## Contact

[contact email]

---

# سياسة الخصوصية – وقت نور (مسودة)

يساعد وقت نور الأهل على تحديد وقت طفلهم على التطبيقات التي يختارونها. **ما لم يربط أحد الوالدين هاتفه،
يبقى كل شيء على الجهاز. لا يبيع وقت نور أي بيانات، ولا يعرض إعلانات، ولا يستخدم أدوات تحليل.**

- **التطبيقات الظاهرة على الشاشة:** يعرف وقت نور اسم حزمة التطبيق المفتوح فقط (بما في ذلك الصورة داخل
  الصورة وتقسيم الشاشة) ليحسب الوقت على التطبيقات المختارة ويعرض شاشة "خلص الوقت". لا يقرأ أبدًا محتوى
  الشاشة ولا ما يُكتب.
- **صوت التطبيقات المحدودة في الخلفية:** تتلقى خدمة إمكانية الوصول إشعارات التطبيقات التي يحدّها ولي الأمر،
  وتأخذ من إشعارات الوسائط أداة التحكم في التشغيل فقط، ليوقف وقت نور موسيقاها أو الفيديو أثناء القفل ويحتسبه
  استخدامًا ما دامت الشاشة مضاءة. لا يقرأ نص الإشعارات أبدًا، ويتجاهل إشعارات التطبيقات الأخرى، ويبقى ذلك على الجهاز.
- **مدة الاستخدام اليومية لكل تطبيق:** تُحفظ على الجهاز فقط لتظهر للأهل.
- **رمز الأهل وإجابة سؤال الأمان:** يُحفظان بصيغة مشفرة أحادية الاتجاه فقط.
- **عند ربط هاتف ولي الأمر (اختياري):** بعد موافقة ولي الأمر على هاتف الطفل، يرسل هاتف الطفل إلى Google
  Firebase ما يلي فقط، ولا يراه إلا حساب ولي الأمر: اسم الهاتف (الشركة المصنّعة والطراز)، وحالة المؤقت (الوقت المتبقي والقفل وحالة
  الحماية)، ودقائق اليوم لكل تطبيق محدد، وأسماء التطبيقات القابلة للفتح، وإعدادات وقت نور، ودقائق ركن التعلّم
  (الرصيد المدّخر ودقائق اليوم المكتسبة)، وتنبيهات الحماية (إعادة تشغيل الهاتف دون وقت نور، أو الانتقال إلى مستخدم
  آخر، أو تفعيل تصحيح USB، أو إيقاف إشعارات وقت نور، أو إعادة تعيين الرمز بسؤال الأمان وموعد بدء الرمز
  الجديد: وقت الحدث فقط)، وتقارير الأعطال إن توقف وقت نور فجأة (موضع الخطأ في كود وقت نور، وإصدار
  Android وطراز الهاتف، إلى Google Firebase Crashlytics لإصلاحه، دون ربطها بحساب ولي الأمر أو بالطفل؛ ويرسلها هاتف
  ولي الأمر أيضًا بعد تسجيل الدخول، ولا يُرسل شيء منها قبل الربط)، وطلبات "أريد وقتًا إضافيًا" التي يرسلها الطفل من شاشة "خلص الوقت" (وقت
  الطلب ورد ولي الأمر فقط، دون أي نص). يحفظ هاتف ولي الأمر اسم حساب Google وبريده، ومعرّف إشعارات (Firebase Cloud Messaging) ما دام مسجّلًا
  للدخول، ليُخبره خادم وقت نور عندما يطلب الطفل وقتًا إضافيًا أو يظهر تنبيه حماية أو ينتظر رمز جديد موافقته؛ لا يحمل
  الإشعار إلا نوع الحدث واسم هاتف الطفل ومعرّفه في وقت نور ومعرّف حساب ولي الأمر (فلا يعرض الهاتف إلا إشعارات
  الحساب المسجّل فيه)، وموعد بدء الرمز الجديد إن وُجد، ويُحذف المعرّف عند تسجيل الخروج (وإن كان الهاتف بلا اتصال
  حينها فإنه يتجاهل إشعارات أي حساب لم يعد مسجّلًا) أو حذف الحساب. لا يتلقى هاتف الطفل أي
  إشعارات من الخادم. لا يُرسل أبدًا اسم الطفل ولا
  عمره ولا الرمز ولا محتوى الشاشة ولا الموقع.
- **الأطفال:** تُعدّ هذه المعلومات عن استخدام الطفل للهاتف معلومات شخصية عنه وفق قوانين خصوصية الأطفال، ولذلك لا
  تُجمع إلا بموافقة ولي الأمر: هو من يربط الهاتفين ويؤكد الربط على هاتف الطفل بعد إدخال رمز الأهل، ويستطيع إيقافها
  في أي وقت بإلغاء الربط، وطلب حذفها. لا تُستخدم إلا لعرض وقت الشاشة لولي الأمر وتغيير الإعدادات، ولا تُباع ولا
  تُشارك مع أي جهة أخرى.
- **خدمة إمكانية الوصول** تُستخدم أيضًا لعرض شاشة الرمز فوق إعدادات الهاتف ومثبّت التطبيقات حتى لا يستطيع الطفل
  إيقاف وقت نور أو حذفه، وللرجوع إلى الشاشة الرئيسية عند انتهاء الوقت.
- **الأذونات:** خدمة إمكانية الوصول، والوصول إلى بيانات الاستخدام، والظهور فوق التطبيقات، ومشرف الجهاز، والإشعارات.
  ويطلب الإعداد من ولي الأمر استثناء وقت نور من تحسين البطارية في إعدادات الهاتف نفسها (دون طلب أي إذن).
- لا إعلانات، ولا تتبع، ولا نسخ احتياطي سحابي. إلغاء الربط يوقف كل الإرسال.
- حذف التطبيق يحذف كل بياناته.

للتواصل: [البريد الإلكتروني]
