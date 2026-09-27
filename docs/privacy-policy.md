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
  and split-screen windows. This is used only to count time on the apps the parent selected and to
  show the "Time's up" screen. Nour Time never reads the text or content of any screen, or anything
  typed.
- **Time used per app per day.** Stored on the device to show the parent today's usage.
- **Settings.** The selected apps, time budget, lock period, schedule, bedtime, the child's age group
  and whether messages should address a boy or a girl.
- **Parent PIN and security question.** The PIN and the answer are stored only as salted one-way
  hashes. The question text is stored as written.

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
- the Nour Time settings the parent can change remotely (time budget, lock period, lock type, bedtime,
  daily reset, limited apps and apps allowed during the lock).

The parent's phone stores the parent's Google account name and email (to show the child's phone who
wants to connect) and the commands the parent sends (extra time, lock now, end the lock). The child's
phone uses an anonymous Firebase account that contains no personal information.

Not sent, ever: the child's name, age group or gender, the PIN or the security answer, what is shown on
the screen or typed, location, contacts, photos or messages.

Data is encrypted in transit (HTTPS). It is kept only while the phones are connected; see "Deleting
data".

## What the app does not do

- Without a connected parent's phone, it sends nothing off the device.
- It contains no ads and no analytics or tracking.
- It does not collect the child's name, photos, location, contacts or messages.
- Data is excluded from cloud backup and device-to-device transfer.

## Permissions

The app explains each permission before asking for it: Accessibility service, Usage access, Display
over other apps, Device admin, Notifications, and running without battery restrictions. All of them
serve the time limit and protecting it from being turned off. Each can be revoked in the phone's
settings.

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

Nour Time is meant to be installed and set up by a parent or guardian. The child only sees the
"Time's up" screens. No personal information about the child is collected.

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
- **مدة الاستخدام اليومية لكل تطبيق:** تُحفظ على الجهاز فقط لتظهر للأهل.
- **رمز الأهل وإجابة سؤال الأمان:** يُحفظان بصيغة مشفرة أحادية الاتجاه فقط.
- **عند ربط هاتف ولي الأمر (اختياري):** بعد موافقة ولي الأمر على هاتف الطفل، يرسل هاتف الطفل إلى Google
  Firebase ما يلي فقط، ولا يراه إلا حساب ولي الأمر: طراز الهاتف، وحالة المؤقت (الوقت المتبقي والقفل وحالة
  الحماية)، ودقائق اليوم لكل تطبيق محدد، وأسماء التطبيقات القابلة للفتح، وإعدادات وقت نور. يحفظ هاتف ولي الأمر
  اسم حساب Google وبريده. لا يُرسل أبدًا اسم الطفل ولا عمره ولا الرمز ولا محتوى الشاشة ولا الموقع.
- لا إعلانات، ولا تتبع، ولا نسخ احتياطي سحابي. إلغاء الربط يوقف كل الإرسال.
- حذف التطبيق يحذف كل بياناته.

للتواصل: [البريد الإلكتروني]
