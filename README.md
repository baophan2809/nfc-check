# NFC Check

Ứng dụng Android **đọc thông tin thẻ/chip NFC ngay trên máy, hoàn toàn offline**, dùng cho mục đích cá nhân: kiểm tra xem thẻ của chính mình có hoạt động NFC không, xem thông tin công khai mà chip trả về, và đọc chip CCCD gắn chip của mình.

> **App chỉ ĐỌC dữ liệu công khai.** Không trừ tiền, không tạo giao dịch, không ghi/sửa thẻ. Chip thanh toán **không bao giờ** trả về CVV/CVV2 hay mã PIN, nên app không thể và không hiển thị các thông tin đó. App **không có quyền truy cập Internet** — dữ liệu không thể rời khỏi máy.

---

## Mục lục
- [Tính năng](#tính-năng)
- [Bảo mật & quyền riêng tư](#bảo-mật--quyền-riêng-tư)
- [Loại thẻ hỗ trợ](#loại-thẻ-hỗ-trợ)
- [Kiến trúc mã nguồn](#kiến-trúc-mã-nguồn)
- [Cách build](#cách-build)
- [Cách cài & dùng](#cách-cài--dùng)
- [Giới hạn đã biết](#giới-hạn-đã-biết)
- [Thư viện sử dụng](#thư-viện-sử-dụng)
- [Pháp lý & đạo đức](#pháp-lý--đạo-đức)

---

## Tính năng

- **Đọc thẻ thanh toán EMV không tiếp xúc** (Visa, Mastercard, JCB, UnionPay, NAPAS nội địa), kể cả thẻ ảo trong Google Wallet (trả về số token):
  - Số thẻ, ngày hết hạn, ngày hiệu lực, tên chủ thẻ (nếu chip có lưu), tổ chức thẻ.
  - Ngân hàng phát hành tra từ đầu số (BIN) bằng **bảng tra offline** gói sẵn trong app.
  - Nhật ký giao dịch lưu trên chip (nếu thẻ hỗ trợ): số tiền, ngày, loại, tiền tệ.
  - Tab **kỹ thuật**: AID, số lần nhập PIN còn lại (9F17), bộ đếm giao dịch ATC (9F36), cùng toàn bộ tag EMV đã giải mã.
  - Tab **LOG**: nhật ký lệnh/đáp ứng APDU (giống tab LOG của các app EMV reader) để tra cứu kỹ thuật.
- **Đọc chip CCCD gắn chip** (chuẩn ICAO 9303 / eMRTD):
  - Quét vùng MRZ ở **mặt sau** bằng camera (xử lý OCR **offline**) để lấy khóa mở chip; có cả chế độ nhập tay.
  - Đọc DG1 (thông tin MRZ), DG2 (ảnh chân dung), DG13 (thông tin cá nhân riêng của CCCD Việt Nam).
  - Ưu tiên giao thức **PACE**, tự lùi về **BAC** nếu chip không hỗ trợ PACE.
- **Đọc thẻ/tag NFC khác** (thẻ ra vào, xe buýt, sinh viên, tag NTAG…):
  - UID, loại chip (MIFARE Classic/Ultralight, NTAG, DESFire, ISO 15693, FeliCa…), ATQA/SAK, dung lượng.
  - Nội dung **NDEF** (link, text) nếu có.
- **Lịch sử quét** lưu ngay trên máy, **mã hóa** bằng khóa trong phần cứng bảo mật.
- **Xuất kết quả** ra file **JSON** hoặc **TXT**, có tùy chọn **che số thẻ** khi xuất.
- Giao diện **tiếng Việt**, nền tối, Material 3.

---

## Bảo mật & quyền riêng tư

Đây là trọng tâm của app:

| Cơ chế | Mô tả |
|---|---|
| **Không có quyền Internet** | `AndroidManifest.xml` cố ý **không** khai báo `android.permission.INTERNET`. Hệ điều hành chặn mọi kết nối mạng → dữ liệu thẻ không thể bị gửi đi đâu. |
| **Che toàn bộ số thẻ** | Mặc định số thẻ/CCCD bị che hết (`•••• •••• •••• ••••`). |
| **Mở bằng 4 số cuối** | Muốn hiện đầy đủ phải nhập đúng **4 số cuối in trên thẻ**. |
| **Sai 1 lần là xóa** | Trong **lịch sử**, nếu nhập sai 4 số cuối **dù chỉ 1 lần**, bản ghi đó bị **xóa vĩnh viễn** ngay. |
| **Mã hóa khi lưu** | Mỗi bản ghi lịch sử được mã hóa **AES-256-GCM**; khóa nằm trong **Android Keystore** (không rời khỏi chip bảo mật của máy). Chỉ mục chỉ chứa thông tin không nhạy cảm (loại thẻ, thời gian, tổ chức, ngân hàng) — **không** chứa số thẻ. |
| **Chặn chụp màn hình** | Bật `FLAG_SECURE`: không chụp/quay được màn hình, và nội dung bị ẩn trong danh sách app gần đây. |
| **Không sao lưu đám mây** | `allowBackup=false` và quy tắc loại trừ sao lưu → dữ liệu không bị đẩy lên Google backup. |
| **Che khi xuất** | Khi xuất JSON/TXT có thể chọn che số; ảnh chân dung CCCD không bao giờ xuất ra text. |

**Lưu ý về thẻ trong Google Wallet:** số đọc được là **số token (DPAN)**, khác số in trên thẻ nhựa. Vì vậy "4 số cuối" để mở khóa cũng là 4 số cuối của token (xem trong Google Wallet → chi tiết thẻ → số tài khoản ảo), không phải 4 số trên thẻ vật lý.

---

## Loại thẻ hỗ trợ

| Nhóm | Ví dụ | Đọc được gì |
|---|---|---|
| Thẻ thanh toán quốc tế | Visa, Mastercard, JCB, UnionPay | Số thẻ, hết hạn, tên, lịch sử giao dịch, tag kỹ thuật |
| Thẻ nội địa | NAPAS (9704xx) | Tương tự, kèm tên ngân hàng từ BIN |
| Ví điện tử | Thẻ trong Google Wallet | Số token, AID, loại thẻ |
| Giấy tờ tùy thân | CCCD gắn chip | MRZ, ảnh chân dung, thông tin cá nhân |
| Thẻ/tag khác | MIFARE, NTAG, DESFire, FeliCa, ISO 15693 | UID, loại chip, NDEF |

---

## Kiến trúc mã nguồn

```
app/src/main/java/com/baophan/nfccheck/
├── NfcCheckApp.kt          # Application: đăng ký provider mật mã cho JMRTD
├── MainActivity.kt         # Host Compose + NFC reader mode + FLAG_SECURE
├── emv/                    # Đọc thẻ thanh toán EMV
│   ├── Tlv.kt              #   bộ phân tích BER-TLV
│   ├── EmvTags.kt          #   tên các tag EMV
│   ├── EmvReader.kt        #   PPSE → SELECT AID → GPO → READ RECORD → GET DATA
│   ├── BinLookup.kt        #   tra tổ chức thẻ + ngân hàng (offline)
│   ├── CurrencyCodes.kt    #   mã tiền tệ ISO 4217
│   └── EmvModels.kt        #   mô hình dữ liệu
├── idcard/                 # Đọc chip CCCD (ICAO 9303)
│   ├── MrzUtil.kt          #   tách MRZ từ text OCR
│   ├── IdCardReader.kt     #   PACE/BAC + đọc DG1/DG2/DG13 (JMRTD)
│   └── IdCardModels.kt
├── nfctag/                 # Đọc thẻ/tag NFC khác
│   ├── NfcTagReader.kt     #   UID, loại chip, NDEF
│   └── NfcTagModels.kt
├── storage/                # Lưu trữ & xuất
│   ├── Crypto.kt           #   AES-GCM qua Android Keystore
│   ├── HistoryStore.kt     #   lịch sử mã hóa + khóa 4 số cuối + xóa khi sai
│   ├── ScanJson.kt         #   (de)serialize JSON
│   └── Exporter.kt         #   xuất JSON/TXT (có che)
├── model/ScanResult.kt     # mô hình kết quả thống nhất
├── util/                   # Hex, Mask
└── ui/                     # Giao diện Jetpack Compose
    ├── Theme.kt, Components.kt
    ├── MainViewModel.kt    #   điều phối đọc thẻ theo chế độ
    ├── HomeScreen.kt       #   màn hình chính + chọn chế độ + xuất
    ├── ResultView.kt       #   hiển thị kết quả, che/mở số
    ├── HistoryScreen.kt    #   lịch sử + mở khóa + xóa
    └── MrzScanScreen.kt    #   camera quét MRZ + nhập tay
```

**Luồng đọc:** `MainActivity` bật chế độ NFC reader → khi chạm thẻ, `MainViewModel.onTag()` chọn bộ đọc theo chế độ (Tự động / Thẻ thanh toán / CCCD / Thẻ khác) → đọc trên luồng nền → lưu mã hóa vào lịch sử → hiển thị.

---

## Cách build

### Cách 1 — GitHub Actions (khuyến nghị, không cần cài gì)
Repo đã có sẵn workflow `.github/workflows/build.yml`. Mỗi lần push lên nhánh `main`/`master` (hoặc bấm **Run workflow**), GitHub sẽ tự build và cho tải **APK** ở mục **Actions → lần chạy → Artifacts → `nfc-check-debug-apk`**.

### Cách 2 — Android Studio
1. Mở thư mục dự án bằng **Android Studio** (bản mới, hỗ trợ AGP 8.5).
2. Chờ Gradle sync (Android Studio tự tạo Gradle wrapper và tải SDK cần thiết).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, hoặc chạy thẳng lên máy đã cắm (`Run`).

### Cách 3 — Dòng lệnh
```bash
# cần JDK 17 + Android SDK (compileSdk 34), Gradle 8.9
gradle assembleDebug
# APK nằm ở: app/build/outputs/apk/debug/app-debug.apk
```

- **minSdk 26** (Android 8.0) • **targetSdk 34** • Kotlin 2.0.21 • Jetpack Compose.

---

## Cách cài & dùng

1. **Cài APK:** bật "Cài đặt từ nguồn không xác định" cho trình quản lý file, mở `app-debug.apk` để cài. (Trên Realme Neo 8 bản nội địa, nếu bị chặn thì vào **Cài đặt → Quyền riêng tư/An toàn** cho phép cài từ nguồn tương ứng.)
2. **Bật NFC:** Cài đặt → NFC.
3. **Chọn chế độ đọc** ở đầu màn hình:
   - **Tự động:** thử thẻ thanh toán trước, không được thì đọc như tag thường.
   - **Thẻ thanh toán / Thẻ khác:** chạm thẻ vào lưng máy, giữ yên vài giây.
   - **CCCD:** bấm **Quét MRZ mặt sau** → đưa vùng 3 dòng ký tự ở mặt sau CCCD vào khung camera (hoặc **Nhập tay**) → sau khi có khóa, áp **mặt sau** CCCD vào lưng máy và giữ yên.
4. **Xem số đầy đủ:** chạm biểu tượng con mắt, nhập **4 số cuối** của thẻ.
5. **Xuất/ lưu:** sau khi quét, bấm **JSON**/**TXT** (có ô "che số thẻ"). Mọi lần quét được lưu vào **Lịch sử** (biểu tượng đồng hồ góc trên).

> Mẹo: ăng-ten NFC mỗi máy nằm một chỗ (thường gần camera hoặc giữa lưng). Nếu đọc không ra, xê dịch vị trí thẻ một chút rồi thử lại.

---

## Giới hạn đã biết

- **CVV/CVV2 và PIN:** không đọc được — chip không lưu/không trả về các giá trị này. Đây là giới hạn của chuẩn thẻ, không phải của app.
- **iOS:** app chỉ dành cho Android. iOS giới hạn app bên thứ ba đọc chip thẻ thanh toán.
- **Nhật ký giao dịch trên chip:** nhiều thẻ (nhất là thẻ token trong ví) không bật tính năng này, nên tab Giao dịch có thể trống.
- **Ảnh chân dung CCCD:** thường ở định dạng JPEG2000; app giải mã bằng thư viện gói sẵn. Một số chip/ROM có thể không giải mã được ảnh — khi đó app vẫn đọc các thông tin còn lại.
- **DG13 (CCCD):** định dạng riêng của Bộ Công an, app trích text ở mức "tốt nhất có thể"; một số trường có thể hiển thị thô.
- **Google Wallet:** mỗi máy tạo token khác nhau cho cùng một thẻ, nên dữ liệu đọc ở hai máy có thể khác nhau — điều này là bình thường.
- **ROM nội địa (Realme Neo 8):** việc đọc thẻ qua NFC không phụ thuộc Google Play Services nên vẫn chạy; OCR MRZ dùng model ML Kit **gói sẵn**, không cần GMS.

---

## Thư viện sử dụng

- **Jetpack Compose + Material 3** — giao diện.
- **CameraX** — xem trước camera để quét MRZ.
- **ML Kit Text Recognition / Barcode (bản bundled)** — OCR offline, không cần Google Play Services.
- **JMRTD + SCUBA + SpongyCastle** — đọc chip eMRTD (CCCD) theo ICAO 9303.
- **Gemalto JP2 decoder** — giải mã ảnh chân dung JPEG2000.
- Bộ đọc EMV và BER-TLV được **tự viết** bằng các lệnh APDU tiêu chuẩn.

---

## Pháp lý & đạo đức

- App dành cho việc **kiểm tra thẻ của chính bạn**. Chỉ quét thẻ/giấy tờ mà bạn sở hữu hoặc được phép.
- App **chỉ đọc dữ liệu công khai** chip tự trả về, giống các app EMV reader mã nguồn mở đã có trên cửa hàng; nó không bẻ khóa, không giải mã dữ liệu được bảo vệ, không tạo giao dịch.
- Dữ liệu đọc được (số thẻ, thông tin CCCD) là **nhạy cảm** — hãy giữ máy an toàn, đừng chia sẻ ảnh chụp màn hình hay file xuất cho người khác.
- Bạn tự chịu trách nhiệm tuân thủ pháp luật nơi mình sống khi sử dụng.
