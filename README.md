# hello_apk1

A new Flutter project.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
需求書
幫我寫一個可以於android 新手機(android 14 例如 redmi Note14 有拍照三倍放大無損的功能)使用的功能，然後最後給我apk，所有介面及我們的溝通請都使用繁體中文：
1.系統名稱: 賽鴿虹膜建檔系統 鴿會名稱: 大鴻 (pama0:大鴻)
2.APP基本功能說明
本系統先以最簡單可以獨立運作為主，只要有賽鴿UID 就可以作業，待功能完成後再保留逐步升級功能，例如每個UID 都有對應的鴿舍代碼及環號
*a 提供系統預設參數(pama1~n)，參數可以調整，例如鏡頭放大功能(pama1:3x)...之後打開APP都以調整後為主
*b 主流程，先以usb-hid感應取得鴿環的uid後，打開主鏡頭預覽，並同時打開內建閃光燈(長亮)，等待當賽鴿的眼睛就定位且大致穩定0.5秒(pama2:0.5秒)後出現綠框框系統將自動拍照，當自動拍照完成後，請語音念好(pama3:好) 、提供預覽約10秒(pama4:10秒)並將檔案存在Android手機的某個路徑(pama5:此路徑將可以讓google 相簿自動同步到雲端) 檔名以uid+日期時間方便辨識為原則；如果照片模糊原則可以(pama6:y)繼續拍，也就是同一個uid 可以存在多張照片，直到感應下一個uid then go top
*c 照片以1:1(pama7:1:1)為主，及提供各種參數如大小、壓縮品質等
*d 照片浮水印左下角UID 右下角日期時間
*e 本系統之須將照片存於本機即可，將由相簿功能內建機制自動同步備份到雲端
*f 本系統將有10台android 同時作業，希望讓整個流程自動化，讓鴿友盡量以自助式的方式進行作業減少工作人員介入，因鴿友通常年紀較大，所以盡量提供語音互動，且音量盡量提高；若系統能自動判斷照片是否模糊則提供語音服務，如不行原則讓作業人員或鴿友判斷是否重拍。其他如即使拍照模糊仍存檔原則不須詢問。預覽過程如果有usb-hid感應則立即中斷預覽以繼續。
*g 希望每個手機有基本統計uid數量等功能
*h 開發原則以flutter 為主，其他請自行建議，例如判斷眼睛的方式跟一般手機內建拍照的功能比較不一樣的是出現眼睛時需要能框出眼睛的部位等等。
*i 以上未列的功能或相關的init 或 agents.md 可以自行提供建議或直接產生


尚未完成或確認的問題
q1.主流程怪怪的，不合需求 ref 請參考最後apk 給我的版本 https://photos.app.goo.gl/hTipzftqTRePVshP9 
*輸入請以感應UID為主 (若以輸入UID的方式只適合測試...但這可能造成混亂...應沒有必要，請改善)
*感應後開始判斷眼睛是否已經於鏡頭前面(此時再請增加一個圓形或方形大約能佔整個螢幕的9成(希望這也列入參數化)作為判斷的範圍，以讓鴿友清楚訊息，快速推進作業。
*當於以上範圍框偵測有眼睛或虹膜時才開始自動判斷取鏡是否已經穩定(穩定前後應該出現不同顏色的框框)再依據條件拍照
* 請盡量讓鴿友於拍照時不容易將鴿眼定到位置的情況下多點協助(最好包含聲音及不同的顏色框)
* 通常感應1次 拍照1次，系統參數”重拍”預設= n ，當=y 時才可免再感應uid再重拍的行為。

q2. 手機將以橫向方式擺設左邊為READER 右下方為主鏡頭，以便鴿友操作方式
*  首頁：目前統計介面會擋到參數設定(是否調整參數開始時點選齒輪才出現)
*  功能頁面，當進入作業主流程後，請不要跳回主頁面，統計的數據也請精簡顯示到某個地方若不行也可考慮使用通知的方式(參數化)，應避免開始大量作業期間跳來跳去，請再調整

q3. 也請列出TODOLIST並隨時更新進度，避免你一再的因為limit 中斷
q4. 其他可依據你的建議進行，但注意，於正式大量作業時以鴿友DIY 方式進行，應減少動用到作業人員，作業人員開始僅以輔助說明為主，每一個感應後到拍照的期間通常應該於10-30秒內完成，過程若鴿友連續2-3次無法順利進行或超過30秒等或非鴿友能單獨完成時則語音通知”請作業人員協助”。

